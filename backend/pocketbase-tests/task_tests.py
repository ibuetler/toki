#!/usr/bin/env python3
"""Provision and test private tasks on an isolated localhost toki- container only."""
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
    parser.add_argument('--container', default='toki-tasks-validation')
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
        payload = dict(owner=owner, title='Ask Peter about the VPN', notes='', priority='normal', completed=False, remind_at='')
        record = suite.call('POST', 'tasks', token, payload)
        rid = record['id']
        for field, value in payload.items(): assert record[field] == value, field
        for actor in [outsider, None]:
            suite.hidden('tasks', rid, actor)
            suite.call('PATCH', 'tasks', actor, {'completed': True}, rid, expected=(403, 404))
            suite.call('PATCH', 'tasks', actor, {'priority': 'urgent'}, rid, expected=(403, 404))
            suite.call('DELETE', 'tasks', actor, rid=rid, expected=(403, 404))
            suite.call('POST', 'tasks', actor, payload, expected=(400, 403, 404))
        suite.call('PATCH', 'tasks', token, {'owner': other}, rid, expected=(400, 403, 404))
        for changes in [dict(priority='critical'), dict(title=''), dict(title='x' * 201), dict(notes='x' * 5001)]:
            suite.call('POST', 'tasks', token, {**payload, **changes}, expected=(400, 403))
        for priority in ['urgent', 'high', 'normal', 'low']:
            updated = suite.call('PATCH', 'tasks', token, {'priority': priority}, rid)
            assert updated['priority'] == priority
        reminder = '2027-01-12 13:30:00.000Z'
        updated = suite.call('PATCH', 'tasks', token, {'title': 'Call Peter', 'notes': 'Bring notes', 'remind_at': reminder}, rid)
        assert updated['remind_at'] == reminder
        updated = suite.call('PATCH', 'tasks', token, {'completed': True}, rid)
        assert updated['completed'] and updated['remind_at'] == reminder
        active = suite.call('GET', 'tasks', token, params={'filter': 'completed=false'})
        assert all(x['id'] != rid for x in active['items'])
        done = suite.call('GET', 'tasks', token, params={'filter': 'completed=true'})
        assert any(x['id'] == rid for x in done['items'])
        updated = suite.call('PATCH', 'tasks', token, {'completed': False, 'remind_at': ''}, rid)
        assert not updated['completed'] and updated['remind_at'] == ''
        # Reopening and clearing a reminder preserve task text/priority.
        assert updated['title'] == 'Call Peter' and updated['notes'] == 'Bring notes' and updated['priority'] == 'low'
        suite.call('DELETE', 'tasks', token, rid=rid, expected=(204,))
        suite.call('GET', 'tasks', token, rid=rid, expected=(404,))
        cascade = suite.call('POST', 'tasks', token, payload)['id']
        suite.call('DELETE', 'users', suite.root, rid=owner, expected=(204,))
        suite.call('GET', 'tasks', suite.root, rid=cascade, expected=(404,))
        if args.update_schema:
            response = requests.get(args.url + '/api/collections/tasks', headers={'Authorization': suite.root}, timeout=15)
            response.raise_for_status()
            collection = response.json()
            allowed = {'id', 'name', 'type', 'listRule', 'viewRule', 'createRule', 'updateRule', 'deleteRule', 'fields', 'indexes'}
            collection = {k: v for k, v in collection.items() if k in allowed}
            path = Path(__file__).parents[1] / 'schema/collections.json'
            data = [c for c in json.loads(path.read_text()) if c['name'] != 'tasks'] + [collection]
            path.write_text(json.dumps(data, indent=2) + '\n')
        print(f'Task authorization/schema checks: {suite.passed} PASS, 0 FAIL')
    finally:
        if suite: suite.cleanup()
        result = subprocess.run(['docker', 'exec', args.container, '/pb/pocketbase', 'superuser', 'delete', email], capture_output=True, text=True)
        if result.returncode and 'only existing superuser' not in result.stdout + result.stderr:
            raise RuntimeError('Could not remove disposable bootstrap account')
    print('Temporary task/user records cleaned up. Remove the disposable container when finished.')


if __name__ == '__main__': main()
