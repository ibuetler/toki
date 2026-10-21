#!/usr/bin/env python3
"""Provision and test private birthdays on an isolated localhost toki- container only."""
import argparse
import json
from pathlib import Path
import secrets
import subprocess
from urllib.parse import urlparse
import requests
from group_tests import GroupTests


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--container', default='toki-birthday-validation')
    parser.add_argument('--url', default='http://127.0.0.1:8097')
    parser.add_argument('--update-schema', action='store_true')
    args = parser.parse_args()
    if urlparse(args.url).hostname not in {'127.0.0.1', 'localhost'} or not args.container.startswith('toki-'):
        parser.error('Only a disposable localhost toki- container is supported')
    password, email = secrets.token_urlsafe(32), secrets.token_hex(8) + '@example.com'
    subprocess.run(['docker', 'exec', args.container, '/pb/pocketbase', 'superuser', 'create', email, password], check=True, capture_output=True)
    suite = None
    try:
        response = requests.post(args.url + '/api/collections/_superusers/auth-with-password', json={'identity': email, 'password': password}, timeout=15)
        response.raise_for_status()
        suite = GroupTests(args.url, response.json()['token'])
        users = []
        for _ in range(2):
            mail, pw = secrets.token_hex(8) + '@example.com', secrets.token_urlsafe(32)
            user = suite.call('POST', 'users', suite.root, {'email': mail, 'password': pw, 'passwordConfirm': pw, 'verified': True})
            response = requests.post(args.url + '/api/collections/users/auth-with-password', json={'identity': mail, 'password': pw}, timeout=15)
            response.raise_for_status()
            users.append((user['id'], response.json()['token']))
        (owner, token), (other, outsider) = users
        payload = dict(owner=owner, name='Alex', month=2, day=29, birth_year=2000, reminder_hour=9, reminder_minute=0, leap_day_policy='feb28', enabled=True)
        record = suite.call('POST', 'birthdays', token, payload)
        rid = record['id']
        for field, value in payload.items():
            assert record[field] == value, field
        suite.call('GET', 'birthdays', token, rid=rid)
        suite.hidden('birthdays', rid, outsider)
        suite.hidden('birthdays', rid, None)
        for actor in [outsider, None]:
            suite.call('PATCH', 'birthdays', actor, {'name': 'Attack'}, rid, expected=(403, 404))
            suite.call('DELETE', 'birthdays', actor, rid=rid, expected=(403, 404))
            suite.call('POST', 'birthdays', actor, payload, expected=(400, 403, 404))
        suite.call('PATCH', 'birthdays', token, {'owner': other}, rid, expected=(400, 403, 404))
        for changes in [dict(month=2, day=30), dict(month=4, day=31), dict(month=0), dict(day=0), dict(reminder_hour=24), dict(reminder_minute=60), dict(leap_day_policy='skip')]:
            suite.call('POST', 'birthdays', token, {**payload, **changes}, expected=(400, 403))
        suite.call('PATCH', 'birthdays', token, {'month': 4, 'day': 31}, rid, expected=(400, 403, 404))
        suite.call('PATCH', 'birthdays', token, {'leap_day_policy': 'mar1', 'birth_year': 0}, rid)
        suite.call('DELETE', 'birthdays', token, rid=rid, expected=(204,))
        suite.call('GET', 'birthdays', token, rid=rid, expected=(404,))
        # Owner deletion cascades private birthdays.
        cascade = suite.call('POST', 'birthdays', token, {**payload, 'month': 10, 'day': 21})['id']
        suite.call('DELETE', 'users', suite.root, rid=owner, expected=(204,))
        suite.call('GET', 'birthdays', suite.root, rid=cascade, expected=(404,))
        if args.update_schema:
            response = requests.get(args.url + '/api/collections/birthdays', headers={'Authorization': suite.root}, timeout=15)
            response.raise_for_status()
            collection = response.json()
            allowed = {'id', 'name', 'type', 'listRule', 'viewRule', 'createRule', 'updateRule', 'deleteRule', 'fields', 'indexes'}
            collection = {k: v for k, v in collection.items() if k in allowed}
            path = Path(__file__).parents[1] / 'schema/collections.json'
            data = [c for c in json.loads(path.read_text()) if c['name'] != 'birthdays'] + [collection]
            path.write_text(json.dumps(data, indent=2) + '\n')
        print(f'Birthday authorization/schema checks: {suite.passed} PASS, 0 FAIL')
    finally:
        if suite: suite.cleanup()
        result = subprocess.run(['docker', 'exec', args.container, '/pb/pocketbase', 'superuser', 'delete', email], capture_output=True, text=True)
        if result.returncode and 'only existing superuser' not in result.stdout + result.stderr:
            raise RuntimeError('Could not remove disposable bootstrap account')
    print('Temporary birthday/user records cleaned up. Remove the disposable container when finished.')


if __name__ == '__main__': main()
