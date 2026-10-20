# Launcher Plan 2: Flamingo Launcher

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the native launcher `com.livingroom.flamingo.launcher` with layouts A, G and N, each with Home and Everything screens and identical functions. Prove it is smooth and overflow-free on the TV, then make it the TV's home with the safer XDA method.

**Architecture:**
- **Pure-Java model** (no Android imports, JUnit-tested on the Mac): config, app ordering, watch history, HDMI state.
- **Android "sources"** feed it from the TV: apps, media sessions, Watch Next, HDMI, TCL picture mode.
- **Three layout views** render the same `Screen` data in their own style, on a shared UI kit. The kit guarantees focus room, ellipsis and fit, and includes an on-device overflow checker.
- **One activity** hosts everything.

**Tech Stack:**
- Java 8 source, plain Android Views, no AndroidX or third-party libraries in the APK.
- Built with `tools/build-apk.sh` (Gradle-free).
- JUnit 4.13.2 + Hamcrest 1.3 for Mac-side tests (jars fetched by script, not committed).
- Fonts (SIL OFL): Inter Tight, Instrument Serif, Space Mono, Doto.

**Spec:** `docs/superpowers/specs/2026-10-21-tv-launcher-design.md`. Results that drive this plan: `docs/superpowers/reports/2026-10-launcher-groundwork.md`. Mockups: `docs/superpowers/specs/2026-10-21-tv-launcher-mockups/`.

## Global Constraints

**The TV and access**
- TV: TCL G10_4K_GB, Android 12 / API 31, armeabi-v7a, ~1.8 GB RAM, UI 1920×1080 at density 320. Use pixel units on a 1920×1080 canvas.
- ADB from the Mac via `tools/tv-adb` only. The Pi is never touched.

**Build rules**
- Package `com.livingroom.flamingo.launcher`; `minSdkVersion 26`, `targetSdkVersion 31`, compiled against `android-34`.
- Native Views only. No WebView, Flutter, Compose or AndroidX.
- Smoothness target, measured on the TV: **under 1% janky frames, 95th-percentile frame time at or below 16 ms**. Report PSS at idle on Home.
- Animate alpha, translation, scale and rotation only. No live blur, no animated full-screen gradients, no per-frame full-screen redraws.
- The record spins **only while Spotify is playing**.

**Layout rules**
- No overflow, ever. Every text is single-line with ellipsis or fit-to-box.
- Focus states never clip: scale, ring and borders stay fully visible at row ends, screen edges, top and bottom.
- Panels have inner padding. Text never overlaps neighbours.

**TV safety**
- **Never disable `com.google.android.tungsten.setupwraith`** except for the seconds in Task 13 step 4, and only with the owner present.
- Never change TV settings without telling the owner first. Every TV change is undoable by a script in this repo.

## Review Focus

- **App installed or removed while the launcher is open:** the Apps list and Home update, with no crash and no stale banner. Test in Task 6.
- **A main app is uninstalled or disabled** (e.g. Hotstar): its Home slot is skipped, not shown blank, and config keeps the choice for when it returns. Test in Task 3.
- **Media session with null or empty title, or a huge artwork bitmap:** history keeps the app name only; artwork is downscaled to 960×540 RGB_565 before storing. Test in Task 4 (model) and Task 6 (downscale).
- **TCL library missing or throwing** (another firmware): the Picture value shows "Open", and OK opens TCL's Picture screen, with no crash. Test in Task 6.
- **Launcher process killed, or TV reboot:** history, config and HDMI names survive; the first frame of Home appears without waiting on slow sources. Test in Task 11.

---

## File Structure

