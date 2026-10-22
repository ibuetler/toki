# TOKI Android user guide

TOKI keeps dated events, yearly birthdays and quick tasks in separate lists. This guide describes the Android app as of 22 October 2026. The screenshots were captured on 21 October 2026 from the real Compose screens on an Android phone with fictional sample data; no personal records or credentials are pictured.

For building, installation, recurrence details and troubleshooting, see the [Android technical guide](../android/README.md). The [iOS app](../ios/README.md) is currently an alarm prototype and does not yet provide these screens.

## Your day at a glance

<img src="screenshots/android/agenda.png" alt="TOKI Agenda with next alarm, next event and next birthday tiles" width="300">

The green **Next alarm** and blue **Next event** tiles put the earlier time on the left. The purple **Next birthday** tile shows the next birthday and upcoming age when the birth year is known. Tap a populated summary tile to jump to its list, scroll to that exact item and expand its details. Next alarm can open an event, birthday or task; Next event opens the event; Next birthday opens the birthday. Test-alarm, setup and empty-alarm tiles open alarm controls. An empty birthday tile opens the Birthdays list; an empty event tile has no detail action. Times use the phone's current time zone.

An event stays visible until its deadline, even after its reminder rings. A birthday stays visible all day. Tasks stay active until you complete them.

## Return to the main page

Use Android **Back** (the system swipe gesture or Back button) to return from Alarms, Account, expanded item details or a scrolled list to the top of Agenda. This also works after opening an item from a summary tile or search result. Details collapse, event filters return to **Upcoming**, and tasks return to **Active**. The selected Events, Birthdays or Tasks list is preserved; returning from a summary item restores the list you were browsing before opening it.

Alarms and Account also have a visible **Return to main page** button. Use the **Alarms** tab in the bottom navigation to open permission settings and the test-alarm controls.

If a form, search screen or confirmation dialog is open, Back closes that first. A form being saved stays open until saving finishes. TOKI only uses normal Android exit behaviour when you press Back from the main Agenda overview at the top, with no expanded details or overlay open.

## Choose the right list

| List | Use it for | Create button |
| --- | --- | --- |
| Events | Meetings, appointments and recurring deadlines | **+ New event** |
| Birthdays | Annual birthdays, with an optional birth year | **+ New birthday** |
| Tasks | Quick thoughts and things to do | **+ New task** |

Each create button opens only the form for its selected list. There is no Event/Birthday selector inside a form.

### Events

<img src="screenshots/android/events.png" alt="Compact events grouped by date, with colored borders and private or group labels" width="300">

Events sort by their displayed date and time. Date headings include the year; matching dates share a border color. Use **Upcoming**, **Today**, **Groups** or **All**. Past one-off events remain under All.

Tap a row to expand its description, recurrence and reminder details. **Edit event** and **Delete event** appear for your own private events and group events you own/administer. Recurring edits and deletion apply to the whole series. Shared changes affect all members. Deletion asks for confirmation.

### Birthdays

<img src="screenshots/android/birthdays.png" alt="Birthdays list with cake icons, yearly labels and the New birthday button" width="300">

Birthdays have cake icons and their own **Upcoming**, **Today** and **All** filters. A birthday remains visible until local midnight after its reminder. The reminder defaults to **09:00 in the phone's time zone** and repeats annually.

Tap a birthday to inspect its age, reminder time and notes, or edit/delete it. For February 29, choose February 28 or March 1 in non-leap years. See the [birthday model](../backend/BIRTHDAYS.md) for exact scheduling behaviour.

### Tasks

<img src="screenshots/android/tasks.png" alt="Tasks ordered Urgent, High, Normal and Low with checkboxes and matching colored badges" width="300">

Tasks are private and default to **Normal** priority. They sort **Urgent → High → Normal → Low**, newest first within each priority. Priority alone never rings an alarm.

Check a task to move it to **Completed** and cancel its pending reminder on this phone. Uncheck it there to reopen it. A reminder that has already passed does not repeat. Tasks stay active after reminders, and completed tasks remain until manually deleted. Expand a row for **Edit task** and **Delete task**. See the [task model](../backend/TASKS.md).

## Create something new

| Event | Birthday | Task |
| --- | --- | --- |
| <img src="screenshots/android/new-event.png" alt="New event form with title, date, time, reminder, duration and repeat options" width="240"> | <img src="screenshots/android/new-birthday.png" alt="New birthday form accepting the European date 8.10.1992" width="240"> | <img src="screenshots/android/new-task.png" alt="New task form with Normal priority and optional notes and reminder" width="240"> |

- **Event:** enter a title, choose date/time and optionally adjust the reminder, duration or repeat. Enabled is preselected; defaults are a five-minute reminder, 60-minute duration and a start time one hour ahead. The phone's time zone is applied automatically. Description stays collapsed until needed.
- **Birthday:** enter a name and `day.month.year`, such as `8.10.1992`. Use `8.10` when the year is unknown. Choose a reminder time if 09:00 does not suit you. Description is optional.
- **Task:** only a title is needed. Change priority when useful; expand notes or turn on **Remind me** to add a one-off reminder. Normal priority and no reminder are the defaults.

Saving, editing, completion/reopening and deletion require an internet connection. Cancel closes a form without saving. Successful writes update the local list and reminders. Other devices receive changes on their next sync.

## Search every list

