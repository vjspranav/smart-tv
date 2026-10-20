# Launcher Plan 1: Performance Test and TV Investigations

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Measure, on the real TV, whether the riskiest visual effects of layouts A, G and N run smoothly, and answer the design's open questions about the TV. Hand the owner one results report to decide from.

**Architecture:**
- **Shared Mac-side tooling:** an ADB wrapper, a Gradle-free APK builder, and a perf runner with a tested gfxinfo parser. Plan 2 reuses all of it.
- **A throwaway native test app** (`com.livingroom.flamingo.perftest`, plain Android Views in Java). It renders three dummy "scenes" driven by D-pad key scripts.
- **Four investigations** on the TV, each producing a findings section in the report.

Plan 2 (the real launcher) is written after the owner has read the report.

**Tech Stack:**
- Java 8 source (`javac --release 8`), Android platform 34 jar, build-tools 34 (d8, aapt2, zipalign, apksigner), JDK 17 from Homebrew.
- Bash for tooling; Python 3 stdlib `unittest` (pytest is not installed).
- `adb` from Homebrew `android-platform-tools`.

**Spec:** `docs/superpowers/specs/2026-10-21-tv-launcher-design.md` (mockups in `docs/superpowers/specs/2026-10-21-tv-launcher-mockups/`)

## Global Constraints

**The TV and access**
- TV: TCL G10_4K_GB, Android 12 / API 31, armeabi-v7a, ~1.8 GB RAM, UI at 1920×1080, density 320 (960×540 dp).
- TV address: `192.168.0.12:5555`. Overridable with env `TV`.
- ADB from the Mac only, using the Pi's trusted key:

      adb kill-server; ADB_VENDOR_KEYS=$HOME/.android/pi_adbkey adb start-server; adb connect 192.168.0.12:5555

- The Raspberry Pi is not touched. Nothing is staged in its `/tmp`.
- The connection sometimes goes "offline". Tooling must reconnect once and then fail loudly, never silently.

**Build rules**
- Native Android UI only. No WebView, no cross-platform runtime, no third-party libraries in APKs.
- Test app package: `com.livingroom.flamingo.perftest`. Real launcher package (Plan 2): `com.livingroom.flamingo.launcher`.
- `minSdkVersion 26`, `targetSdkVersion 31`, compile against `android-34`.
- Smoothness targets, verbatim from the spec: **"under 1% janky frames, 95th-percentile frame time at or below 16 ms"**, measured with `dumpsys gfxinfo`. Also report PSS from `dumpsys meminfo`.
- Animate only alpha, translation and scale (plus rotation for the record). No live blur, no animated full-screen gradients, no per-frame full-screen redraw. The one exception is the G "layout" variant, which exists only to be measured against.

**Leaving the TV as it was**
- Every TV change in this plan is temporary and undone in the task that makes it: enabling the HOME alias, disabling `launcherx`. The test app is uninstalled in the last task.

## Review Focus

- **TV asleep or in standby when a perf run starts:** the run must refuse (wakefulness ≠ `Awake`) instead of recording garbage. Test in Task 3.
- **ADB drops to "offline" mid-run:** the runner reconnects once, and on second failure exits non-zero with a clear message. No partial result line. Test in Task 3.
- **Startup frames polluting the numbers:** stats must be reset *after* the scene has settled, so launch frames aren't counted. Test in Task 3.
- **`launcherx` left disabled if the check aborts halfway:** the TV would boot with no proper home. Task 8's script must re-enable it on any exit (trap). Test in Task 8.
- **Someone watching the TV during runs:** runs switch apps and inputs on screen, so every on-TV task starts by confirming with the owner that the TV is free. Steps in Tasks 4–8.

---

## File Structure

```
tv-clean/
  .gitignore
  tools/
    tv-adb                 # wrapper: ensure connection, then `adb -s $TV "$@"`
    build-apk.sh           # Gradle-free APK build for an app dir with app.conf
    perf-run.sh            # run one scene with a key script, print one result line
    gfxinfo_parse.py       # parse `dumpsys gfxinfo` + `meminfo` text → dict / JSON
    test_gfxinfo_parse.py  # unittest
    testdata/gfxinfo-sample.txt, meminfo-sample.txt
  perftest/                # THROWAWAY test app
    app.conf
    AndroidManifest.xml
    res/values/strings.xml
    src/com/livingroom/flamingo/perftest/
      Main.java            # picks scene from intent extra "scene"
      Art.java             # dummy bitmaps, generated once
      SceneA.java          # A: full-screen artwork crossfade following focus
      SceneG.java          # G: widening columns, mode "layout" | "transform"
      SceneN.java          # N: static dot grid, widgets, rotating record
    keys/A.txt G.txt N.txt # D-pad scripts
  investigations/
    launcherx-check.sh
  docs/superpowers/reports/2026-10-launcher-groundwork.md   # results for the owner
```

