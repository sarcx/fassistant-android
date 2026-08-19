# Changelog

## 0.1.0 (unreleased)

First build. Runs on a OnePlus 5T (Android 10); long-running survival is not yet soak-tested.

Measured on that phone rather than assumed: `targetSdk 25` does **not** restore
`getRunningAppProcesses()`, `getRunningServices()` or `/proc` visibility. That restriction tracks
the OS version, not the level an app targets, so from Android 8 up detection rests on the
notification and media signals plus the per-app mode.

- Foreground watchdog service with five independent restart paths: boot receiver, reinstall
  receiver, exact alarm chain, persisted `JobScheduler` job, and screen-on broadcasts.
- Liveness ladder over five signals — recent foreground, process enumeration, ongoing notification,
  media session, package stopped bit — each able to abstain so a missing grant degrades detection
  rather than breaking it.
- Three per-app modes for when no signal is conclusive: reopen only on a death signal, keep in
  front, or reopen on a timer.
- App picker showing a measured detectability badge per app, so coverage is visible rather than
  assumed. Apps with no launcher activity are listed greyed out with the reason.
- Relaunch with per-app exponential backoff and a global rate cap. Detects a launch the platform
  dropped and falls back to a tappable notification.
- On-device log including our own process exit reasons on Android 11+.
- Manufacturer autostart screen that deep-links to the vendor settings this phone actually has.
- `probe/` module answering the four platform questions the plan could not settle from docs.