```
tv-clean/
  tools/
    junit.sh                 # fetch junit/hamcrest jars to tools/lib (gitignored); run Mac-side tests
    keys_to_monkey.py        # key file → monkey script (one long-lived injector)  [+ test]
    perf-run.sh              # MODIFIED: monkey injector, awake re-check, usage, foreground check
    overflow-check.sh        # launch every screen (normal + stress), collect OVERFLOW lines
    grant-launcher.sh        # one-time grants for the launcher
    become-home.sh           # safer-XDA takeover with watcher
    undo-home.sh             # re-enable Google TV home
  launcher/
    app.conf  AndroidManifest.xml
    res/values/strings.xml  res/drawable/banner.png
    assets/fonts/*.ttf
    src/com/livingroom/flamingo/launcher/
      model/   HomeConfig.java  AppEntry.java  AppOrder.java  MediaItem.java  History.java  HdmiPort.java
      data/    Apps.java  MediaListener.java  WatchNext.java  Hdmi.java  TvSettings.java  Store.java  Sources.java
      ui/      Kit.java  Fonts.java  Overflow.java  Screen.java  LayoutView.java  LayoutA.java  LayoutG.java  LayoutN.java  SettingsView.java
      Home.java
    test/com/livingroom/flamingo/launcher/model/*Test.java
```

**Shared types** (defined in Task 3 and used everywhere):
- `AppEntry { String pkg; String label; boolean leanback; }`
- `MediaItem { String pkg; String title; String subtitle; long positionMs; long durationMs; long updatedAt; boolean playing; String artPath; }`
- `HdmiPort { int port; String inputId; String name; State state; enum State { ON, STANDBY, EMPTY } }`
- `Screen`: an immutable snapshot passed to layouts.
  - `List<MainSlot> main` (`MainSlot { AppEntry app | HdmiPort port; MediaItem cont; }`)
  - `List<HdmiPort> inputs`, `List<AppEntry> apps`, `MediaItem listen`
  - `String pictureValue`, `String clock`, `String date`, `HomeConfig.Layout layout`

---

### Task 1: Launcher skeleton, test runner and a better perf runner

**Files:**
- Create: `launcher/app.conf` (`NAME=launcher`, `KEYSTORE=launcher.keystore`, `KEYALIAS=launcher`, `KEYPASS` chosen once and noted in `launcher/README.md`), `launcher/AndroidManifest.xml`, `launcher/res/values/strings.xml` ("Flamingo"), `launcher/res/drawable/banner.png` (320×180, black, "Flamingo" in Doto), `launcher/src/.../Home.java` (black screen), `tools/junit.sh`, `tools/keys_to_monkey.py`, `tools/test_keys_to_monkey.py`
- Modify: `tools/perf-run.sh`, `.gitignore` (add `tools/lib/`)

**Manifest:** application `android:banner`, `android:label="Flamingo"`, `hardwareAccelerated`, plus `<uses-library android:name="com.tcl.tv.display" android:required="false"/>`.
- **Activity `.Home`:** `singleTask`, `stateNotNeeded="true"`, landscape, broad `configChanges`, theme `@android:style/Theme.Black.NoTitleBar.Fullscreen`. Intent filter MAIN with categories HOME, DEFAULT, LEANBACK_LAUNCHER, LAUNCHER.
- **Service `.data.MediaListener`:** NotificationListenerService, `BIND_NOTIFICATION_LISTENER_SERVICE`.
- **Permissions:** `READ_TV_LISTINGS`, `QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS`.
- **Features:** leanback required=false, touchscreen required=false.

**Interfaces:**
- Produces:
  - `tools/junit.sh`: downloads `junit-4.13.2.jar` and `hamcrest-core-1.3.jar` from Maven Central into `tools/lib/` if missing, compiles `launcher/src/**/model/*.java` and `launcher/test/**/*.java`, and runs `org.junit.runner.JUnitCore` on all `*Test` classes. Exits non-zero on failure.
  - `keys_to_monkey.script(lines: list[str]) -> str`: a monkey script. Header `type= raw events`, `count= N`, `speed= 1.0`, `start data >>`. Then for each line `KEYCODE_X ms`: `DispatchKey(0,0,0,<code>,0,0,0,0)`, `DispatchKey(0,0,1,<code>,0,0,0,0)`, `UserWait(<ms>)`. Lines starting with `#` and blank lines are skipped. Raises `ValueError` on an unknown keycode or if there are no keys.

- [ ] **Step 1:** Write `tools/test_keys_to_monkey.py`:
  - `test_basic`: `["KEYCODE_DPAD_RIGHT 250"]` gives a script containing `DispatchKey(0,0,0,22,0,0,0,0)`, `DispatchKey(0,0,1,22,0,0,0,0)` and `UserWait(250)`.
  - `test_skips_comments_blank`.
  - `test_empty_raises`.
  - `test_unknown_key_raises`.
  - Keycode table: DPAD_UP 19, DOWN 20, LEFT 21, RIGHT 22, CENTER 23, BACK 4, HOME 3, UNKNOWN 0.
