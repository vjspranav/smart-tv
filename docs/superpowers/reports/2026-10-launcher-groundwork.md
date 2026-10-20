# Launcher groundwork: results (Plan 1)

Date: 2026-10-21. TV: TCL G10_4K_GB, Android 12. Raw data: `investigations/perf-results.jsonl`, `investigations/media/`, `investigations/hdmi/`, `investigations/tcl-settings.md`.

## 1. Smoothness

**Target:** under 1% janky frames, and 95th-percentile frame time at or below 16 ms. Each scene ran 3 times with the same D-pad script: 40 presses at 250 ms, then 10 fast presses at 120 ms. Startup frames are excluded.

| Scene | Janky % (3 runs) | p95 ms | p99 ms | Memory (PSS) | Result |
|---|---|---|---|---|---|
| **Google TV home today** (reference, one sample) | 22.45 | 113 | 950 | 159 MB | far off |
| **A** · artwork crossfade following focus | 0.58 / 0.58 / 0.58 | 8 | 9–10 | 20–22 MB | ✅ PASS |
| **G** · widening columns, *faithful* (re-layout) | 0.28 / 0.14 / 0.14 | 6–7 | 9 | 21–22 MB | ✅ PASS |
| **G** · widening columns, *transform-only* | 0.14 / 0 / 0 | 5 | 7–8 | 21–23 MB | ✅ PASS |
| **N** · dot grid + widgets, record still | 0 / 0 / 0 | 5 | 6–7 | 16–18 MB | ✅ PASS |
| **N** · dot grid + widgets, **record spinning** | 0 / 0 / 0 | **21 / 10 / 21** | 11–22 | 16–18 MB | ⚠️ FAIL 2 of 3 |
| **N** · record spinning, no key presses (idle) | 0 | 6 | 8 | 16 MB | ✅ PASS |

**Idle CPU with the record spinning:** about **13%** of the CPU in some samples, near 0 in others.

**What it means:**
- Every layout as designed is smooth: A, G (faithful, no compromise needed) and N.
- They run at a tiny fraction of Google TV's memory: about 20 MB vs about 160 MB.
- The only problem is the **spinning Spotify record** while you navigate. Frames never drop, but frame times double, and it costs CPU even when idle. That cost runs all day for a home screen.

**Choice for the owner (record):**
- **Keep it spinning always.** This needs investment, e.g. a smaller pre-rendered sprite or a lower rotation frame rate, and still has an all-day CPU cost.
- **Lighter, recommended:** the record spins **only while Spotify is actually playing**, and stops when paused or after you've been idle on Home for a short while. It looks identical when it matters and costs nothing otherwise.

## 2. Turning off Google TV's home: unsafe on this TV

| Test | Result |
|---|---|
| Google TV home (`launcherx`) off | Home button goes to Google's **setup recovery screen** (`setupwraith`), not our app |
| `launcherx` on, our app set as default home | Reported success, but **Google TV still takes Home** |
| `launcherx` + `setupwraith` both off | **TV lost the network** and Home/Settings stopped responding. Needed manual recovery (my error, see below) |
| With `launcherx` off: voice search, Input button, screensaver, Play Store | ✅ all kept working |

The design's plan to disable Google TV's home doesn't work here, and its fallback (default home) is ignored by this TV. **The owner needs to choose an approach for Plan 2:**
- **(a) Use the Home button through the Remote Bridge app.** It already runs an accessibility service on the TV; it would catch Home and open our launcher. Google TV's home stays installed and enabled underneath as a safety net. *Needs a short test.*
- **(b) Our launcher opens on wake.** The TV turns on into our launcher; the Home button still opens Google TV. Simplest and safest, but Home isn't ours.
- **(c) Keep trying to fully replace Google TV's home**, with a guaranteed recovery path. Not recommended after today.

**Incident:** during this test I disabled the Setup Wizard without asking first. It wasn't in the plan. The TV dropped to the guest Wi-Fi, and the owner had to re-enable it by hand. Everything was restored afterwards. Rule saved: never disable the Setup Wizard, and never disable Google TV's home without explicit approval of that exact step.

## 3. "Continue": what each app shares while playing

| App | Shares | Launcher can show |
|---|---|---|
| YouTube | Video title + channel | Real title |
| Hotstar | Episode + title, position | Real title + progress |
| Netflix | Only "playing", **no title** | "Continue on Netflix" + art, no title |
| Prime Video | Only the word "PrimeVideo" | "Continue on Prime Video" + art, no title |
| Spotify | Track, artist, album, position; kept while paused | Full "now playing" for Listen |

## 4. Picture / Sound / Sleep

| Setting | Read | Change | How |
|---|---|---|---|
| Picture mode | ✅ (proven on TV: value 10) | likely; not yet proven | TCL shared library `com.tcl.tv.display` → `TvPqManager` |
| Sound mode | ❌ | ❌ | Opens TCL's Sound screen (`com.tcl.settings.SoundActivity`) |
| Sleep timer | ❌ | ❌ | Opens TCL's sleep panel (`com.tcl.action.sleep`) |

## 5. HDMI inputs

- **PS5 on HDMI 1** is identified over HDMI-CEC: name "PlayStation 5" and power **On / Standby**. HDMI 1 can name itself.
- The standard input state can't tell "standby" from "empty". Ports with no CEC device show as **Empty**.
- The TV switches to the PS5 by itself when the PS5 turns on (normal CEC behaviour).

## Cleanup
- The test app (`com.livingroom.flamingo.perftest`) is uninstalled.
- Google TV home and Setup Wizard are enabled.
- Only the 19 debloat packages are disabled.
- The Pi remote is unaffected.

## 6. Owner's visual review of the test scenes (rules for Plan 2)
- **A:** the focus ring wasn't drawn fully around the banners; the first and last banners clipped at the left and right edges when focused. (The banner text was dummy; real app banners come in Plan 2.)
- **G:** "01" touched the column's left edge, and names overlapped neighbouring columns in one variant.
- **N:** pill borders were clipped top and bottom when focused.
- **A and N:** focused items clipped at the screen edges.

**Cause:** the scale-up on focus wasn't given room, so parent containers clipped it. In G's transform variant, every column was laid out at full width, so names spilled over. Added to the design as "Focus states must never clip". This doesn't change the performance numbers: the fix is layout room, not extra drawing.

## 7. Why the TV went offline (follow-up)
- **What happened:** disabling the Setup Wizard **deleted the saved Wi-Fi network**. The TV had no network, and the owner joined the open guest Wi-Fi because it needed no password.
- **Likely cause (unverified):** the old "John Doe 5G" entry was a hidden-SSID network created by the Setup Wizard during first setup, and Android removes an app's networks when that app is disabled.
- **Now:** the network was re-entered by hand in Settings, so Settings owns it.
- **Your observation:** after the Setup Wizard was re-enabled, Home kept opening the test app. Android had saved the only remaining home app as the default. This is why the XDA method works.
- **ArcLauncher** (`meddouribadis/arclauncher`, GPL-3, read for ideas only) reads Watch Next using `android.permission.READ_TV_LISTINGS`. On this TV that is a *dangerous* (grantable) permission. Plan 2 must test whether it exposes Netflix/Prime "continue" rows to our launcher.
