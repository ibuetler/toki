# TOKI Android: first milestone

Kotlin/Compose app for `https://pocketbase.hacking-lab.com`, package `com.hackinglab.toki`, minimum Android 8 (API 26).

## Implemented

- Email/password sign-in with PocketBase user authentication (no superuser credentials).
- Passwords are never saved. Session tokens are encrypted with an Android Keystore AES-GCM key; app data is excluded from cloud backup/device transfer.
- Manual sync fetches every page of accessible groups, deadlines, and personal reminder preferences. The session is refreshed first. A complete snapshot replaces Room data in one transaction; a failed fetch keeps the previous snapshot.
- Private/group labels, event-zone timestamps, and effective per-user reminder settings. Revoked/deleted records disappear on the next successful sync and their scheduled alarms are cancelled.
- Timed one-off events schedule exact alarms and start a foreground alarm player using the alarm audio stream. Sound loops until dismissed, with a two-minute timeout. User-granted notification and exact-alarm access are required. Permissions and scheduled counts are visible in the app.
- Cached reminders are restored after reboot (after unlock), app update, clock/time-zone changes, and granting exact-alarm access. Signing out clears cache, token, notifications, and scheduled reminders.
- A lock-screen alarm activity provides a Dismiss button when full-screen alarm access is allowed. A notification Dismiss action works without that additional permission. The device stays locked.
- A local 30-second test reminder is available even before sign-in, once permissions are granted. It does not create a backend record.

## Try on the phone

1. Open TOKI and sign in using an existing PocketBase user account.
2. Tap **Allow notifications** and **Allow exact alarms**. Return to TOKI after changing settings.
3. Allow **lock-screen alarms** if prompted. Tap **Test alarm in 30 seconds**, then lock the screen. Check alarm volume and the TOKI notification channel if it is silent. DND and device settings still apply.
4. For a real event test, create an enabled, non-recurring timed deadline in PocketBase at least 10 minutes ahead, with `reminder_minutes = 5`. Tap **Sync now** and confirm the reminder count increases.
5. Go offline or close the app normally and wait for the scheduled sound. Do not force-stop the app: Android blocks alarm delivery to force-stopped apps until reopened.
6. Reopen offline to check cached events. After editing or deleting an event, reconnect and tap **Sync now** to reschedule or cancel its reminder.

Never put account passwords, access tokens, local SDK paths, or signing keys into the repository.

## Current limits

Recurring and all-day events are shown but **not scheduled**, with a visible explanation. RRULE/EXDATE/RDATE calculation, all-day reminder policy, and leap-day behavior are the next milestone. The APK cannot yet edit events, memberships, or reminder preferences; manage these in PocketBase.

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

Unit tests cover PocketBase UTC timestamps, reminder offsets crossing midnight, and rejection of unsupported recurring/all-day scheduling. Phone tests cover Room replacement, encrypted session round-trip/clear, TLS health connectivity to PocketBase, and Compose screen rendering. Tests use isolated storage, make no authenticated backend requests, and do not create backend data. User sign-in and actual audible/offline/reboot behavior need manual phone verification with permissions granted.