- [ ] **Step 2:** `python3 -m unittest tools/test_keys_to_monkey.py`. Expect FAIL (import).
- [ ] **Step 3:** Implement `tools/keys_to_monkey.py`, with a CLI `keys_to_monkey.py <keysfile>` that prints the script.
- [ ] **Step 4:** Run the tests again. Expect PASS.
- [ ] **Step 5:** Change `perf-run.sh`:
  - Push the monkey script to `/data/local/tmp/keys.monkey` and run `monkey -f /data/local/tmp/keys.monkey 1`, with wall-clock timing added to the JSON as `"elapsed_s"`.
  - Re-check `mWakefulness=Awake` before dumping (exit 3).
  - After settle, check `mCurrentFocus` contains the package (exit 5 "perf-run: app not in front").
  - Print a usage message when fewer than 3 args.
  - Also fix the `tools/tv-adb` killer (`( sleep 8; kill "$pid" ) >/dev/null 2>&1 &`).
- [ ] **Step 6:** Write `tools/junit.sh` and a placeholder `launcher/test/.../model/SmokeTest.java` asserting `1 == 1`. Verify: `tools/junit.sh` prints `OK (1 test)`.
- [ ] **Step 7:** Build and install the skeleton: `tools/build-apk.sh launcher && tools/tv-adb install -r launcher/build/launcher.apk`.
  - Verify it opens with `tools/tv-adb shell am start -n com.livingroom.flamingo.launcher/.Home`.
  - Verify **Home still goes to Google TV**: `input keyevent KEYCODE_HOME`, then `mResumedActivity` shows launcherx.
- [ ] **Step 8:** Commit.

### Task 2: On-TV probes that shape the sources

No visible changes except step 3, which needs the owner's OK first.

**Files:**
- Create: `launcher/src/.../Probe.java` (debug-only: `Home` runs it when started with `--es probe 1`, logs `FlamingoProbe` lines, then finishes)
- Modify: `tools/grant-launcher.sh` (create)

`tools/grant-launcher.sh` applies these grants:
- `pm grant com.livingroom.flamingo.launcher android.permission.READ_TV_LISTINGS`
- `appops set com.livingroom.flamingo.launcher GET_USAGE_STATS allow`
- `appops set com.livingroom.flamingo.launcher APP_AUTO_START allow` (TCL blocks service binding without it; see `tv-remote/README.md`)
- `cmd notification allow_listener com.livingroom.flamingo.launcher/com.livingroom.flamingo.launcher.data.MediaListener`

- [ ] **Step 1:** Write the probe. It logs:
  - (a) up to 20 rows of `TvContract.WatchNextPrograms` (package, title, episode, duration, last engagement) plus the row count;
  - (b) `TvPqManager.getPictureMode()`;
  - (c) every `TvInputInfo` from `TvInputManager.getTvInputList()` with id, `getHdmiDeviceInfo()` display name/port/device type, and `getInputState(id)`.
- [ ] **Step 2:** Build, install, run `grant-launcher.sh`, run the probe, and save the log to `investigations/probe-launcher.txt`.
  - **Decide (ledger):** do Watch Next rows from Netflix/Prime/YouTube appear? Yes → Task 6 includes `WatchNext`. No → media sessions only.
  - **Record** the CEC TvInputInfo that names "PlayStation 5" and its parent HDMI input id.
- [ ] **Step 3:** Ask the owner, then set the picture mode once through `TvPqManager.setPictureMode(0, mode, EN_PICTURE_MODE_FIELD_GLOBAL, <act>)`. Use the enum values found in `investigations/tvos/src-com.tcl.tv.display` and keep the screen visible. Then restore the original mode.
  - Walk TCL's Picture screen (`com.tcl.settings.PictureActivity`) through each mode and read `getPictureMode()` after each, to build the int → name table. Write it into `investigations/probe-launcher.txt`.
  - **Decide (ledger):** picture write works → OK changes the mode inline; otherwise OK opens TCL's screen.
