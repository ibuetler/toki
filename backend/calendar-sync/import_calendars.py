#!/usr/bin/env python3
"""Reconcile complete ICS snapshots into private, read-only PocketBase occurrences.

Default is a dry run. No calendar content, feed URLs or credentials are logged.
"""
import argparse
import configparser
import datetime as dt
import fcntl
import hashlib
import json
import os
from pathlib import Path
import stat
import sys
from urllib.parse import urlsplit
from zoneinfo import ZoneInfo

import requests
from icalendar import Calendar
import recurring_ical_events

UTC = dt.timezone.utc
COLLECTION = 'calendar_events'
MAX_FEED_BYTES = 20_000_000


def instant(value, zone):
    if isinstance(value, dt.datetime):
        return (value if value.tzinfo else value.replace(tzinfo=zone)).astimezone(UTC)
    return dt.datetime.combine(value, dt.time(), zone).astimezone(UTC)


def stamp(value, zone):
    return instant(value, zone).isoformat(timespec='seconds').replace('+00:00', 'Z')


def event_key(uid, recurrence, zone):
    identity = stamp(recurrence, zone) if recurrence is not None else 'single'
    return hashlib.sha256((uid + '\0' + identity).encode()).hexdigest()


def parse_feed(data, source, zone, start, end, limit=20000):
    if len(data) > MAX_FEED_BYTES or not data.strip().endswith(b'END:VCALENDAR'):
        raise ValueError('Incomplete or oversized calendar')
    calendar = Calendar.from_ical(data)
    if calendar.name != 'VCALENDAR' or calendar.errors:
        raise ValueError('Invalid calendar')
    masters = {}
    recurring = set()
    for event in calendar.walk('VEVENT'):
        if event.errors or not event.get('UID') or not event.get('DTSTART'):
            raise ValueError('Invalid event; refusing partial snapshot')
        uid = str(event['UID'])
        if event.get('RRULE') or event.get('RDATE') or event.get('RECURRENCE-ID'):
            recurring.add(uid)
        if not event.get('RECURRENCE-ID'):
            masters[uid] = event
        # Unknown TZIDs must not silently become floating times.
        for field in ('DTSTART', 'DTEND', 'RECURRENCE-ID'):
            prop = event.get(field)
            if prop and prop.params.get('TZID') and isinstance(prop.dt, dt.datetime) and prop.dt.tzinfo is None:
                raise ValueError('Unresolved calendar timezone')
    result = {}
    # x-wr-timezone support plus embedded VTIMEZONE handling are provided by the libraries.
    for event in recurring_ical_events.of(calendar).between(start, end):
        if len(result) >= limit:
            raise ValueError('Occurrence limit exceeded')
        uid = str(event['UID'])
        master = masters.get(uid, event)
        cancelled = str(event.get('STATUS', master.get('STATUS', ''))).upper() == 'CANCELLED'
        began = event.decoded('DTSTART')
        all_day = not isinstance(began, dt.datetime)
        recurrence = event.decoded('RECURRENCE-ID', began) if uid in recurring else None
        key = event_key(uid, recurrence, zone)
        modified = event.decoded('LAST-MODIFIED', event.decoded('DTSTAMP', dt.datetime(1970, 1, 1, tzinfo=UTC)))
        payload = {
            'external_key': key, 'title': str(event.get('SUMMARY', 'Untitled meeting'))[:500],
            'description': str(event.get('DESCRIPTION', ''))[:20000],
            'location': str(event.get('LOCATION', ''))[:2000],
            'start_at': stamp(began, zone), 'end_at': stamp(event.decoded('DTEND', began), zone),
            'timezone': zone.key if all_day else 'UTC', 'all_day': all_day,
            'enabled': not all_day, 'reminder_minutes': 5,
            'source_names': source,
        }
        candidate = {'payload': payload, 'cancelled': cancelled,
                     'revision': (int(event.get('SEQUENCE', master.get('SEQUENCE', 0))), stamp(modified, zone)),
                     'sources': {source}}
        old = result.get(key)
        if old is None or candidate['revision'] >= old['revision']:
            result[key] = candidate
    return result


