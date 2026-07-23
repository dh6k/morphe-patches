# Brave Origin Patches

Morphe patch bundle for Brave Browser on Android. Contains one patch: Brave Origin.

## Supported builds

| Build | Package name | Support status |
| --- | --- | --- |
| Brave Browser | `com.brave.browser` | Tested on `1.92.140` |
| Brave Beta | `com.brave.browser_beta` | Experimental; version-unpinned |
| Brave Nightly | `com.brave.browser_nightly` | Experimental; version-unpinned |

Beta and Nightly share Brave Origin code paths, but require APK validation for each release before promotion from experimental support.

## Patch

<!-- PATCHES_START EXPANDED -->
> **[v1.0.1](https://github.com/dh6k/morphe-patches/releases/tag/v1.0.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;1 patch total
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

<!-- PATCHES_END -->

## Install

1. Install [Morphe Manager](https://morphe.software) on Android.
2. Add `https://github.com/bufferk/morphe-patches` as patch source.
3. Select Brave Browser, Brave Beta, or Brave Nightly. Select `Brave Origin`.
4. Patch APKM and install output.

## Build

```bash
./gradlew :patches:buildAndroid
```

## License

Licensed under [GPLv3](LICENSE). See [NOTICE](NOTICE) for additional GPLv3 Section 7 conditions.