- [ ] **Step 4:** Commit the probe, the script and the findings.

### Task 3: Model: config, app ordering, HDMI ports

**Files:**
- Create: `model/HomeConfig.java`, `model/AppEntry.java`, `model/AppOrder.java`, `model/HdmiPort.java`, plus tests `HomeConfigTest`, `AppOrderTest`, `HdmiPortTest`

**Interfaces:**
- Produces:
  - `HomeConfig`: fields `Layout layout` (`enum Layout { A, G, N }`), `List<String> main` (default `com.google.android.youtube.tv`, `com.netflix.ninja`, `in.startv.hotstar`, `com.amazon.amazonvideo.livingroom`, `hdmi:1`), `Map<Integer,String> hdmiNames` (default `{1: "PlayStation 5"}`). Plus `String serialize()` and `static HomeConfig parse(String)`. Unknown or corrupt input → defaults, never throws.
  - `AppOrder.others(List<AppEntry> installed, HomeConfig cfg, Map<String,Long> lastUsed, String selfPkg) -> List<AppEntry>`: Spotify (`com.spotify.tv.android`) first if installed; then the rest by `lastUsed` descending, then label A–Z (case-insensitive). Excludes main apps, `selfPkg` and duplicates by package.
  - `AppOrder.mainSlots(HomeConfig cfg, Set<String> installedPkgs) -> List<String>`: config order, skipping uninstalled packages (`hdmi:N` always kept).
  - `HdmiPort.resolve(int port, String inputId, String userName, String cecName, Integer inputState) -> HdmiPort`:
    - name = userName, else cecName, else "HDMI " + port;
    - state = ON if inputState == 0; else STANDBY if cecName != null; else EMPTY.

- [ ] **Step 1:** Write the failing tests:
  - `HomeConfigTest`: round-trip; defaults on `""`; defaults on `"garbage=\u0000"`; layout persists; hdmi name with `=` and newline characters survives a round-trip.
  - `AppOrderTest`: Spotify first even when least used; ties ordered A–Z; main apps excluded; self excluded; `mainSlots` skips missing Hotstar but keeps `hdmi:1`.
  - `HdmiPortTest`: user name beats CEC name; CEC name beats default; state ON/STANDBY/EMPTY cases above.
- [ ] **Step 2:** `tools/junit.sh`. Expect FAIL (classes missing).
- [ ] **Step 3:** Implement the four classes.
- [ ] **Step 4:** `tools/junit.sh`. Expect all PASS.
- [ ] **Step 5:** Commit.

### Task 4: Model: watch history

**Files:**
- Create: `model/MediaItem.java`, `model/History.java`, `HistoryTest`

**Interfaces:**
- Produces:
  - `History.record(MediaItem m) -> boolean` (true if stored). Keeps the latest per `pkg`.
  - **Ignores:**
    - `durationMs > 0 && durationMs < 60_000` (trailers, clips);
    - items whose title matches `(?i)\b(trailer|teaser|preview)\b`;
    - our own package.
  - **Empty or null titles:** stored with `title = null` (layouts then show "Continue on <App>").
  - **Playback state:** a paused item keeps its last position; `playing` reflects the latest state.
  - `History.latest(String pkg) -> MediaItem` (or null).
  - `History.serialize()` and `History.parse(String)`. Corrupt input gives an empty history and never throws.

- [ ] **Step 1:** Write the failing tests:
  - `stores_latest_per_app`;
  - `ignores_short_and_trailers`;
  - `null_title_kept_as_null`;
  - `pause_keeps_position`;
  - `roundtrip`;
  - `corrupt_parse_empty`.
- [ ] **Step 2:** Run. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run. Expect PASS.
- [ ] **Step 5:** Commit.

### Task 5: UI kit: fonts, focus room, text rules, overflow checker

**Files:**
- Create: `ui/Fonts.java`, `ui/Kit.java`, `ui/Overflow.java`, `tools/overflow-check.sh`, `launcher/assets/fonts/` (InterTight-Regular/Medium/Bold/Black, InstrumentSerif-Regular/Italic, SpaceMono-Regular/Bold, Doto-Black). Download the static TTFs from the `google/fonts` GitHub repo, and keep their `OFL.txt` files.

