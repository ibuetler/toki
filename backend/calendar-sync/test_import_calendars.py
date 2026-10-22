import datetime as dt
import configparser
import unittest
from zoneinfo import ZoneInfo
from import_calendars import parse_feed, merge_feeds, reconcile, exclude_titles, route_sources, routed_snapshot, reconcile_users

ZONE = ZoneInfo('Europe/Zurich')
START = dt.datetime(2026, 10, 20, tzinfo=dt.timezone.utc)
END = dt.datetime(2026, 11, 1, tzinfo=dt.timezone.utc)


def feed(*events):
    return ('BEGIN:VCALENDAR\r\nVERSION:2.0\r\n' + ''.join('BEGIN:VEVENT\r\n' + e.replace('\n', '\r\n') + '\r\nEND:VEVENT\r\n' for e in events) + 'END:VCALENDAR\r\n').encode()


def parse(data, source='A'):
    return parse_feed(data, source, ZONE, START, END)


BASE = 'UID:meeting\nDTSTART:20261022T100000Z\nDTEND:20261022T110000Z\nSUMMARY:Meeting'


class FakeAPI:
    def __init__(self, records=(), fail=False):
        self.rows = list(records)
        self.writes = []
        self.fail = fail

    def records(self, owner):
        return self.rows

    def request(self, method, path, payload=None):
        if self.fail:
            raise RuntimeError('write failed')
        self.writes.append((method, path, payload))


