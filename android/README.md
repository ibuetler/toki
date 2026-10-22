# TOKI Android

Kotlin/Compose app for `https://pocketbase.hacking-lab.com`, package `com.hackinglab.toki`, minimum Android 8 (API 26).

Read the [illustrated Android user guide](../docs/ANDROID_GUIDE.md) for screenshots of Events, Birthdays, Tasks, search, creation forms, alarm setup and back-to-top navigation.

## Implemented

- Email/password sign-in with PocketBase user authentication (no superuser credentials).
- Passwords are never saved. Session tokens are encrypted with an Android Keystore AES-GCM key; app data is excluded from cloud backup/device transfer.
- Manual sync fetches every page of accessible groups, deadlines, personal reminder preferences, birthdays and tasks. The session is refreshed first. A complete snapshot replaces Room data in one transaction; a failed fetch keeps the previous snapshot.
- Private/group labels, phone-local timestamps (saved event-zone time is also shown in expanded details when different), and effective per-user reminder settings. Revoked/deleted records disappear on the next successful sync and their scheduled alarms are cancelled.
- Timed one-off and recurring events use Android alarm-clock scheduling (visible in the system next-alarm indicator) and start a foreground alarm player using the alarm audio stream. Sound loops until dismissed, with a two-minute timeout. User-granted notification and exact-alarm access are required. Permission status and the next alarm are visible in the app.
- Cached reminders are restored after reboot (after unlock), app update, clock/time-zone changes, and granting exact-alarm access. Signing out clears cache, token, notifications, and scheduled reminders.
- A lock-screen alarm activity provides a Dismiss button when full-screen alarm access is allowed. A notification Dismiss action works without that additional permission. The device stays locked.
- A local 30-second test reminder is available even before sign-in, once permissions are granted. It does not create a backend record.

## Interface

- **Tasks** sits alongside Events and Birthdays in Agenda. Tap **+ New task** and enter only a title for quick capture. Normal is the default; choose Urgent (red), High (orange), Normal (blue), or Low (grey). Active tasks sort by priority, then newest first within each priority. Notes and an optional one-off reminder are tucked away. Priority alone never triggers an alarm. A task stays active after its reminder passes. Check it off to move it to **Completed**; uncheck there to reopen it. Expand a row for **Edit task** and **Delete task** (with confirmation). Completed tasks are retained until you delete them; there is no automatic expiration. Task reminders appear in Next alarm; tasks do not occupy Next event. The magnifying glass searches all cached lists, including completed tasks. Existing tasks work offline for viewing; saving, completion/reopening, and deletion need a connection. Other devices learn of changes on their next sync. If Tasks says setup is pending after installing, tap refresh once.

- **Birthdays**: open the Birthdays tab and tap **+ New birthday**, enter a name and a European birth date such as `8.10.1968` (8 October 1968). Use `8.10` if the year is unknown. The reminder defaults to 09:00 in the phone’s local zone and repeats yearly. February 29 offers February 28 or March 1 for non-leap years. Cake icons and the Birthdays tab distinguish them from deadlines, and they remain visible throughout the day. See the [birthday model and deployment guide](../backend/BIRTHDAYS.md).