**Interfaces:**
- Produces:
  - `Fonts.get(Context, String name) -> Typeface`, cached.
  - `Kit.label(Context, Typeface, float px, int color) -> TextView`: single line, `TruncateAt.END`, `includeFontPadding=false`.
  - `Kit.fit(TextView, float maxPx, float minPx)`: shrinks the text size until it fits the measured width, then ellipsizes.
  - `Kit.focusRoom(ViewGroup row, float maxScale)`: sets `clipChildren/clipToPadding=false` up the tree to the screen root, and pads the row by `(maxScale-1)/2` × child size plus ring and shadow, so focus never clips.
  - `Kit.ring(int radiusPx, int strokePx) -> Drawable`: an inset ring drawn *outside* the content bounds.
  - `Overflow.check(View root) -> List<String>`: walks the tree after layout. For each visible view it computes its transformed bounds (scale, translation) and reports:
    - `OVERFLOW clip <id/class> <text>` if the bounds extend past the screen or past an ancestor that clips;
    - `OVERFLOW overlap` if two sibling TextViews' drawn text rectangles intersect;
    - `OVERFLOW text` if a TextView's layout width exceeds its width without ellipsis.
    - It logs to tag `FlamingoOverflow` and returns the list.
  - `tools/overflow-check.sh [stress]`: for each layout A/G/N × screen home/everything × focus first/last item, start `Home` with extras `layout`, `screen`, `focus`, `stress` and `check=1`. Collect `FlamingoOverflow` lines, take a screenshot into `investigations/overflow/<case>.png`, and print a summary. Exits non-zero if any OVERFLOW line appears.

- [ ] **Step 1:** Fonts, Kit and Overflow, plus a debug `TestScreen` (behind extra `kittest=1`). It deliberately has one clipped item, one overlapping pair and one non-ellipsized long text, and a clean copy of each.
- [ ] **Step 2:** Run `tools/overflow-check.sh` against `kittest=1`. Expect exactly the 3 deliberate OVERFLOW lines and none for the clean copies.
- [ ] **Step 3:** Commit.

### Task 6: Android sources

**Files:**
- Create: `data/Store.java` (files in `getFilesDir()`: `config.txt`, `history.txt`, `art/<pkg>.png`), `data/Apps.java`, `data/MediaListener.java`, `data/WatchNext.java` (only if Task 2 said yes), `data/Hdmi.java`, `data/TvSettings.java`, `data/Sources.java`

**Interfaces:**
- Consumes: the Task 3/4 model.
- Produces:
  - `Sources.get(Context)`: a process singleton.
  - `Sources.snapshot(boolean stress) -> Screen`: never blocks on I/O. Slow data loads on a background thread, and `Sources.listen(Runnable onChange)` fires on the main thread.
  - `Apps.list() -> List<AppEntry>`: leanback and launcher apps, disabled ones excluded. `Apps.banner(pkg) -> Bitmap`: RGB_565, max 640×360, cached; falls back to the icon on a brand-colour background. Live updates through a `LauncherApps.Callback`.
  - `MediaListener` → `MediaSessionManager.getActiveSessions(component)` plus `OnActiveSessionsChangedListener` and `MediaController.Callback`:
    - builds `MediaItem`s and calls `History.record`;
    - downscales artwork to 960×540 RGB_565, pre-dims it once, and saves it as `art/<pkg>.png`;
    - persists history at most every 5 s.
  - `Hdmi.ports() -> List<HdmiPort>` from `TvInputManager`, using the Task 2 mapping (CEC device input → parent HDMI port). `Hdmi.open(port)` → ACTION_VIEW `content://android.media.tv/passthrough/<encoded id>`.
  - `TvSettings`:
    - `pictureName() -> String` (or "Open"); `nextPicture()` if Task 2 proved write;
    - `openPicture()`, `openSound()`, `openSleep()`, `openAll()` (`Settings.ACTION_SETTINGS`), `openWifi()` (`Settings.ACTION_WIFI_SETTINGS`);
    - all reflection wrapped, never throwing.
  - **Stress mode** (`stress=true`) replaces the data with: 40 apps with 60-character labels, a 120-character title, HDMI names of 50 characters, and missing artwork.

