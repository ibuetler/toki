#!/usr/bin/env python3
"""Group authorization integration tests for a disposable localhost container only.

Creates disposable users and a bootstrap superuser. Removes test records in finally
blocks; PocketBase retains the last superuser until the disposable volume is removed.
Never use this runner with a production database or container.
"""
import argparse
import json
from pathlib import Path
import secrets
import subprocess
from urllib.parse import urlparse

import requests

from testing import Suite
import configparser


class GroupTests:
    def __init__(self, url, token):
        self.url = url
        self.root = token
        self.passed = 0
        self.records = []

    def call(self, method, collection, actor=None, payload=None, rid='', expected=(200,), params=None):
        response = requests.request(method, f'{self.url}/api/collections/{collection}/records' +
                                    (f'/{rid}' if rid else ''),
                                    headers={'Authorization': actor} if actor else {},
                                    json=payload, params=params, timeout=15)
        assert response.status_code in expected, (method, collection, response.status_code,
                                                  response.text)
        self.passed += 1
        data = response.json() if response.content else {}
        if method == 'POST' and response.status_code == 200:
            self.records.append((collection, data['id']))
        return data

    def hidden(self, collection, rid, token):
        self.call('GET', collection, token, rid=rid, expected=(403, 404))
        result = self.call('GET', collection, token,
                           params={'filter': f'id = "{rid}"'})
        assert result['items'] == [], f'{collection} leaked through list'
        self.passed += 1

    def run(self, users):
        owner, admin, member, outsider = users
        oid, ot = owner
        aid, at = admin
        mid, mt = member
        xid, xt = outsider
        group = self.call('POST', 'groups', ot, {'name': 'Family', 'owner': oid})['id']
        other = self.call('POST', 'groups', xt, {'name': 'Other family', 'owner': xid})['id']
        self.call('POST', 'groups', None, {'name': 'Anonymous', 'owner': oid}, expected=(400, 403))
        self.call('POST', 'groups', mt, {'name': 'Spoofed', 'owner': oid}, expected=(400,))
        membership = self.call('POST', 'group_members', ot,
                               {'group': group, 'user': aid, 'role': 'admin'})['id']
        memberrow = self.call('POST', 'group_members', at,
                              {'group': group, 'user': mid, 'role': 'member'})['id']
        self.call('POST', 'group_members', ot,
                  {'group': group, 'user': mid, 'role': 'member'}, expected=(400,))
        self.call('POST', 'group_members', xt,
                  {'group': group, 'user': xid, 'role': 'admin'}, expected=(400,))
        # Being an admin elsewhere must not elevate a member in this group.
        self.call('POST', 'group_members', xt,
                  {'group': other, 'user': mid, 'role': 'admin'})
        self.call('PATCH', 'group_members', mt, {'role': 'admin'}, memberrow, expected=(404,))
        self.call('PATCH', 'group_members', at, {'group': other}, memberrow, expected=(404,))
        self.call('PATCH', 'group_members', at, {'user': xid}, memberrow, expected=(404,))
        for token in [ot, at, mt]:
            self.call('GET', 'groups', token, rid=group)
            self.call('GET', 'group_members', token, rid=memberrow)
        for token in [xt, None]:
            self.hidden('groups', group, token)
            self.hidden('group_members', memberrow, token)
        self.call('PATCH', 'groups', at, {'name': 'Family events'}, group)
        self.call('PATCH', 'groups', mt, {'name': 'Hijacked'}, group, expected=(404,))
        self.call('PATCH', 'groups', ot, {'owner': xid}, group, expected=(404,))
        self.call('DELETE', 'groups', at, rid=group, expected=(404,))

        event = Suite.new_deadline(None, oid, 'Group birthday')
        event['group'] = group
        event['rrule'] = 'FREQ=YEARLY'
        deadline = self.call('POST', 'deadlines', ot, event)['id']
        event['owner'] = aid
        admin_event = self.call('POST', 'deadlines', at, event)['id']
        for token, uid in [(mt, mid), (xt, xid)]:
            event['owner'] = uid
            self.call('POST', 'deadlines', token, event, expected=(400,))
        for token in [ot, at, mt]:
            self.call('GET', 'deadlines', token, rid=deadline)
        for token in [xt, None]:
            self.hidden('deadlines', deadline, token)
        self.call('PATCH', 'deadlines', at, {'description': 'Updated by admin'}, deadline)
        self.call('PATCH', 'deadlines', mt, {'title': 'No'}, deadline, expected=(404,))
        self.call('DELETE', 'deadlines', mt, rid=deadline, expected=(404,))
        self.call('PATCH', 'deadlines', at, {'group': other}, deadline, expected=(404,))
        self.call('PATCH', 'deadlines', ot, {'group': ''}, deadline, expected=(404,))
        self.call('PATCH', 'deadlines', at, {'owner': aid}, deadline, expected=(404,))
        private = self.call('POST', 'deadlines', ot, Suite.new_deadline(None, oid, 'Private'))['id']
        self.hidden('deadlines', private, mt)
        self.call('PATCH', 'deadlines', ot, {'group': group}, private, expected=(404,))

        pref = self.call('POST', 'deadline_preferences', mt,
                         {'user': mid, 'deadline': deadline, 'reminder_minutes': 60, 'enabled': True})['id']
        ownerpref = self.call('POST', 'deadline_preferences', ot,
                              {'user': oid, 'deadline': deadline, 'reminder_minutes': 5, 'enabled': True})['id']
        self.call('POST', 'deadline_preferences', mt,
                  {'user': mid, 'deadline': deadline}, expected=(400,))
        self.call('POST', 'deadline_preferences', xt,
                  {'user': xid, 'deadline': deadline}, expected=(400,))
        self.call('POST', 'deadline_preferences', ot,
                  {'user': mid, 'deadline': admin_event}, expected=(400,))
        self.call('POST', 'deadline_preferences', mt,
                  {'user': mid, 'deadline': private}, expected=(400,))
        for token in [ot, at, xt, None]:
            self.hidden('deadline_preferences', pref, token)
            self.call('PATCH', 'deadline_preferences', token, {'enabled': False}, pref, expected=(403, 404))
            self.call('DELETE', 'deadline_preferences', token, rid=pref, expected=(403, 404))
        self.call('PATCH', 'deadline_preferences', mt, {'reminder_minutes': 120}, pref)
        self.call('PATCH', 'deadline_preferences', mt, {'reminder_minutes': -1}, pref, expected=(400,))
        self.call('PATCH', 'deadline_preferences', mt, {'user': oid}, pref, expected=(404,))
        self.call('PATCH', 'deadline_preferences', mt, {'deadline': admin_event}, pref, expected=(404,))
        own = self.call('GET', 'deadline_preferences', ot, rid=ownerpref)
        assert own['reminder_minutes'] == 5
        self.passed += 1
        # Revocation also applies to an event's creator and to their preferences.
        self.call('DELETE', 'group_members', at, rid=memberrow, expected=(204,))
        self.hidden('deadlines', deadline, mt)
        self.hidden('deadline_preferences', pref, mt)
        self.call('PATCH', 'deadline_preferences', mt, {'enabled': False}, pref, expected=(404,))
        self.call('DELETE', 'deadline_preferences', mt, rid=pref, expected=(204,))
        self.call('DELETE', 'group_members', ot, rid=membership, expected=(204,))
        self.hidden('deadlines', admin_event, at)
        self.call('PATCH', 'deadlines', at, {'title': 'No longer admin'}, admin_event, expected=(404,))
        # The implicit owner remains an admin with no membership rows.
        self.call('GET', 'deadlines', ot, rid=admin_event)
        self.call('DELETE', 'groups', ot, rid=group, expected=(204,))
        self.call('GET', 'deadlines', self.root, rid=deadline, expected=(404,))
        self.call('GET', 'deadlines', self.root, rid=admin_event, expected=(404,))
        self.call('GET', 'deadline_preferences', self.root, rid=ownerpref, expected=(404,))
        self.call('GET', 'deadlines', ot, rid=private)

    def cleanup(self):
        for collection, rid in reversed(self.records):
            self.call('DELETE', collection, self.root, rid=rid, expected=(204, 404))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--container', default='toki-groups-validation-pocketbase-1')
    parser.add_argument('--url', default='http://127.0.0.1:8090')
    args = parser.parse_args()
    if urlparse(args.url).hostname not in {'127.0.0.1', 'localhost'} or not args.container.startswith('toki-'):
        parser.error('This provisioning runner is restricted to localhost and toki- containers')
    password = secrets.token_urlsafe(32)
    email = secrets.token_hex(8) + '@example.com'
    subprocess.run(['docker', 'exec', args.container, '/pb/pocketbase', 'superuser', 'create', email, password],
                   check=True, capture_output=True)
    root = None
    suite = None
    uid = None
    try:
        response = requests.post(args.url + '/api/collections/_superusers/auth-with-password',
                                 json={'identity': email, 'password': password}, timeout=15)
        response.raise_for_status()
        auth = response.json()
        root, uid = auth['token'], auth['record']['id']
        suite = GroupTests(args.url.rstrip('/'), root)
        # The documented target snapshot must match the actually migrated schema.
        snapshot = json.loads((Path(__file__).parents[1] / 'schema/collections.json').read_text())
        for expected in snapshot:
            result = requests.get(args.url + '/api/collections/' + expected['name'],
                                  headers={'Authorization': root}, timeout=15)
            result.raise_for_status()
            actual = result.json()
            for key in ['listRule', 'viewRule', 'createRule', 'updateRule', 'deleteRule', 'indexes']:
                assert actual[key] == expected[key], (expected['name'], key)
            fields = {field['name']: field for field in actual['fields']}
            for field in expected['fields']:
                for key, value in field.items():
                    assert fields[field['name']][key] == value, (expected['name'], field['name'], key)
        print('Target schema matches migrated collections, fields, indexes, and access rules.')
        users = []
        config = configparser.ConfigParser(interpolation=None)
        config['pocketbase'] = {'url': args.url, 'timeout': '15'}
        config['users'], config['passwords'] = {}, {}
        for alias in ['owner', 'admin', 'member', 'outsider']:
            p = secrets.token_urlsafe(24)
            mail = secrets.token_hex(8) + '@example.com'
            record = suite.call('POST', 'users', root,
                                 {'email': mail, 'password': p, 'passwordConfirm': p, 'verified': True})
            result = requests.post(args.url + '/api/collections/users/auth-with-password',
                                   json={'identity': mail, 'password': p}, timeout=15)
            result.raise_for_status()
            users.append((record['id'], result.json()['token']))
            config['users'][alias], config['passwords'][alias] = mail, p
        # Existing private-deadline behavior must continue to work.
        private = Suite(config)
        try:
            private.run(list(config['users']), list(config['users']))
            private.recurrence_tests(list(config['users']))
        finally:
            private.cleanup()
        assert private.failed == 0, 'Private deadline regression failed'
        print(f'Private regression: {private.passed} PASS, {private.failed} FAIL')
        suite.run(users)
        print(f'Group checks: {suite.passed} PASS, 0 FAIL (before cleanup)')
    finally:
        try:
            if suite:
                suite.cleanup()
        finally:
            if root and uid:
                cleanup = subprocess.run(['docker', 'exec', args.container, '/pb/pocketbase',
                                          'superuser', 'delete', email], capture_output=True, text=True)
                if cleanup.returncode:
                    if "only existing superuser" not in cleanup.stdout + cleanup.stderr:
                        raise RuntimeError('Could not clean up local bootstrap superuser')
                    print('PocketBase retains the last bootstrap superuser; remove the disposable volume when finished.')
    print('Temporary users, groups, deadlines, and preferences cleaned up.')


if __name__ == '__main__':
    main()
