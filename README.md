# ChromeTabList_hook

Xposed module for Chrome Android tab switcher compact mode.

## Behavior

- Targets `com.android.chrome` only.
- Finds Chrome's `tab_list_recycler_view`.
- Hides tab thumbnails.
- Forces each visible tab item to a compact height.
- Vertically centers the tab title area.
- Recommends only `com.android.chrome` as the LSPosed scope.

## Fix Included

The hook patches each `tab_list_recycler_view` instance instead of using a
single process-wide patched flag. This keeps the module working after Chrome is
sent to the background and later rebuilds the tab switcher view.

## Build

GitHub Actions builds the release APK on every push to `main` and on version tags.
Pushing a tag such as `v1.0.0` also creates a GitHub Release and uploads the APK.

The workflow uses:

- JDK 17
- Gradle 9.1.0
- Android Gradle Plugin 9.0.0
- Android SDK platform 34

Release APKs from `v1.0.2` onward are signed with the same release keystore from
GitHub Actions secrets, so later versions can be installed over earlier
fixed-signed releases. Moving from `v1.0.0` or `v1.0.1` may require uninstalling
once because those APKs used CI debug signing.
