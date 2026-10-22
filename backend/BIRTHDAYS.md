# Private birthdays

Birthdays are a separate `birthdays` collection. They do not change existing `deadlines`, groups, preferences, or their access rules. The first release supports private birthdays, one reminder on the observed birthday, and an optional birth year. Group sharing and advance reminders are not implemented for this type yet.

## Model

| Field | Meaning |
| --- | --- |
| `owner` | Required user relation; only that user can list/read/create/update/delete the birthday. Ownership is immutable. Deleting the user cascades their birthdays. |
| `name`, `description` | Person’s name and optional notes. |
| `month`, `day` | Calendar month/day, independent of UTC and time zone. February 29 is supported. |
| `birth_year` | Optional year; zero means unknown. Android shows the age on the observed birthday when known. |
| `reminder_hour`, `reminder_minute` | Reminder wall time in the device’s current zone. Android defaults to 09:00 and submits both fields explicitly. |
| `leap_day_policy` | Required `feb28` or `mar1`: the observed date for February 29 in non-leap years. Leap years always use February 29. |
| `enabled` | Whether reminders are scheduled. Android explicitly submits true by default. |

PocketBase numeric bounds and access rules reject invalid month/day combinations, invalid reminder times, unauthorized access, and ownership changes. Android additionally validates a supplied birth year against the current year and the actual birth date. These birthday settings are private, without separate group reminder preferences.

## Android behavior

See the [illustrated Android guide](../docs/ANDROID_GUIDE.md#birthdays) for current app screenshots.

Open **Agenda → Birthdays → + New birthday**. Enter a name and a European birth date, for example `8.10.1968` for 8 October 1968, and choose the reminder time. Use `8.10` if the year is unknown. The date is always parsed as day.month.year, independently of the phone’s language. February 29 exposes an explicit February 28/March 1 choice; February 28 is initially selected. Notes remain collapsed under Add description.

Birthdays have a cake icon and a dedicated Birthdays tab under Your events. A compact Next birthday tile shows the nearest birthday, date, days remaining, and age when known; tapping it selects that tab, scrolls to the shown birthday and expands its details. Normal deadlines remain in Events and in the blue Next event summary. Birthday reminders still participate in the green Next alarm summary. Today’s birthday remains visible until the next local midnight, even after its reminder. Adding a birthday after today’s reminder time does not ring it late: it remains visible today and the next reminder is scheduled for the following year. Only its next future reminder is scheduled; after delivery it advances offline to next year. Phone time-zone changes rebuild alarms, so 09:00 continues to mean local 09:00. For a chosen reminder in a DST gap, the clock time shifts forward by the size of the gap; an overlap uses the earlier offset once.

Birthday record IDs use a `birthday:` prefix only in the Android cache and alarm identifiers. The raw PocketBase ID is used for the birthdays API. This prevents collisions with deadline IDs. Room version 3 added birthday metadata without dropping existing cached events. The current cache is version 4, which also adds tasks; versions 1–3 upgrade through the migration chain.

If the backend does not have the collection yet, normal deadline sync continues, and birthday creation remains disabled with a setup message. After installing the migration, tap refresh once. Read errors other than a missing collection fail sync rather than being mistaken for an empty birthday list.

## Migration and deployment

Migration: `pb_migrations/1792540800_birthdays.js`. A fresh installation applies it after the baseline and group migrations. For existing Jupiter installations, install **only this new migration**; do not replay the baseline.

From the repository root, rehearse over SSH first:

```sh
./backend/scripts/apply-jupiter-birthdays.sh --host root@jupiter
```

To rehearse and then deploy:

```sh
./backend/scripts/apply-jupiter-birthdays.sh --host root@jupiter --apply
```

Defaults are container `pocketbase` and `/opt/applic/pocketbase`; use `--container` and `--app-dir` if needed. This requires SSH keys, remote Python 3/SQLite, Docker, tar, and working persistent binds at `/pb/pb_data` and `/pb/pb_migrations`. It verifies the actual paths and the running PocketBase 0.40.5 image. Do not run it against a partially deployed migration or an already existing birthdays collection.

The rehearsal snapshots both databases, migrates an isolated network-disabled copy with the exact live image, and verifies all existing records, settings, collection rules, and migration history remain intact. It does not stop or migrate production. The wrapper removes temporary database copies; an interrupted host/SSH session may leave sensitive `/tmp/toki-birthdays.*` directories for manual cleanup.

With `--apply`, the worker stops PocketBase briefly, backs up the complete data directory, extracts the backup, verifies restored database integrity, and installs only the birthday migration. Ownership follows `pb_data` for Docker user-namespace remapping. It starts the same existing container, checks health, and verifies preserved data and the new private collection. It does not modify Compose or pull/build an image. Avoid concurrent record edits until post-migration verification finishes.

Retain the printed private `backups-toki-birthdays-*` directory. If preparation fails before installation, the original service restarts. If installation/startup/verification fails, PocketBase stays stopped; the script does not automatically overwrite production. For recovery, keep the failed data separately, restore `pb_data.tar.gz` while stopped with ownership preserved, remove the birthday migration from the persisted migration directory, and start the original container. Leaving the new migration mounted would apply it again after restoration. Backups contain credentials and user records and must never enter Git.

## Local validation

On October 21, 2026, local validation passed 31 birthday API checks, 162 private deadline checks, 114 group checks, and 29 Kotlin unit tests. The seeded local rollout passed rehearsal, backup extraction, deployment, health and preservation checks; injected backup and post-install failures exercised recovery boundaries. The 13 phone checks also passed: birthday creation/leap-day choices, all-day visibility, old-backend compatibility, existing navigation/deletion, cache migration, and metadata persistence. An isolated rehearsal against Jupiter also passed, preserving existing records, settings and access rules. The migration was deployed to Jupiter on October 21, 2026. Health, preservation checks, and authenticated birthday-list requests for both configured normal test accounts passed. The private backup is retained on Jupiter at `/opt/applic/pocketbase/backups-toki-birthdays-20261021T083443Z-yq5c7hhi`; do not commit its contents. Tap refresh in Android after deployment to discover birthday support.

Run against a disposable, migrated localhost instance only:

```sh
python backend/pocketbase-tests/birthday_tests.py \
  --container toki-birthday-validation --url http://127.0.0.1:8097
```

The runner provisions disposable accounts, checks birthday CRUD/privacy, rejects invalid dates/times and owner transfer, verifies account-deletion cascades, and cleans up its records. The last bootstrap superuser may remain until the disposable container/volume is removed. `--update-schema` updates only the public birthday collection definition in `schema/collections.json` from the test server; it never exports account records or auth secrets.

The existing private/group regression suite must continue to pass against the migrated test instance. Kotlin tests cover annual reminders, all-day visibility, leap-day policies, travel/time-zone conversion, DST gaps/overlaps, optional age, invalid dates, and isolation from the unsupported generic all-day-event policy.
