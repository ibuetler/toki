# Outlook calendar imports

Jupiter fetches published ICS feeds for Compass Security, Hacking-Lab and OST. Imported occurrences are private to the TOKI user specified in each calendar section. URLs, importer credentials and calendar contents must stay outside Git.

## Behaviour

- The systemd timer runs every four hours by default, with up to one minute of jitter. Edit its schedule with `systemctl edit toki-calendar-sync.timer`.
- Android syncs on initial launch, on returning to the foreground and when you press Sync. It does not poll in the background. The phone learns Outlook changes only after Jupiter imports them and the phone syncs.
- Every timed occurrence gets an alarm five minutes before its start. ICS alarms are ignored. All-day entries appear without alarms and remain visible through their exclusive end date.
- Events show source calendar names in Android. Shared meetings with the same UID and recurrence identity appear once, listing all matching sources. Distinct UIDs remain separate even when their titles match. Manually created TOKI events are not deduplicated against imports.
- A one-off move updates the same record. Recurring instances use their original recurrence identity, so moving an instance also preserves its record. EXDATE, RDATE, embedded time zones and recurrence exceptions are expanded on the server.
- Copies with conflicting revisions use the highest SEQUENCE, then LAST-MODIFIED/DTSTAMP. A cancellation wins a revision tie. Equal revisions otherwise use configuration order. Removing one copy retains a meeting still present in another source; an explicit newer cancellation overrides older copies.
- The default rolling horizon is seven days in the past through 366 days ahead. Old entries leave TOKI as the window advances. Outlook publication itself can expose a narrower range; only occurrences present in the feeds can be imported.
- All configured feeds must download and parse successfully before writes begin. A failed feed leaves the backend untouched. Upserts finish before stale entries are deleted. Backend write failures can leave partial updates; the next run reconciles them. Deterministic record IDs and a unique owner/key index prevent duplicates on retry. This is not an atomic multi-record snapshot.
- Imported records are read-only for normal users, including their owner. Edit or delete the original in Outlook. User-created events, birthdays and tasks are stored separately and never touched by this importer.

## Assign calendars to TOKI users

Each calendar section specifies the recipient by their existing TOKI sign-in email:

```ini
[calendar:compass]
name = Compass Security
url = https://example.invalid/compass.ics
user = ivan.buetler@compass-security.com
```

Set `user` independently for every source. Sources assigned to the same account are merged and deduplicated together; the same meeting assigned to two accounts produces one private copy for each account. Source labels only list calendars assigned to that recipient.

The importer resolves every email to an existing PocketBase user before any writes. A missing or unknown recipient aborts the import. It does not create accounts or use the recipient's password. Legacy configurations with a global `[pocketbase] owner` record ID still work for calendar sections without `user`; new configurations should use per-calendar emails.

Changing `user` moves the source's imports on the next successful job. The previous account keeps meetings still supplied by its other sources; copies no longer belonging to that account are removed. Phones learn these changes on their next sync. Exclusion rules apply to all recipients.

Run only **one importer configuration against this collection**. This job manages all `calendar_events` records, including cleaning up previous recipients when a source is removed or reassigned. Independent jobs with different configurations would remove each other's records.

## Exclude unwanted events

Edit the private configuration on Jupiter:

```sh
ssh root@jupiter
sudoedit /etc/toki-calendar/config.ini
```

Add or edit this section, keeping the other sections unchanged:

```ini
[exclude]
titles =
    Marina in CH
    Another exact title
```

Enter one complete title per line. Matching ignores case and leading/trailing whitespace; it does not use substring matching or wildcards. The rules apply across every configured calendar **after deduplication**, so another source's copy cannot reintroduce the matching merged event. Only imported calendar records are affected; manually created TOKI events are untouched. The job logs the excluded count, without logging titles.

Run `systemctl start toki-calendar-sync.service` to apply the change immediately, or wait for the next scheduled run. Existing matching imports are deleted on a successful import. Open TOKI or press Sync afterward to remove them and cancel their alarms on the phone. Removing a title from this list allows matching events to return on the next successful import. Leave `titles =` empty to disable title exclusions.

The initial rule excludes **Marina in CH**. Reinstalling the service preserves remotely maintained exclusions unless the supplied configuration explicitly includes an `[exclude]` section.

## Configuration

Copy `config.ini.example` to `config.ini` and run `chmod 600 config.ini`. This filename is ignored by Git. Use stable, unique source names. Floating times and all-day dates use the configured time zone; explicit ICS time zones take precedence for timed events.

For a manual dry run, run `.venv/bin/python import_calendars.py --config config.ini`; add `--apply` to write changes.

The importer uses a dedicated PocketBase superuser because it must reconcile a collection locked against client writes. Protect its credentials as server administration secrets. No credentials or event text are logged. The installer creates a random dedicated identity; it does not reuse your TOKI password.