- Creation follows the selected Agenda list: **Events → + New event**, **Birthdays → + New birthday**, **Tasks → + New task**. Each button opens only its own form; there is no Event/Birthday selector.
- **New event** on the Events list creates a private timed event. Enabled is preselected. The phone’s current time zone is applied automatically when saving, with no time-zone field in the form. The initial date/time is one hour ahead, the reminder is five minutes before, and duration is 60 minutes. Description is collapsed by default: tap **+ Add description** to enter notes, or **Edit description** to reopen saved draft text. Hiding it preserves the text for submission. Choose date/time, reminder offset, duration, and no repeat or daily/weekly/monthly/yearly repeat. Saving requires connectivity and automatically syncs the new event. Invalid time zones and nonexistent DST times are rejected.
- A small **↑ Back to top** floating button appears at the lower left after scrolling down any Agenda list. It smoothly returns to the top and keeps Events, Birthdays or Tasks selected. The matching create button stays on the right.
- **Agenda** shows compact green next-alarm and blue next-event tiles side by side, ordered by time with the earlier one on the left (alarm first for equal times, tiles without a time last), with times shown in the phone’s current time zone and countdowns. The next event stays visible after its reminder, until its deadline. Tap the magnifying glass to the left of refresh to search titles, descriptions, groups, birthday names and task notes across all cached lists. Results show Event, Birthday or Task, with Completed on finished tasks. Tap a result to switch to its list, reveal past/completed items when needed, and expand its details. Search works offline; Done closes it without filtering the lists. Compact event rows expand when tapped. Rows sort by the displayed deadline ascending; events without a future occurrence use their original date, including past events under All. Equal dates sort by title and record ID. Date headings include the year; events on the same phone-local date share a subtle border color. A six-color palette cycles across dates. Always check the year when similarly named dates appear in different years. Under Your events, switch between Events, Birthdays, and Tasks tabs. Events excludes birthdays; Birthdays shows only birthdays. Both tabs offer Upcoming, Today (phone time zone), and All filters; Groups is available for Events. Search covers all tabs.
- A compact purple **Next birthday** tile below the two summary tiles shows the nearest birthday, date, days remaining, and upcoming age when known. Tap a populated summary tile to jump to its list, scroll to that exact item and expand its details. Next alarm can open an event, birthday or task; Next event opens the event; Next birthday opens the birthday. Test-alarm, setup and empty-alarm tiles open alarm controls. An empty birthday tile opens the Birthdays list; an empty event tile has no detail action. The blue Next event tile covers regular events; the green Next alarm tile considers every enabled event type.
- Tap an event card for its description, reminder offset, recurrence rule, or an explanation of disabled/invalid reminders. Use All to inspect past events and events without a valid next occurrence.
- **Edit event** appears beside Delete event in expanded details for your own events and birthdays, or groups you own/administer. It fetches current server values, prefills the form, and saves changes online. Birthday dates accept `8.10.1968`. Event type stays fixed. Shared edits apply to everyone; personal reminder overrides stay unchanged. Editing preserves the saved event zone, custom recurrence rules, recurrence exceptions, all-day flag, owner, and group. Changing recurrence edits the entire series. Save automatically syncs and rebuilds alarms; Cancel leaves the event unchanged. Other devices receive edits on their next sync. If saving succeeds but sync fails, tap Sync to update local reminders.
- **Delete event** appears in expanded details for your private events and groups you own/administer. Tap refresh once after installing this feature to load action permissions. Confirmation explains that recurring deletion removes the entire series and shared deletion removes the event for all members. Deletion requires connectivity; after PocketBase confirms success, the app removes its local copy and cancels its future reminders. Other devices learn of deletion on their next successful sync. A normal group member cannot delete an event, even if they originally created it.
- **Alarms** brings permission setup, the 30-second sound test, and delivery timing together.
- **Account** shows the connected account, private/shared event counts, manual sync, and sign-out.
- Light and dark appearances follow the phone setting. Group creation and membership management remain in PocketBase.

## Navigation

Tap a populated Next alarm, Next event or Next birthday tile to reveal and expand that exact item. The **Alarms** tab in the bottom navigation opens alarm permissions, the sound test and delivery timing.

Android **Back** (swipe or button) returns from Alarms, Account, expanded details or a scrolled list to the main Agenda overview at the top. Details collapse and filters reset to **Upcoming** for events and **Active** for tasks. The selected list is preserved; returning from a summary item restores the originating list. Alarms and Account also provide **Return to main page**.

Back dismisses an open search screen, creation/edit form or confirmation dialog first. Saving forms consume Back until saving finishes. Only Back from the main overview at the top, without an open overlay or expanded details, uses normal Android exit behaviour. The floating **↑ Back to top** button only scrolls; it keeps the current list, filter and expanded details.

## Try on the phone

