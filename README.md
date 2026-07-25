# Brave Origin and Universal Patches

Morphe patch bundle for Brave Browser plus app-independent Android resource patches.

## Brave Origin support

| Build | Package name | Support status |
| --- | --- | --- |
| Brave Browser | `com.brave.browser` | Tested on `1.92.140` |
| Brave Beta | `com.brave.browser_beta` | Experimental; version-unpinned |
| Brave Nightly | `com.brave.browser_nightly` | Experimental; version-unpinned |

Beta and Nightly share Brave Origin code paths, but require APK validation for each release before promotion from experimental support.

## Patches

<!-- PATCHES_START EXPANDED -->
> **[v1.0.2](https://github.com/dh6k/morphe-patches/releases/tag/v1.0.2)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;3 patches total
<details open>
<summary>📦 Brave Browser&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 1.92.140 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Brave Origin](#brave-origin) | Unlocks Brave Origin and enables feature toggle controls. |  |

</details>

<details open>
<summary>📦 Brave Beta&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Brave Origin](#brave-origin) | Unlocks Brave Origin and enables feature toggle controls. |  |

</details>

<details open>
<summary>📦 Brave Nightly&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Brave Origin](#brave-origin) | Unlocks Brave Origin and enables feature toggle controls. |  |

</details>

<details open>
<summary>🌐 Universal&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Change app icon](#change-app-icon) | Changes the Android launcher icon using a custom PNG image. Use a square image with transparent adaptive-icon padding. | • Custom app icon |
| [Change app name](#change-app-name) | Changes the app name shown by Android launchers. Set the desired name in the patch options. | • App name |

</details>

<!-- PATCHES_END -->

## Install

1. Install [Morphe Manager](https://morphe.software) on Android.
2. Add `https://github.com/dh6k/morphe-patches` as patch source.
3. Select an app and enable the desired patch. Universal patches are available for any app.
4. Patch the APK or APKM and install output.

## Build

```bash
./gradlew :patches:buildAndroid
```

## License

Licensed under [GPLv3](LICENSE). See [NOTICE](NOTICE) for additional GPLv3 Section 7 conditions.

`Change app name` is adapted from
[durgesh0505/chiggi_morphe_patches](https://github.com/durgesh0505/chiggi_morphe_patches)
at commit `6b8a9a36cbd36faa4d5b8ce6e811fb428eb365f9`.
