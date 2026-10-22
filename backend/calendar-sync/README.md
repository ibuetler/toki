# Outlook calendar imports

Users manage their private published-calendar URLs and title exclusions in **Android → Account → Calendars & exclusions**. PocketBase stores connections and rules; Jupiter fetches the feeds every four hours. Android syncs when opened or manually refreshed and does not poll in the background.

## Manage calendars in Android

- **Add calendar:** enter a name and published HTTPS ICS subscription URL. The signed-in account automatically owns it; no owner email or administrator credentials are entered in Android.
- **Edit calendar:** change its name or URL. URLs are masked in the form by default and omitted from the connection list.
- Use the switch to enable or disable importing. **Remove calendar** asks for confirmation. Disabled/removed sources lose their exclusive imports at the next successful server run; copies still supplied by other enabled sources remain. Removing a source also removes its source-scoped rules.
- Each connection shows its last successful import and a generic error if fetching failed. **Reload settings** reloads PocketBase settings/status; it does not fetch the ICS feeds.
- Writes require connectivity. Link drafts stay in memory instead of saved-instance state; connection URLs are not stored in the Android event cache.

Published URLs are credentials: anyone possessing one can read the published feed. The owner-private `calendar_sources` collection contains owner, name, URL, enabled and server-managed import status. Normal clients cannot transfer ownership or forge status fields. Account deletion cascades to its sources and rules. Do not export these records or URLs into Git or logs.

## Title exclusions and wildcard patterns

In **Account → Calendars & exclusions → Add exclusion**, enter a pattern, choose **All my calendars** or one calendar, then save. Rules can be edited, switched off or deleted. A preview counts matching cached imported events, including individually hidden entries; events already excluded or not yet imported cannot be counted.

| Pattern | Matches |
| --- | --- |
| `Marina *` | Titles starting with `Marina ` |
| `*Vacation*` | Titles containing `Vacation` |
| `Team ?` | `Team A`, `Team B`, etc. |
| `Marina in CH` | That exact title |

`*` matches any number of characters; `?` matches one character. Matching is case-insensitive against the whole title and ignores outer whitespace. Other characters, including regex punctuation, are literal. Turning off **Use * and ? wildcards** treats stars and question marks literally too. Patterns are limited to 200 characters and use bounded dynamic matching rather than arbitrary regular expressions.

Rules are private to their owner in `calendar_exclusions`. A scoped rule can reference only that owner's source. Rules apply **after deduplication**: a matching merged occurrence is excluded if its selected source is among its copies, so a duplicate from another source cannot reintroduce it. Other users and manually created events, birthdays and tasks are unaffected.

Rules take effect at the next successful Jupiter import. Matching existing imports are removed; disabling/deleting a rule allows them to return. Open TOKI or press Sync afterward to update the phone and cancel removed events' alarms. Rule changes do not modify Outlook. Existing individual hide preferences remain independent.

## Import behaviour

- Timed occurrences always get a five-minute reminder. ICS alarms are ignored. All-day entries have no alarm and remain visible through their exclusive end date.
- The default horizon is seven days in the past through 366 days ahead. Outlook can publish a narrower window. Entries leave TOKI as they leave the imported window.
- Shared meetings merge by UID and original recurrence identity **within each user** and list all matching source names. Different UIDs are not merged by title. The same meeting assigned to two users produces two private copies.
- Recurrence exceptions, exclusions/additions and embedded time zones are expanded on Jupiter. Moving a one-off or recurring occurrence preserves its identity. The highest SEQUENCE, then modification timestamp, wins conflicts; cancellation wins ties. Otherwise source order resolves equal revisions.
- Each account's enabled feeds must all succeed before its events are changed. A failed feed preserves that account's existing imports, including while rules are being edited. Other accounts can continue importing. Import-status messages never include URL or event content.
- Upserts complete before stale records are deleted. Backend write failures can leave partial updates; retrying reconciles them. Deterministic IDs and unique owner/key indexes prevent duplicates. Multi-record synchronization is not atomic.
- One importer configuration manages this dedicated `calendar_events` collection. Do not run independent configurations against the same collection.

## Hide individual occurrences

Expand an imported event and use **Hide in TOKI** directly beneath its title. Restore through **Events → Hidden events → Restore event** (swipe the filter row if necessary). Hiding suppresses normal lists, search, summary tiles and future alarms, without changing Outlook or other users.

Owner-private `calendar_preferences` stores the stable occurrence key and hidden flag separately from imported records. Hides survive deletion/reimport and leaving/re-entering the horizon. Other updated devices learn changes on sync. Restoring does not replay a reminder that already passed. Entire-series hiding is not implemented. The Hidden events filter shows currently imported occurrences; entries absent from Outlook or excluded by a rule cannot be restored there until imported again.

## Runtime configuration and approved hosts

`/etc/toki-calendar/config.ini` now holds only service credentials and runtime settings, not user URLs or exclusions. Keep it mode 600 and outside Git. Example:

```ini
[pocketbase]
url = https://pocketbase.hacking-lab.com
email = PRIVATE_SERVICE_IDENTITY
password = PRIVATE_SERVICE_PASSWORD

[sync]
settings_source = pocketbase
allowed_hosts = outlook.office365.com
past_days = 7
future_days = 366
timezone = Europe/Zurich
max_occurrences = 20000
```

The dedicated importer superuser performs reconciliation; Android uses normal user credentials. Treat the service identity as a server-administration secret. HTTPS feed hosts must be explicitly approved in `allowed_hosts` (whitespace-separated hostnames); Outlook is approved by default. Credentials in URLs, nonstandard ports, fragments, redirects and private/reserved DNS addresses are rejected before fetching user-managed feeds. Additional providers require administrator approval, not a user-controlled allowlist.

