# TOKI

TOKI is a deadline management project with a PocketBase backend and a working Kotlin/Compose Android app. It helps users remember meetings and recurring events, such as birthdays, through audible reminders—normally five minutes before an event.

The project follows the [initial design and backend setup discussion](https://chatgpt.com/share/6ad72066-9274-83eb-9568-a003b315f269).

## Current status

- A remote PocketBase backend has been configured at `https://pocketbase.hacking-lab.com`.
- This repository contains Python authorization and recurrence regression tests. The two-user remote suite returned **54 PASS, 0 FAIL**, including deletion of all four temporary records. Local group validation also passes the four-user private regression and group authorization checks described below.
- Group events and personal reminder preferences are deployed to Jupiter; the live group suite passed **75 checks with 0 failures**. See [group events](backend/GROUPS.md).
- The Android app implements sign-in, offline Room caching, private event creation, permission-aware event deletion, private/group deadline viewing, and timed one-off/recurring audible reminders. Its Agenda has compact event rows, toolbar search, and next-alarm/next-event tiles ordered by time. New events and displayed timestamps use the phone’s time zone by default. See the [Android guide](android/README.md) for setup, tests, and current limits.
- An [iOS SwiftUI/AlarmKit prototype](ios/README.md) provides a native 30-second test alarm for iOS 26+. PocketBase integration and event management are not yet implemented on iOS.
- Captured Docker deployment files, collection schema, access rules, and a fresh-instance migration are included. See the [backend guide](backend/README.md) for the verified local setup and remaining operational configuration.

## Architecture

```text
PocketBase                         Android app
Authentication            <--REST-->  Synchronization
User-owned deadlines                  Local Room database
Recurrence rules                      Occurrence calculation
                                      AlarmManager → audible reminders
```

PocketBase stores deadlines and controls access to each user's records. Android caches deadlines locally, calculates upcoming occurrences, and schedules reminders on the device so that cached reminders work offline. Synchronization runs on sign-in, after event creation, and when requested manually.

The app uses **Kotlin, Jetpack Compose, Room, and AlarmManager**. Alarm-clock scheduling drives a foreground audio player and a lock-screen Dismiss screen. Notification and exact-alarm permissions are required; full-screen alarm access enables the lock-screen display. Cached alarms are restored after reboot/unlock, app updates, and clock/time-zone changes.

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

PocketBase stores recurrence data; Android calculates occurrences locally. For example, a Monday meeting at 09:00 in Zurich can use:

```json
{
  "start_at": "2026-10-26 08:00:00.000Z",
  "timezone": "Europe/Zurich",
  "rrule": "FREQ=WEEKLY;BYDAY=MO",
  "exdates": ["2026-11-02T09:00:00"],
  "rdates": ["2026-11-04T09:00:00"]
}
```

This excludes November 2 and adds November 4 at 09:00 local time. These exception strings follow the local-time format used by the current tests. Timed recurrence supports DST transitions, exclusions/additions, and offline advancement. Existing series keep their saved event zone, while the app displays times in the phone’s current zone. All-day reminders are not scheduled until an explicit reminder-time policy is defined. See the [recurrence contract](android/README.md#recurrence).

## Use the Android app

1. Install the debug build from Android Studio or follow the [build instructions](android/README.md#build-and-verify), then sign in with a PocketBase user account.
2. Open **Alarms**, enable Notifications and Exact alarms, and enable Lock-screen display. Try the 30-second test before relying on reminders.
3. In **Agenda**, tap **+ New event** to create a private event. Enabled, a five-minute reminder, a 60-minute duration, and the phone’s time zone are the defaults. The initial date/time is one hour ahead. Saving requires a connection and automatically syncs the event.
4. The green **Next alarm** and blue **Next event** tiles show the earlier time on the left. After an alarm, the event stays visible as **Due soon** until its deadline. Past one-off events remain under **All**.
5. To delete an event, refresh once to load permissions, expand its row, tap **Delete event**, and confirm. Recurring deletion removes the entire series; shared events are deleted for everyone and require group owner/admin rights.
6. Use the magnifying glass left of refresh to search; tap a compact event row for details. **Today** uses the phone’s date. Refresh after changes made in PocketBase.

Existing event detail edits, group event creation, memberships, and personal reminder preferences are managed in PocketBase. Background sync and all-day alarms are not implemented. See the [Android guide](android/README.md) for limitations and troubleshooting.

## Repository layout

```text
android/                         Kotlin/Compose Android application and tests
ios/                             SwiftUI/AlarmKit iOS prototype and Xcode project
backend/docker/                  Captured deployment and isolated local Compose setup
backend/pb_migrations/           Baseline and group-event schema migrations
backend/schema/                  Target collection schema and access rules
backend/scripts/                 Jupiter migration rehearsal, deployment, and checks
backend/fixtures/                Shared Python/Kotlin recurrence cases
backend/pocketbase-tests/        API authorization, group, and recurrence tests
  config.ini.example             Configuration template
  config.ini                     Local credentials; ignored by Git
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

The Python recurrence checks do not execute Android code or validate audible delivery. Android has separate unit and phone UI tests; the latest feature checks passed 21 unit tests, seven phone UI tests, and one isolated cache-deletion test on October 21, 2026. See the [Android testing guide](android/README.md#build-and-verify). Ownership-transfer tests target another real user. List checks filter by the known temporary record ID to avoid pagination gaps, and anonymous list access must not expose that record. Unexpectedly created records are tracked for cleanup, with other configured users tried if ownership has changed. These checks do not constitute a complete security audit.

## Next steps

1. Retain the verified migration backup and maintain the [deployment and recovery instructions](backend/GROUPS.md). Group sharing and SMTP are working on Jupiter.
2. Validate a fresh backend installation with the strengthened regression suite.
3. Define the all-day reminder-time policy. Timed recurrence, time zones, exclusions/additions, and shared Python/Kotlin fixtures are implemented.
4. Extend Android with background synchronization, editing existing events, group event creation, and personal preference controls.
5. Verify reminders on devices, including offline operation, reboot recovery, permission changes, and edited or deleted events.
