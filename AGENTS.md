# TV project: required context

Read `docs/superpowers/reports/2026-11-01-picture-mode-recovery.md` before working on TV picture settings, gaming latency, TCL APIs, or launcher settings. It records a verified persistent override incident and recovery, not a proposed fix. Read `CLAUDE.md` as well.

- The owner requires **no picture overrides from Flamingo**. Keep the launcher read-only for picture state; its Picture tile opens TCL's native panel.
- Do not reintroduce `setPictureMode`, picture setters hidden in reflection/debug probes, or startup/resume/input-change picture writes. Commit `b11fe34` removed these paths.
- A successful setter return or immediate mode readback does not establish recovery. Check saved priority slots, source changes, reboot, native controls, and real PS5 Game mode. User-perceived latency requires the owner's gameplay check.
- Do not repeat the five-argument `GLOBAL_OFF` recovery: on this TV firmware it saves a mode back into the override layer. The verified four-argument recovery is documented in the report; do not run it automatically or assume it applies to another firmware.
- Do not reset picture calibration or factory-reset as a substitute for diagnosing a priority lock. Do not change Setup Wizard, Home takeover, Wi-Fi, or debloat configuration as part of picture repair.
- Keep the report updated with confirmed findings and explicitly separate pending user checks from verified results.
