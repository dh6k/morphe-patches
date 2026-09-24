<p align="center">
  <a href="https://github.com/dh6k/morphe-patches/releases/latest"><img src="https://img.shields.io/github/v/release/dh6k/morphe-patches?color=7928CA&label=Release&logo=github&style=flat-square" alt="Latest Release" /></a>
  <a href="https://github.com/dh6k/morphe-patches/releases"><img src="https://img.shields.io/github/downloads/dh6k/morphe-patches/total?style=flat-square&logo=github" alt="Total Downloads" /></a>
  <img src="https://img.shields.io/badge/Runtime-Morphe_Patcher_1.7.0-8A2BE2?style=flat-square" alt="Runtime" />
  <img src="https://img.shields.io/badge/License-GPLv3-blue?style=flat-square" alt="License" />
</p>

<h1 align="center">Brave, Quetta &amp; Universal Morphe Patches</h1>

<p align="center">
  Morphe patch bundle for <b>Brave Browser</b> (Origin, Startup Performance, NTP), <b>Quetta Browser</b>, Titanium keep-alive/refresh helpers, and app-independent Android resource patches on Android using the <b><a href="https://morphe.software">Morphe</a></b> patcher framework.
</p>

<p align="center">
  <a href="https://morphe.software/add-source?github=dh6k/morphe-patches"><img src="https://img.shields.io/badge/Morphe_Manager-Add_Patch_Source-8A2BE2?style=for-the-badge&logo=android&logoColor=white" alt="Add Source to Morphe Manager" /></a>
  &nbsp;&nbsp;
  <a href="https://github.com/dh6k/morphe-patches/releases/latest"><img src="https://img.shields.io/badge/Direct_Download-Get_.MPP_Bundle-0070F3?style=for-the-badge&logo=github&logoColor=white" alt="Download Latest Release .mpp Bundle" /></a>
</p>

---

## Install

