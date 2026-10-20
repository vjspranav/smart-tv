# Living Room TV launcher: design

Date: 2026-10-21. Project folder: `tv-clean/`. Mockups: `2026-10-21-tv-launcher-mockups/` (open the HTML files in a browser).

## Intent

The Living Room TV runs Google TV. Its home screen is cluttered, pushes ads and recommendations the owner doesn't want, and moving between rows feels slow. The owner wants a home screen of their own: clean, distinctive and modern-looking, with only what they use, and **perfectly smooth on this low-end TV**.

What they use:
- **Daily:** YouTube, Netflix, Hotstar, Prime Video, and the PlayStation 5 on HDMI 1.
- **Sometimes:** Settings, the HDMI inputs, Spotify.

App banners are welcome. Ads and suggested content are not.

A separate debloat pass already turned off TCL/Google extras (see `tv-clean/debloat.sh`). The owner tried AT4K Launcher and uninstalled it: it felt laggy and "didn't solve the purpose".

## Goals

1. A native Android TV launcher that replaces the Google TV home screen.
2. **Three layouts**, chosen once in the launcher's own settings and rarely changed: **A · Cinema**, **G · Editorial**, **N · Nothing**. Each has a **Home** screen and an **Everything** screen (reached with ↓) in its own visual style.
3. **All three layouts have exactly the same functions.** Only the look differs.
4. Butter-smooth navigation on this TV, proven by measurement on the TV.
5. **Light code that doesn't cost features:** everything the TV makes possible should be possible from the launcher.

## Non-goals

- Recommendations, "For you" rows, ads, or any content the owner didn't watch.
- Replacing apps' own players, Google Assistant / voice search, casting, or the system Settings app.
- Weather or other online data. The owner never asked for it, and there's no source.
- Running anything on the Raspberry Pi. The Pi is memory-constrained, and the owner asked to keep it out of this.

## Anti-goals (a failure even if it "works")

- **Any lag or dropped frames during normal navigation.** Smoothness is an absolute necessity; the WebView prototype was rejected for lag.
- **Any overflow:** text running past its box, wrapping badly, clipped widgets, or rows spilling off-screen. It must look like a neatly designed, finished app at all times, including with long titles and many apps.
- **Generic-looking UI.** Plain dashboard grids and tiles were rejected.
- **Hiding the rest of the TV.** Other apps, inputs and settings must stay easy to reach.

## Constraints

**The TV**
- TCL "Smart TV Pro" (G10_4K_GB), Google TV on Android 12 (API 31), 32-bit armeabi-v7a, Realtek SoC.
- About 1.8 GB RAM. Memory is the bottleneck: about 500 MB was routinely swapped out.
- Panel is 3840×2160 but UI renders at **1920×1080, density 320** (so 960×540 dp).
- Address `192.168.0.12:5555` (DHCP; MAC `14:14:16:24:ac:66`).

**Access and tooling**
- ADB from the Mac only. The TV never shows the authorization prompt to the Mac, so the Mac reuses the Pi's already-trusted key:

      adb kill-server; ADB_VENDOR_KEYS=$HOME/.android/pi_adbkey adb start-server; adb connect 192.168.0.12:5555

- The connection occasionally drops to "offline"; reconnect and continue.
- Build tools on the Mac: JDK 17, Android build-tools 34, platform 34. `tv-remote/tv-bridge/build.sh` shows a working Gradle-free build (javac → d8 → aapt2 → apksigner).

**Installed apps and other services**
- Main app packages: YouTube `com.google.android.youtube.tv`, Netflix `com.netflix.ninja`, Hotstar `in.startv.hotstar` (label "JioHotstar"), Prime Video `com.amazon.amazonvideo.livingroom`.
- PS5 is HDMI 1: TV input `com.tcl.tvinput/.TvPassThroughService/HW15`. HDMI 2 is `…/HW16` and HDMI 3 is `…/HW17`. Switch with an ACTION_VIEW intent on `content://android.media.tv/passthrough/<url-encoded input id>`.
- The existing Remote Bridge app (`com.livingroom.bridge`) and the Pi's `tv-remote` service must keep working. The launcher must not depend on them.