<img src="screenshots/android/search.png" alt="Global search for Alex showing a birthday, an event and a completed task" width="300">

Tap the magnifying glass beside refresh. Search covers event titles, descriptions and group names, birthday names/descriptions, and task titles/notes. It includes **past events and completed tasks**, regardless of the current list or filter.

Results show **Event**, **Birthday** or **Task**, with **Completed** on finished tasks. Tap a result to switch to its list, scroll to it and expand its details. **Clear** resets the query; **Done** closes search. Search uses cached records and works offline; refresh first when you need changes made on another device.

## Return to the top

<img src="screenshots/android/back-to-top.png" alt="A long birthday list with a small up-arrow button at lower left and New birthday at lower right" width="300">

After scrolling down, the small **↑ Back to top** button appears at the lower left, opposite the create button. It smoothly returns to the top and keeps the selected Events, Birthdays or Tasks list. It disappears again near the top. This scroll shortcut keeps your current filter and expanded details; use Android Back for the full return to the main page described above.

## Set up audible reminders

<img src="screenshots/android/alarms.png" alt="Alarms screen showing notification, exact-alarm and lock-screen permissions" width="300">

Open **Alarms** and allow **Notifications** and **Exact alarms**. Allow **Lock-screen display** for the full-screen Dismiss screen. Scroll down to **Test alarm in 30 seconds**, start it and lock the phone to verify delivery.

Alarms use the phone's alarm volume, loop until dismissed, and stop after two minutes. DND, notification settings and device restrictions still apply. Cached reminders can ring offline; force-stopping the app prevents delivery until it is reopened.

## Outlook calendar events

Imported meetings appear in **Events**, with their source calendar names. A meeting shared across calendars appears once when its calendar identifiers match, with all matching sources listed. Imported items are read-only: make changes in Outlook. Your private exclusion rules can hide matching event titles across all your imported calendars or one selected source; manually created TOKI events are unaffected. After the next successful import, sync TOKI to remove excluded entries and their alarms. Removing an exclusion allows matching items to return. Manage them in **Account → Calendars & exclusions**, or read the [calendar integration guide](../backend/calendar-sync/README.md#title-exclusions-and-wildcard-patterns). Timed meetings ring five minutes before; all-day calendar entries have no alarm.

Jupiter checks the feeds every four hours by default. After that import, open TOKI or press refresh to update the list and alarms on your phone. Outlook publication may introduce additional delay. See the [calendar integration guide](../backend/calendar-sync/README.md).

### Connect calendars and exclude titles

Open **Account → Calendars & exclusions**. Add a calendar name and its published HTTPS ICS URL; it belongs automatically to your signed-in TOKI account. Edit, disable or remove connections there, and check their last import/error status. Links are masked in the form and never displayed in the connection list. Outlook publishing links are supported by default; other providers need administrator approval.

Tap **Add exclusion**, enter a title pattern and choose all your calendars or one source:

| Pattern | Excludes |
| --- | --- |
| `Marina *` | Titles starting with “Marina ” |
| `*Vacation*` | Titles containing “Vacation” |
| `Team ?` | Titles such as “Team A” |

Matching ignores case and covers the whole title. `*` means any number of characters; `?` means one. Other regex punctuation is literal. Turn off **Use * and ? wildcards** for a completely literal title match. The preview counts currently cached imported occurrences, including hidden ones; items already excluded or not imported yet cannot be counted. Rules can be edited, disabled or deleted.

Saving settings needs a connection. Rules and connection changes apply during the next successful server import, normally every four hours; open TOKI or press Sync afterward to update phone events and alarms. **Reload settings** refreshes settings/status, not the feeds themselves. Deleting a connection removes imports supplied only by it after the next successful import. Removing a rule can let events return, while personal hides remain in place. Outlook and other users are unaffected.

### Hide an imported occurrence

Expand an imported event and tap the prominent **Hide in TOKI** button directly below its title. It disappears from the normal event list, search and summary tiles, and its future alarm is cancelled on this phone. Outlook and other users' calendars are unchanged. Hiding requires an internet connection; the preference is applied locally after PocketBase confirms it.

To undo it, open **Events**, swipe the horizontal filter row to **Hidden events**, and select it. Expand the occurrence and tap **Restore event**. It returns to the normal list, and a future five-minute reminder can be scheduled again. A reminder time that has already passed is not replayed.

This hides only the selected occurrence, including its duplicate copies from other source calendars. Other occurrences in the same recurring series stay visible. The preference belongs to your TOKI account, survives reimports and is applied on other updated devices at their next sync. Hidden state is cached for offline viewing and alarm suppression. The Hidden events filter lists occurrences still present in the current import window; items removed by a server exclusion or deleted from Outlook are not listed there.

## Sync and offline use

TOKI syncs when launched or brought back to the foreground. Tap refresh or **Account → Sync now** whenever you want another sync. Events/birthdays sync after creation and editing; task writes update the local cache directly. Android does not poll for updates in the background.

Cached lists and global search work offline. Revoked access and remote edits/deletions are learned on the next successful sync. Signing out clears the session, cached events/birthdays/tasks and scheduled reminders.

If an event appears missing, refresh, choose **All**, or use global search. If an alarm is missing, check permissions, Enabled, the reminder time and alarm volume. For scheduling details and current limits, see the [technical guide](../android/README.md#current-limits).