---

### Task 1: Project hygiene

**Files:**
- Create: `.gitignore`
- Delete: `spike/` (the WebView preview spike; its app was already uninstalled)

- [ ] **Step 1:** `git init` in `tv-clean/`.
- [ ] **Step 2:** Write `.gitignore` with these lines: `.superpowers/`, `**/build/`, `*.apk`, `*.keystore`, `__pycache__/`.
- [ ] **Step 3:** Delete `spike/` (`rm -rf spike`). This is the cleanup owed for the preview spike.
- [ ] **Step 4:** Verify: `git status --short` lists `.gitignore`, `debloat.sh` and `docs/`, and no `spike/` or `.superpowers/`.
- [ ] **Step 5:** Commit: `git add -A && git commit -m "chore: start tv-clean repo; remove preview spike"`.

### Task 2: ADB wrapper and Gradle-free builder

**Files:**
- Create: `tools/tv-adb`, `tools/build-apk.sh`, `perftest/app.conf`, `perftest/AndroidManifest.xml`, `perftest/res/values/strings.xml`, `perftest/src/com/livingroom/flamingo/perftest/Main.java` (stub)

**Interfaces:**
- Produces:
  - `tools/tv-adb <adb args…>`: ensures the adb server holds the Pi key (`ADB_VENDOR_KEYS=$HOME/.android/pi_adbkey`) and `$TV` (default `192.168.0.12:5555`) is in state `device`. It reconnects once if offline, else exits 2 with `tv-adb: TV not reachable`. Then it runs `adb -s $TV "$@"`.
  - `tools/build-apk.sh <appdir>`: reads `<appdir>/app.conf` (shell vars `NAME`, `SRC`, `KEYSTORE`, `KEYALIAS`, `KEYPASS`) and writes `<appdir>/build/$NAME.apk`. It creates the keystore if missing. It follows the steps of `tv-remote/tv-bridge/build.sh` (javac `--release 8` → d8 `--min-api 26` → aapt2 compile/link with `-A assets` if present → zipalign → apksigner). Exits non-zero on any failure.

- [ ] **Step 1:** Write `tools/tv-adb` and `chmod +x` it.
- [ ] **Step 2:** Verify: `tools/tv-adb shell getprop ro.product.model` prints `Smart TV Pro`.
- [ ] **Step 3:** Write `tools/build-apk.sh` and the stub test app:
  - `app.conf` sets `NAME=perftest`, `SRC=src`, `KEYSTORE=perftest.keystore`, `KEYALIAS=perftest`, `KEYPASS=perftest123`.
  - The manifest uses `<application android:label="Flamingo Perf Test" android:hardwareAccelerated="true">`, with one exported activity `.Main` (theme `@android:style/Theme.Black.NoTitleBar.Fullscreen`, MAIN + LEANBACK_LAUNCHER).
  - Also add a **disabled** `<activity-alias android:name=".Home" android:targetActivity=".Main" android:enabled="false" android:exported="true">` with MAIN + HOME + DEFAULT categories. Task 8 uses it.
  - `Main` is an empty black Activity for now.
- [ ] **Step 4:** Verify: `tools/build-apk.sh perftest` exits 0, and `apksigner verify perftest/build/perftest.apk` passes.
- [ ] **Step 5:** Verify on the TV: `tools/tv-adb install -r perftest/build/perftest.apk` prints `Success`.
- [ ] **Step 6:** Commit: `git add tools perftest && git commit -m "feat: tv-adb wrapper, gradle-free builder, perftest stub"`.

### Task 3: gfxinfo parser and perf runner

**Files:**
- Create: `tools/gfxinfo_parse.py`, `tools/test_gfxinfo_parse.py`, `tools/testdata/gfxinfo-sample.txt`, `tools/testdata/meminfo-sample.txt`, `tools/perf-run.sh`