1. Open the **Add Patch Source** button above (or add `https://github.com/dh6k/morphe-patches` manually in [Morphe Manager](https://morphe.software)).
2. Pick an app and enable the desired patches. Universal patches apply to any app.
3. Patch the APK or APKM and install the output.

**Or** grab the latest `.mpp` with **Get .MPP Bundle** above if you prefer side-loading the patch package yourself.

---

## Supported applications

### Quetta Browser

| Build | Package name | Support status |
| --- | --- | --- |
| Quetta Browser (Play Store edition) | `net.quetta.browser` | version-unpinned; refresh-rate patch statically validated on `2.0.5` |
| Quetta Browser (Direct APK edition) | `net.quetta.browser.official` | version-unpinned; extension patch tested on `2.0.2 (5307)` |

Two Quetta-local patches:

- **Block Quetta bundled extension installation** — bundled extension install/reinstall block.
- **Force highest refresh rate** — Quetta-adapted sibling of the Titanium patch. Separate fingerprints: Quetta 2.0.5 keeps `WindowAndroid.setPreferredRefreshRate(F)V` but obfuscates the nearest-mode worker and drops the Titanium log literal; this patch matches the structural shape (`getRefreshRate` + `getModeId` + `Window.setAttributes`) instead of editing the helium/Titanium implementation. Version-unpinned, experimental, fail-closed on ambiguity. Intended for arm64-v8a APKs; the framework does not enforce ABI.

Blocked bundled extensions:

- `nnedfbcpeenmccjbdcnlnhogapndfeoa` — Q30 from Quetta Translator
- `gadlcodpkkelmagfhkldjlobfncbkbmd` — Q30 from Quetta

Analyzed manifests show these extensions have broad page access and background behavior, and can communicate with remote services (or telemetry in short). Blocking bundled installation/reinstallation reduces bundled background code and remote-service exposure, giving users more privacy and control without claiming what those services collect. **All related functions work probably fine without these extensions in the first place, so these are definitely bloated components**.

This does not block all Quetta telemetry or every Quetta network connection (for that just use DNS with blocklist instead), and does not remove copies already installed in existing profiles. Remove existing copies through ~~Quetta's extension-management UI~~ [SimpleExtManager Beta, since it's hidden from the Inbuilt Extension management UI](https://chromewebstore.google.com/detail/simpleextmanager-beta/bbgbjeiedibajiehaenkindljahjkodi) and install it from [here](https://www.crx4chrome.com/crx-downloader/) if download is interrupted from the Web store, ~~or use a clean profile as appropriate~~ **Clean install already removed that, just patch it as usual and enjoy**. Static validation is anchored to supplied Quetta `2.0.2-530` Official (Direct APK from website) arm64-v8a APK; this is not broad runtime proof. Future versions may change fingerprints; patch should fail safely rather than modify unrelated methods.

### Brave Browser

| Build | Package name | Support status |
| --- | --- | --- |
| Brave Browser | `com.brave.browser` | Origin tested on `1.92.140`; Startup Performance validated on Nightly `1.98.21` |
| Brave Beta | `com.brave.browser_beta` | Experimental; version-unpinned |
| Brave Nightly | `com.brave.browser_nightly` | Experimental; version-unpinned; Startup Performance validated on `1.98.21` |

Beta and Nightly share Brave Origin code paths, but require APK validation for each release before promotion from experimental support.

See [Patch notes](#patch-notes) for Startup Performance, Custom NTP wallpaper, and the WebAPK limitation under Brave Origin.

### Titanium Browser

| Build | Package name | Support status |
| --- | --- | --- |
| Titanium Browser for Android | (see patch catalog) | Experimental; version-unpinned keep-alive + refresh-rate helpers |

---

## Patches

<!-- PATCHES_START EXPANDED -->
> **[v1.7.0](https://github.com/dh6k/morphe-patches/releases/tag/v1.7.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;9 patches total
<details open>
<summary>📦 Quetta Browser&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Block Quetta bundled extension installation](#block-quetta-bundled-extension-installation) | Blocks bundled extension installation/reinstallation on arm64-v8a APKs (the framework does not enforce ABI restrictions). Does not remove copies already present in existing profiles. Takes effect immediately on clean installs. |  |
| [Force highest refresh rate](#force-highest-refresh-rate) | Quetta-adapted experimental version-unpinned patch: forces Chromium WindowAndroid to pick the highest-refresh Display mode by writing Float.MAX_VALUE into setPreferredRefreshRate(F) and the structural nearest-mode worker (getRefreshRate + getModeId + Window.setAttributes). Validated statically on Quetta 2.0.5 base APK; may increase battery usage; ambiguous targets fail closed. |  |

</details>

<details open>
<summary>📦 Quetta Browser Official&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Block Quetta bundled extension installation](#block-quetta-bundled-extension-installation) | Blocks bundled extension installation/reinstallation on arm64-v8a APKs (the framework does not enforce ABI restrictions). Does not remove copies already present in existing profiles. Takes effect immediately on clean installs. |  |
| [Force highest refresh rate](#force-highest-refresh-rate) | Quetta-adapted experimental version-unpinned patch: forces Chromium WindowAndroid to pick the highest-refresh Display mode by writing Float.MAX_VALUE into setPreferredRefreshRate(F) and the structural nearest-mode worker (getRefreshRate + getModeId + Window.setAttributes). Validated statically on Quetta 2.0.5 base APK; may increase battery usage; ambiguous targets fail closed. |  |

</details>

<details open>
<summary>📦 Brave Browser&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Brave Origin](#brave-origin) | Unlocks Brave Origin and enables feature toggle controls. |  |
| [Brave Startup Performance Optimization](#brave-startup-performance-optimization) | Optimizes startup time and eliminates background CPU/disk overhead by disabling unused OEM carrier partner customizations. Marks PartnerBrowserCustomizations initialized without SharedPreferences/ContentResolver/ThreadPool/timeout work, drains init callbacks immediately, and forces partner homepage and incognito lockdown gates closed. |  |
| [Custom NTP wallpaper](#custom-ntp-wallpaper) | Alpha experimental version-unpinned patch (issue #13): forces the Brave new-tab background to a custom PNG chosen at patch time. Rewrites the Java ambient wallpaper catalog (BackgroundImage drawable resource id) and makes wallpaper callbacks use it instead of native branded/URL images. IMPORTANT: crop the image to your current screen resolution first, then select that file in the patch options. Brave's New tab page settings only toggle "Show background images". Default off. | • Custom NTP wallpaper |

</details>

<details open>
<summary>📦 Brave Beta&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Brave Origin](#brave-origin) | Unlocks Brave Origin and enables feature toggle controls. |  |
| [Brave Startup Performance Optimization](#brave-startup-performance-optimization) | Optimizes startup time and eliminates background CPU/disk overhead by disabling unused OEM carrier partner customizations. Marks PartnerBrowserCustomizations initialized without SharedPreferences/ContentResolver/ThreadPool/timeout work, drains init callbacks immediately, and forces partner homepage and incognito lockdown gates closed. |  |
| [Custom NTP wallpaper](#custom-ntp-wallpaper) | Alpha experimental version-unpinned patch (issue #13): forces the Brave new-tab background to a custom PNG chosen at patch time. Rewrites the Java ambient wallpaper catalog (BackgroundImage drawable resource id) and makes wallpaper callbacks use it instead of native branded/URL images. IMPORTANT: crop the image to your current screen resolution first, then select that file in the patch options. Brave's New tab page settings only toggle "Show background images". Default off. | • Custom NTP wallpaper |

</details>

<details open>
<summary>📦 Brave Nightly&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Brave Origin](#brave-origin) | Unlocks Brave Origin and enables feature toggle controls. |  |
| [Brave Startup Performance Optimization](#brave-startup-performance-optimization) | Optimizes startup time and eliminates background CPU/disk overhead by disabling unused OEM carrier partner customizations. Marks PartnerBrowserCustomizations initialized without SharedPreferences/ContentResolver/ThreadPool/timeout work, drains init callbacks immediately, and forces partner homepage and incognito lockdown gates closed. |  |
| [Custom NTP wallpaper](#custom-ntp-wallpaper) | Alpha experimental version-unpinned patch (issue #13): forces the Brave new-tab background to a custom PNG chosen at patch time. Rewrites the Java ambient wallpaper catalog (BackgroundImage drawable resource id) and makes wallpaper callbacks use it instead of native branded/URL images. IMPORTANT: crop the image to your current screen resolution first, then select that file in the patch options. Brave's New tab page settings only toggle "Show background images". Default off. | • Custom NTP wallpaper |

</details>

<details open>
<summary>📦 Titanium Browser for Android&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Force highest refresh rate](#force-highest-refresh-rate) | Experimental version-unpinned patch: forces Chromium to pick the highest-refresh display mode by requesting Float.MAX_VALUE through WindowAndroid. Works on any panel (60/90/120/144/165Hz+) without knowing the max at patch time. May increase battery usage; ambiguous targets fail closed. |  |
| [Keep Titanium Extensions Child Processes Alive](#keep-titanium-extensions-child-processes-alive) | Experimental version-unpinned structural/data-flow patch: starts one main-process foreground service with persistent low-priority notification and forces child STRONG binding plus IMPORTANT/STRONG priority updates. Tolerates routine signature, register, and helper-name changes; ambiguous targets fail closed. May increase RAM, battery, and process pressure; mitigates LMK kills only. To hide the notification, use Android Settings > Apps > Titanium > Notifications (the keep-alive service stays active either way). | • Notification title<br>• Notification text |

</details>

<details open>
<summary>🌐 Universal&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Change app icon](#change-app-icon) | Changes the Android launcher icon using a custom PNG image. Use a square image with transparent adaptive-icon padding. | • Custom app icon |
| [Change app name](#change-app-name) | Changes the app name shown by Android launchers. Set the desired name in the patch options. | • App name |
| [Disable analytics](#disable-analytics) | Disables analytics and tracking from multiple SDKs, including AppMetrica, MyTracker, Firebase, Sentry, Google Analytics, Amplitude, Mixpanel, Adjust, AppsFlyer, Facebook, MoEngage, and comScore, through manifest opt-outs and exact runtime entry points when present. Custom or server-side telemetry is not covered. |  |

</details>

<!-- PATCHES_END -->

---

## Patch notes

Deep dives for patches that need more than the catalog description.

### Brave Origin

Unlocks **Brave Origin** and enables the Origin feature-toggle controls (subscription / feature-policy surface). Compatible with `com.brave.browser`, `com.brave.browser_beta`, and `com.brave.browser_nightly`.

#### Web app installation limitation

Patched Brave APKs are re-signed. WebAPK installation through **Install and create shortcut > Install** may remain stuck on `Installing`, especially when the package name is also changed. This is separate from the Brave Origin bytecode patch: Brave Origin modifies subscription and feature-policy behavior, not the Chromium WebAPK installer.

Use **Create shortcut** as the supported workaround. It uses Android's pinned-shortcut flow and does not install a WebAPK.

When reporting this problem, include the app version, final package name, whether **Create shortcut** works, and filtered ADB output:

```powershell
adb logcat -c
# Reproduce the failed Install action, then run:
adb logcat -d -v threadtime |
    Select-String -Pattern 'webapk|shortcut|packageinstaller|finsky|playcore|install'
```

Also verify the installed package and patched APK certificate:

```powershell
adb shell dumpsys package <package-name>
apksigner verify --print-certs <patched.apk>
```

Do not use Chromium's GServices WebAPK package/signing-check overrides as an end-user fix. Those overrides are intended for development builds and may require privileged device access.

### Brave Startup Performance Optimization

New patch for [issue #16](https://github.com/dh6k/morphe-patches/issues/16). Brave runs OEM/carrier **partner customizations** (`org.chromium.chrome.browser.partnercustomizations.PartnerBrowserCustomizations`) during browser startup. On devices with no partner package this still performs main-thread `SharedPreferences` reads, spawns a background resolver, and schedules a **10-second `PostTask` timeout** that re-runs pending callbacks — pure CPU/disk overhead on the launch path.

The patch marks the component initialized immediately and forces every partner gate closed:

| Hook | Fingerprint anchor | Effect |
| --- | --- | --- |
| `initializeAsync(Context)V` | `Chrome.Homepage.PartnerCustomizedDefaultGurl` / `...DefaultUri` preference keys | writes `Boolean.TRUE` into the init flag and returns; the original 12 calls (2x prefs reads, async resolver, timeout task) become dead code |
| `setOnInitializeAsyncFinished(Runnable)V` | `setOnInitializeAsyncFinished` literal | runs the pending `Runnable` inline via `invoke-interface` instead of queuing it behind the (now skipped) init |
| `isIncognitoDisabled()Z` | exact method name | always `false` — a carrier can no longer disable Incognito |
| homepage accept `(GURL)Z` | `is too long.` literal | always `false` — partner homepage URLs are rejected |
| homepage delegate `(...)Z` | `Partner homepage delegate URL read failed : ` literal | always `false` — no delegate URL is accepted |

It depends on **Brave Native Library Extraction Compatibility**, which sets `android:extractNativeLibs="true"` for 16 KB-page and BTI compatibility on modern ARM64 devices.

Supported on `com.brave.browser` (APK and APKM), `com.brave.browser_beta` (APK), and `com.brave.browser_nightly` (APK and APKM); version-unpinned, enabled by default.

**Validation:** statically validated on Brave Nightly `1.98.21` (`com.brave.browser_nightly`, arm64-v8a universal standalone APK) — 5/5 hooks applied, 0 fingerprint mismatches. Bytecode comparison confirms the original init path is unreachable after the prologue (`sget-object` / `iput-object` / `return-void`). No on-device cold-start timing was measured; the eliminated work is startup-path disk I/O plus a 10 s timeout wakeup, so the gain is device-dependent.

By design this disables OEM partner homepage and Incognito-lockdown behavior. On non-carrier devices these were already inert.

### Custom NTP wallpaper

Alpha experimental patch for [issue #13](https://github.com/dh6k/morphe-patches/issues/13). Brave's **New tab page** settings only expose **Show background images**; this patch forces the NTP background to a patch-time PNG by rewriting the Java ambient wallpaper catalog (`BackgroundImage` drawable resource id) and making wallpaper callbacks use it instead of native branded/URL images.

**How to use:** crop the wallpaper to your **current screen resolution** first (gallery / any crop tool, exact width × height of the device), then select that PNG in the patch options. Default off. Ambiguous targets fail closed. Intended for arm64-v8a APKs; the framework does not enforce ABI.

### Keep Titanium Extensions Child Processes Alive

Experimental version-unpinned two-layer mitigation for [issue #57](https://github.com/jqssun/android-titanium-browser/issues/57): child processes receive Chromium STRONG binding (`0x4`) and IMPORTANT/STRONG priority (`0x3`), while one main-process foreground service keeps extension background runtime visible through a persistent low-priority notification. Structural and local data-flow resolution tolerates routine signature, register, helper-name, and process-launch changes, then fails closed when relevant bytecode is genuinely ambiguous. Disabled by default and version-unpinned (no pinned Titanium version). Affects all relevant Titanium child processes, not only extensions. This can raise RAM, battery, and process pressure and only mitigates LMK kills; it does not guarantee survival, bypass force-stop or OEM task killers, detect or reload crashed extensions, or run a watchdog/polling loop/wake lock. A persistent low-importance foreground-service notification may appear. Future incompatible APKs may fail during patching. No reliable extension-only discriminator exists in inspected Titanium code, so child binding applies to all child processes.

---

## Build

```bash
./gradlew :patches:buildAndroid
```

Release CI regenerates `patches-list.json`, `patches-bundle.json`, and the catalog between `<!-- PATCHES_START … -->` / `<!-- PATCHES_END -->`. Do not hand-edit that block.

---

## License

Licensed under [GPLv3](LICENSE). See [NOTICE](NOTICE) for additional GPLv3 Section 7 conditions.

`Change app name` is adapted from
[durgesh0505/chiggi_morphe_patches](https://github.com/durgesh0505/chiggi_morphe_patches)
at commit `6b8a9a36cbd36faa4d5b8ce6e811fb428eb365f9`.