- [ ] **Step 1:** Implement the sources, plus a debug `--es dump 1` that logs the snapshot as JSON under tag `FlamingoDump`.
- [ ] **Step 2:** Verify on the TV and compare the dump with reality:
  - apps include Spotify, Play Store and Settings;
  - main slots are the five defaults;
  - HDMI 1 is "PlayStation 5", ON/STANDBY matching the PS5;
  - picture value is the expected name;
  - after the owner plays something in YouTube, Hotstar and Spotify, history has those titles and Spotify has `playing=true` while it plays.
- [ ] **Step 3:** Review Focus tests on the TV:
  - install and uninstall the old preview APK while the dump listener runs (the app appears and disappears; no crash in `logcat`);
  - run with the TCL library forced off (`--ez notcl 1`, which makes `TvSettings` skip reflection): the picture value is "Open".
- [ ] **Step 4:** Commit.

### Task 7: Activity, navigation and the layout contract

**Files:**
- Create: `ui/Screen.java`, `ui/LayoutView.java`, and the real `Home.java`

**Interfaces:**
- Produces:
  - `abstract class LayoutView extends FrameLayout`:
    - `bind(Screen s)`: rebinds, with no full rebuild if the structure is unchanged;
    - `boolean key(int keycode)`: returns handled;
    - `void showEverything(boolean on)`;
    - `int frameBudgetNotes()` (debug).
  - `Home`:
    - builds the layout from `HomeConfig.layout`;
    - routes D-pad and OK/Back keys to `LayoutView.key`;
    - Back on Home does nothing; Back in Everything returns Home;
    - `onNewIntent` (Home pressed) returns to Home focus on the first slot;
    - debug extras `layout`, `screen`, `focus`, `stress`, `check`, `dump`, `probe`;
    - **default-home check** on resume (`resolveActivity(HOME)`). If another app has taken Home, show a one-line notice in Home screen settings: "Google TV has taken Home back — run tools/become-home.sh".

Navigation (from the spec):
- **A and N:** ↓ from the app row opens Everything.
- **G:** ↓ on the last item opens Everything.
- **N:** ↑ from the pills focuses the Continue widget.
- **Everywhere:** OK on a setting opens an inline chooser (← → pick, OK confirm) when changing in place is possible; otherwise it opens TCL's screen.

- [ ] **Step 1:** Implement with a placeholder layout that lists the `Screen` as plain text.
- [ ] **Step 2:** On the TV: OK opens YouTube; Back from YouTube returns to the launcher (`am start` it first); ↓ opens Everything; Back returns.
- [ ] **Step 3:** Commit.

### Tasks 8–10: Layouts A, G, N

Each layout has its own task, with the same steps. Each layout is a `LayoutView` subclass implementing **Home + Everything** exactly as in the spec and mockups (`layouts-A-G-N.html`, and for G Everything the G3 column in `G-everything-options.html`).

**Per-layout requirements:**
- **A (`LayoutA`):**
  - Hero from the `art/<pkg>.png` of the focused main slot, crossfaded with a hardware layer over 220 ms (Plan 1 Scene A technique).
  - Real app banners.
  - Everything rows: Inputs, **Listen** (Spotify now-playing card: track, artist, art; OK opens Spotify), Apps (Spotify first), TV; the hero title describes the focused item.
- **G (`LayoutG`):**
  - Home: the original G list (big type, resume line, art behind a left gradient).
  - Everything: G3 numbered columns with faithful widening (Plan 1 "layout" variant); the focused column shows its options. The Apps column starts with a **Listen** line ("Spotify — <track>" in italic serif).
  - **Inner padding** in each column ("01" never touches the edge); names fit or ellipsize inside their own column.
- **N (`LayoutN`):**
  - Window-background dot grid; Doto clock.
  - Home: monochrome pills, focused pill white with a red dot, Continue widget with a dotted progress bar.
  - Everything: Input switch with LED, Listen record (**spins only while `listen.playing`**), TV knobs, app drawer.

