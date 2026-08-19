#!/bin/bash
# One-time setup for a phone. Connect it over adb first, then run this from the repo root.
#
#   tools/setup-phone.sh [path/to.apk]
#
# Everything here is grantable over adb. The manufacturer autostart toggles are not, and the app
# has a screen that deep-links to them — do those by hand afterwards.

set -uo pipefail

PKG=dev.todor.fassistant
ADB="${ADB:-adb}"
APK="${1:-}"

if [ -z "$APK" ]; then
  APK=$(ls -t dist/*.apk 2>/dev/null | head -1)
fi

if [ -z "$APK" ] || [ ! -f "$APK" ]; then
  echo "No APK found. Run ./gradlew :app:dist first, or pass a path as the first argument." >&2
  exit 1
fi

step() {
  local label="$1"
  shift
  printf '==> %s\n' "$label"
  if ! "$ADB" shell "$@" >/dev/null 2>&1; then
    printf '    could not set this over adb — do it by hand in Settings\n'
  fi
}

printf '==> installing %s\n' "$APK"
if ! "$ADB" install -r "$APK"; then
  # Android 15 and later refuse to install below targetSdk 24; this app sits at 25, but if a
  # future release raises the floor past us, the block can still be bypassed for sideloading.
  "$ADB" install -r --bypass-low-target-sdk-block "$APK"
fi

# The one that makes reopening possible at all.
step "allow drawing over other apps"     appops set "$PKG" SYSTEM_ALERT_WINDOW allow

# Tells us which app is in front.
step "allow usage access"                appops set "$PKG" GET_USAGE_STATS allow

# Our strongest death signal, and what media-session checks ride on.
step "allow reading notifications"       cmd notification allow_listener "$PKG/dev.todor.fassistant.liveness.NotificationWatcher"

# Stops Doze deferring the checks while the screen is off.
step "exempt from battery optimisation"  dumpsys deviceidle whitelist "+$PKG"
step "allow running in the background"   appops set "$PKG" RUN_IN_BACKGROUND allow
step "allow any background work"         appops set "$PKG" RUN_ANY_IN_BACKGROUND allow

# Only needed on Android 13+, and only because the status notification would otherwise be hidden.
step "allow posting notifications"       pm grant "$PKG" android.permission.POST_NOTIFICATIONS

printf '==> starting the app\n'
"$ADB" shell am start -n "$PKG/dev.todor.fassistant.ui.MainActivity" >/dev/null 2>&1

cat <<'DONE'

Done. Two things left, both by hand on the phone:

  1. Open the app, tap "Manufacturer settings", and work through the list. Skipping these is the
     most common reason a watchdog quietly stops working.
  2. Tap "Add apps" and pick what to watch. Each row shows how well that app can be detected.

Optional, dedicated phones only — makes the app impossible to uninstall or force-stop, and needs a
factory-reset phone with no accounts signed in:

  adb shell dpm set-device-owner dev.todor.fassistant/.AdminReceiver

DONE
