# TOKI Android

Kotlin/Compose app for `https://pocketbase.hacking-lab.com`, package `com.hackinglab.toki`, minimum Android 8 (API 26).

## Implemented

- Email/password sign-in with PocketBase user authentication (no superuser credentials).
- Passwords are never saved. Session tokens are encrypted with an Android Keystore AES-GCM key; app data is excluded from cloud backup/device transfer.
- Manual sync fetches every page of accessible groups, deadlines, and personal reminder preferences. The session is refreshed first. A complete snapshot replaces Room data in one transaction; a failed fetch keeps the previous snapshot.
- Private/group labels, phone-local timestamps (saved event-zone time is also shown in expanded details when different), and effective per-user reminder settings. Revoked/deleted records disappear on the next successful sync and their scheduled alarms are cancelled.
- Timed one-off and recurring events use Android alarm-clock scheduling (visible in the system next-alarm indicator) and start a foreground alarm player using the alarm audio stream. Sound loops until dismissed, with a two-minute timeout. User-granted notification and exact-alarm access are required. Permissions and scheduled counts are visible in the app.
- Cached reminders are restored after reboot (after unlock), app update, clock/time-zone changes, and granting exact-alarm access. Signing out clears cache, token, notifications, and scheduled reminders.
- A lock-screen alarm activity provides a Dismiss button when full-screen alarm access is allowed. A notification Dismiss action works without that additional permission. The device stays locked.
- A local 30-second test reminder is available even before sign-in, once permissions are granted. It does not create a backend record.

## Interface

- **New event** on Agenda creates a private timed event. Enabled and the phone’s current time zone are preselected; the event zone can be changed before saving. Choose date/time, reminder offset, duration, and no repeat or daily/weekly/monthly/yearly repeat. Saving requires connectivity and automatically syncs the new event. Invalid time zones and nonexistent DST times are rejected.
- **Agenda** shows compact green next-alarm and blue next-event tiles side by side, ordered by time with the earlier one on the left (alarm first for equal times, tiles without a time last), with times shown in the phone’s current time zone and countdowns. The next event stays visible after its reminder, until its deadline. Tap the magnifying glass to the left of refresh to search titles, descriptions, or groups. Clear the search to restore the full list. Compact event rows expand when tapped. Filter by Upcoming, Today (phone time zone), Groups, or All.
- Tap an event card for its description, reminder offset, recurrence rule, or an explanation of disabled/invalid reminders. Use All to inspect past events and events without a valid next occurrence.
- **Alarms** brings permission setup, the 30-second sound test, and delivery timing together.
- **Account** shows the connected account, private/shared event counts, manual sync, and sign-out.
- Light and dark appearances follow the phone setting. Editing existing events and managing groups remain in PocketBase.

## Try on the phone

1. Open TOKI and sign in using an existing PocketBase user account.
2. Open the **Alarms** tab and tap **Enable** beside Notifications and Exact alarms. Return to TOKI after changing settings.
3. Enable **Lock-screen display** in the Alarms tab. Tap **Test alarm in 30 seconds**, note the displayed target/countdown, then lock the screen. The button is disabled while the countdown runs so repeated taps cannot reset it. Check alarm volume and the TOKI notification channel if it is silent. DND and device settings still apply.
4. For a real event test, create an enabled, non-recurring timed deadline in PocketBase at least 10 minutes ahead, with `reminder_minutes = 5`. Tap **Sync now** and confirm the reminder count increases.
5. Go offline or close the app normally and wait for the scheduled sound. Do not force-stop the app: Android blocks alarm delivery to force-stopped apps until reopened.
6. Reopen offline to check cached events. After editing or deleting an event, reconnect and tap **Sync now** to reschedule or cancel its reminder.

Never put account passwords, access tokens, local SDK paths, or signing keys into the repository.

## Recurrence

Display times follow the phone’s current time zone. Existing events retain their saved recurrence zone, so travelling changes the displayed time without changing the series’ wall-clock schedule in its original zone.

