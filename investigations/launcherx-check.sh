#!/bin/sh
# RETIRED 2026-10-21 — do not run. Disabling launcherx is unsafe on this TV (see report section 2);
# known flaws: Ctrl-C does not abort (trap returns), restore() does not verify success.
echo "launcherx-check.sh is retired; see docs/superpowers/reports/2026-10-launcher-groundwork.md" >&2; exit 1
# Plan 1 / Task 8: does the TV keep working with Google TV's home (launcherx) disabled?
# Usage: investigations/launcherx-check.sh [--no-restore]
#   --no-restore leaves launcherx disabled on exit (used once, to test a reboot); restore by hand after.
# Always restores on Ctrl-C or error unless --no-restore was given.
here="$(cd "$(dirname "$0")/.." && pwd)"
A="$here/tools/tv-adb"
LX=com.google.android.apps.tv.launcherx
APK="$here/perftest/build/perftest.apk"
# Android 12 forbids the shell from enabling another app's component, so the check installs a
# build whose HOME alias is enabled in the manifest, and restore reinstalls the normal build.
build() {
  (cd "$here/perftest" && sed "$1" AndroidManifest.xml > build-manifest.xml) &&
  MANIFEST=build-manifest.xml "$here/tools/build-apk.sh" "$here/perftest" >/dev/null && rm -f "$here/perftest/build-manifest.xml" &&
  "$A" install -r "$APK" >/dev/null
}

restore() {
  "$A" shell pm enable --user 0 $LX >/dev/null
  build 's/x/x/'
  "$A" shell input keyevent KEYCODE_HOME
  echo "restored: launcherx enabled, normal perftest (no home alias) installed"
}
[ "$1" = --no-restore ] || trap 'restore' EXIT INT TERM

front() { "$A" shell "dumpsys window | grep -m1 mCurrentFocus" | sed 's/.*u0 //; s/}.*//'; }
check() {  # name, expected-substring, command...
  name="$1"; want="$2"; shift 2
  "$A" shell "$@" >/dev/null 2>&1; sleep 4
  got="$(front)"
  case "$got" in *"$want"*) r=PASS ;; *) r=FAIL ;; esac
  printf '%-12s %-4s %s\n' "$name" "$r" "$got"
  "$A" shell input keyevent KEYCODE_BACK; sleep 1
}

build 's/android:enabled="false"/android:enabled="true"/' || exit 1
"$A" shell pm disable-user --user 0 $LX
sleep 2
check Home        perftest        input keyevent KEYCODE_HOME
check Voice       katniss         input keyevent KEYCODE_SEARCH
check Assistant   katniss         input keyevent KEYCODE_ASSIST
check Input       tcl             input keyevent KEYCODE_TV_INPUT
check Screensaver dream            am start -n com.android.systemui/.Somnambulator
check PlayStore   vending         monkey -p com.android.vending -c android.intent.category.LEANBACK_LAUNCHER 1
printf '%-12s %s\n' Cast "mediashell pid: $("$A" shell pidof com.google.android.apps.mediashell)"
printf '%-12s %s\n' Screensaver-component "$("$A" shell settings get secure screensaver_components)"