**Interfaces:**
- Produces:
  - `gfxinfo_parse.parse_gfx(text: str) -> dict` with keys `frames:int`, `janky:int`, `janky_pct:float`, `p50:int`, `p90:int`, `p95:int`, `p99:int` (ms).
  - `gfxinfo_parse.parse_pss_kb(meminfo_text: str) -> int` (the `TOTAL PSS:` value).
  - CLI: `python3 tools/gfxinfo_parse.py <gfx.txt> <meminfo.txt> <label>` prints one JSON line. That line is `{"label":…, "frames":…, "janky_pct":…, "p50":…, "p90":…, "p95":…, "p99":…, "pss_kb":…, "pass": janky_pct < 1.0 and p95 <= 16}`.
  - `tools/perf-run.sh <label> <component> <keysfile> [extra am args…]` prints exactly that JSON line and nothing else on stdout.

- [ ] **Step 1:** Capture real samples from the TV:
  - `tools/tv-adb shell dumpsys gfxinfo com.android.tv.settings > tools/testdata/gfxinfo-sample.txt` (open Settings first so it has frames).
  - `tools/tv-adb shell dumpsys meminfo com.android.tv.settings > tools/testdata/meminfo-sample.txt`.
- [ ] **Step 2: Write the failing tests** in `tools/test_gfxinfo_parse.py`, comparing against the values read by eye from the sample files:
  - `test_parses_sample`: `parse_gfx(sample)` equals the frame count, janky count, janky %, and p50/p90/p95/p99 from the sample.
  - `test_pss`: `parse_pss_kb(meminfo_sample)` equals the `TOTAL PSS:` number.
  - `test_pass_flag`: synthetic text with `Janky frames: 5 (0.50%)` and `95th percentile: 16ms` gives `pass == True`; `17ms` gives `False`; `1.00%` gives `False`.
  - `test_missing_fields_raise`: empty text raises `ValueError` (never returns zeros).
- [ ] **Step 3:** Run `python3 -m unittest tools/test_gfxinfo_parse.py -v`. Expect FAIL with an import error.
- [ ] **Step 4:** Implement `tools/gfxinfo_parse.py` with regexes on `Total frames rendered:`, `Janky frames: N (X%)` and `Nth percentile: Xms`. Use the first (app-level) block only.
- [ ] **Step 5:** Run the tests again. Expect all PASS.
- [ ] **Step 6:** Write `tools/perf-run.sh`, in this order:
  1. Check `dumpsys power` contains `mWakefulness=Awake`, else exit 3 with `perf-run: TV not awake`.
  2. `am start -W -S -n <component> [extra args]`, then sleep 3 s to let it settle.
  3. `dumpsys gfxinfo <pkg> reset`.
  4. Replay the keys file. Each line is `KEYCODE_X <ms>`. Send them as a single on-device shell script (`input keyevent X; sleep s; …`) to avoid one adb round-trip per key.
  5. Dump gfxinfo and meminfo to a temp dir, then call the parser.
  - Any `tv-adb` failure exits non-zero, with nothing on stdout.
- [ ] **Step 7:** Verify: `tools/perf-run.sh smoke com.livingroom.flamingo.perftest/.Main perftest/keys/smoke.txt` prints one JSON line. Create `keys/smoke.txt` with 5 × `KEYCODE_DPAD_RIGHT 300`.
- [ ] **Step 8:** Verify the failure paths:
  - With the TV in standby (`tools/tv-adb shell input keyevent KEYCODE_SLEEP`), it exits 3 with no stdout. Wake the TV after (`KEYCODE_WAKEUP`).
  - With `TV=192.168.0.250:5555`, it exits 2 with no stdout.
- [ ] **Step 9:** Commit: `git add tools && git commit -m "feat: perf runner and tested gfxinfo parser"`.

### Task 4: Scene A, full-screen artwork following focus

Before running on the TV, ask the owner whether the TV is free for about 10 minutes.

**Files:**
- Create: `perftest/src/com/livingroom/flamingo/perftest/Art.java`, `SceneA.java`, `perftest/keys/A.txt`
- Modify: `Main.java` (choose the scene from extra `scene`: `A` | `G` | `N`)

**Interfaces:**
- Produces:
  - `Art.hero(int i): Bitmap`: five 960×540 `RGB_565` bitmaps (pre-dimmed radial-gradient "artwork" in the app colours `#ff0033`, `#e50914`, `#1f80e0`, `#00a8e1`, `#0070d1`). Generated once and cached.
  - `Art.banner(int i): Bitmap`: five 320×180 banners.

- [ ] **Step 1:** Implement `SceneA` (a `FrameLayout`):
  - Two stacked full-screen `ImageView`s, scaled up from the 960×540 art.
  - On focus change, set the next art on the back view and crossfade with `animate().alpha()` over 220 ms (`withLayer()`), then swap the views.
  - Title, meta and a progress bar on the left.
  - A row of five banners with focus = `scaleX/Y 1.08` over 150 ms plus a white 4 dp stroke.