## Deployment from macOS

Rehearse and deploy the additive migration, then install the service. The schema wrapper defaults to rehearsal; `--apply` makes a verified backup and briefly stops PocketBase.

```sh
./backend/scripts/apply-jupiter-calendars.sh --host root@jupiter
./backend/scripts/apply-jupiter-calendars.sh --host root@jupiter --apply
./backend/scripts/install-jupiter-calendars.sh \
  --host root@jupiter \
  --owner-email YOUR_TOKI_EMAIL \
  --config backend/calendar-sync/config.ini
```

The installer targets the existing `pocketbase` container on Jupiter and `https://pocketbase.hacking-lab.com`. It validates the CLI default account read-only, fills missing calendar `user` values from `--owner-email` without overriding explicit recipients, installs the service under `/opt/toki-calendar`, stores private configuration at `/etc/toki-calendar/config.ini`, validates a dry run, runs the initial import and enables the timer. Reinstalling keeps the existing importer identity. Do not rerun the schema deployment once installed.

Inspect status without exposing secrets:

```sh
ssh root@jupiter 'systemctl list-timers toki-calendar-sync.timer; systemctl status toki-calendar-sync.service'
ssh root@jupiter 'journalctl -u toki-calendar-sync.service -n 20 --no-pager'
```

Run manually:

```sh
ssh root@jupiter 'systemctl start toki-calendar-sync.service'
```

### Change the schedule

Connect to Jupiter and open the timer override:

```sh
ssh root@jupiter
systemctl edit toki-calendar-sync.timer
```

For example, run every two hours:

```ini
# systemctl edit toki-calendar-sync.timer
[Timer]
OnCalendar=
OnCalendar=*-*-* 00/2:00:00
```

The empty `OnCalendar=` clears the previous schedule. Save the override, then apply it and check the next run:

```sh
systemctl daemon-reload
systemctl restart toki-calendar-sync.timer
systemctl list-timers toki-calendar-sync.timer
```

| Frequency | `OnCalendar` value |
| --- | --- |
| Every hour | `*-*-* *:00:00` |
| Every four hours (default) | `*-*-* 00,04,08,12,16,20:00:00` |
| Daily at 06:00 | `*-*-* 06:00:00` |

Schedules follow Jupiter’s configured time zone and may run up to one minute later because of the timer’s randomized delay. Configuration changes to source URLs, exclusions or import horizon take effect on the next run; they do not require a timer restart.

To pause imports, run `systemctl disable --now toki-calendar-sync.timer` and stop any active service. Existing records remain. To remove a source, remove its section and run the importer; meetings belonging only to that source disappear. At least one source must remain configured. To remove every imported record, use a deliberate administrator operation; an accidentally empty configuration is rejected.

## Tests

```sh
python3 -m venv /tmp/toki-calendar-venv
/tmp/toki-calendar-venv/bin/pip install -r backend/calendar-sync/requirements.txt
cd backend/calendar-sync
/tmp/toki-calendar-venv/bin/python -m unittest -v test_import_calendars
```

`test_backend.py` additionally exercises real PocketBase rules, owner isolation, denied client writes, repeated-import idempotency, moved events, deletion and account cascades. Set `TOKI_TEST_URL`, `TOKI_TEST_EMAIL` and `TOKI_TEST_PASSWORD` only for a disposable instance with all migrations installed. Never run this fixture against production.

The calendar migration was rehearsed against Jupiter, preserving existing records/settings/rules. The stopped-backup verifier includes retained SQLite WAL data instead of ignoring it. Retain the printed verified backup outside Git. Recovery follows the [existing migration recovery procedure](../GROUPS.md): stop the service and timer, preserve failed data, restore the full archive with ownership, remove the new migration file before restart, and restore the matching app version.

## Deployment verification (22 October 2026)

Deployed to Jupiter with a verified backup at `/opt/applic/pocketbase/backups-toki-calendar_events-20261022T121327Z-hg7ogshu`. The first import created 380 occurrences, including 111 with multiple source calendars and 11 all-day entries. A subsequent dry run reported 380 unchanged records and no creates, updates or deletions. Counts reflect the published feeds and rolling horizon at that time. The timer is enabled. Real feed URLs and service credentials are excluded from Git.

The exact-title exclusion was deployed on 22 October 2026. It removed 45 matching occurrences, leaving 335 imports at verification time. No matching imports remained, and the timer stayed active. These counts change as the feeds and import window change.

Validation passed 20 importer unit cases, a real disposable-backend API/privacy lifecycle test, and the retained-WAL backup regression. Android passed 48 unit tests plus the calendar source-label and API mapping checks.

Per-calendar recipient emails were deployed on 22 October 2026. All three existing sources remain assigned to the original account; no ownership transfer was requested for them. Recipient resolution and reassignment privacy were checked against a disposable backend.