def merge_feeds(feeds):
    """A newer cancellation wins over a stale copy in a different calendar."""
    result = {}
    for feed in feeds:
        for key, entry in feed.items():
            previous = result.get(key)
            sources = entry['sources'] | (previous['sources'] if previous else set())
            # Cancellation wins ties; otherwise source order resolves identical revisions.
            if previous is None or (entry['revision'], entry['cancelled']) > (previous['revision'], previous['cancelled']):
                result[key] = dict(entry)
            result[key]['sources'] = sources
    for entry in result.values():
        entry['payload'] = dict(entry['payload'], source_names=' · '.join(sorted(entry['sources'])))
    return {key: entry['payload'] for key, entry in result.items() if not entry['cancelled']}


def exclude_titles(events, titles):
    """Filter the merged snapshot; exact title matches ignore case/outer space."""
    excluded = {title.strip().casefold() for title in titles if title.strip()}
    return {key: event for key, event in events.items()
            if event['title'].strip().casefold() not in excluded}


def fetch_feed(url):
    parsed = urlsplit(url)
    if parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password:
        raise ValueError('Calendar feeds require HTTPS')
    # Redirects are refused so a published credential cannot redirect to another service.
    with requests.get(url, timeout=(10, 60), stream=True, allow_redirects=False) as response:
        if response.status_code != 200:
            raise ValueError('Calendar fetch failed')
        parts, size = [], 0
        for chunk in response.iter_content(65536):
            size += len(chunk)
            if size > MAX_FEED_BYTES:
                raise ValueError('Calendar feed too large')
            parts.append(chunk)
        return b''.join(parts)


class PocketBase:
    def __init__(self, config):
        self.url = config['url'].rstrip('/')
        parsed = urlsplit(self.url)
        if parsed.scheme != 'https' and not (parsed.scheme == 'http' and parsed.hostname in {'127.0.0.1', 'localhost', '::1'}):
            raise ValueError('PocketBase requires HTTPS or localhost')
        self.session = requests.Session()
        result = self.request('POST', '/api/collections/_superusers/auth-with-password',
                              {'identity': config['email'], 'password': config['password']})
        self.session.headers['Authorization'] = result['token']

    def request(self, method, path, payload=None, params=None):
        response = self.session.request(method, self.url + path, json=payload, params=params, timeout=(10, 60), allow_redirects=False)
        if not 200 <= response.status_code < 300:
            # PocketBase validation bodies can contain event content; never log them.
            raise RuntimeError('PocketBase request failed (HTTP %s)' % response.status_code)
        return response.json() if response.content else None

    def resolve_user(self, email):
        email = email.strip().casefold()
        if not email or '@' not in email:
            raise ValueError('Calendar user must be a TOKI account email')
        result = self.request('GET', '/api/collections/users/records',
                              params={'filter': 'email=' + json.dumps(email), 'perPage': 2})
        matches = [row for row in result['items'] if row['email'].casefold() == email]
        if len(matches) != 1:
            raise ValueError('Calendar user must identify one existing TOKI account')
        return matches[0]['id']

    def records(self, owner=None):
        records, page = [], 1
        while True:
            result = self.request('GET', '/api/collections/calendar_events/records',
                                  params=dict({'page': page, 'perPage': 500}, **({'filter': 'owner=' + json.dumps(owner)} if owner else {})))
            records.extend(result['items'])
            if page >= result['totalPages']:
                return records
            page += 1


def reconcile(api, owner, desired, apply=False, delete_stale=True):
    if not owner.isalnum() or len(owner) != 15:
        raise ValueError('Expected a PocketBase owner record ID')
    existing = {row['external_key']: row for row in api.records(owner)}
    counts = dict(created=0, updated=0, deleted=0, unchanged=0)
    # Complete all upserts before deleting stale records. Retrying a partial write is idempotent.
    for key, payload in desired.items():
        payload = dict(payload, owner=owner)
        old = existing.get(key)
        if old is None:
            counts['created'] += 1
            if apply:
                record_id = hashlib.sha256((owner + key).encode()).hexdigest()[:15]
                api.request('POST', '/api/collections/calendar_events/records', dict(payload, id=record_id))
        elif any((stamp(dt.datetime.fromisoformat(old[field].replace('Z', '+00:00')), UTC) != value
                  if field in {'start_at', 'end_at'} else old.get(field) != value)
                 for field, value in payload.items()):
            counts['updated'] += 1
            if apply:
                api.request('PATCH', '/api/collections/calendar_events/records/' + old['id'], payload)
        else:
            counts['unchanged'] += 1
    for key, old in existing.items():
        if delete_stale and key not in desired:
            counts['deleted'] += 1
            if apply:
                api.request('DELETE', '/api/collections/calendar_events/records/' + old['id'])
    return counts


