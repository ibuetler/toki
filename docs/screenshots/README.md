# Android screenshots

These ten PNGs are direct captures of TOKI's Compose screens on a physical Android 16 phone, captured on 21 October 2026. All names, events, tasks, session values and permission/sync indicators come from an in-memory demonstration fixture. There are no personal records, actual login credentials or system notification screenshots. Displayed dates and times follow the phone at capture time.

The fixture in `android/app/src/androidTest/java/com/hackinglab/toki/TokiDocumentationScreenshots.kt` never reads the user's session/cache, contacts the backend, saves records or schedules alarms. The form examples are cancelled rather than submitted. The images are used in the [illustrated Android guide](../ANDROID_GUIDE.md) and the main README. Keep the original PNGs; Markdown sets their display width.

## Regenerate

Connect an unlocked phone with USB debugging enabled and keep it awake. Put the Android SDK's `adb` on PATH. Run from the repository root:

```sh
./android/gradlew -p android :app:assembleDebugAndroidTest :app:installDebug
adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.hackinglab.toki.TokiDocumentationScreenshots \
  com.hackinglab.toki.test/androidx.test.runner.AndroidJUnitRunner
```

Confirm `OK (1 test)` before replacing the checked-in images:

```sh
mkdir -p docs/screenshots/android
for name in agenda events birthdays tasks search new-event new-birthday new-task alarms back-to-top; do
  adb exec-out run-as com.hackinglab.toki cat "cache/documentation/$name.png" \
    > "docs/screenshots/android/$name.png"
done
adb shell am start -n com.hackinglab.toki/.MainActivity
```

Review every image before committing: ensure the expected screen/header is visible, the keyboard is hidden, and all data is fictional. `installDebug` updates the app in place; do not uninstall or clear storage. Avoid `connectedDebugAndroidTest` on a signed-in phone because its cleanup can uninstall the main app.