**Data the launcher can and cannot read**
- **No Continue Watching from other apps.** Reading other apps' Continue Watching / Watch Next rows needs `ACCESS_ALL_EPG_DATA` / `ACCESS_WATCHED_PROGRAMS`, which are signature|privileged. Only Google TV holds them, so our launcher cannot read them.
- **HDMI states** come from `TvInputManager`. All three currently report state 1 (connected-standby). An eARC audio device is connected.
- **Picture mode, sound mode and sleep timer** are not in the Android settings provider. TCL keeps them in its own system services (`com.tvos` framework, TCL settings apps). Exposing them needs investigation; see Open items.

## Decided with the owner

### The three layouts

The mockups are the visual reference. Where the mockups and this document disagree, this document wins.

**A · Cinema**
- **Home:** the selected app's last-watched item fills the screen as artwork, with "Continue on <App>", a large title, episode/time info and a progress bar. The clock is top right. One row of the five main items sits along the bottom as **real app banners** (16:9, rounded, white focus ring, slight scale-up). ← → moves along the row; the hero follows focus.
- **Everything:** artwork dims and desaturates. Banner-style rows scroll up: **Inputs** (PS5, HDMI 2, HDMI 3), **Apps** (Spotify first, then the rest), **TV** (Picture, Sound, Sleep with current values; All settings; Home screen). A large title at the top always describes the focused item, as on Home.

**G · Editorial**
- **Home:** exactly the original G. A vertical list of the five main items in very large bold type on the left, unselected items dimmed. Under the selected item: "▶ Resume · <title> · <episode> · <time left>" and a thin progress bar. That item's artwork fills the screen behind, behind a dark left-to-right gradient. Date and time sit top left in small mono caps.
- **Everything:** G3 "Columns". A Swiss-style grid of four numbered columns, **01 Inputs, 02 Apps, 03 TV, 04 Home**, each with a thin top rule. The focused column widens and shows its options as rows; the others stay slim and show a short preview. ← → moves between columns, ↑ ↓ moves within one.

**N · Nothing** (in the spirit of Nothing OS: monochrome, dot-matrix, one red accent)
- **Home:**
  - Pure black with a faint dot grid.
  - Large dot-matrix clock top left; date and room name in monospace caps beneath.
  - A rounded "Continue · <App>" widget with title, episode/time and a dotted progress bar.
  - The five main items as monochrome, text-only pill buttons. No logos.
  - The focused pill is white with black text and a red dot.
- **Everything:** rounded widgets on the dot grid.
  - **Input:** a vertical switch, with a red LED glowing on the active input.
  - **Listen:** Spotify drawn as a vinyl record.
  - **TV:** ring "knobs" for Picture, Sound and Sleep with values in mono caps, plus "All settings".
  - **Apps:** a drawer of monochrome pills, Spotify first, plus "Home screen ›".

### The same functions in every layout

| Area | Function |
|---|---|
| Home | Clock (updates once per minute) |
| Home | "Continue" for the selected app, from the launcher's own watch history (below) |
| Home | Five main items (default: YouTube, Netflix, Hotstar, Prime Video, PS5); OK opens the app or switches to the input |
| Everything › Inputs | HDMI 1, 2, 3 with name and the state the TV reports; OK switches input |
| Everything › Apps | Spotify pinned first, then every other launchable app; OK opens. Covers every installed app, including Play Store |
| Everything › TV | Picture mode, Sound mode, Sleep timer showing current values, changeable from the launcher (see Open items); "All settings" opens system Settings |
| Everything › Listen | Spotify's current or last track (title, artist, artwork) from the same media-session history; OK opens Spotify. N shows it as the vinyl record; A and G show it in their own style |
| Everything › Home screen | Layout (A / G / N); choose and order the five main items; rename HDMI inputs (HDMI 1 defaults to "PlayStation 5") |

**Scope is this table.** Goal 5 means doing these functions as fully as the TV allows. It doesn't mean adding more: volume and audio output, Bluetooth, network, notifications and screensaver stay in system Settings, reached through "All settings". *(Owner's decision.)*

