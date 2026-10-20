# TOKI

TOKI is a deadline management project with a PocketBase backend and a planned Android app. It helps users remember meetings and recurring events, such as birthdays, through audible reminders—normally five minutes before an event.

The project follows the [initial design and backend setup discussion](https://chatgpt.com/share/6ad72066-9274-83eb-9568-a003b315f269).

## Current status

- A remote PocketBase backend has been configured at `https://pocketbase.hacking-lab.com`.
- This repository contains Python authorization and recurrence regression tests. The two-user remote suite returned **54 PASS, 0 FAIL**, including deletion of all four temporary records. Local group validation also passes the four-user private regression and group authorization checks described below.
- Group events and personal reminder preferences are deployed to Jupiter; the live group suite passed **75 checks with 0 failures**. See [group events](backend/GROUPS.md).
- Android development has not started; `android/` is empty.
- Captured Docker deployment files, collection schema, access rules, and a fresh-instance migration are included. See the [backend guide](backend/README.md) for the verified local setup and remaining operational configuration.

## Planned architecture

```text
PocketBase                         Android app
Authentication            <--REST-->  Synchronization
User-owned deadlines                  Local Room database
Recurrence rules                      Occurrence calculation
                                      AlarmManager → audible reminders
```

PocketBase stores deadlines and controls access to each user's records. Android will cache deadlines locally, calculate upcoming occurrences, and schedule reminders on the device so that already scheduled reminders can work offline.

The planned app stack is **Kotlin, Jetpack Compose, Room, and AlarmManager**. Implementation must account for alarm permissions, device restarts, background restrictions, and rescheduling after deadline changes.

## Deadline model

The test suite expects a `users` authentication collection and a `deadlines` collection with these fields:

| Field | Purpose |
| --- | --- |
| `owner` | Private owner or shared-event creator |
| `group` | Optional group relation; empty for private events |
| `title`, `description` | Event details |
| `start_at` | Start timestamp represented in UTC |
| `timezone` | IANA time zone, such as `Europe/Zurich` |
| `rrule` | iCalendar recurrence rule; empty for a one-off event |
| `exdates` | JSON array of excluded occurrences |
| `rdates` | JSON array of additional occurrences |
| `duration_minutes` | Event duration |
| `reminder_minutes` | Reminder offset before the event |
| `all_day` | Whether the event is an all-day event |
| `enabled` | Whether the deadline is enabled |

Private deadlines are visible only to their owner. Group deadlines are visible to group members and managed by group admins. Each user can keep private reminder preferences for a shared event. Ownership and an event's group are immutable. See the [group model and rollout guide](backend/GROUPS.md).

PocketBase stores recurrence data; the planned Android app will calculate occurrences. For example, a Monday meeting at 09:00 in Zurich can use:

```json
{
  "start_at": "2026-10-26 08:00:00.000Z",
  "timezone": "Europe/Zurich",
  "rrule": "FREQ=WEEKLY;BYDAY=MO",
  "exdates": ["2026-11-02T09:00:00"],
  "rdates": ["2026-11-04T09:00:00"]
}
```

This excludes November 2 and adds November 4 at 09:00 local time. These exception strings follow the local-time format used by the current tests. The complete recurrence contract, including all-day events and ambiguous daylight-saving times, still needs to be defined.

## Repository layout

```text
android/                         Planned Android application
backend/pocketbase-tests/
  testing.py                     API and local recurrence tests
  config.ini.example             Configuration template
  config.ini                     Local credentials; ignored by Git
  requirements.txt               pip dependencies
  Pipfile / Pipfile.lock          Pipenv dependency configuration
```

## Run the tests

Use Python 3.9 or newer with IANA time-zone data available. The Pipenv configuration specifies Python 3.14; these commands use a standard virtual environment.

```sh
cd backend/pocketbase-tests
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements.txt
# For initial setup only; retain an existing configured file.
cp config.ini.example config.ini
```

Edit `config.ini` with your backend URL and existing test accounts. **Keys in `[users]` and `[passwords]` must match.** For example:

```ini
[pocketbase]
url = https://pocketbase.hacking-lab.com
timeout = 15

[users]
ivan = user1@example.com
marina = user2@example.com

[passwords]
ivan = REPLACE_WITH_PASSWORD
marina = REPLACE_WITH_PASSWORD
```

Keep real passwords in the ignored `config.ini`. Do not commit credentials.

```sh
# Full suite
python testing.py -all

# Select an attacker account for cross-user checks
python testing.py -user ivan

# Use another configuration file
python testing.py -all -config /path/to/config.ini
```

`-user ivan` still authenticates all configured users and creates records for them; it selects Ivan for cross-user attack attempts. Use at least two test accounts for cross-user coverage.

The suite creates disabled temporary deadlines with a `PBSEC-` prefix, updates them, attempts unauthorized operations, and deletes tracked records during cleanup. Check the output for any records requiring manual removal. The process exits with status `1` if any checks fail.

### Coverage and limits

API tests cover authentication, own-record operations, cross-user and anonymous access attempts, recurrence exception persistence, and cleanup. Two local Python `dateutil` checks cover recurrence expansion and preservation of 09:00 Zurich time across the spring daylight-saving transition.

The local recurrence checks do not validate Android recurrence calculation or reminders. Ownership-transfer tests target another real user. List checks filter by the known temporary record ID to avoid pagination gaps, and anonymous list access must not expose that record. Unexpectedly created records are tracked for cleanup, with other configured users tried if ownership has changed. These checks do not constitute a complete security audit.

## Next steps

1. Retain the verified migration backup and maintain the [deployment and recovery instructions](backend/GROUPS.md). Group sharing and SMTP are working on Jupiter.
2. Validate a fresh backend installation with the strengthened regression suite.
3. Define shared recurrence fixtures and semantics for time zones, birthdays, all-day events, exclusions, and additions.
4. Implement Android authentication, synchronization, local persistence, recurrence calculation, and alarms.
5. Verify reminders on devices, including offline operation, reboot recovery, permission changes, and edited or deleted events.
