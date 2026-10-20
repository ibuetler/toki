# Group events

Group sharing is implemented by migration `1792454401_group_events.js`. It is tested locally and has **not been deployed to Jupiter**. The baseline migration remains unchanged. `schema/collections.json` describes the combined target schema.

## Data and permissions

| Collection | Fields | Behavior |
| --- | --- | --- |
| `groups` | `name`, `owner` | Any authenticated user may create a group for themselves. The owner is an implicit member and permanent admin. Members may read the group; admins may rename it; only the owner may delete it. Ownership is immutable. |
| `group_members` | `group`, `user`, `role` | Additional members with `member` or `admin` role. Owner/admins add existing users, change roles, and remove membership. Members may read the roster. Unique `(group, user)` prevents duplicates. Group/user relations are immutable. |
| `deadlines` | Existing fields plus optional `group` | Empty group means a private event. Members and the group owner may read shared events. Group owner/admins create, update, and delete them. The `owner` remains the creator and is immutable. |
| `deadline_preferences` | `user`, `deadline`, `reminder_minutes`, `enabled` | Private per-user override, unique per user/deadline. Only that user may create/read/update it, and only while they can access the event. They may delete their own preference even after membership removal. |

An event's group is immutable after creation. Moving a private event into a group, between groups, or back to private requires creating a new record; this avoids accidental disclosure through an ordinary edit. No existing event is shared by applying the migration.

Group ownership guarantees a permanent administrator without requiring an initial membership row. Membership admins may promote/demote other membership admins, including themselves; they cannot remove the owner's implicit rights. There is no invitation or email lookup flow yet: admins add users by their existing record IDs. The `users` API still exposes only the authenticated user's own record.

Removing membership revokes access immediately on the server, even to shared events that person created. Preference records can remain stored, but cannot be read or changed until access is restored. Future Android synchronization must remove inaccessible cached events/preferences and cancel their alarms. A device that remains offline cannot learn of revocation until it reconnects.

Deleting a group cascades to its membership rows and shared events; deleting an event cascades to its preferences. Deleting a group's owner account also cascades to the group. Account deletion remains superuser-only. Back up before deleting a group or owner account.

## Reminder semantics for Android

`deadlines.enabled` controls whether the event is active. With no preference, inherit the event's `reminder_minutes`. With a preference, use its `reminder_minutes` and `enabled`; a disabled event must remain disabled regardless of preference. Offsets are whole minutes from 0 to 525600; zero means at the event start. The client must send both preference fields explicitly when creating one (an omitted boolean defaults to false).

All-day reminder times and leap-day birthday behavior still need a recurrence contract. A morning-of-birthday policy is not implemented simply by adding group sharing.

## Examples

Create a group as its owner:

```json
{"name":"Family","owner":"CURRENT_USER_ID"}
```

Add another existing user through `group_members`:

```json
{"group":"GROUP_ID","user":"OTHER_USER_ID","role":"member"}
```

Create a shared birthday through `deadlines` with the existing event fields, the authenticated creator as `owner`, a yearly RRULE, and `"group":"GROUP_ID"`. A shared record's `owner` alone does not grant access after the creator leaves the group.

Create a personal override through `deadline_preferences`:

```json
{"user":"CURRENT_USER_ID","deadline":"DEADLINE_ID","reminder_minutes":60,"enabled":true}
```

## Local tests

Follow the fresh-instance commands in [the backend guide](README.md), then run from the repository root with the test virtual environment active:

```sh
python backend/pocketbase-tests/group_tests.py --container toki-validation-pocketbase-1
```

This runner requires Docker CLI access and a disposable localhost instance. It creates random accounts without using `config.ini`, runs the existing private-event regression with four users, tests group roles, cross-group isolation, privacy, preferences, revocation, and deletion cascades, and cleans up test records. PocketBase prevents deletion of its last superuser; on an otherwise empty instance its random bootstrap superuser remains until that disposable volume is removed.

Validation on October 20, 2026: **162 private regression checks and 114 group checks passed**, excluding cleanup requests from the group count. A separate upgrade check seeded a private recurring deadline on the old schema, applied the group migration, and verified every existing record field stayed unchanged, with the new `group` field empty.

## Jupiter rollout

1. Make a consistent PocketBase backup and verify it restores in an isolated environment. The copied SQLite/WAL directory alone is not proof of a valid backup.
2. Stop the service for a controlled migration window. Preserve its actual existing `pb_migrations` directory and migration history.
3. Copy **only `1792454401_group_events.js`** into the deployed migration directory for this existing installation. Do not replay the captured baseline over Jupiter's existing auth settings. Ensure `/pb/pb_migrations` is persisted or included in the deployment; the copied production Compose currently mounts only `pb_data`.
4. Start the same PocketBase version, check migration logs, and test the new collections with disposable group records and normal user tokens. The migration deliberately has no automatic down operation because dropping shared collections would destroy events and preferences.
5. If rollback is required, restore the verified pre-migration backup with the corresponding application version while stopped. Do not erase group relations to simulate rollback.

Deployment to Jupiter requires a separate rollout action. These local tests did not modify the real server or send SMTP mail.

## Rehearse the migration over SSH on Jupiter

From your local repository:

```sh
./backend/scripts/test-jupiter-migration.sh --host jupiter
# If your SSH configuration requires an explicit user:
./backend/scripts/test-jupiter-migration.sh --host root@jupiter
```

Defaults are container `pocketbase` and host data directory `/opt/applic/pocketbase/pb_data`. Override them with `--container` and `--data-dir`. The script verifies that the directory matches the container's actual `/pb/pb_data` bind mount. Paths with spaces are not supported.

The SSH user needs key-based login, Docker access, Python 3 with SQLite support, and read access to `pb_data`. Use a configured SSH host alias for a custom port or identity file. No passwords or database contents are printed or downloaded to your local machine.

The script uploads only the migration and a worker script, takes a consistent database snapshot through SQLite's backup API, and checks snapshot integrity. It uses the exact running container image and requires PocketBase 0.40.5. The migration runs in a temporary container with networking disabled, without serving HTTP, mounting production data, or stopping the live service. It checks integrity again, compares all original application records, confirms the new group collections/rules exist, and verifies all original deadlines remain private. The temporary snapshot, migration result, and container are removed on normal exit or handled failure. If SSH is interrupted or the host crashes, inspect `/tmp/toki-migration-test.*` and `toki-migration-test-*` containers for leftovers; they can contain sensitive database copies.

A successful run exits zero and ends with `Migration rehearsal passed`. It does **not** deploy the migration, test SMTP, or exercise REST authorization. Use the separate group integration suite for API coverage. It refuses a database where group collections already exist rather than claiming to test the migration twice.

The SSH transport was simulated locally for verification; the full backup/migration/comparison flow ran against the copied pre-group database with real Docker and preserved its two users and one deadline. Actual SSH connectivity, remote permissions, and dependencies on Jupiter remain to be checked by running the command above.
