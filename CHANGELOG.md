# Changelog

## 0.7.0

Says whether the phone told it about a reboot, which is the difference between "we were never
asked to start" and "we were asked and failed".

The boot broadcast is now recorded the instant it arrives, before anything can return early. The
main screen compares that against the time the phone actually started, and if the broadcast never
came it says so plainly along with what to do about it. Nearly always this is the manufacturer
withholding permission to launch automatically.

**Corrects a wrong diagnosis in 0.5.0.** It claimed a force-stop whenever both the restart alarm
and the scheduled job were missing. A reboot always clears alarms, so after any restart that
message could appear with no force-stop involved. Only the job survives a reboot, so only a missing
job means anything, and the check now rests on that alone.

The log also records when a boot broadcast is received but ignored because the watchdog is switched
off — previously that returned silently and looked identical to never being told.

## 0.6.0

Watches apps that have no icon, such as the plugin a remote-control app uses to drive the
touchscreen. Two things stopped that working, and both are fixed.

The picker only ever listed apps with a launcher icon, so a plugin never appeared at all. There is
now an **Also show apps with no icon** switch, off by default so the usual list stays short.

Reopening also assumed every app had a screen to open. It now tries, in order: the app's icon, a
leanback icon, any exported activity, any exported background service, and finally reading from an
exported content provider. Starting any part of a package starts its process, which is the point.

Starting a background part is **invisible** — nothing appears on screen and nothing has to be put
back afterwards. For an app with no icon that is the normal route, and a better one than opening a
screen.

The detail screen now says which of those routes a given app would use, and the **Cannot start**
badge now means exactly that rather than merely "has no icon".

## 0.5.0

Makes downtime visible. After a week of running, the app stopped and could say nothing about it —
not even when. The tick timestamp only ever lived in memory, so it died with the process.

- The tick is now written to disk, at most once a minute. Downtime survives the process being
  killed, so the main screen can say when the watchdog last actually ran.
- On starting, the service compares that timestamp with the clock and records any real gap. The
  main screen lists recent ones with how long each lasted.
- The main screen reports whether the restart alarm and the scheduled job still exist. If the
  service is stopped and both are gone, it says so plainly: **that is what a force-stop looks
  like**, and a force-stopped app cannot restart itself.

That last point is the honest answer to why nothing recovered. Android clears a stopped package's
alarms and withholds its broadcasts, so every one of the five restart paths is disabled at once,
reboot included. It is deliberate platform behaviour, not a fault in the mesh, and nothing an
ordinary app can do will get around it — which is why the manufacturer settings matter so much on
phones that force-stop apps on their own.

## 0.4.1

Fixes self-update, which never worked. Every update was rejected as "signed by someone else",
including genuine ones.

On Android 9 and later, `getPackageArchiveInfo()` returns a null `signingInfo` even when asked for
signing certificates, so the check read nothing for the downloaded file and treated that as proof
of forgery. It now falls back to the deprecated `signatures` field, which is the only one populated
for an APK file.

The worse mistake was the design: an unreadable certificate was treated as a failed check. Android
refuses an update signed with a different key by itself, so that refusal is the real guarantee and
this check only exists to give a clear reason instead of a failed install prompt. It now blocks
only on a genuine mismatch; if the certificates cannot be read it proceeds and lets the installer
decide. A mismatch logs both fingerprints, so the next such failure can be diagnosed rather than
guessed at.

**This fix cannot arrive through the updater**, because the broken check is in the version you are
running. Install 0.4.1 by hand once; self-update works from there on.

## 0.4.0

Reopening an app no longer leaves you looking at it.

Android has no way to start another app's activity without bringing it to the front, so the app is
reopened and then whatever was on screen before is brought back — once per check, however many apps
were reopened. If nothing is known to have been in front, the home screen is used instead.

- New **Go back to what I was doing** switch on the main screen, on by default.
- Apps set to *keep it in front* are exempt, since that mode wants the opposite.
- Reopening no longer animates, because the transition was the most visible part of an operation
  meant to go unnoticed.

Two honest limits. The app being reopened may still flick past for a moment; there is no way to
avoid that without root or device owner. And a backgrounded app is easier for the system to kill
than one on screen, so this trades a little survivability for not hijacking the screen — worth
pairing with the battery exemptions from 0.3.0.

## 0.3.0

Applies the phone's own anti-kill settings to the apps being watched, not just to Fassistant.
Stopping an app from being killed works better than reopening it afterwards.

- **Keep watched apps alive** screen: every watched app with its battery status read live, and a
  button per app that still has limits. Reachable from the main screen, which now says how many
  watched apps can still be stopped to save battery.
- The same section appears per app on its detail screen, next to the settings that decide what
  happens once it does die.
- Manufacturer autostart screens are offered here too. They list every app at once and the phone
  gives no way to read back what was chosen, so they stay a manual checklist, now recorded per app.

Only one of Fassistant's own permissions matters to a watched app's survival — the
battery-optimisation exemption. Drawing over other apps, usage access and notification access
decide what Fassistant may watch and start; they do nothing for whether a watched app stays
resident, so they are deliberately not offered. The screen says so, rather than leaving it to be
guessed at.

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
