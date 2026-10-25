# 0.2.1 preview

- Keep Quick Settings → Watch Timer inside the existing accessibility overlay.
- Back returns from Watch Timer to Quick Settings without bringing Home forward.
- Restore Settings and the selected row after leaving Activity submenus such as Sound Effects.
- Restore Settings on Back from first-level Widgets, App Layout and App Order popups.
- Share timer UI and clean up its refresh callbacks when closing the panel.
- Build/signature and host logic checks passed; physical-TV verification remains pending.

# 0.2.0 preview

- Persistent Classic / Horizontal app layout choice, with distinct icons and shared app order.
- Switch-style horizontal scrolling and clipping inside the glass background.
- Correct focus return from Settings and visible-card-only entrance animation.
- Watch Timer with a 1–240 minute slider, text-only remaining-time overlay and cancel-on-screen-off/reboot behavior.
- Liquid glass 30-second sleep countdown, dark translucent backdrop, Sleep / Cancel actions.
- Watch Timer beside Screensaver in Quick Settings.
- English/Indonesian timer labels follow system locale.
- Android 9+ guard for Watch Timer; Android 12+ exact-alarm permission flow.

# 0.1.1 preview

Bundle all seven existing sound packs and restore per-pack event playback, pack selection, volume and sleep/wake hooks. Preserve the selected-pack-only SoundPool loading and wake navigation guard. Add source provenance for the 91 recordings.

# 0.1.0 preview

Separate application ID, portable Windows builder, API 26 minimum with guarded modern APIs, native settings fallbacks, generic input discovery and system Home detection. Private defaults, proprietary sound assets, local credentials and device scripts excluded. Device testing pending.