## Run manually and change the schedule

From macOS:

```sh
ssh root@jupiter 'systemctl start toki-calendar-sync.service'
ssh root@jupiter 'journalctl -u toki-calendar-sync.service -n 20 --no-pager'
ssh root@jupiter 'systemctl list-timers toki-calendar-sync.timer'
```

Then open TOKI or press Sync. The server import and phone refresh are separate steps; a phone refresh does not trigger a server import. Outlook publication can introduce additional delay.

Change the schedule on Jupiter:

```sh
ssh root@jupiter
systemctl edit toki-calendar-sync.timer
```

For example, every two hours:

```ini
[Timer]
OnCalendar=
OnCalendar=*-*-* 00/2:00:00
```

The empty entry clears the old schedule. Apply it:

```sh
systemctl daemon-reload
systemctl restart toki-calendar-sync.timer
systemctl list-timers toki-calendar-sync.timer
```

| Frequency | `OnCalendar` |
| --- | --- |
| Hourly | `*-*-* *:00:00` |
| Every four hours (default) | `*-*-* 00,04,08,12,16,20:00:00` |
| Daily at 06:00 | `*-*-* 06:00:00` |

Times follow Jupiter's configured zone, with up to one minute of random delay. To pause imports, disable the timer with `systemctl disable --now toki-calendar-sync.timer` and stop any active service. Existing records remain.

## Schema deployment and legacy migration

Apply the calendar schema, then private hide preferences, then private user settings. Each wrapper defaults to an isolated rehearsal. Use `--apply` only after rehearsal; it creates and verifies a stopped-service backup, preserves data/rules and checks health. Do not rerun a wrapper once its migration is installed.

```sh
./backend/scripts/apply-jupiter-calendars.sh --host root@jupiter
./backend/scripts/apply-jupiter-calendar-preferences.sh --host root@jupiter
./backend/scripts/apply-jupiter-calendar-settings.sh --host root@jupiter
```

For a new runtime, follow `config.ini.example` and install with:

```sh
./backend/scripts/install-jupiter-calendars.sh \
  --host root@jupiter --owner-email YOUR_EXISTING_TOKI_EMAIL \
  --config backend/calendar-sync/config.ini
```

Users then add connections in Android. The CLI email validates the existing account during installation; it does not override app-managed source ownership.

Legacy `settings_source=config` mode still supports `[calendar:...]` sections with `name`, `url`, `user`, and the old exact `[exclude] titles` list. To migrate an existing deployment, first retain a private copy of the old config, install `migrate_settings.py` beside the updated importer, stop its timer/service, and run with the runtime Python:

```sh
/opt/toki-calendar/.venv/bin/python /opt/toki-calendar/migrate_settings.py \
  --config /etc/toki-calendar/config.ini
/opt/toki-calendar/.venv/bin/python /opt/toki-calendar/migrate_settings.py \
  --config /etc/toki-calendar/config.ini --apply
```

Migration resolves legacy recipient emails, preserves existing exact rules, creates deterministic settings records and switches to PocketBase mode only after successful writes. It refuses to overwrite existing app-managed changes. Repeating after a partial migration resumes matching records. It removes user URL/exclusion sections from the active INI. Then restart the timer and run the service. The installer preserves PocketBase mode on subsequent installs.

## Verification and recovery

On 22 October 2026, Jupiter's three sources and four exact-title exclusions migrated to PocketBase. The first comparison reported 268 unchanged imported occurrences, with no creates, updates or deletions caused by migration. The four-hour timer remains in use. Source URLs and service credentials were never added to Git.

Retained private backups:

- Calendar events: `/opt/applic/pocketbase/backups-toki-calendar_events-20261022T121327Z-hg7ogshu`
- Hide preferences: `/opt/applic/pocketbase/backups-toki-calendar_preferences-20261022T132851Z-w30ncmiz`
- User settings: `/opt/applic/pocketbase/backups-toki-calendar_settings-20261022T135558Z-uu65r7uz`
- Previous runtime/config: `/var/backups/toki-calendar-settings-20261022T135758Z`

Recovery follows the [migration recovery procedure](../GROUPS.md): stop timer/service, preserve failed data, restore the full archive with ownership, remove the corresponding new migration before restarting and use the matching app/runtime. Do not drop collections containing user settings. The backup verifier includes committed SQLite WAL data.

## Tests

```sh
python3 -m venv /tmp/toki-calendar-venv
/tmp/toki-calendar-venv/bin/pip install -r backend/calendar-sync/requirements.txt
/tmp/toki-calendar-venv/bin/python -m unittest discover -s backend/calendar-sync -v
python3 backend/scripts/test_migration_snapshot.py
```

The disposable `test_backend.py` lifecycle suite needs `TOKI_TEST_URL`, `TOKI_TEST_EMAIL` and `TOKI_TEST_PASSWORD` pointing only at an isolated backend with all migrations installed. It verifies private URLs/rules, denied ownership/status changes, source scoping, deletion cascades, hide persistence and event reconciliation. Never run that provisioning test against production. Android tests cover wildcard preview/scoping and connection forms with fictional data.

Validation on 22 October 2026 passed 24 importer/settings unit cases, the disposable-backend lifecycle/privacy suite, 53 Android unit tests and 52 phone UI/API/cache checks (one activity-availability failure passed on targeted retry). Build and lint passed. Migration and runtime checks preserved the existing imported occurrences.
