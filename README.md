# Fassistant

A small Android watchdog. It starts itself on boot, resists being killed, and reopens apps from a
list you choose when the system kills them.

Sideloaded onto your own phones. Not on the Play Store, so it gets to make choices a published app
could not.

- **minSdk 23** (Android 6.0)
- **targetSdk 25** — deliberate, see below
- **68 KB** release APK, zero runtime dependencies beyond the Kotlin standard library

## Two things Android does not allow

Both requirements collide with deliberate platform restrictions, and knowing the shape of them
explains most of the code.

**You cannot ask whether another app's process is alive.** `getRunningAppProcesses()` is restricted
to the caller's own process, and `/proc` has been unreadable since Android 7. So death is *inferred*
from an ordered ladder of signals in `liveness/`, each of which can answer "alive", "dead" or "no
opinion":

| Signal | What it proves |
| --- | --- |
| `ForegroundSignal` | The app was resumed recently, so it is alive. Needs usage access. |
| `ProcessSignal` | Exact, where the platform still allows it. Decided at runtime, never assumed. |
| `NotificationSignal` | An ongoing notification that vanishes while the app is not on screen means it died. Strongest signal available without root. |
| `MediaSignal` | Same, for anything that plays audio. |
| `StoppedFlagSignal` | Separates a user force-stop from a memory kill. Experimental — see *Unknowns*. |

When nothing is conclusive, each app's own mode decides what happens: reopen only on a death signal,
reopen whenever it is not the app on screen, or reopen on a timer. That is a real limit, not a
missing feature — without root there is no way to close it.

**You cannot launch an app from the background.** From Android 10, `startActivity()` from a process
with no visible window is silently dropped. The one exemption available to a sideloaded app is
*Draw over other apps*, granted per phone in `tools/setup-phone.sh`. When a reopen looks like it was
dropped, the app notices (the target never comes to the front) and posts a tappable notification
instead — an activity started from a notification the user taps is separately exempt.

## Why targetSdk 25

`compileSdk` and `targetSdk` are independent, so the app is built against Android 16 while opting
out of behaviour changes gated on the level it targets:

- plain background services and implicit broadcasts, which become extra revival triggers
- exact alarms without `SCHEDULE_EXACT_ALARM`
- every installed package visible without `QUERY_ALL_PACKAGES`
- no foreground-service type declarations, no notification runtime permission
- immune to the Android 15 narrowing of the overlay exemption, which applies from targetSdk 35

Android 15 and 16 refuse to install anything below targetSdk 24, so 25 clears the floor by one. If a
future release raises it, `adb install --bypass-low-target-sdk-block` buys time and moving to 28
is the real fix — it costs the first two bullets and nothing else.

`LOCKED_BOOT_COMPLETED` is deliberately not handled: the watchlist lives in credential-encrypted
storage, so there is nothing useful to do before the phone is unlocked.

## Staying alive

Five components, none of them a single point of failure. The service keeps rewriting the schedules
of the two that can restart it, so a cleared alarm table cannot orphan it.

- foreground service, `START_STICKY`, `stopWithTask="false"`, restarts itself in `onTaskRemoved`
- `TickAlarm` — exact alarm, re-armed on every fire, ~20s
- `TickJob` — persisted `JobScheduler` job, 15 min platform floor, the backstop
- `BootReceiver` — boot, quick-boot, and reinstall
- screen-on and unlock broadcasts as opportunistic extra ticks

`DeathLog` writes to disk and records our own exit reasons on Android 11+, because the interesting
entries come from a process that is about to be killed. That log is how you find out *why* a phone
keeps losing the service — read it in the app, or pull it with `adb`.

## Unknowns, and the probe that answers them

The `probe/` module is a throwaway that fills in what the docs would not confirm. Install it
alongside the app and run it on your oldest and newest phones:

1. Does `getRunningAppProcesses()` return other apps at targetSdk 25 on a modern device? If yes,
   detection becomes exact and the heuristics are a fallback nobody hits.
2. Same for `getRunningServices()`.
3. Is `ApplicationInfo`'s stopped bit (`1 shl 21`) readable and meaningful to a third-party app?
4. Does `getProcessesInErrorState()` return anything useful?

Delete `probe/` from `settings.gradle.kts` once the answers are written down here.

## Building

Needs JDK 17 and an Android SDK with platform 36. On macOS:

```
brew install openjdk@17
brew install --cask android-commandlinetools
```

Then point `local.properties` at the SDK and build:

```
./gradlew :app:dist          # release APK into dist/, versioned filename
./gradlew assembleDebug      # both modules
```

Gradle is pinned to 8.14.5 and AGP to 8.13.2. Do not move to AGP 9 without checking its minimum
`minSdk` — Android 6 support is the constraint, and Gradle 9.6 dropped an internal API that AGP 8.x
relies on, so the two must move together.

## Signing

An unsigned APK cannot be installed, so `keystore/fassistant.jks` is committed with a throwaway
password. That is on purpose: the signature has to stay stable for `adb install -r` to upgrade in
place, and a debug keystore tied to one machine would break that. Anyone with this repo can build an
update these phones will accept. Fine for personal devices; not fine if that ever changes.

## Versions

`VERSION_NAME` and `VERSION_CODE` in `gradle.properties` are the only place either number appears.
Bump both, tag `v<version>`, add a `CHANGELOG.md` entry. The version shows in the persistent
notification, so you can read what a phone is running off its status bar without plugging it in.

## Setting up a phone

ADB makes this quicker, but nothing here needs it.

**With ADB:**

```
tools/setup-phone.sh
```

Installs, then grants the overlay permission, usage access, notification access, the battery
exemption and the notification permission.

**Without ADB** — no cable, no wireless debugging, works on any version:

1. Serve the APKs to the phone over the local network:
   ```
   ./gradlew :app:dist :probe:assembleDebug
   tools/serve-and-collect.py
   ```
   It stages the built APKs, prints the address to open on the phone, and serves a page with the
   two downloads. Android will ask once whether to allow installs from the browser.
2. Open the app and work down the **Permissions** list. Each row has a **Fix** button that opens the
   exact Settings page for that grant. All four are user-grantable — the ADB commands are a
   convenience, not a requirement.
3. Tap **Manufacturer settings** and work through that checklist too.

Only two things are ADB-only, and neither is required: the `RUN_IN_BACKGROUND` app-ops, which most
phones also expose through the app's battery settings, and `dpm set-device-owner`.

Read the probe's findings and the watchdog's log with their **Copy** buttons rather than pulling
files over ADB. The same page has a paste box that posts the text straight back to the laptop —
reports land in `tools/serve/results/`, one file per phone, so there is no round trip through a
messaging app.

## Android version notes

Background activity launch blocking starts at **Android 10**. On Android 6 through 9 the silent
relaunch works with no overlay permission at all, so *Draw over other apps* only matters from
Android 10 onward. The app still asks for it everywhere, because it costs nothing and the phone
may be upgraded.

Notification runtime permission is Android 13+, so it is irrelevant below that.