- [ ] **Step 2:** Write `keys/A.txt`: 40 key presses alternating runs of RIGHT ×4 and LEFT ×4, 250 ms apart, then 10 at 120 ms apart (fast scrolling).
- [ ] **Step 3:** Build, install and screenshot (`tools/tv-adb exec-out screencap -p > /tmp/…`). Check the scene renders: hero, title and five banners, with nothing clipped.
- [ ] **Step 4:** Run `tools/perf-run.sh A com.livingroom.flamingo.perftest/.Main perftest/keys/A.txt --es scene A` three times. Keep all three JSON lines.
- [ ] **Step 5:** Commit with the three lines pasted into the commit message body: `git commit -m "perf: scene A"`.

### Task 5: Scene G, widening columns in two variants

**Files:**
- Create: `SceneG.java`, `perftest/keys/G.txt`

- [ ] **Step 1:** Implement `SceneG` with five columns as in the G3 mockup: top rule, mono number, name, and an art block in the focused column. Extra `mode` selects the variant:
  - **`layout`:** the focused column's width animates from 1× to 2.6× with a `ValueAnimator` changing `LayoutParams.weight` (re-layout each frame). This is the honest version of the mockup.
  - **`transform`:** fixed widths. The focused column's content is revealed with alpha and translation, and its neighbours slide with `translationX` only (no re-layout).
- [ ] **Step 2:** Write `keys/G.txt`: the same pattern as A.
- [ ] **Step 3:** Build, install and screenshot both modes. Check the layout fits with no clipping.
- [ ] **Step 4:** Run `perf-run.sh` three times for each mode (`--es scene G --es mode layout` / `transform`).
- [ ] **Step 5:** Commit with the six lines in the body.

### Task 6: Scene N, dot grid, widgets and record

**Files:**
- Create: `SceneN.java`, `perftest/keys/N.txt`

- [ ] **Step 1:** Implement `SceneN`:
  - **Dot grid:** a static window background using a `BitmapShader` with a 24 px tile and a 2 px dot at 9% white. It's drawn once and never invalidated.
  - **Clock:** dot-matrix style, as a placeholder bitmap font or `Typeface.MONOSPACE`. The real Doto font comes in Plan 2.
  - **Widgets:** five pill buttons plus three rounded widgets. Focus = background swap plus `scale 1.05`.
  - **Record:** a 300 px vinyl bitmap rotating with `animate().rotationBy(360)` on a hardware layer, at 1 turn per 4 s, looping. Extra `record=on|off`.
- [ ] **Step 2:** Write `keys/N.txt`: the same pattern as A.
- [ ] **Step 3:** Build, install and screenshot. Check the layout.
- [ ] **Step 4:** Run `perf-run.sh` three times with `record=off`, three times with `record=on`, plus one idle run: an empty keys file with a single `KEYCODE_UNKNOWN 10000` line, to measure the record's idle cost.
- [ ] **Step 5:** Commit with the lines in the body.

### Task 7: How much each app shares while playing (media sessions)

This needs the owner. Ask them to play about 30 seconds of something in YouTube, Netflix, Hotstar, Prime Video and Spotify, one at a time.

- [ ] **Step 1:** For each app, while it plays, run `tools/tv-adb shell dumpsys media_session` and save it to `investigations/media-<app>.txt`.
- [ ] **Step 2:** For each app, record what is present: package, title, subtitle/artist, artwork (yes/no and size), duration, position, and whether the session survives pause.
- [ ] **Step 3:** Write the table into the report under "Continue: what each app shares".
- [ ] **Step 4:** Commit `investigations/` and the report.

### Task 8: Can Google TV's home be turned off safely?

Ask the owner for a free 10 minutes first. The TV's home screen changes during this.

**Files:**
- Create: `investigations/launcherx-check.sh`

- [ ] **Step 1:** Write `launcherx-check.sh`:
  1. Set a `trap` on EXIT/INT/TERM that runs `pm enable --user 0 com.google.android.apps.tv.launcherx` and `pm disable --user 0 com.livingroom.flamingo.perftest/.Home`, then sends `KEYCODE_HOME`.
  2. Enable the `.Home` alias and run `pm disable-user --user 0 com.google.android.apps.tv.launcherx`.
  3. For each check, send the key or intent, wait 3 s, and record `mResumedActivity` and `mCurrentFocus`:
     - **Home:** `KEYCODE_HOME` → expect the perftest Main.
     - **Voice:** `KEYCODE_SEARCH`, then `KEYCODE_ASSIST` → expect a `com.google.android.katniss` window.
     - **Input:** `KEYCODE_TV_INPUT` → expect a TCL input panel.
     - **Screensaver:** `settings get secure screensaver_components`, then start it with `am start -n com.android.systemui/.Somnambulator` → expect a dream window.
     - **Play Store:** `monkey -p com.android.vending 1` → expect Vending resumed.
     - **Casting:** `pidof com.google.android.apps.mediashell` is non-empty, and the owner casts a YouTube video from their phone.
  4. Print a PASS/FAIL table.
