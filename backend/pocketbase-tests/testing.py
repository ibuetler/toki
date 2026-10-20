#!/usr/bin/env python3
"""PocketBase deadlines API authorization regression tests.

Uses temporary records only. Requires requests: pip install requests
"""
import argparse
import configparser
import datetime as dt
import sys
import uuid
from zoneinfo import ZoneInfo
from dateutil.rrule import rrulestr, rruleset
from dataclasses import dataclass
from pathlib import Path

import requests

ROOT = '/api/collections'

@dataclass
class Actor:
    alias: str
    email: str
    token: str
    uid: str

class Suite:
    def __init__(self, config):
        self.base = config['pocketbase']['url'].rstrip('/')
        self.timeout = config.getfloat('pocketbase', 'timeout', fallback=15)
        self.session = requests.Session()
        self.actors = {}
        self.passed = 0
        self.failed = 0
        self.created = []
        self.cfg = config
        self.tag = 'PBSEC-' + uuid.uuid4().hex[:12]

    def request(self, method, path, actor=None, payload=None, params=None):
        headers = {}
        if actor:
            headers['Authorization'] = 'Bearer ' + actor.token
        try:
            response = self.session.request(method, self.base + path,
                headers=headers, json=payload, params=params, timeout=self.timeout)
            try:
                data = response.json()
            except ValueError:
                data = {}
            return response.status_code, data
        except requests.RequestException as exc:
            return 0, {'message': str(exc)}

    def list_record(self, rid, actor=None):
        # Filter by the known temporary ID so pagination cannot hide the result.
        return self.request('GET', f'{ROOT}/deadlines/records', actor,
                            params={'filter': f'id = "{rid}"', 'perPage': 1})

    def track_created(self, data, owner):
        if data.get('id'):
            self.created.append((owner, data['id']))

    def check(self, label, actual, expected, extra_ok=True, detail=''):
        ok = actual in expected and extra_ok
        self.passed += int(ok)
        self.failed += int(not ok)
        print(f"[{'PASS' if ok else 'FAIL'}] {label}: HTTP {actual}, expected {sorted(expected)}" +
              (f' ({detail})' if detail else ''))
        return ok

    def authenticate(self, alias):
        email = self.cfg['users'][alias]
        password = self.cfg['passwords'][alias]
        code, data = self.request('POST', f'{ROOT}/users/auth-with-password',
                                  payload={'identity': email, 'password': password})
        good = code == 200 and bool(data.get('token')) and bool(data.get('record', {}).get('id'))
        self.check(f'{alias}: password authentication', code, {200}, good,
                   '' if good else data.get('message', 'missing token or user ID'))
        if good:
            self.actors[alias] = Actor(alias, email, data['token'], data['record']['id'])
        return good

    def new_deadline(self, owner, title):
        # A future one-off test record, deliberately not a real meeting.
        start = (dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S.000Z')
        return dict(title=title, description='Automated authorization test; safe to delete',
                    owner=owner, start_at=start, timezone='Europe/Zurich', rrule='',
                    duration_minutes=30, reminder_minutes=5, all_day=False, enabled=False)

    def create(self, actor, title):
        code, data = self.request('POST', f'{ROOT}/deadlines/records', actor,
                                  self.new_deadline(actor.uid, title))
        rid = data.get('id')
        good = bool(rid) and data.get('owner') == actor.uid
        self.check(f'{actor.alias}: create own deadline', code, {200}, good,
                   data.get('message', '') if not good else '')
        self.track_created(data, actor)
        return rid if good else None

    def run(self, aliases, attackers):
        for alias in aliases:
            self.authenticate(alias)
        if not self.actors:
            return
        records = {}
        for actor in self.actors.values():
            records[actor.alias] = self.create(actor, self.tag + '-' + actor.alias)

        for actor in self.actors.values():
            rid = records.get(actor.alias)
            if not rid:
                continue
            base = f'{ROOT}/deadlines/records'
            code, data = self.request('GET', f'{base}/{rid}', actor)
            self.check(f'{actor.alias}: view own record', code, {200},
                       data.get('id') == rid)
            code, data = self.list_record(rid, actor)
            ids = {x['id'] for x in data.get('items', []) if 'id' in x}
            self.check(f'{actor.alias}: list includes own record', code, {200}, rid in ids)
            code, data = self.request('PATCH', f'{base}/{rid}', actor,
                                      {'description': 'Updated by test suite'})
            self.check(f'{actor.alias}: update own record', code, {200},
                       data.get('description') == 'Updated by test suite')
            for target in self.actors.values():
                if target.uid == actor.uid:
                    continue
                code, data = self.request('PATCH', f'{base}/{rid}', actor,
                                          {'owner': target.uid})
                self.check(f'{actor.alias}: cannot transfer owner to {target.alias}',
                           code, {400, 403, 404})

            # Another user's ID must not be visible, even if guessed.
            for other in self.actors.values():
                if other.alias == actor.alias or other.alias not in attackers:
                    continue
                code, data = self.request('GET', f'{base}/{rid}', other)
                self.check(f'{other.alias}: cannot view {actor.alias} record', code, {404})
                code, data = self.request('PATCH', f'{base}/{rid}', other,
                                          {'title': 'ATTACKED'})
                self.check(f'{other.alias}: cannot modify {actor.alias} record', code, {404})
                code, data = self.request('DELETE', f'{base}/{rid}', other)
                self.check(f'{other.alias}: cannot delete {actor.alias} record', code, {404})
                code, data = self.request('POST', base, other,
                                          self.new_deadline(actor.uid, self.tag + '-spoof'))
                self.track_created(data, actor)
                self.check(f'{other.alias}: cannot create for {actor.alias}', code,
                           {400, 403, 404})
                code, data = self.list_record(rid, other)
                other_ids = {x['id'] for x in data.get('items', []) if 'id' in x}
                self.check(f'{other.alias}: list excludes {actor.alias} record', code,
                           {200}, rid not in other_ids)

            code, data = self.request('GET', f'{base}/{rid}')
            self.check('anonymous: cannot view record', code, {403, 404})
            code, data = self.request('PATCH', f'{base}/{rid}',
                                      payload={'title': 'ATTACKED'})
            self.check('anonymous: cannot modify record', code, {403, 404})
            code, data = self.request('DELETE', f'{base}/{rid}')
            self.check('anonymous: cannot delete record', code, {403, 404})
            code, data = self.request('POST', base,
                                      payload=self.new_deadline(actor.uid, self.tag + '-anonymous'))
            self.track_created(data, actor)
            self.check('anonymous: cannot create record', code, {400, 403, 404})
            code, data = self.list_record(rid)
            self.check('anonymous: list cannot expose record', code, {200, 403, 404},
                       code != 200 or data.get('items') == [])

            # Check ownership and contents remain intact before cleanup.
            code, data = self.request('GET', f'{base}/{rid}', actor)
            self.check(f'{actor.alias}: record unchanged by attacks', code, {200},
                       data.get('owner') == actor.uid and data.get('title') == self.tag + '-' + actor.alias)

    def recurrence_tests(self, attackers):
        base = f'{ROOT}/deadlines/records'
        exclusions = ['2026-11-02T09:00:00']
        additions = ['2026-11-04T09:00:00']
        for actor in self.actors.values():
            rid = self.create(actor, self.tag + '-recurrence-' + actor.alias)
            if not rid:
                continue
            payload = {'start_at': '2026-10-26 08:00:00.000Z',
                       'timezone': 'Europe/Zurich', 'rrule': 'FREQ=WEEKLY;BYDAY=MO',
                       'exdates': exclusions, 'rdates': additions}
            code, data = self.request('PATCH', f'{base}/{rid}', actor, payload)
            self.check(f'{actor.alias}: save recurrence exceptions', code, {200},
                       data.get('exdates') == exclusions and data.get('rdates') == additions)
            code, data = self.request('GET', f'{base}/{rid}', actor)
            self.check(f'{actor.alias}: read recurrence exceptions', code, {200},
                       data.get('exdates') == exclusions and data.get('rdates') == additions
                       and data.get('rrule') == payload['rrule'])
            for other in self.actors.values():
                if other.alias == actor.alias or other.alias not in attackers:
                    continue
                code, data = self.request('PATCH', f'{base}/{rid}', other,
                                          {'exdates': [], 'rdates': []})
                self.check(f'{other.alias}: cannot alter {actor.alias} recurrence', code, {404})
            code, data = self.request('GET', f'{base}/{rid}', actor)
            self.check(f'{actor.alias}: recurrence intact after attacks', code, {200},
                       data.get('exdates') == exclusions and data.get('rdates') == additions)
            code, data = self.request('PATCH', f'{base}/{rid}', actor,
                                      {'exdates': [], 'rdates': []})
            self.check(f'{actor.alias}: clear recurrence exceptions', code, {200},
                       data.get('exdates') == [] and data.get('rdates') == [])
            code, data = self.request('GET', f'{base}/{rid}', actor)
            self.check(f'{actor.alias}: cleared exceptions persist', code, {200},
                       data.get('exdates') == [] and data.get('rdates') == [])

        tz = ZoneInfo('Europe/Zurich')
        start = dt.datetime(2026, 10, 26, 9, 0, tzinfo=tz)
        schedule = rruleset()
        schedule.rrule(rrulestr('FREQ=WEEKLY;BYDAY=MO;COUNT=4', dtstart=start))
        schedule.exdate(dt.datetime(2026, 11, 2, 9, 0, tzinfo=tz))
        schedule.rdate(dt.datetime(2026, 11, 4, 9, 0, tzinfo=tz))
        actual = [x.date().isoformat() for x in schedule]
        expected = ['2026-10-26', '2026-11-04', '2026-11-09', '2026-11-16']
        self.check('RRULE engine: EXDATE and RDATE expansion',
                   200 if actual == expected else 0, {200},
                   detail=f'actual={actual}' if actual != expected else '')

        spring = list(rrulestr('FREQ=WEEKLY;COUNT=4',
                              dtstart=dt.datetime(2027, 3, 15, 9, tzinfo=tz)))
        utc_hours = [x.astimezone(dt.timezone.utc).hour for x in spring]
        good = all(x.hour == 9 for x in spring) and utc_hours == [8, 8, 7, 7]
        self.check('RRULE engine: Zurich DST preserves 09:00',
                   200 if good else 0, {200},
                   detail=f'UTC hours={utc_hours}' if not good else '')

    def cleanup(self):
        print('\nCleanup of temporary records:')
        for actor, rid in self.created:
            code, data = self.request('DELETE', f'{ROOT}/deadlines/records/{rid}', actor)
            if code != 204:
                # A successful ownership-transfer attack may have hidden it.
                for other in self.actors.values():
                    if other.uid != actor.uid:
                        retry, _ = self.request('DELETE', f'{ROOT}/deadlines/records/{rid}', other)
                        if retry == 204:
                            code = retry
                            break
            self.check(f'{actor.alias}: delete temporary record', code, {204})
            if code != 204:
                print(f'  WARNING: manually remove test record {rid} if necessary')


def main():
    parser = argparse.ArgumentParser(description='PocketBase multi-user authorization tests')
    selection = parser.add_mutually_exclusive_group(required=True)
    selection.add_argument('-user', metavar='ALIAS', help='Select attacker; all configured users are authenticated')
    selection.add_argument('-all', action='store_true', help='Test all configured users')
    parser.add_argument('-config', default=str(Path(__file__).with_name('config.ini')))
    args = parser.parse_args()
    cfg = configparser.ConfigParser(interpolation=None)
    if not cfg.read(args.config) or not cfg.has_section('users') or not cfg.has_section('passwords'):
        parser.error('Missing or invalid config.ini')
    aliases = list(cfg['users'])
    attackers = aliases if args.all else [args.user]
    if len(aliases) < 2:
        parser.error('At least two configured users are required for cross-user tests')
    if args.user and args.user not in aliases:
        parser.error(f'Unknown user alias {args.user!r}')
    for alias in aliases:
        if alias not in cfg['users'] or alias not in cfg['passwords']:
            parser.error(f'Missing user/password for alias {alias!r}')
    suite = Suite(cfg)
    try:
        suite.run(aliases, attackers)
        suite.recurrence_tests(attackers)
    finally:
        suite.cleanup()
    print(f'\nResults: {suite.passed} PASS, {suite.failed} FAIL')
    return 1 if suite.failed else 0

if __name__ == '__main__':
    sys.exit(main())