### Continue watching = the launcher's own history

- The launcher keeps a small history of what played, per app: title, subtitle/episode, artwork, position/duration when given, and when.
- Capture it from the system's active media sessions while apps play. The launcher holds its own notification-listener grant, applied over ADB.
- Keep only the latest item per app.
- **OK opens the app.** It doesn't deep-link into the episode; the owner accepted this.
- When an app has no history, show its banner/brand colour instead of a show (and no progress bar).

### Replacing Google TV — "safer XDA" method (owner's decision after Plan 1)

Plan 1 found:
- Disabling only Google TV home sends Home to the Setup Wizard's recovery screen.
- Setting a default home is ignored while Google TV home is enabled.
- Disabling the Setup Wizard **deleted the saved Wi-Fi network**. It was probably created by the wizard; the owner has since re-entered it in Settings.
- While both are disabled, Android saves the only remaining HOME app as the default, and that choice survives re-enabling the Setup Wizard.

Procedure (owner present, Wi-Fi password at hand):
1. Install and verify the launcher first. It must offer Settings and Wi-Fi settings from its own screens, so the TV can never be left unreachable.
2. Start a restore watcher. It re-enables both packages if the TV stops responding to the launcher check.
3. Disable `com.google.android.apps.tv.launcherx` and `com.google.android.tungsten.setupwraith`, then press Home and confirm our launcher (choose "Always" if a chooser appears).
4. **Re-enable `setupwraith` immediately.** Only `launcherx` stays disabled.
5. Verify: Home and boot open our launcher; Wi-Fi is still John Doe 5G; voice, Input, screensaver and Play Store still work.
6. Record it in `debloat.sh`, with an undo script (enable `launcherx`).

**Never disable `setupwraith` for longer than step 3.**

### Decisions from Plan 1 results (2026-10-21)

**Smoothness and layout**
- All three layouts passed the smoothness target. G keeps the **faithful widening columns**.
- The N Spotify record **spins only while Spotify is playing**. Otherwise it stays still, to save idle CPU and keep frame times low.

**Data sources**
- **Picture mode:** read (and write if proven) through the `com.tcl.tv.display` shared library (`TvPqManager`).
- **Sound mode:** opens TCL's Sound screen (`com.android.tv.settings/com.tcl.settings.SoundActivity`).
- **Sleep timer:** opens TCL's sleep panel (action `com.tcl.action.sleep`).
- **HDMI:** names and on/standby come from HDMI-CEC (the PS5 reports "PlayStation 5"). Ports without a CEC device show as Empty.
- **Continue:**
  - Media sessions give titles for YouTube and Hotstar, track info for Spotify, and only "playing" for Netflix and Prime.
  - Also test `READ_TV_LISTINGS` (runtime-grantable here) for Watch Next rows from other apps. Prefer real titles wherever either source has them.

**Ideas from ArcLauncher** (GPL-3; no code copied)
- One activity carrying HOME + DEFAULT + LEANBACK_LAUNCHER + LAUNCHER, in `singleTask` mode.
- An app banner, and the leanback feature declared.
- An app list combining leanback and sideloaded apps.
- A LauncherApps callback, so the list updates live.
- An "am I the default home?" check.

## Hard quality rules

**Smoothness**
- Native Android UI. **No WebView**, and no web or cross-platform runtime.
- Animate only cheap properties: alpha, translation and scale, on views already drawn.
- No live blur, no animated full-screen gradients, nothing that redraws the whole screen each frame.
- Pre-render artwork once when captured: downscaled, and already dimmed/blurred where a layout needs it.
- Keep memory small: few and small bitmaps, no heavy libraries.
- The launcher is resident all day, so its idle cost matters.

**Proof of smoothness, on the TV**
- Script D-pad runs through every screen of every layout with `adb shell input keyevent`.
- Collect `dumpsys gfxinfo <pkg>` frame stats.
- **Target:** under 1% janky frames, 95th-percentile frame time at or below 16 ms.
- Report the launcher's PSS memory (`dumpsys meminfo`) at idle on Home.