1. Open TOKI and sign in using an existing PocketBase user account.
2. Open the **Alarms** tab and tap **Enable** beside Notifications and Exact alarms. Return to TOKI after changing settings.
3. Enable **Lock-screen display** in the Alarms tab. Tap **Test alarm in 30 seconds**, note the displayed target/countdown, then lock the screen. The button is disabled while the countdown runs so repeated taps cannot reset it. Check alarm volume and the TOKI notification channel if it is silent. DND and device settings still apply.
4. In **Agenda**, tap **+ New event**, enter a title, and choose a time at least 10 minutes ahead with a five-minute reminder. Keep Enabled on and save. The event syncs automatically. For an event created in PocketBase, tap refresh in Agenda or **Sync now** in Account.
5. Go offline or close the app normally and wait for the scheduled sound. Do not force-stop the app: Android blocks alarm delivery to force-stopped apps until reopened.
6. After dismissing the alarm, confirm the event stays visible as **Due soon** until its deadline. The blue tile shows the next deadline; the green tile independently shows the next future alarm. Reopen offline to check cached events. After editing or deleting an event, reconnect and tap **Sync now** to reschedule or cancel its reminder.

7. Tap a populated summary tile and confirm its exact item expands. Swipe Back to return to the Agenda overview. Try Back from Alarms and Account, then verify their **Return to main page** buttons. With a form or search open, Back should close it first; only Back from the main overview should leave TOKI.

Never put account passwords, access tokens, local SDK paths, or signing keys into the repository.

## Time zones and visibility

The creation form automatically uses the phone’s current IANA time zone at save time. There is no time-zone field or separate configuration to maintain. To create an event explicitly tied to another zone, use PocketBase. The chosen local time is converted to UTC for PocketBase’s `start_at`; `timezone` stores the zone used for recurrence. New times inside a daylight-saving gap are rejected; an ambiguous overlap uses the earlier offset.

Tiles, compact rows, and Today filtering use the phone’s current zone. Expanded details also show the saved event-zone time when it differs. Changing the phone’s zone changes the display, not an existing event’s stored start or recurrence zone.

The agenda follows deadline time, independently of the reminder offset. An event at 16:30 with a 30-minute reminder remains in Upcoming after the 16:00 reminder, until 16:30. After that, a recurring series shows its next occurrence; a one-off remains under All. Keeping a reminded event visible does not schedule it again.

If an event is missing, tap refresh and select **All**, or use global search. Expand the event to inspect disabled reminders or recurrence errors. Backend time zones must be valid IANA names, for example `Europe/Zurich` rather than `Europe/Zürich`. Confirm that the signed-in account owns the private event or belongs to its group.

## Recurrence

Display times follow the phone’s current time zone. Existing timed deadlines retain their saved recurrence zone, so travelling changes the displayed time without changing the series’ wall-clock schedule in its original zone. Birthday reminders instead follow the phone’s local zone.

RRULE expansion uses `org.dmfs:lib-recur:0.17.1` with strict RFC 5545 parsing. Supported frequencies are DAILY, WEEKLY, MONTHLY, and YEARLY, with standard BY* selectors, INTERVAL, COUNT, and UNTIL. Multiple BYHOUR/BYMINUTE/BYSECOND values, sub-daily frequencies, non-Gregorian rules, and COUNT above 10000 are rejected with a visible per-event explanation. Recurring DTSTART values must have whole-second precision.

The recurrence set is DTSTART plus RRULE plus RDATE, minus EXDATE; duplicates collapse. The backend's `exdates`/`rdates` JSON arrays are cached. ISO strings without an offset use the event's IANA zone; Z/offset timestamps are absolute. Exceptions match occurrence instants. Nonexistent DST wall times are skipped without counting toward COUNT; overlaps use the first (earlier) offset and ring once. A generic deadline with a February 29 RRULE recurs only in leap years unless its rule says otherwise. The dedicated Birthday type instead uses its February 28/March 1 policy in non-leap years. Month-day 31 skips months without that date.

Only the next occurrence whose reminder is still in the future is registered per event. After delivery, only that event advances to its next occurrence, without network access; simultaneous events retain their own alarms. Sync, reboot, app update, and clock changes also rebuild schedules. Missed reminder times are not replayed on an ordinary sync. Invalid rules affect only their event, not the entire local schedule.

Room cache versions 1–3 migrate to version 4 without dropping data. Version 4 adds a separate tasks table. Version 2 added recurrence exceptions; version 3 adds birthday metadata. Old cached recurring events are not armed until one successful **Sync now** loads their exception arrays. This avoids ringing for dates that were excluded on the server. The agenda keeps the current occurrence visible until its deadline, even after its reminder time has passed (shown as Due soon). Alarm scheduling independently advances to the next future reminder, so keeping an event visible does not ring it again. At the deadline, a recurring series advances to its next occurrence; past one-off events remain available under All.

