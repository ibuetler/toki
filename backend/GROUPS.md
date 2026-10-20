# Group events

Group sharing is implemented by migration `1792454401_group_events.js`. It is deployed to Jupiter; the live two-user group regression passed on October 20, 2026. The baseline migration remains unchanged. `schema/collections.json` describes the combined target schema.

## Data and permissions

| Collection | Fields | Behavior |
| --- | --- | --- |
| `groups` | `name`, `owner` | Any authenticated user may create a group for themselves. The owner is an implicit member and permanent admin. Members may read the group; admins may rename it; only the owner may delete it. Ownership is immutable. |
| `group_members` | `group`, `user`, `role` | Additional members with `member` or `admin` role. Owner/admins add existing users, change roles, and remove membership. Members may read the roster. Unique `(group, user)` prevents duplicates. Group/user relations are immutable. |
| `deadlines` | Existing fields plus optional `group` | Empty group means a private event. Members and the group owner may read shared events. Group owner/admins create, update, and delete them. The `owner` remains the creator and is immutable. |
| `deadline_preferences` | `user`, `deadline`, `reminder_minutes`, `enabled` | Private per-user override, unique per user/deadline. Only that user may create/read/update it, and only while they can access the event. They may delete their own preference even after membership removal. |

An event's group is immutable after creation. Moving a private event into a group, between groups, or back to private requires creating a new record; this avoids accidental disclosure through an ordinary edit. No existing event is shared by applying the migration.

Group ownership guarantees a permanent administrator without requiring an initial membership row. Membership admins may promote/demote other membership admins, including themselves; they cannot remove the owner's implicit rights. There is no invitation or email lookup flow yet: admins add users by their existing record IDs. The `users` API still exposes only the authenticated user's own record.

Removing membership revokes access immediately on the server, even to shared events that person created. Preference records can remain stored, but cannot be read or changed until access is restored. Android removes inaccessible cached events and cancels their alarms on the next successful sync. It fetches preferences again for accessible events. A device that remains offline cannot learn of revocation until it reconnects.

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

## Deploy from macOS over SSH

Run this from the local repository when you are ready for a brief PocketBase outage:

```sh
./backend/scripts/apply-jupiter-migration.sh --host root@jupiter
```

This command **applies the migration to the live backend**. Defaults are `/opt/applic/pocketbase` and container `pocketbase`; use `--app-dir` and `--container` to override them. It uses key-based SSH and requires remote Python 3, tar, Docker Compose, and permission to read/write the application directory. No local Python packages or database credentials are required.

The script:

1. Runs the isolated SSH rehearsal and proceeds only if it passes.
2. Checks that the selected live container, Compose project, data directory, image, and pre-group schema agree.
3. Stops PocketBase, archives the complete `pb_data` directory into a private `backups-toki-groups-*` directory beside it, extracts that archive, and checks the restored databases for integrity.
4. Installs only the group migration. If needed, creates a conventional Compose override with a persistent migration-directory mount, preserving the base file and existing migrations. An existing custom override without this mount must be updated manually first; the script refuses to overwrite it.
5. Recreates PocketBase using the exact previously running image, without building or pulling an update.
6. Checks HTTP health inside the container, migration history, the new collections/rules, unchanged original application records, and empty group relations on original deadlines.

The backup remains on Jupiter; its path is printed. Backup archives contain sensitive records and must remain private. Temporary working copies and rehearsal containers are removed. Avoid editing records during rollout: concurrent edits immediately after restart can fail the strict before/after comparison.

If backup or preparation fails before migration is attempted, the original container is restarted. If startup or post-migration checks fail, the script stops PocketBase and leaves the backup for manual recovery. It does not automatically replace live data. The backup directory contains `pb_data.tar.gz`, `compose.before`, the prior override if any, and `image.before`. Restore while PocketBase is stopped, preserve the failed data separately for diagnosis, restore the previous Compose files, remove the new migration/override if they were added, and restore the archive before starting the old configuration. Never start the old schema with the group migration still pending in its mounted directory.

The script checks database preservation and health without creating production accounts or events. You can subsequently run `backend/pocketbase-tests/testing.py -all` from your configured Python environment for the normal two-user API regression. The disposable `group_tests.py` runner remains restricted to local test instances.

The complete deployment was verified against a disposable local backend using real Docker and simulated SSH. Injected backup failure restarted the original service; injected post-migration verification failure stopped it and retained the backup. Jupiter deployment is performed only when you run the command above.

### Docker user namespace remapping

With `userns-remap`, container root is a different host UID. The rollout now matches the new migration directory and file ownership to the working `pb_data` directory, keeping directory mode 0700 and file mode 0600. The original script omitted this step, which can prevent startup when the root-owned migration directory is bind-mounted.

If that original rollout failed on Jupiter, inspect `docker logs --tail 60 pocketbase` for permission errors. On the host, correct ownership without changing the data or backup, then start the existing container:

```sh
cd /opt/applic/pocketbase
chown --reference=pb_data pb_migrations pb_migrations/1792454401_group_events.js
chmod 700 pb_migrations
chmod 600 pb_migrations/1792454401_group_events.js
docker start pocketbase
docker logs --tail 60 pocketbase
docker exec pocketbase wget -q -O - http://127.0.0.1:8090/api/health
```

The failed rollout already installed the persistent mount. Starting that container retries any unapplied migration; do not rerun the deployment wrapper against a partially applied schema. Keep the printed backup until health, collections, and API checks pass.


## Live group regression with existing users

Run from `backend/pocketbase-tests` in the configured Python environment:

```sh
python3 group_live_tests.py --users ivan marina
```

This uses the ignored `config.ini` and two existing user accounts. It requires no SSH, Docker, superuser token, or account provisioning. It creates temporary groups, membership, disabled recurring/private events, and personal reminder preferences; all mutations target records created by that run. Cleanup attempts both configured actors and accepts records already removed by deletion cascades. Failures report record IDs requiring manual cleanup. Normal account authentication may trigger the backend's configured login alerts.

The checks exercise outsider and anonymous denial, member reads, blocked edits/self-promotion, independent reminder preferences, admin promotion and edits, immutable ownership/group links, membership revocation, and group deletion cascades. The runner uses both roles sequentially; the four-user disposable suite remains useful for broader simultaneous-role coverage and superuser schema inspection.

On October 20, 2026, the live Jupiter run completed with **75 PASS, 0 FAIL**, including eight cleanup checks. Existing user records, memberships, and events were not edited by the test flow.
