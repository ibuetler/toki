#!/usr/bin/env python3
"""Disposable PocketBase integration test; requires TOKI_TEST_URL/EMAIL/PASSWORD.
Never point at production: this creates and deletes synthetic user accounts.
"""
import os
import secrets
import unittest
import requests
from import_calendars import PocketBase, reconcile, reconcile_users
from test_import_calendars import BASE, feed, parse, merge_feeds


@unittest.skipUnless(os.environ.get('TOKI_TEST_URL'), 'Disposable backend not configured')
class BackendTests(unittest.TestCase):
    def test_private_import_lifecycle(self):
        api = PocketBase({'url': os.environ['TOKI_TEST_URL'], 'email': os.environ['TOKI_TEST_EMAIL'], 'password': os.environ['TOKI_TEST_PASSWORD']})
        accounts = []
        try:
            for _ in range(2):
                mail, password = secrets.token_hex(8) + '@example.invalid', secrets.token_urlsafe(24)
                row = api.request('POST', '/api/collections/users/records', {'email': mail, 'password': password, 'passwordConfirm': password})
                login = api.request('POST', '/api/collections/users/auth-with-password', {'identity': mail, 'password': password})
                accounts.append((row['id'], login['token']))
            owner, token = accounts[0]
            other, outsider = accounts[1]
            self.assertEqual(owner, api.resolve_user(api.request('GET', '/api/collections/users/records/' + owner)['email']))
            desired = merge_feeds([parse(feed(BASE)), parse(feed(BASE), 'B')])
            self.assertEqual(1, reconcile(api, owner, desired, True)['created'])
            self.assertEqual(1, reconcile(api, owner, desired, True)['unchanged'])
            row = api.records(owner)[0]
            self.assertEqual('A · B', row['source_names'])
            endpoint = os.environ['TOKI_TEST_URL'] + '/api/collections/calendar_events/records'
            def call(method, auth=None, rid='', payload=None):
                return requests.request(method, endpoint + ('/' + rid if rid else ''), headers={'Authorization': auth} if auth else {}, json=payload, timeout=15)
            self.assertEqual(200, call('GET', token, row['id']).status_code)
            for auth in (outsider, None):
                self.assertEqual(404, call('GET', auth, row['id']).status_code)
                self.assertEqual([], call('GET', auth).json()['items'])
            for auth in (token, outsider, None):
                self.assertIn(call('POST', auth, payload=dict(next(iter(desired.values())), owner=owner)).status_code, (400, 403, 404))
                self.assertIn(call('PATCH', auth, row['id'], {'title': 'Attack'}).status_code, (400, 403, 404))
                self.assertIn(call('DELETE', auth, row['id']).status_code, (400, 403, 404))
            # Reassign the same sources to another recipient: only their own
            # authenticated account can read the resulting copy.
            reassigned = reconcile_users(api, {other: desired}, True)
            self.assertEqual(1, reassigned['created'])
            self.assertEqual(1, reassigned['deleted'])
            self.assertEqual([], api.records(owner))
            transferred = api.records(other)[0]
            self.assertEqual(404, call('GET', token, transferred['id']).status_code)
            self.assertEqual(200, call('GET', outsider, transferred['id']).status_code)
            reconcile_users(api, {owner: desired}, True)
            self.assertEqual([], api.records(other))
            preferences = os.environ['TOKI_TEST_URL'] + '/api/collections/calendar_preferences/records'
            def prefcall(method, auth, rid='', payload=None):
                return requests.request(method, preferences + ('/' + rid if rid else ''), headers={'Authorization': auth}, json=payload, timeout=15)
            key = row['external_key']
            response = prefcall('POST', token, payload={'owner': owner, 'external_key': key, 'hidden': True})
            self.assertEqual(200, response.status_code)
            preference = response.json()['id']
            self.assertEqual(404, prefcall('GET', outsider, preference).status_code)
            self.assertEqual([], prefcall('GET', outsider).json()['items'])
            for payload in ({'owner': owner, 'external_key': key, 'hidden': True}, {'owner': other, 'external_key': 'f' * 64, 'hidden': True}):
                self.assertIn(prefcall('POST', outsider, payload=payload).status_code, (400, 403, 404))
            self.assertIn(prefcall('PATCH', token, preference, {'owner': other}).status_code, (400, 403, 404))
            self.assertIn(prefcall('PATCH', token, preference, {'external_key': 'f' * 64}).status_code, (400, 403, 404))
            self.assertEqual(200, prefcall('PATCH', token, preference, {'hidden': False}).status_code)
            self.assertFalse(prefcall('GET', token, preference).json()['hidden'])
            self.assertEqual(200, prefcall('PATCH', token, preference, {'hidden': True}).status_code)
            moved = merge_feeds([parse(feed(BASE.replace('20261022T100000Z', '20261023T100000Z')))])
            self.assertEqual(1, reconcile(api, owner, moved, True)['updated'])
            self.assertEqual(row['id'], api.records(owner)[0]['id'])
            reconcile(api, owner, {}, True)
            self.assertEqual([], api.records(owner))
            reconcile(api, owner, desired, True)
            self.assertTrue(prefcall('GET', token, preference).json()['hidden'])
            api.request('DELETE', '/api/collections/users/records/' + owner)
            self.assertEqual([], api.records(owner))
            self.assertEqual(0, api.request('GET', '/api/collections/calendar_preferences/records')['totalItems'])
            accounts.pop(0)
        finally:
            for user, _ in accounts:
                api.request('DELETE', '/api/collections/users/records/' + user)


if __name__ == '__main__':
    unittest.main()
