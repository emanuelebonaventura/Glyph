# Glyph - ColorOS Notification Icon Enhancer

[English](README.md) | [简体中文](README_zh.md)

---

Optimizes notification icons for ColorOS / realme UI, adapting them to native Android notification icon specifications.

> Fork of [fankes/ColorOSNotifyIcon](https://github.com/fankes/ColorOSNotifyIcon) with enhancements and modernization. The Hook layer has been refactored using [modern libxposed API 102](https://github.com/libxposed/api), and the UI has been redesigned with [Miuix](https://github.com/compose-miuix-ui/miuix).

## Features

- **Status Bar Notification Icons**: Clean, consistent icon replacement in the status bar.
- **Always-On Display (AOD)**: Notification icon replacement on the AOD clock display.
- **ColorOS 16 Lock Screen Island**: Replaces icons in the bottom notification capsule on ColorOS 16.
- **Notification Shade Icons**: Notification center small icon replacement (optional, toggleable).
- **Dual Icon Sources**: Choose between **Rule Icons** or **Desktop Theme** (icons dynamically follow your launcher's active theme or custom icon pack).
- **Oplus Push Notification Handling**: Dedicated handling for system push notifications (toggleable).
- **Fallback Placeholder for Unadapted Apps**: Use a generic circular notification icon for apps without dedicated rules (toggleable).
- **Local Rule Management**: Enable per application or enforce full replacement across the board.
- **Manual Icon Assignment**: Manually assign any icon from the rule database to an app, overriding package name matching.
- **Manual Rule Sync**: On-demand synchronization with remote rule repositories (no background battery drain).
- **Framework-side Config Persistence**: Settings written directly via Xposed framework with instant SystemUI refresh triggers.
- **Hide Launcher Icon**: Option to hide the app drawer icon, remaining accessible via LSPosed manager.

## Settings Overview

| Setting | Description |
| --- | --- |
| **Enable Icon Enhancement** | Master toggle. When disabled, rule and theme icons are disabled, retaining only native valid `smallIcon` protection. |
| **Icon Source** | Switch between **Rule Icons** and **Desktop Theme**. In Desktop Theme mode, notification icons adapt to your current launcher theme or custom icons. |
| **Notification Center Replacement** | Controls small icons inside the notification shade. When disabled, notification center retains default ColorOS behavior without affecting the status bar. |
| **System Push Handling** | In Rule Icon mode, controls whether Oplus Push notifications prioritize enabled rules for the target application. When disabled, retains system default behavior. |
| **Unadapted Placeholder** | In Rule Icon mode, if an application lacks a rule match and the original icon is not a tintable notification mask, substitutes it with a generic circular placeholder icon. |
| **Hide Launcher Icon** | Hides the Glyph icon from the home screen / app drawer (still accessible via LSPosed module manager). |

In **Rule Icon** mode, each application rule contains two options:
- **Enable Replacement**: Allows the application to use rule-based icons.
- **Replace All**: Ignores the app's built-in compliant `smallIcon` and forces the rule-based icon.

## Icon Sources

The status bar, AOD clock, Lock Screen Island, notification center small icons, and Oplus aggregated notifications share unified source resolution:

- **Rule Icons**: Fetches icons from the module's rule database, supporting manual assignment, fallback placeholders, and per-app forced overrides.
- **Desktop Theme**: Retrieves icons from the active launcher theme or custom icon pack. If extraction fails, falls back directly to the original system notification icon (avoiding mixed icon sources).

## Decision Logic

In **Rule Icon** mode, both the status bar and notification center follow an evaluated decision pipeline rather than unconditionally overriding system behavior:

1. Oplus Push notification + System Push Handling disabled &rarr; Keep system default.
2. Oplus Push notification + System Push Handling enabled + Matches enabled rule &rarr; Use rule icon.
3. Matches enabled rule + "Replace All" enabled &rarr; Use rule icon.
4. Matches enabled rule + Original `smallIcon` is not a tintable monochrome mask &rarr; Use rule icon.
5. No rule match + "Unadapted Placeholder" enabled + Original `smallIcon` is not a tintable monochrome mask &rarr; Use generic circular notification icon.
6. Original `smallIcon` is a valid tintable monochrome mask &rarr; Restore original native `smallIcon`.
7. None of the above satisfied &rarr; Retain ColorOS current output.

### Manual Overrides & Mask Validation
- **Rule Matching**: Matches by the notification's package name. Users can manually assign another icon from the rule database to any app; manual assignment takes precedence over package name matching. Assignments only borrow the target's original definition (non-transitive, preventing cyclic references). Installed apps not in the rule database can also receive a manual icon assignment.
- **Validation**: Verifies alpha geometry and monochrome foreground contracts: icons must have a transparent background, and visible pixels must be grayscale or single-toned. Multicolored icons, mixed tones, dense rounded solid backplates, `AdaptiveIconDrawable`, fully opaque, or fully transparent drawables are not treated as valid notification masks for forced tinting.
- **Coloring**: Restored native `smallIcon`s, rule icons, and placeholders are uniformly tinted by SystemUI. Desktop theme icons are full-color application icons and retain their original colors without grayscale mask tinting.
- **Special Cases**: Contact avatars in conversations retain native SystemUI paths and are never replaced. Media playback notifications are skipped in the notification center to preserve system media controls.

## Installation

1. Download the latest APK from [GitHub Releases](https://github.com/emanuelebonaventura/Glyph/releases/latest).
2. Enable the module in LSPosed.
3. Check the scope:
   - System Framework (`system`)
   - System UI (`com.android.systemui`)
4. Open Glyph (accessible from the LSPosed module list if the launcher icon is hidden) and synchronize rules.
5. Configure your preferred icon source, toggles, and individual application rules.
6. Tap **Restart SystemUI** to apply the configuration.

> [!TIP]
> After initial installation or updating a version that modifies `system_server` hooks, a full device reboot is recommended.

## Compatibility

- **Target System**: Specifically designed for **ColorOS 16 / realme UI 7.0** + **LSPosed**.
- Not compatible with older Android/ColorOS versions, legacy Xposed frameworks, or other non-LSPosed environments.

## Rule Sources

Integrated with the [Android Notification Icon Project (ANIP)](https://github.com/BetterAndroid/android-notification-icon-project) repository:

- [Android Notification Icon Project (ANIP)](https://github.com/BetterAndroid/android-notification-icon-project)
- [ColorOS Manifest](https://raw.githubusercontent.com/BetterAndroid/android-notification-icon-project/main/icons/system/coloros/manifest.json)
- [Game Manifest](https://raw.githubusercontent.com/BetterAndroid/android-notification-icon-project/main/icons/game/manifest.json)
- [App Manifest](https://raw.githubusercontent.com/BetterAndroid/android-notification-icon-project/main/icons/app/manifest.json)

Rule merging order during synchronization: ColorOS &rarr; Games &rarr; Apps. If the same package appears in multiple manifests, the later one takes precedence.

## Differences from Upstream

Forked from [fankes/ColorOSNotifyIcon](https://github.com/fankes/ColorOSNotifyIcon), maintaining original licensing, copyright declarations, and acknowledgements.

Key improvements:
- Rewrote the Hook architecture using [modern libxposed API 102](https://github.com/libxposed/api).
- Removed obsolete framework compatibility code.
- Redesigned the UI using [Miuix](https://github.com/compose-miuix-ui/miuix) (Home / Rules / About tabs with a liquid glass bottom bar).
- Added in-app links and release update checking via GitHub.
- Streamlined focus exclusively to notification icon enhancement.
- Added support for notification icons adapting to desktop themes / custom icon packs.
- Added support for ColorOS classic AOD clock notification icons (`LockScreenNotificationIconData` &rarr; AodPlugin and legacy `NotificationLayout`).
- Added ColorOS 16 Lock Screen Island bottom notification capsule icon support (`CapsuleNotificationDataController`).
- Supported manual icon selection from rule repository per app.
- Eliminated background polling and auto-sync in favor of manual user-triggered sync.

## Notes & Disclaimers

1. This project is free, open-source, and created for educational and personal use. If you paid for this software, you were scammed.
2. Licensed under the **AGPL 3.0** license. Any distribution or modification must adhere to AGPL terms and provide corresponding source code.
3. Please retain original copyright and author credits.

## License & Privacy

- Privacy Policy: [PRIVACY.md](PRIVACY.md)
- License: [GNU Affero General Public License v3.0 (AGPL-3.0)](https://www.gnu.org/licenses/agpl-3.0.html)

Original project copyright belongs to Fankes Studio (`qzmmcn@163.com`). Changes in this fork are likewise distributed under AGPL-3.0.

## Acknowledgements

- [fankes](https://github.com/fankes) for the original open-source project foundation.
- Contributors and maintainers of the notification icon rule sets.
- Google Material Icons ([`circle_notifications`](https://github.com/google/material-design-icons)), used under the [Apache-2.0](third_party/material-design-icons/LICENSE) license.
