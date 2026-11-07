# TCL Intelligent picture-mode lock: root cause and verified recovery

Date: 2026-11-01, Asia/Kolkata. Device: TCL G10_4K_GB, Android 12/API 31, Realtek, 32-bit ARM. Launcher: `com.livingroom.flamingo.launcher`, commit `b11fe34`.

## Outcome and remaining check

The saved global picture override was cleared at 18:06:37. Native picture changes worked on Home and HDMI 1, including after a TV reboot. With the PS5 reconnected, the TV automatically entered HDR Game mode (21), with MEMC configuration, blur, and judder all zero. A further Home → HDMI 1 round trip returned Home to Movie (3) and PS5 to Game (21).

On 2026-11-01, after recovery, the owner reported gameplay “feels super responsive for now” and said they would report any recurrence. This confirms improved perceived responsiveness in the initial gameplay check. The changing-quality effect was not separately confirmed resolved, and long-term stability remains unverified.

## What caused it

An earlier Flamingo picture-writing path used the four-argument `setPictureMode(0, mode, FIELD_GLOBAL, EXEC_SAVE)`. This saved a global override across sources/dynamic ranges. The global layer outranks native menu choices and automatic Game mode. Removing that code prevented new writes but did not remove previously saved overrides.

There are two different numbering schemes: API/runtime fields are GLOBAL=0, GLOBAL_OFF=1, NORMAL=2, SUB=3; database slots are global=0, normal=1, sub=2. Database picture-mode value **255 means empty**. Do not confuse a normal API request at priority 2 with a database slot numbered 2.

Native requests could be refused while Intelligent remained active. Earlier recovery could appear successful immediately, then fail when switching sources loaded the saved global row again. This affected non-gaming sources too.

## Why the earlier attempts did not persist

The attempts reviewed in Claude's conversation used the **five-argument** overload containing `EnApplyType`, including GLOBAL_OFF attempts. On this firmware, its native save implementation initially sets the empty sentinel but subsequently maps the original picture type back into an actual mode and saves that mode into global database slot 0. In observed earlier logs, this stored Intelligent variants such as 10, 26, and 41 instead of clearing the slots.

This explains the misleading success: runtime changes and `true` return values did not prove the saved lock was gone. Switching inputs reloaded it. Broad picture reset attempts also changed state and made later comparisons harder; they were unnecessary for the targeted recovery used here. The earlier investigation had not distinguished the native save paths of the two overloads or verified persistent empty global slots across source changes/reboot.

Native evidence from `/system_ext/lib/libtpq.so`:

- SHA-256: `e5cc803458c33281ac68c5475952345c54188d90b8e0a92764fb413c14d01b5a`.
- Four-argument API reaches `TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)` at `0xf5370`. GLOBAL_OFF writes 255 at `0xf5470`; its branch bypasses mode remapping and calls `dataSetPicMode` at `0xf5588` with global slot 0. It loops the source/range slots.
- Five-argument API reaches the save overload with `TPQSDK_VIDEO_APPLY_T` at `0xf56a8`. It sets 255 at `0xf57a4`, but `getPictureModeByType` at `0xf5950` replaces that value before the database write.
- The disassembly and focused logs are retained alongside this report. These conclusions apply to the inspected firmware, not every TCL TV.

## Recovery actually performed

A temporary Android diagnostic service, `local.flamingo.picturecheck`, read the current mode without changing the foreground input. The mutation required an explicit action and an expected-mode guard. An initial attempt expecting 10 aborted because Claude's later work had changed the current mode to Vivid (1). After re-reading state, the successful call was exactly:

```java
// One-time repair, NOT launcher functionality and NOT an automatic startup action.
manager.setPictureMode(
    0,
    1, // observed current Vivid mode; do not hard-code for future repairs
    EnPicModeFieldType.EN_PICTURE_MODE_FIELD_GLOBAL_OFF,
    EnTclActType.EN_TCL_ACT_EXEC_SAVE
);
```

There is **no EnApplyType argument**. The distinction matters. Recovery returned true and logs recorded 210 distinct writes: codecs 0–29 × dynamic ranges 0–6, each `picmode=255,priority=0`. It clears the global layer rather than imposing another preset. No new picture reset was performed in this recovery. The temporary app was uninstalled after verification.

Do not blindly repeat this call if symptoms recur. First identify the active firmware, current modes, saved layers, and any writer. The successful run used a non-Game current type; native code has special Game-type branches, so do not extrapolate the same coverage to arbitrary arguments.

## Verification

| Check | Observed result |
|---|---|
| Persistent clear | 210 distinct global rows saved as 255 |
| Home native controls, before reboot | Movie 3 → Intelligent 10 → Movie 3 |
| HDMI 1 native controls, before reboot | Vivid 1 → Standard 0 → Vivid 1 |
| Reboot | Home retained Movie 3; native controls still changed/restored it |
| Launcher Picture tile after reboot | Opened TCL's native Picture panel showing Movie |
| Saved slots read after reboot/source switches | Home (21) and HDMI 1 (15), SDR global slot 0 read 255 |
| PS5 reconnected after reboot | Native panel showed “HDR image” / “Game”; engine mode 21 |
| Automatic low-latency state | Native signal log: ALLM 1, connected 1, dynamic range 1, Game on; MEMC config/blur/judder 0 |
| Final Home → PS5 switch | Home 3 → PS5 21; MEMC zero again |
| Temporary recovery app | Uninstalled successfully; PS5 left visible |
| Perceived game latency | Owner reports “feels super responsive for now” after recovery |
| Quality fluctuations / long-term stability | Not separately confirmed; owner will report any recurrence |

The generic `getTvVideoInfo` diagnostic returned zero/disconnected even when a real PS5 signal was present. Do not use that read alone to infer cable state; native signal logs and the actual HDR Game panel provided the reliable evidence here.

## Preventing recurrence

Commit `b11fe34` removes Flamingo's inline picture chooser, all picture setters, and write-capable picture probe actions. Picture state is read-only. The Picture tile opens `com.android.tv.settings/com.tcl.settings.PictureActivity` with **`android.settings.DISPLAY_SETTINGS`**. Without that action, this firmware's activity can silently close without displaying its panel.

Keep those boundaries: no lifecycle/input-triggered picture writes, no global “restore” logic, and no hidden debug setters. Let TCL own picture selection and automatic Game switching. New picture-related work must review these constraints and verify source transitions and reboot, not just return values or one successful readback. This removes the known launcher cause; it is not a guarantee against future firmware bugs or other software writing overrides.

`AGENTS.md` and `CLAUDE.md` at the project root point here so future Codex and Claude sessions in this project encounter this context. Older Claude memory about the five-argument GLOBAL_OFF fix is superseded by this report.

## Provenance

Historical Claude session: `~/.claude/projects/-Users-vjspranav-Projects-Pi-Projects-tv-clean/223750e0-62db-4bb0-8feb-2853989b5acc.jsonl`. Reviewed later November 1 attempts as well as the original incident.

Working capture directory: `/private/tmp/flamingo-picture-20261101/`. Full logs contain unrelated device/app data and are not copied into the repository. The adjacent evidence file contains only selected picture-engine diagnostics; the adjacent assembly file preserves the two native save functions.