**Steps for each layout task:**
- [ ] **Step 1:** Implement the Home screen.
- [ ] **Step 2:** `tools/overflow-check.sh` and `tools/overflow-check.sh stress` for this layout's Home. Expect exit 0. Look at the screenshots for focus at both row ends.
- [ ] **Step 3:** Implement the Everything screen.
- [ ] **Step 4:** Run both overflow checks for Everything. Expect exit 0. Look at the screenshots.
- [ ] **Step 5:** Run `tools/perf-run.sh <layout>-home …` and `<layout>-everything …` 3× each with key scripts that cover every row and section. Expect `"pass": true` in each run.
- [ ] **Step 6:** Commit with the JSON lines in the message body.

### Task 11: Home screen settings, persistence and restart

**Files:**
- Create: `ui/SettingsView.java` (styled per current layout: layout picker A/G/N, reorder and choose the five main items, rename HDMI 1–3)

- [ ] **Step 1:** Implement it. Changes save through `Store` immediately.
- [ ] **Step 2:** On the TV:
  - switch layout A → G → N → A; each applies instantly;
  - rename HDMI 2 to "Test"; it shows in Everything;
  - `am force-stop` the launcher and reopen: the settings and history survive.
  - Overflow check the settings screen (normal + stress): exit 0.
- [ ] **Step 3:** Time the cold start: `am start -W` gives `TotalTime`. Record it; Home must render its first frame before sources finish (verify with a log timestamp).
- [ ] **Step 4:** Commit.

### Task 12: Full verification on the TV

- [ ] **Step 1:** `tools/overflow-check.sh` and `tools/overflow-check.sh stress`, all cases. Expect exit 0.
- [ ] **Step 2:** Perf: every layout × screen, 3 runs each, plus idle Home for 60 s with Spotify paused (record still), and with Spotify playing (record spinning). All `"pass": true`. Also record idle CPU (`top`) and PSS.
- [ ] **Step 3:** Owner visual review on the TV, using the remote. List each remark and fix it, re-running the overflow and perf checks for the touched screens.
- [ ] **Step 4:** Write `docs/superpowers/reports/2026-10-launcher-release.md` with the tables. Commit.

### Task 13: Make it the TV's home (safer XDA)

The owner must be present with the Wi-Fi password, and agree right before step 4.

**Files:**
- Create: `tools/become-home.sh`, `tools/undo-home.sh`
- Modify: `debloat.sh` (add a comment line recording that `launcherx` is disabled for the launcher, and how to undo)

- [ ] **Step 1:** Write `undo-home.sh`: enable `launcherx` and `setupwraith`, press HOME, and verify `mResumedActivity` is launcherx.
- [ ] **Step 2:** Write `become-home.sh`:
  1. **Preflight:** the launcher is installed and `grant-launcher.sh` applied; `cmd wifi list-networks` contains "John Doe 5G" (abort otherwise); the launcher opens Settings and Wi-Fi from its screens (manual confirmation prompt).
  2. Start a background watcher. If `tools/tv-adb` fails for 60 s after step 3 starts, keep retrying `undo-home.sh` every 10 s for 30 minutes.
  3. Disable `launcherx`, then `setupwraith`, then press HOME. Wait for the owner to press OK on "Always" if a chooser shows.
  4. **Enable `setupwraith` immediately.**
  5. Verify: HOME resolves to `com.livingroom.flamingo.launcher/.Home`; HOME opens it; `cmd wifi list-networks` still has "John Doe 5G"; KEYCODE_SEARCH opens katniss; KEYCODE_TV_INPUT opens TCL's panel. Print PASS/FAIL. Stop the watcher only if all PASS; otherwise run `undo-home.sh`.
- [ ] **Step 3:** Run it with the owner. Then reboot (`tools/tv-adb reboot`) and confirm the TV boots into Flamingo and Wi-Fi reconnects.
- [ ] **Step 4:** Update `debloat.sh` and commit.

### Task 14: Cleanup and handover

- [ ] **Step 1:** Remove `investigations/tvos/` (local decompiles). Leave the Plan 1 investigation files that were committed.
- [ ] **Step 2:** Write `launcher/README.md`: build, install, grants, takeover and undo, where data lives, and the keystore warning (keep `launcher.keystore`; updates need it).
- [ ] **Step 3:** Ask the owner whether to delete `~/.android/pi_adbkey` from the Mac. Updates to the launcher need ADB, so recommend keeping it.
- [ ] **Step 4:** Commit.
