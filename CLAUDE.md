# Required project context

Read `AGENTS.md` and `docs/superpowers/reports/2026-11-01-picture-mode-recovery.md` before investigating TV picture modes, game lag, or changing launcher settings.

The Intelligent-mode lock was traced and persistently cleared on 2026-11-01. Earlier five-argument `GLOBAL_OFF` attempts were ineffective because of the native save implementation. The four-argument clear wrote sentinel 255 to all 210 global source/range slots; recovery survived reboot and PS5 HDR Game mode resumed automatically.

Flamingo must never write picture modes, including via probes or lifecycle hooks. Its Picture tile opens TCL's native settings with `android.settings.DISPLAY_SETTINGS`. Do not restore the old inline mode picker or assume older Claude memory describing the five-argument fix is correct. Use the report's evidence and current device state.

Hardware/software verification passed. On 2026-11-01, the owner reported gameplay “feels super responsive for now” and will report any recurrence. The changing-quality effect was not separately confirmed resolved; long-term stability remains unverified.
