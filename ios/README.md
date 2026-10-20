# TOKI for iOS

Native SwiftUI alarm prototype, bundle identifier `com.hackinglab.toki`, for iPhone and iPad running **iOS/iPadOS 26 or newer**. The repository project builds with Xcode 27.1. Android remains the full TOKI client; this iOS milestone validates native alarm behavior first.

## What is implemented

- TOKI branding and a screen that follows light/dark appearance.
- Native AlarmKit authorization, status refresh, and a one-time test alarm.
- The test is scheduled 30 seconds after authorization succeeds, using a fixed instant. Its displayed target and countdown use the device's time zone.
- A stable test identifier prevents multiple prototype alarms. The app reads the system's alarm list on launch and return to foreground, and observes authorization/alarm updates. The target is recovered from the system, not from an in-memory countdown.
- Cancel a pending test or dismiss an alerting test from the app. The system alarm screen also provides dismissal.
- `NSAlarmKitUsageDescription` explains why TOKI requests alarm access. There is no custom AlarmKit entitlement or background audio workaround.

PocketBase sign-in, credential storage, event synchronization/cache, event creation, recurrence, groups, and the Android Agenda interface are **not implemented on iOS yet**. No backend requests are made by this prototype.

## Run on an iPhone

1. Open `ios/toki.xcodeproj` in Xcode.
2. Select the **toki** target, then **Signing & Capabilities**. Select your Apple development team; automatic signing is already enabled. If your team cannot provision `com.hackinglab.toki`, use a bundle identifier registered to your team. The project intentionally does not embed a developer team ID or signing credentials.
3. Connect and unlock an iPhone running iOS 26 or newer, trust the computer, and enable Developer Mode if prompted. Select that iPhone as the run destination and run the app.
4. Tap **Test alarm in 30 seconds** and allow alarm access. If access was denied, use **Open TOKI Settings**, allow alarms, and return to the app.
5. Check the displayed target time, then lock the phone. Confirm audible delivery and dismissal from the system alarm screen. Return to TOKI and verify that another test can be scheduled. Also test leaving the app before delivery.
6. For cancellation, schedule another test and tap **Cancel / dismiss test alarm** before its target. Verify that it does not ring. Reopen the app while a test is pending and verify its target is retained.

AlarmKit provides system alarm presentation and sound. Simulator launch/build success does not establish physical-device audio, lock-screen delivery, timing, or permission behavior. Those checks require an actual iPhone. No audible-delivery test has been completed for this prototype yet.

## Validation

Verified on October 20, 2026 with Xcode 27.1: generic iOS Simulator build, unsigned generic iPhone build, simulator installation and launch, visual inspection of the initial screen, and the generated alarm usage-description/minimum-version settings. Both builds completed without compiler warnings. Alarm scheduling, authorization interaction, audible delivery, cancellation, and physical-device signing still need runtime verification on an iPhone.

## Command-line builds

The machine may still select standalone Command Line Tools. Set `DEVELOPER_DIR` per command to use Xcode without changing the machine-wide selection:

```sh
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild \
  -project ios/toki.xcodeproj -scheme toki \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath /tmp/toki-ios-build CODE_SIGNING_ALLOWED=NO build
```

For a hardware compile check, replace `iphonesimulator` with `iphoneos` and the destination with `generic/platform=iOS`. An unsigned build cannot be installed on a physical iPhone; run from Xcode with your team selected for that.

The source files live under `ios/toki/toki/` in a filesystem-synchronized Xcode group. Xcode user state, build products, and `.DS_Store` are ignored by `ios/.gitignore`. Do not commit signing keys or provisioning profiles.

## Next milestone

Verify native alarms on an iPhone first. Then add PocketBase user authentication with Keychain token storage, synchronization and offline event storage, recurrence using the shared fixtures in `backend/fixtures/recurrence.tsv`, and a matching Agenda. iOS scheduling and background execution must be validated separately from Android.

Reference: [Apple's AlarmKit scheduling guide](https://developer.apple.com/documentation/AlarmKit/scheduling-an-alarm-with-alarmkit).
