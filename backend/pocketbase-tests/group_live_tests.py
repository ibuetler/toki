#!/usr/bin/env python3
"""Test group features using two existing configured users and temporary records.

No superuser, Docker, or account provisioning. Only records created by this run
are modified/deleted. Test events are disabled. Existing data is not enumerated.
"""
import argparse
import configparser
from pathlib import Path
import sys

from testing import Suite


class LiveGroups(Suite):
    def call(self, label, method, collection, actor=None, payload=None, rid=None,
             expected=(200,), valid=lambda data: True):
        path = f'/api/collections/{collection}/records'
        code, data = self.request(method, path + (f'/{rid}' if rid else ''), actor, payload)
        # Track unexpected successful creations too, before checking authorization.
        if method == 'POST' and code == 200 and data.get('id'):
            self.created.append((collection, data['id']))
        self.check(label, code, set(expected), valid(data) if code == 200 else True)
        if method == 'POST' and expected == (200,) and (code != 200 or not data.get('id')):
            raise RuntimeError(f'Cannot continue: {label}')
        return data

    def hidden(self, label, collection, rid, actor):
        self.call(label + ': view blocked', 'GET', collection, actor, rid=rid, expected=(403, 404))
        code, data = self.request('GET', f'/api/collections/{collection}/records', actor,
                                  params={'filter': f'id = "{rid}"'})
        self.check(label + ': list hides record', code, {200, 403, 404},
                   code != 200 or data.get('items') == [])

    def run_groups(self):
        owner, guest = list(self.actors.values())
        group = self.call('owner creates group', 'POST', 'groups', owner,
                          {'name': self.tag + '-family', 'owner': owner.uid})['id']
        other = self.call('second user creates separate group', 'POST', 'groups', guest,
                          {'name': self.tag + '-other', 'owner': guest.uid})['id']
        event = self.new_deadline(owner.uid, self.tag + '-birthday')
        event.update(group=group, rrule='FREQ=YEARLY')
        deadline = self.call('owner creates shared birthday', 'POST', 'deadlines', owner, event)['id']
        private = self.call('owner creates private event', 'POST', 'deadlines', owner,
                            self.new_deadline(owner.uid, self.tag + '-private'))['id']
        for actor in [guest, None]:
            label = actor.alias if actor else 'anonymous'
            for collection, rid in [('groups', group), ('deadlines', deadline)]:
                self.hidden(label + ' outsider ' + collection, collection, rid, actor)
            self.call(label + ' cannot self-enroll', 'POST', 'group_members', actor,
                      {'group': group, 'user': guest.uid, 'role': 'admin'}, expected=(400, 403))
        membership = self.call('owner adds member', 'POST', 'group_members', owner,
                               {'group': group, 'user': guest.uid, 'role': 'member'})['id']
        self.call('duplicate membership rejected', 'POST', 'group_members', owner,
                  {'group': group, 'user': guest.uid, 'role': 'member'}, expected=(400,))
        self.call('member reads group', 'GET', 'groups', guest, rid=group)
        self.call('member reads roster record', 'GET', 'group_members', guest, rid=membership)
        self.call('member reads recurring birthday', 'GET', 'deadlines', guest, rid=deadline,
                  valid=lambda d: d.get('rrule') == 'FREQ=YEARLY' and d.get('group') == group)
        self.hidden('private event remains private', 'deadlines', private, guest)
        self.call('member cannot promote self', 'PATCH', 'group_members', guest,
                  {'role': 'admin'}, membership, expected=(404,))
        self.call('member cannot edit shared event', 'PATCH', 'deadlines', guest,
                  {'title': self.tag + '-blocked'}, deadline, expected=(404,))
        self.call('member cannot delete shared event', 'DELETE', 'deadlines', guest,
                  rid=deadline, expected=(404,))
        self.call('member cannot create shared event', 'POST', 'deadlines', guest,
                  dict(event, owner=guest.uid), expected=(400,))
        pref = self.call('member creates personal reminder', 'POST', 'deadline_preferences', guest,
                         {'user': guest.uid, 'deadline': deadline, 'reminder_minutes': 60, 'enabled': True})['id']
        ownpref = self.call('owner creates independent reminder', 'POST', 'deadline_preferences', owner,
                            {'user': owner.uid, 'deadline': deadline, 'reminder_minutes': 5, 'enabled': True})['id']
        for actor in [owner, None]:
            self.hidden('reminder privacy', 'deadline_preferences', pref, actor)
            self.call('other actor cannot edit reminder', 'PATCH', 'deadline_preferences', actor,
                      {'enabled': False}, pref, expected=(403, 404))
        self.call('duplicate reminder rejected', 'POST', 'deadline_preferences', guest,
                  {'user': guest.uid, 'deadline': deadline}, expected=(400,))
        self.call('member changes own reminder', 'PATCH', 'deadline_preferences', guest,
                  {'reminder_minutes': 120}, pref, valid=lambda d: d.get('reminder_minutes') == 120)
        self.call('owner reminder unaffected', 'GET', 'deadline_preferences', owner, rid=ownpref,
                  valid=lambda d: d.get('reminder_minutes') == 5 and d.get('enabled') is True)
        self.call('negative reminder rejected', 'PATCH', 'deadline_preferences', guest,
                  {'reminder_minutes': -1}, pref, expected=(400,))
        self.call('owner promotes member to admin', 'PATCH', 'group_members', owner,
                  {'role': 'admin'}, membership)
        self.call('admin edits shared event', 'PATCH', 'deadlines', guest,
                  {'description': self.tag + '-updated'}, deadline,
                  valid=lambda d: d.get('description') == self.tag + '-updated')
        self.call('admin renames group', 'PATCH', 'groups', guest,
                  {'name': self.tag + '-renamed'}, group)
        self.call('admin cannot transfer event owner', 'PATCH', 'deadlines', guest,
                  {'owner': guest.uid}, deadline, expected=(404,))
        self.call('admin cannot move event to another group', 'PATCH', 'deadlines', guest,
                  {'group': other}, deadline, expected=(404,))
        self.call('admin cannot change membership user', 'PATCH', 'group_members', guest,
                  {'user': owner.uid}, membership, expected=(404,))
        self.call('admin cannot move membership', 'PATCH', 'group_members', guest,
                  {'group': other}, membership, expected=(404,))
        self.call('admin cannot transfer group ownership', 'PATCH', 'groups', guest,
                  {'owner': guest.uid}, group, expected=(404,))
        self.call('admin cannot delete group', 'DELETE', 'groups', guest, rid=group, expected=(404,))
        admin_event = self.call('admin creates group event', 'POST', 'deadlines', guest,
                                dict(event, owner=guest.uid, title=self.tag + '-admin-event'))['id']
        self.call('owner removes membership', 'DELETE', 'group_members', owner,
                  rid=membership, expected=(204,))
        for collection, rid in [('groups', group), ('deadlines', deadline),
                                ('deadlines', admin_event), ('deadline_preferences', pref)]:
            self.hidden('removed member access revoked', collection, rid, guest)
        self.call('removed admin cannot edit own shared event', 'PATCH', 'deadlines', guest,
                  {'enabled': True}, admin_event, expected=(404,))
        self.call('removed member may delete own preference', 'DELETE', 'deadline_preferences', guest,
                  rid=pref, expected=(204,))
        self.call('implicit owner still accesses group event', 'GET', 'deadlines', owner, rid=admin_event)
        self.call('owner deletes test group', 'DELETE', 'groups', owner, rid=group, expected=(204,))
        for collection, rid in [('deadlines', deadline), ('deadlines', admin_event),
                                ('group_members', membership), ('deadline_preferences', ownpref)]:
            self.call('group deletion cascades', 'GET', collection, owner, rid=rid, expected=(404,))
        self.call('private event survives group deletion', 'GET', 'deadlines', owner, rid=private)

    def cleanup_groups(self):
        print('\nCleanup (only records created by this run):')
        for collection, rid in reversed(self.created):
            results = []
            for actor in self.actors.values():
                code, _ = self.request('DELETE', f'/api/collections/{collection}/records/{rid}', actor)
                results.append(code)
                if code == 204:
                    break
            good = 204 in results or (results and all(c == 404 for c in results))
            actual = 204 if 204 in results else 404 if good else 0
            self.check(f'cleanup {collection}/{rid} (deleted or already cascaded)', actual, {204, 404})
            if not good:
                print(f'Manual cleanup required: {collection}/{rid} (run {self.tag})')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('-config', default=str(Path(__file__).with_name('config.ini')))
    parser.add_argument('--users', nargs=2, metavar=('OWNER', 'MEMBER'),
                        help='Two config aliases; defaults to the first two configured users')
    args = parser.parse_args()
    cfg = configparser.ConfigParser(interpolation=None)
    if not cfg.read(args.config) or not all(cfg.has_section(s) for s in ['pocketbase', 'users', 'passwords']):
        parser.error('Missing configuration sections')
    aliases = args.users or list(cfg['users'])[:2]
    if len(set(aliases)) != 2 or any(a not in cfg['users'] or a not in cfg['passwords'] for a in aliases):
        parser.error('Two distinct configured aliases and matching password keys are required')
    suite = LiveGroups(cfg)
    try:
        for alias in aliases:
            if not suite.authenticate(alias):
                return 1
        if len({actor.uid for actor in suite.actors.values()}) != 2:
            parser.error('The aliases must refer to two different user records')
        print(f'Testing {suite.base} with temporary run {suite.tag}')
        suite.run_groups()
    except (RuntimeError, KeyError) as error:
        print(f'Test aborted: {error}')
        suite.failed += 1
    finally:
        suite.cleanup_groups()
        print(f'\nResults: {suite.passed} PASS, {suite.failed} FAIL')
    return 1 if suite.failed else 0


if __name__ == '__main__':
    sys.exit(main())
