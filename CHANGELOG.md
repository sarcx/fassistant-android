# Changelog

## 0.2.0

The app can now update itself, and releases are built by CI.

- Checks one URL once a day for a newer build, downloads it, and offers it. The address is baked in
  at build time and points at this repository's latest release, so it never has to change.
- Two checks before you are ever asked to install: the SHA-256 from the manifest, and the APK's
  signing certificate, which must match the running app's.
- The install itself is a normal Android confirmation. Silent installation needs device owner or
  root, so it is not available here.
- New **Updates** screen: current version, what is available, the release notes, and an address
  field for installing from somewhere else — a machine on your own network, for instance.
- **Install unknown apps** joins the permissions list on Android 8 and later, with a deep link.
- Tagging `v<version>` builds, signs and publishes a release, then deletes older releases so only
  the newest is ever published.

**The signing key has moved out of the repository, and has been replaced.** It was committed while
this was local-only; that is not safe for a public repo, because the key is exactly what installed
copies use to decide an update is genuine. The new key is RSA 4096 with a generated password, read
from `local.properties` or CI secrets, and `*.jks` is gitignored.

Because the key changed, **0.1.0 has to be uninstalled before 0.2.0 can be installed** — Android
refuses an update signed by a different key. This is the only release that needs that; 0.2.0
onwards upgrades in place.

## 0.1.0

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