class CalendarTests(unittest.TestCase):
    def test_duplicate_meeting_once_with_both_sources(self):
        result = merge_feeds([parse(feed(BASE)), parse(feed(BASE), 'B')])
        self.assertEqual(1, len(result))
        self.assertEqual('A · B', next(iter(result.values()))['source_names'])

    def test_exact_title_exclusion_ignores_case_and_outer_space(self):
        desired = merge_feeds([parse(feed(BASE))])
        self.assertEqual({}, exclude_titles(desired, ['  MEETING  ', '']))
        self.assertEqual(desired, exclude_titles(desired, ['Meet', 'Other meeting']))
        self.assertEqual(desired, exclude_titles(desired, []))

    def test_exclusion_after_merge_removes_all_source_copies(self):
        desired = merge_feeds([parse(feed(BASE)), parse(feed(BASE), 'B')])
        self.assertEqual({}, exclude_titles(desired, ['Meeting']))

    def test_excluded_import_is_deleted_and_rule_removal_restores_it(self):
        desired = merge_feeds([parse(feed(BASE))])
        row = dict(next(iter(desired.values())), id='a' * 15, owner='u' * 15)
        api = FakeAPI([row])
        counts = reconcile(api, 'u' * 15, exclude_titles(desired, ['Meeting']), True)
        self.assertEqual(1, counts['deleted'])
        self.assertEqual('DELETE', api.writes[0][0])
        restored = reconcile(FakeAPI(), 'u' * 15, exclude_titles(desired, []))
        self.assertEqual(1, restored['created'])

    def test_calendar_users_resolve_once_and_are_required(self):
        class Users:
            def __init__(self): self.calls = []
            def resolve_user(self, email):
                self.calls.append(email)
                if email != 'ivan@example.invalid': raise ValueError('Unknown user')
                return 'u' * 15
        config = configparser.ConfigParser()
        config.read_dict({'pocketbase': {}, 'calendar:a': {'user': ' Ivan@Example.Invalid '}, 'calendar:b': {'user': 'ivan@example.invalid'}})
        api = Users()
        routes = route_sources(config, ['calendar:a', 'calendar:b'], api)
        self.assertEqual({'calendar:a': 'u' * 15, 'calendar:b': 'u' * 15}, routes)
        self.assertEqual(['ivan@example.invalid'], api.calls)
        config['calendar:b']['user'] = 'unknown@example.invalid'
        with self.assertRaises(ValueError): route_sources(config, ['calendar:a', 'calendar:b'], api)
        config['calendar:b']['user'] = ''
        with self.assertRaises(ValueError): route_sources(config, ['calendar:a', 'calendar:b'], api)

    def test_duplicate_meetings_merge_only_within_recipient(self):
        snapshots = {'a': parse(feed(BASE)), 'b': parse(feed(BASE), 'B')}
        same = routed_snapshot(snapshots, {'a': 'ivan', 'b': 'ivan'}, [])
        self.assertEqual(1, len(same['ivan']))
        self.assertEqual('A · B', next(iter(same['ivan'].values()))['source_names'])
        different = routed_snapshot(snapshots, {'a': 'ivan', 'b': 'marina'}, [])
        self.assertEqual({'ivan', 'marina'}, set(different))
        self.assertEqual('A', next(iter(different['ivan'].values()))['source_names'])
        self.assertEqual('B', next(iter(different['marina'].values()))['source_names'])
        self.assertEqual({'ivan': {}, 'marina': {}}, routed_snapshot(snapshots, {'a': 'ivan', 'b': 'marina'}, ['Meeting']))

    def test_reassignment_removes_former_recipient_copy(self):
        old_owner, new_owner = 'u' * 15, 'v' * 15
        desired = merge_feeds([parse(feed(BASE))])
        row = dict(next(iter(desired.values())), id='a' * 15, owner=old_owner)
        class RoutedAPI(FakeAPI):
            def records(self, owner=None):
                return [r for r in self.rows if owner is None or r['owner'] == owner]
        api = RoutedAPI([row])
        counts = reconcile_users(api, {new_owner: desired}, True)
        self.assertEqual(1, counts['created'])
        self.assertEqual(1, counts['deleted'])
        created = next(payload for method, _, payload in api.writes if method == 'POST')
        self.assertEqual(new_owner, created['owner'])
        self.assertTrue(any(method == 'DELETE' for method, _, _ in api.writes))

    def test_distinct_uids_not_merged_by_title(self):
        self.assertEqual(2, len(parse(feed(BASE, BASE.replace('UID:meeting', 'UID:other')))))

    def test_move_keeps_key(self):
        a = parse(feed(BASE))
        b = parse(feed(BASE.replace('20261022T100000Z', '20261023T100000Z')))
        self.assertEqual(a.keys(), b.keys())
        self.assertNotEqual(next(iter(a.values()))['payload']['start_at'], next(iter(b.values()))['payload']['start_at'])

    def test_recurrence_move_and_exclusion(self):
        recurring = BASE + '\nRRULE:FREQ=DAILY;COUNT=3\nEXDATE:20261023T100000Z'
        moved = BASE.replace('20261022T100000Z', '20261024T120000Z').replace('20261022T110000Z', '20261024T130000Z') + '\nRECURRENCE-ID:20261024T100000Z\nSEQUENCE:2'
        result = parse(feed(recurring, moved))
        self.assertEqual(2, len(result))
        self.assertIn('2026-10-24T12:00:00Z', [x['payload']['start_at'] for x in result.values()])

    def test_newer_copy_wins(self):
        old = parse(feed(BASE))
        changed = parse(feed(BASE.replace('SUMMARY:Meeting', 'SUMMARY:Changed') + '\nSEQUENCE:2'), 'B')
        self.assertEqual('Changed', next(iter(merge_feeds([old, changed]).values()))['title'])

    def test_cancellation_wins_over_stale_copy(self):
        cancelled = parse(feed(BASE + '\nSTATUS:CANCELLED\nSEQUENCE:2'), 'B')
        self.assertEqual({}, merge_feeds([parse(feed(BASE)), cancelled]))

    def test_reminders_fixed(self):
        data = feed(BASE + '\nBEGIN:VALARM\nACTION:DISPLAY\nTRIGGER:-PT30M\nDESCRIPTION:Reminder\nEND:VALARM')
        row = next(iter(merge_feeds([parse(data)]).values()))
        self.assertEqual(5, row['reminder_minutes'])
        self.assertTrue(row['enabled'])

    def test_all_day_no_alarm(self):
        data = feed('UID:day\nDTSTART;VALUE=DATE:20261022\nDTEND;VALUE=DATE:20261024\nSUMMARY:Away')
        row = next(iter(merge_feeds([parse(data)]).values()))
        self.assertTrue(row['all_day'])
        self.assertFalse(row['enabled'])
        self.assertEqual('2026-10-21T22:00:00Z', row['start_at'])

    def test_dst_wall_time(self):
        data = feed('UID:dst\nDTSTART;TZID=Europe/Zurich:20261024T090000\nRRULE:FREQ=DAILY;COUNT=3\nSUMMARY:Morning')
        rows = merge_feeds([parse(data)])
        self.assertEqual(['2026-10-24T07:00:00Z', '2026-10-25T08:00:00Z', '2026-10-26T08:00:00Z'], sorted(x['start_at'] for x in rows.values()))

    def test_bad_feed_rejected(self):
        for data in (b'<html>login</html>', feed(BASE)[:-12], feed('DTSTART:20261022T100000Z')):
            with self.assertRaises(Exception):
                parse(data)

    def test_missing_one_source_keeps_other(self):
        self.assertEqual(1, len(merge_feeds([parse(feed()), parse(feed(BASE), 'B')])))

    def test_deletion_and_dry_run(self):
        api = FakeAPI([{'external_key': 'gone', 'id': 'a' * 15}])
        self.assertEqual(1, reconcile(api, 'u' * 15, {})['deleted'])
        self.assertEqual([], api.writes)
        reconcile(api, 'u' * 15, {}, True)
        self.assertEqual('DELETE', api.writes[0][0])

    def test_failed_upsert_never_deletes(self):
        api = FakeAPI([{'external_key': 'gone', 'id': 'a' * 15}], fail=True)
        with self.assertRaises(RuntimeError):
            reconcile(api, 'u' * 15, merge_feeds([parse(feed(BASE))]), True)
        self.assertEqual([], api.writes)

    def test_idempotent_reconciliation(self):
        desired = merge_feeds([parse(feed(BASE))])
        row = dict(next(iter(desired.values())), id='a' * 15, owner='u' * 15)
        # PocketBase returns date fields with a space and fractional seconds.
        row['start_at'] = '2026-10-22 10:00:00.000Z'
        row['end_at'] = '2026-10-22 11:00:00.000Z'
        api = FakeAPI([row])
        self.assertEqual(1, reconcile(api, 'u' * 15, desired, True)['unchanged'])
        self.assertEqual([], api.writes)


if __name__ == '__main__':
    unittest.main()
