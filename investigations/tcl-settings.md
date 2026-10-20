# TCL picture / sound / sleep: working notes (Plan 1, Task 9)

Sources: `tcl-surfaces.txt`, and decompiled `/system_ext/framework/{com.tcl.tv.display,tcl-framework,tcl-service}.jar` (jadx, kept locally in `investigations/tvos/`, not committed).

## Picture mode
- **API:** `com.tcl.tv.display` is a **shared library** listed by `pm list libraries`, so an app can declare `<uses-library android:name="com.tcl.tv.display" android:required="false"/>`.
- **Read:** `com.tcl.tv.pq.TvPqManager.getInstance().getPictureMode(): int`.
- **Write:** `TvPqManager.setPictureMode(int windowId, int picmode, EnPicModeFieldType field, EnTclActType act)`.
- **Mode names:** the `tcl_app_hotel_picture_mode_preset_*` global settings map to Standard / Vivid / Movie / Sport / SmartHDR. The int → name mapping still has to be confirmed on the TV.
- **Fallback screen:** `com.android.tv.settings/com.tcl.settings.PictureActivity`.
- **Status:** read and write to be proven from an app on the TV (probe scene `P` in perftest).

## Sound mode
- **Where it lives:** TCL's audio service in `system_server` (`TclAudioServiceEx`, `TclSoundSetting`), driven through HAL effects. Mode names: standard, smart, movie, music, news, sports, game, live, user.
- **No public client API found.**
- `sound_effects_type` is *UI click sounds*, not the sound mode.
- **Fallback screen:** `com.android.tv.settings/com.tcl.settings.SoundActivity` (also action `com.android.tv.action.VIEW_SOUND_SETTINGS`).
- **Status:** opening TCL's Sound screen is guaranteed. Reading or writing the mode needs more reverse-engineering, which isn't worth it before Plan 2.

## Sleep timer
- **Action:** `com.tcl.action.sleep` (also `com.google.android.pano.action.SLEEP`), handled by TV Settings. It opens TCL's sleep panel.
- `Settings.Secure sleep_timeout` is the auto-standby *inactivity* timeout, not the sleep timer.
- **Status:** the launcher can open the sleep panel. The current value is not readable yet.
