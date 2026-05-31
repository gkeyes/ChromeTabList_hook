# ChromeTabList_hook

Xposed module for Chrome Android tab switcher compact mode.

## Behavior

- Targets `com.android.chrome` only.
- Finds Chrome's `tab_list_recycler_view`.
- Hides tab thumbnails.
- Forces each visible tab item to a compact height.
- Vertically centers the tab title area.

## Fix Included

The hook patches each `tab_list_recycler_view` instance instead of using a
single process-wide patched flag. This keeps the module working after Chrome is
sent to the background and later rebuilds the tab switcher view.

## Build

GitHub Actions builds the debug APK on every push to `main` and on version tags.
Pushing a tag such as `v1.0.0` also creates a GitHub Release and uploads the APK.

The workflow uses:

- JDK 17
- Gradle 9.1.0
- Android Gradle Plugin 9.0.0
- Android SDK platform 34

The uploaded APK is the debug variant, which is signed by the Android build
tools and can be installed for module testing.
