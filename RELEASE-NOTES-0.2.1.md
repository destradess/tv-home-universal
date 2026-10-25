# TV Home Universal 0.2.1 Preview

## Fixes

- Watch Timer opened from Quick Settings now stays in the accessibility overlay instead of bringing the Home screen in front of YouTube or another app.
- Back from Watch Timer returns to Quick Settings; Back again closes the panel. Closing these menus does not cancel the running timer.
- Back from Sound Effects and other Activity-based Settings submenus restores Settings and the previously selected row.
- First-level Widgets, App Layout and App Order popups return to Settings on Back.
- Timer settings share one UI implementation, with refresh callbacks stopped when the panel closes.

## Install or update

Download **TV-Home-Universal-0.2.1-debug.apk** and install it over the existing Universal version. The application ID and signing certificate are unchanged. Do not uninstall or clear data to update.

Launcher: Android TV / Google TV 8.0+. Watch Timer: Android 9+, enabled TV Home accessibility service, and Alarms & reminders permission on Android 12+ where required. No AI, Shizuku or ongoing ADB connection is required for normal use.

## Validation and preview status

Build, APK signature verification and existing host logic checks passed, including timer boundaries and 12,960 carousel positions. Physical-TV verification of the new overlay and Back navigation is still pending; firmware determines overlay and sleep behavior.

Test YouTube → Quick Settings → Watch Timer → Back, and Settings → Sound Effects → Back. Keep the stock launcher available while testing.

Private API keys, signing keys and device preferences are excluded from the source archive. Existing third-party audio notices still apply.