Eight shared cases live in `backend/fixtures/recurrence.tsv` and are checked by both Kotlin tests and the offline Python reference runner:

```sh
python backend/pocketbase-tests/recurrence_fixtures.py
```

## Current limits

Generic all-day deadlines can be inspected under **All** but are **not scheduled** until an all-day reminder-time policy is defined. The birthday type has its own explicit local reminder time and is scheduled. The APK creates private timed events; creating group events, managing memberships, and changing personal reminder preferences remain in PocketBase.

Sync runs on sign-in, after event/birthday creation or editing, or explicitly through refresh/**Sync now**. Successful task writes update their cached records directly and rebuild reminders. There is no periodic background sync yet. Offline devices retain their last known access until the next successful sync. Exact-alarm availability, notification/channel settings, DND, alarm volume, OEM background restrictions, and force-stop behavior affect delivery. Alarm audio uses the alarm stream rather than the notification stream. The lock-screen Dismiss screen uses a full-screen intent with user-controlled access; without that access, tap the alarm notification or its Dismiss action. Alarm volume and DND still apply. Overlapping alarms share one player and Dismiss control, with the latest title shown.

## Build and verify

Navigation verification on **22 October 2026**: **40 navigation/form UI checks passed**, and build and lint passed. These cover returning from subpages, direct and summary-opened details, modal dismissal, and the main-page exit boundary. The **45 unit tests** and separate ten-screen capture fixture were last verified on **21 October 2026**. Screenshots use fictional in-memory data; these checks do not establish audible delivery, offline delivery or reboot behaviour. Earlier API/cache/migration checks are described in the backend guides and device test sources.

Use Android Studio's configured JDK/SDK and the committed Gradle wrapper:

```sh
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:installDebug
```

To run the navigation/form checks while preserving a signed-in app, keep the phone unlocked with its screen on and use the SDK’s `adb`:

```sh
cd android
./gradlew :app:assembleDebugAndroidTest :app:installDebug
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.hackinglab.toki.TokiNavigationTest \
  com.hackinglab.toki.test/androidx.test.runner.AndroidJUnitRunner
```

This uses synthetic events and callbacks; it does not authenticate or create backend records. It avoids the connected-test task’s uninstall cleanup. Reopen TOKI afterward to resume the interactive app and recheck alarms. Never uninstall or clear app storage to preserve sign-in and cached data.

For the reproducible documentation capture and image export commands, see [screenshots](../docs/screenshots/README.md).

Room uses Java entities/DAO and the Room Java annotation processor, avoiding a separate Kotlin code-generation plugin with AGP's built-in Kotlin support.

Unit tests cover task priority sorting, reminder opt-in, completion and one-off delivery, partial edit payloads, birthday edits, preservation of unchanged timestamps and advanced fields, private/group deletion permission hints, phone-zone defaults, explicit zone conversion, deadline visibility after reminders, UTC timestamps, reminder offsets, shared recurrence fixtures, DST gaps/overlaps, finite schedules, malformed data, migration safeguards, and offline advancement. Phone tests cover task capture/editing, completion/reopening, task priority order, task API payloads and missing-backend capability, Room replacement, encrypted session round-trip/clear, TLS health connectivity to PocketBase, Compose screen rendering, global offline search across record types, completed/past result navigation, back-to-top in all three lists, chronological summary tiles, event creation defaults/submission, edit prefilling/custom recurrence/cancellation, edit action permissions, in-memory PATCH transport checks, compact row expansion, deletion confirmation/cancellation, member-only restrictions, targeted cache deletion, and a separate version-1 cache migration fixture. Tests use isolated storage, make no authenticated backend requests, and do not create backend data. User sign-in and actual audible/offline/reboot behavior need manual phone verification with permissions granted.

Test diagnostics use the `TokiAlarmTiming` logcat tag (target, delivery, audio-start timestamps only; no event titles or credentials). After returning to the app, the last test delivery delay is shown. Alarm-clock scheduling replaces allow-while-idle scheduling to avoid its closely-spaced-alarm throttling.
