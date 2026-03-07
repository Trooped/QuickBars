package dev.trooped.tvquickbars.utils

import dev.trooped.tvquickbars.ui.dialog.ChangeItem

/**
 * Manages application versioning information, mapping internal version codes to display names
 * and maintaining a comprehensive registry of changelog entries for each release.
 */
object AppUpdateManager {
    // Map of version code to version name for display
    private val versionNames = mapOf(
        15 to "1.1",
        17 to "1.2",
        18 to "1.2.1",
        19 to "1.2.2",
        20 to "1.2.3",
        21 to "1.2.4",
        22 to "1.3",
        23 to "1.3.1",
        24 to "1.3.2",
        25 to "1.3.3",
        // Add future versions here
    )

    // Get display version name for a version code
    fun getVersionName(versionCode: Int): String {
        return versionNames[versionCode] ?: "Unknown"
    }

    // Returns changes for a specific version
    fun getChangesForVersion(versionCode: Int): List<ChangeItem> {
        return when (versionCode) {

            25 -> listOf(
                ChangeItem(
                    title = "QuickBars is now Open-Source!",
                    description = "You can now find the official GitHub repository at https://github.com/Trooped/QuickBars. Feel free to explore the code or contribute to the project!"
                ),
                ChangeItem(
                    title = "Home Assistant 2026.3 Compatibility",
                    description = "Updated light entity controls to handle deprecated features introduced in HA 2026.3. QuickBars now uses kelvin-based values instead of mired-based values for better future-proofing."
                )
            )

            24 -> listOf(
                ChangeItem(
                    title = "Improved Auto-close Timer for QuickBars",
                    description = "Now, the timer restarts whenever you click the DPAD keys (arrows or center_dpad/confirm key), while the QuickBar is open."
                ),
                ChangeItem(
                    title = "Advanced RTSP Streams Configuration",
                    description = "The Camera PiP blueprint now contains advanced configuration for RTSP streams - software decoding, latency and transport protocol. If the RTSP streams aren't working for you, I recommend tinkering with the settings there. Requires a re-import of the blueprint."
                ),
            )

            23 -> listOf(
                ChangeItem(
                    title = "Auto-close Timer for QuickBars",
                    description = "You can now set an auto-close timer per QuickBar inside its settings. Choose between Never (default), 15s, 30s and 60s to automatically close the QuickBar after it’s opened."
                ),
                ChangeItem(
                    title = "Updated camera_proxy Images in Notifications",
                    description = "When using a camera_proxy as the image source for a notification (with an MJPEG camera entity), the banner now always fetches an updated snapshot from the live stream instead of showing a stale image."
                ),
                ChangeItem(
                    title = "RTSP Stream Compatibility",
                    description = "RTSP camera streams have been updated to work reliably across all devices. If RTSP still doesn’t work on your setup, please reach out so I can investigate your device specifically."
                ),
                ChangeItem(
                    title = "Global “Show Toast on Entity Triggers”",
                    description = "New global setting in QuickBars Settings → Advanced Settings: disable all “X triggered” toasts for entities and camera PiP that are activated via Trigger Keys. Error toasts still appear when something goes wrong."
                )
            )

            22 -> listOf(
                ChangeItem(
                    title = "TV Notifications (Overlay)",
                    description = "Rich banner with title, message, and optional icon (MDI). Action Buttons which emit a 'quickbars.action' event with the chosen id for automations when clicked. Images support absolute http/https URLs, local HA URLs, and camera proxy snapshots. Sounds support absolute http/https URLs and local sound files. Control volume 0–200% (values above 100 use software boost). Customize RGB background, opacity, duration, and position (any corner). Supports interrupt (replace current notification) and targeting a specific TV by App ID."
                ),
                ChangeItem(
                    title = "Camera PiP",
                    description = "Open a camera by entity, QuickBars alias, or RTSP URL. Choose size mode: auto, preset (S/M/L), or custom {w,h}, and place it in any corner. Options include mute audio (RTSP only), auto-hide (0–300s, 0 = never), show/hide or customize the title, and show/hide the toast when toggled. You can also target a specific TV with App ID."
                ),
                ChangeItem(
                    title = "QuickBar Toggle",
                    description = "Open/close a QuickBar by alias via event, now with optional App ID to target a specific TV."
                ),
                ChangeItem(
                    title = "Onboarding & UI",
                    description = "New onboarding screen for a smoother first-run experience. Splash screen now uses a dark background to better match the app theme. Added a direct link to the website in the side menu."
                ),
                ChangeItem(
                    title = "Sensor Display",
                    description = "Cleaner formatting: integers show no decimals; non-integers show up to 3 decimals."
                )
            )

            21 -> listOf(
                ChangeItem(
                    title = "Performance & Stability",
                    description = "Camera PiP now uses fewer resources and stutters less. QuickBar opens faster and entities populate sooner. Overlay show/hide was reworked to avoid background crashes (even while video apps are playing). General stability improvements across the app."
                ),
                ChangeItem(
                    title = "QuickBar & Camera PiP",
                    description = "Home Assistant 'quickbars.open' event now toggles the QuickBar when it’s already open (same behavior as Camera PiP). Triggering a second Camera PiP in a different corner now switches to the correct stream."
                ),
                ChangeItem(
                    title = "Home Assistant Integration",
                    description = "Fixed an issue where on some Android 11 devices a QuickBar could not be triggered via the Home Assistant event."
                ),
                ChangeItem(
                    title = "Input Handling",
                    description = "Fixed cases where a long-press to trigger an action would also fire the button’s original action on release in certain apps/devices. Pressing BACK to close a QuickBar no longer sends BACK to the underlying app (e.g., Netflix/Plex). Potentially fixed DPAD_CENTER not working reliably with the accessibility service enabled (seen on older Sony Bravia). Potentially fixed cases where after closing a QuickBar with BACK, some keys (e.g., DPAD_DOWN) didn’t work until pressing BACK again."
                ),
                ChangeItem(
                    title = "Bug Fixes",
                    description = "Various reliability fixes around overlays and focus. Overall smoother behavior when showing Camera PiP and QuickBar simultaneously."
                )
            )


            20 -> listOf(
                ChangeItem(
                    title = "Performance & Stability",
                    description = "QuickBar opens a bit faster. Reworked show/hide logic for QuickBar & Camera PiP to reduce jank and avoid background crashes. Improved cold-start reliability on slower devices. Improved general stability and performance in many other places."
                ),
                ChangeItem(
                    title = "Network & Setup",
                    description = "Smarter URL handling: automatically tries :443 for HTTPS reverse-proxy setups. Better invalid-URL handling. Fixed cases where homeassistant.local (and similar local DNS) connected but showed no entities."
                ),
                ChangeItem(
                    title = "Entity Importer",
                    description = "Hardened loading state to prevent focus glitches and rare crashes while entities are loading. Navigation is smoother during load."
                ),
                ChangeItem(
                    title = "Trigger Keys",
                    description = "More reliable single/double/long handling. Failsafe toggle no longer triggers an action on release. The OK/DPAD-center key can’t be mapped anymore to avoid conflicts on some TVs."
                ),
                ChangeItem(
                    title = "Bug Fixes",
                    description = "Fixed crash when launching an app shortcut if its activity isn’t available. Fixed a case where a long-press QuickBar would close immediately and also trigger the failsafe. Fixed bug where MJPEG streams won't load on some cameras. Fixed bug with focus for Entity's name change / camera alias."
                )
            )

            19 -> listOf(
                ChangeItem(
                    title = "Performance & Stability",
                    description = "QuickBar opens a bit faster. Reworked show/hide logic for QuickBar & Camera PiP to reduce jank and avoid background crashes. Improved cold-start reliability on slower devices. Improved general stability and performance in many other places."
                ),
                ChangeItem(
                    title = "Network & Setup",
                    description = "Smarter URL handling: automatically tries :443 for HTTPS reverse-proxy setups. Better invalid-URL handling. Fixed cases where homeassistant.local (and similar local DNS) connected but showed no entities."
                ),
                ChangeItem(
                    title = "Entity Importer",
                    description = "Hardened loading state to prevent focus glitches and rare crashes while entities are loading. Navigation is smoother during load."
                ),
                ChangeItem(
                    title = "Trigger Keys",
                    description = "More reliable single/double/long handling. Failsafe toggle no longer triggers an action on release. The OK/DPAD-center key can’t be mapped anymore to avoid conflicts on some TVs."
                ),
                ChangeItem(
                    title = "Bug Fixes",
                    description = "Fixed crash when launching an app shortcut if its activity isn’t available. Fixed a case where a long-press QuickBar would close immediately and also trigger the failsafe."
                )
            )

            18 -> listOf(
                ChangeItem(
                    title = "Bug Fixes",
                    description = "Fixed a rare crash where the Accessibility Service could stop when starting the QuickBar or PiP overlay, also - fixed a bug where the \"+\" button inside Climate and Light cards in vertical grid view could appear squished, and fixed an issue where light entities didn’t behave correctly (on/off lights appeared as expandable and lights with extra capabilities lacked their extra options in settings); if lights still aren’t fixed, try deleting and re-importing them, and if the problem persists please contact me."
                )
            )

            // v1.2 (code 16)
            17 -> listOf(
                ChangeItem(
                    title = "Media Player Support",
                    description = "Added support for media_player entities. Expanded cards show controls for play/pause, skip, volume, and power toggle.",
                ),
                ChangeItem(
                    title = "Trigger Key Overhaul",
                    description = "Control locks, covers, climate, and fans directly. A single press on the same key now closes the QuickBar. Long press actions fire instantly (without lifting finger), and a 5-second hold disables Trigger Key remapping as a failsafe.",
                ),
                ChangeItem(
                    title = "UI & Quality of Life",
                    description = "A \"What's New\" dialog now shows after updates (like this one). Light cards display the current color in a small circle. Action buttons (inside an expanded card) have a new click animation. Toasts for Trigger Key actions now use your custom entity name.",
                ),
                ChangeItem(
                    title = "Bug Fixes",
                    description = "Scenes now trigger correctly in grid QuickBars. Fixed crashes when setting entity actions and focus problems when editing entity names/ camera alias. Improved action reliability with Background Connection enabled. Addressed bugs with light brightness values (254 as 100%), and allowing http/https in manual setup."
                )
            )


            // v1.1 (code 15)
            15 -> listOf(
                ChangeItem(
                    title = "Background Connection (Experimental)",
                    description = "Optional persistent WebSocket to Home Assistant for live state updates, faster QuickBar loading, and letting HA automations trigger a QuickBar or open Camera PIP. May be limited by some Android TV OEMs; toggle in Settings.",
                ),
                ChangeItem(
                    title = "Camera PIP",
                    description = "Add MJPEG camera entities as Picture-in-Picture. Choose corner, size (3 presets), auto-hide (15s/30s/1m/5m/never), and title display. Launch from QuickBar, Trigger Key, or HA event; press again to hide. One stream at a time.",
                ),
                ChangeItem(
                    title = "Manual Backup & Restore (Experimental)",
                    description = "Create/restore a single JSON file with QuickBars, Trigger Keys, and saved entities (no HA URL/token). Works across devices. Please report results.",
                ),
                ChangeItem(
                    title = "QuickBar Options",
                    description = "Show current time in the title row. Allow HA automation to trigger a QuickBar (requires Background Connection). Optional auto-close after actions for selected domains."
                ),
                ChangeItem(
                    title = "Automation Entities",
                    description = "New entity type: run an automation’s actions directly, or enable/disable the automation and its triggers (configurable in Entity Settings)."
                ),
                ChangeItem(
                    title = "General Improvements",
                    description = "Smoother light/climate controls. Clearer white focus border on QuickBar. Trigger Keys dialog warns against risky keys. Back press on main screen now shows a confirmation. Menus are more compact with better focus borders."
                ),
                ChangeItem(
                    title = "Bug Fixes",
                    description = "Light controls appear immediately after importing with extra attributes. Fan cards no longer look scrollable. Fixed Android quirk that could show an extra menu when launching an app from the launcher. Long-press on a Trigger Key executes without needing to release. Other minor fixes and performance improvements."
                )
            )

            // Add more versions here

            // Default empty list for unknown versions
            else -> emptyList()
        }
    }
}