RRULE expansion uses `org.dmfs:lib-recur:0.17.1` with strict RFC 5545 parsing. Supported frequencies are DAILY, WEEKLY, MONTHLY, and YEARLY, with standard BY* selectors, INTERVAL, COUNT, and UNTIL. Multiple BYHOUR/BYMINUTE/BYSECOND values, sub-daily frequencies, non-Gregorian rules, and COUNT above 10000 are rejected with a visible per-event explanation. Recurring DTSTART values must have whole-second precision.

The recurrence set is DTSTART plus RRULE plus RDATE, minus EXDATE; duplicates collapse. The backend's `exdates`/`rdates` JSON arrays are cached. ISO strings without an offset use the event's IANA zone; Z/offset timestamps are absolute. Exceptions match occurrence instants. Nonexistent DST wall times are skipped without counting toward COUNT; overlaps use the first (earlier) offset and ring once. February 29 birthdays recur only in leap years unless a different RRULE is provided. Month-day 31 skips months without that date.

Only the next occurrence whose reminder is still in the future is registered per event. After delivery, only that event advances to its next occurrence, without network access; simultaneous events retain their own alarms. Sync, reboot, app update, and clock changes also rebuild schedules. Missed reminder times are not replayed on an ordinary sync. Invalid rules affect only their event, not the entire local schedule.

Room cache version 1 migrates to version 2 without dropping data. Old cached recurring events are not armed until one successful **Sync now** loads their exception arrays. This avoids ringing for dates that were excluded on the server. The agenda keeps the current occurrence visible until its deadline, even after its reminder time has passed (shown as Due soon). Alarm scheduling independently advances to the next future reminder, so keeping an event visible does not ring it again. At the deadline, a recurring series advances to its next occurrence; past one-off events remain available under All.

Eight shared cases live in `backend/fixtures/recurrence.tsv` and are checked by both Kotlin tests and the offline Python reference runner:

```sh
python backend/pocketbase-tests/recurrence_fixtures.py
```

## Current limits

All-day events are shown but **not scheduled** until an all-day reminder-time policy is defined. The APK creates private timed events; editing existing events, creating group events, managing memberships, and changing personal reminder preferences remain in PocketBase.

Sync is currently explicit, on sign-in or via **Sync now**. There is no periodic background sync yet. Offline devices retain their last known access until the next successful sync. Exact-alarm availability, notification/channel settings, DND, alarm volume, OEM background restrictions, and force-stop behavior affect delivery. Alarm audio uses the alarm stream rather than the notification stream. The lock-screen Dismiss screen uses a full-screen intent with user-controlled access; without that access, tap the alarm notification or its Dismiss action. Alarm volume and DND still apply. Overlapping alarms share one player and Dismiss control, with the latest title shown.

## Build and verify

Use Android Studio's configured JDK/SDK and the committed Gradle wrapper:

```sh
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
# Device tests can uninstall their APKs afterward. Install the interactive app last:
./gradlew :app:installDebug
```

Room uses Java entities/DAO and the Room Java annotation processor, avoiding a separate Kotlin code-generation plugin with AGP's built-in Kotlin support.

Unit tests cover UTC timestamps, reminder offsets, shared recurrence fixtures, DST gaps/overlaps, finite schedules, malformed data, migration safeguards, and offline advancement. Phone tests cover Room replacement, encrypted session round-trip/clear, TLS health connectivity to PocketBase, Compose screen rendering, and a separate version-1 cache migration fixture. Tests use isolated storage, make no authenticated backend requests, and do not create backend data. User sign-in and actual audible/offline/reboot behavior need manual phone verification with permissions granted.

Test diagnostics use the `TokiAlarmTiming` logcat tag (target, delivery, audio-start timestamps only; no event titles or credentials). After returning to the app, the last test delivery delay is shown. Alarm-clock scheduling replaces allow-while-idle scheduling to avoid its closely-spaced-alarm throttling.