**No overflow**
- Every text has a rule: single-line with ellipsis, or auto-fit within a min/max size.
- Every list scrolls inside its own region; nothing pushes layout off-screen.
- Test with stress data: very long titles and app names, 40+ apps, long HDMI names, missing artwork. Check every screen by screenshot.

**Focus states must never clip** (owner's review of the Plan 1 test scenes, 2026-10-21)
- A focused item's scale-up, ring and shadow must be fully visible. This includes the first and last item in a row, against every screen edge, and top and bottom.
- Reserve room for the focused size in the layout: padding, plus no clipping by parent containers.
- The A focus ring must be drawn completely around the banner.
- N pill borders must stay intact top and bottom when focused.
- Text never overlaps neighbouring items. In G, a narrow column's name must fit or be cut off with "…" inside its own column, never spill into the next one.
- Every panel or column has inner padding: G's "01" must not touch the column's left edge.
- Screenshot every focus state at both row ends, in every layout.

**Fonts and assets**
- The mockups use Inter Tight, Instrument Serif, Space Mono and Doto (dot-matrix), all from Google Fonts under the SIL OFL.
- Nothing's own fonts are not licensed for this; use Doto instead.
- Bundle only the weights used.

## Order of work: test the risky effects first

Before building the real app, build a **throwaway native performance test** on the TV with dummy data. It recreates only the riskiest effects:
- A's full-screen artwork following focus.
- G's widening columns.
- N's dot grid under moving widgets.
- The Spotify record, if it animates.

Measure frame times and memory with the method under "Proof of smoothness". **Show the owner the numbers.** For any effect that misses the target, the owner chooses: keep the look and invest in making it smooth, or use a lighter version that keeps the look's character. Examples of lighter versions: A fades between flat colours instead of artwork; G highlights a column in place instead of widening it.

Then build the real app on what the test showed, and uninstall the test app.

## Open items (investigate, then build the best version possible)

1. **Picture mode, Sound mode, Sleep timer.** Find how TCL stores and changes them (TCL framework services, TCL settings/quick-panel apps, intents they expose).
   - Preferred: read and change them directly from the launcher.
   - If changing isn't possible, show the value and open TCL's own panel for that setting.
   - If even reading isn't possible, the button opens TCL's panel.
   - The owner wants the most capable version that's possible.
2. **What each app publishes to media sessions** (title, episode, artwork, position) for YouTube, Netflix, Hotstar and Prime. Play something in each and log it. This sets how rich "Continue" can be per app.
3. **HDMI state meaning on this TV.** All inputs report state 1 even when nothing is plugged in. If states can't tell "device on" from "empty", show the name only, or use a better source if one exists.

## Left to the builder

- Language (Java or Kotlin), and the exact Android UI approach within the smoothness rules.
- Code structure; how layouts share the function layer.
- How history is stored.
- Focus/scroll mechanics, exact animation curves and durations (snappy, ~150–250 ms).
- Exact typography sizes and spacing, matching the mockups' character.
- How Home screen settings are laid out in each style.

## Calls made by the designer (not discussed with the owner; check these)

- In **N Home**, ↑ from the app pills focuses the Continue widget; OK there opens that app.
- In **G Home**, ↓ on the last item (PlayStation) enters Everything. In A and N, ↓ from the app row enters Everything. ↑ from the top of Everything, or Back, returns Home. Back on Home does nothing.
- **Changing a setting:** OK on Picture/Sound/Sleep opens a small inline chooser in the layout's style; ← → picks, OK confirms. This replaces cycling with arrows, which would clash with column/row navigation.
- **"Every other app"** is sorted by most recently used, then alphabetically.
- **Disabled apps** (from debloat) never appear.
- The launcher's **package name** is `com.livingroom.flamingo.launcher` (owner asked for "flamingo" in the name).
- The **clock** follows the TV's 12/24-hour system setting. The mockups mix both.
- **History ignores** trailers, autoplay previews and items shorter than about 60 seconds.
- **HDMI names** live only inside the launcher; the TV's own input names are not changed.
- A **Pi check**: `tv-remote`'s state parser treats any unknown foreground package as "home", so the new launcher doesn't break the phone remote.
