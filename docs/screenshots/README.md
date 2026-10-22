# Android screenshots

These 17 PNGs are direct captures of TOKI's real Compose screens on a physical Android 16 phone. The ten original screens were captured on 21 October 2026; the seven calendar/settings/hide screens were captured on 22 October 2026. All names, events, tasks, sessions, URLs and permission/sync indicators come from an in-memory demonstration fixture. No personal records, real calendar links, credentials or system notifications are pictured. Dates/times follow the phone at capture time, except fictional import-status examples.

The fixture in `android/app/src/androidTest/java/com/hackinglab/toki/TokiDocumentationScreenshots.kt` never reads the user's session/cache, contacts the backend, saves records or schedules alarms. Forms are cancelled rather than submitted. The images are used in the [illustrated Android guide](../ANDROID_GUIDE.md) and main README. Keep the original PNG resolution; Markdown controls display width.

| Capture method | Images | Last captured |
| --- | --- | --- |
| `captureIllustratedGuide` | agenda, events, birthdays, tasks, search, new-event, new-birthday, new-task, alarms, back-to-top | 21 October 2026 |
| `captureCalendarGuide` | account, calendar-connections, exclusion-rules, new-exclusion, new-calendar, imported-event, hidden-events | 22 October 2026 |

The calendar fixture demonstrates two fictional sources, wildcard rules, a two-occurrence preview, the prominent Hide in TOKI button and restoring an individually hidden occurrence. Cached examples may match saved exclusions because server rules do not remove phone entries until an import and subsequent phone sync.

## Prepare

Connect an unlocked phone with USB debugging enabled and keep it awake. Put the Android SDK's `adb` on PATH. From the repository root:

```sh
./android/gradlew -p android :app:assembleDebugAndroidTest :app:installDebug
adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

`installDebug` updates in place. Do not uninstall or clear storage. Avoid `connectedDebugAndroidTest` on a signed-in phone because its cleanup can uninstall the main app.

## Capture the calendar/settings screens

```sh
adb shell am instrument -w \
  -e class 'com.hackinglab.toki.TokiDocumentationScreenshots#captureCalendarGuide' \
  com.hackinglab.toki.test/androidx.test.runner.AndroidJUnitRunner
```

Confirm `OK (1 test)` before exporting:

```sh
mkdir -p docs/screenshots/android
for name in account calendar-connections exclusion-rules new-exclusion new-calendar imported-event hidden-events; do
  adb exec-out run-as com.hackinglab.toki cat "cache/documentation/$name.png" \
    > "docs/screenshots/android/$name.png"
done
```

## Capture the original screens

```sh
adb shell am instrument -w \
  -e class 'com.hackinglab.toki.TokiDocumentationScreenshots#captureIllustratedGuide' \
  com.hackinglab.toki.test/androidx.test.runner.AndroidJUnitRunner
```

After `OK (1 test)`:

```sh
for name in agenda events birthdays tasks search new-event new-birthday new-task alarms back-to-top; do
  adb exec-out run-as com.hackinglab.toki cat "cache/documentation/$name.png" \
    > "docs/screenshots/android/$name.png"
done
```

## Review and restore the interactive app

View every image before committing. Check the relevant controls are visible, the keyboard is hidden, and all data is fictional. The new-calendar image intentionally masks its fictional URL. Dialog captures use their Compose viewport; the others show the app surface. Scroll positions are captured as displayed by the app.

Reopen normal TOKI after testing:

```sh
adb shell am start -n com.hackinglab.toki/.MainActivity
```

Update capture dates here and in the user guide whenever replacing images. Screenshot capture verifies rendering and navigation, not actual audible alarm delivery.