- [ ] **Step 2:** Run it. Then reboot the TV (`tools/tv-adb reboot`) *during* the disabled state (re-run steps 2–3 and reboot before the trap fires), and record what the TV boots into. Then restore by hand: enable launcherx and disable the alias. Confirm the Google TV home is back.
- [ ] **Step 3:** Verify the restore: `pm list packages -d` doesn't list `launcherx`. `KEYCODE_HOME` shows the `launcherx` HomeActivity.
- [ ] **Step 4:** Test the trap: run the script and Ctrl-C it after step 2. Check the TV is restored (same checks as Step 3).
- [ ] **Step 5:** Write "Turning off Google TV home" into the report: the PASS/FAIL table, the boot result, and a recommendation (disable, or default-home with `launcherx` enabled). Commit.

### Task 9: TCL picture mode, sound mode, sleep timer

**Files:**
- Create: `investigations/tcl-settings.md` (working notes)

- [ ] **Step 1:** List candidate surfaces:
  - `dumpsys package` for `com.tcl.tv`, `com.android.tv.settings`, `com.tcl.suspension`, `com.tcl.providers.config`, `com.tcl.systemserver`: exported activities, actions, providers, and the permissions they require.
  - `tools/tv-adb shell service list | grep -i -E "tcl|tvos|picture|sound|pq"`.
  - `content query` on any TCL provider URIs found.
- [ ] **Step 2:** Pull and decompile the TCL framework:
  - `tools/tv-adb shell pm path com.tvos` and `ls /system_ext/framework`, then `adb pull` the jar into `investigations/tvos/` (gitignored; add `investigations/tvos/` to `.gitignore`).
  - `brew install jadx`, then `jadx -d investigations/tvos/src <jar>`.
  - Find the picture-mode, sound-mode and sleep-timer getters and setters, and the permissions they check.
- [ ] **Step 3:** For each of the three settings, establish one of three outcomes, with the exact verified ADB/API call:
  - (a) readable and writable by a normal app;
  - (b) readable only;
  - (c) neither. Then record the exact intent that opens TCL's own panel for it.
  - Prove (a)/(b) by changing the value with the found call from a shell and seeing it change on screen. Ask the owner first, and restore the original value.
- [ ] **Step 4:** Write "Picture / Sound / Sleep" into the report: an outcome per setting plus the call. Commit (without `investigations/tvos/`).

### Task 10: What HDMI state means on this TV

This needs the owner to turn the PS5 on and off once.

- [ ] **Step 1:** With the PS5 off, then on, save:
  - `dumpsys tv_input` (the state per `HW15/16/17`);
  - `dumpsys hdmi_control` (the CEC device list: logical address, power status, OSD name, port).
- [ ] **Step 2:** Decide the best source for "device on / standby / empty" per port, and whether the CEC OSD name (e.g. "PS5") can auto-name HDMI 1. Write "HDMI inputs" into the report. Commit.

### Task 11: Results report for the owner, and uninstall

**Files:**
- Create: `docs/superpowers/reports/2026-10-launcher-groundwork.md` (sections from Tasks 7–10 already exist; add the perf section on top)

- [ ] **Step 1:** Perf section: one row per scene/variant with the median of 3 runs. Columns: frames, janky %, p95 ms, p99 ms, PSS MB, PASS/FAIL against "under 1% janky frames, 95th-percentile frame time at or below 16 ms". Plus the N idle-record row.
- [ ] **Step 2:** For every FAIL, write the two choices in plain words: keep the look and invest in making it smooth (with what that would take), or the lighter version (what it looks like).
- [ ] **Step 3:** Uninstall the test app: `tools/tv-adb uninstall com.livingroom.flamingo.perftest`. Then check `pm list packages | grep flamingo` is empty and `launcherx` is enabled.
- [ ] **Step 4:** Commit the report. Publish it to the owner (as an artifact page), and ask for their decision on each FAIL row. Those decisions are the input to Plan 2.