def route_sources(config, sections, api):
    """Resolve every recipient before fetching or changing any calendar records."""
    resolved, routes = {}, {}
    for section in sections:
        email = config.get(section, 'user', fallback='').strip().casefold()
        if email:
            if email not in resolved:
                resolved[email] = api.resolve_user(email)
            routes[section] = resolved[email]
        else:
            # Compatibility for existing installations with a global owner record ID.
            owner = config.get('pocketbase', 'owner', fallback='').strip()
            if not owner:
                raise ValueError('Each calendar needs a TOKI user email')
            api.request('GET', '/api/collections/users/records/' + owner)
            routes[section] = owner
    return routes


def routed_snapshot(snapshots, routes, titles):
    grouped = {}
    for section, snapshot in snapshots.items():
        grouped.setdefault(routes[section], []).append(snapshot)
    return {owner: exclude_titles(merge_feeds(feeds), titles) for owner, feeds in grouped.items()}


def reconcile_users(api, desired, apply=False):
    # One importer configuration owns this dedicated collection. Include former
    # recipients so moving/removing a user's last source removes their old copies.
    existing = api.records()
    counts = dict(created=0, updated=0, deleted=0, unchanged=0)
    # Finish upserts for every account before removing any old recipient copies.
    for owner in sorted(desired):
        result = reconcile(api, owner, desired[owner], apply, delete_stale=False)
        for key, value in result.items():
            counts[key] += value
    for row in existing:
        if row['external_key'] not in desired.get(row['owner'], {}):
            counts['deleted'] += 1
            if apply:
                api.request('DELETE', '/api/collections/calendar_events/records/' + row['id'])
    return counts


def run(config_path, apply=False):
    path = Path(config_path)
    if stat.S_IMODE(path.stat().st_mode) & 0o077:
        raise ValueError('Configuration must be private (chmod 600)')
    config = configparser.ConfigParser(interpolation=None)
    config.read(path)
    zone = ZoneInfo(config.get('sync', 'timezone', fallback='Europe/Zurich'))
    now = dt.datetime.now(UTC)
    past = config.getint('sync', 'past_days', fallback=7)
    future = config.getint('sync', 'future_days', fallback=366)
    limit = config.getint('sync', 'max_occurrences', fallback=20000)
    if not (0 <= past <= 3660 and 1 <= future <= 3660 and 1 <= limit <= 100000):
        raise ValueError('Invalid import horizon or occurrence limit')
    start, end = now - dt.timedelta(days=past), now + dt.timedelta(days=future)
    sections = [s for s in config.sections() if s.startswith('calendar:')]
    if not sections:
        raise ValueError('No calendar sources configured')
    names = [config[s]['name'] for s in sections]
    if len(set(names)) != len(names):
        raise ValueError('Calendar source names must be unique')
    api = PocketBase(config['pocketbase'])
    routes = route_sources(config, sections, api)
    # All sources and recipients must succeed before any writes for any user.
    snapshots = {s: parse_feed(fetch_feed(config[s]['url']), config[s]['name'], zone, start, end, limit) for s in sections}
    unique_count = sum(len(merge_feeds([snapshots[s] for s in sections if routes[s] == owner])) for owner in set(routes.values()))
    if unique_count > limit:
        raise ValueError('Combined occurrence limit exceeded')
    titles = config.get('exclude', 'titles', fallback='').splitlines()
    desired = routed_snapshot(snapshots, routes, titles)
    retained = sum(len(events) for events in desired.values())
    print('Validated %s feeds for %s users; %s unique occurrences; %s excluded; %s retained.' %
          (len(sections), len(desired), unique_count, unique_count - retained, retained))
    counts = reconcile_users(api, desired, apply)
    print(('Applied: ' if apply else 'Dry run: ') + json.dumps(counts, sort_keys=True))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', required=True)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--lock', help='Optional lock file in a writable state directory')
    args = parser.parse_args()
    try:
        # One writer per configuration, including manual invocations alongside systemd.
        lock_path = args.lock or str(Path(args.config).resolve()) + '.lock'
        fd = os.open(lock_path, os.O_CREAT | os.O_RDWR, 0o600)
        with os.fdopen(fd, 'w') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            run(args.config, args.apply)
    except Exception as error:
        # Exception text from parsers/HTTP clients may contain confidential feed data.
        print('Calendar sync failed (%s). Existing data may be partially updated; retry after resolving the cause. No stale records are deleted before upserts finish.' % type(error).__name__, file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
