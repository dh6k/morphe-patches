<p align="center">
  <a href="https://github.com/dh6k/morphe-patches/releases/latest"><img src="https://img.shields.io/github/v/release/dh6k/morphe-patches?color=7928CA&label=Release&logo=github&style=flat-square" alt="Latest Release" /></a>
  <a href="https://github.com/dh6k/morphe-patches/releases"><img src="https://img.shields.io/github/downloads/dh6k/morphe-patches/total?style=flat-square&logo=github" alt="Total Downloads" /></a>
  <img src="https://img.shields.io/badge/Runtime-Morphe_Patcher_1.15.0-8A2BE2?style=flat-square" alt="Runtime" />
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
> **[v1.9.0-dev.4](https://github.com/dh6k/morphe-patches/releases/tag/v1.9.0-dev.4)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;11 patches total
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
<summary>📦 Brave Browser&nbsp;&nbsp;•&nbsp;&nbsp;5 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Brave AMOLED theme](#brave-amoled-theme) | Patch-time AMOLED dark theme (issue #21): rewrites Brave dark chrome surfaces to pure black (or a custom opaque hex). Raised surfaces — buttons, cards, sheets and the Material You container ladder — can take their own surface color instead of one flat black, so the UI keeps an elevation step like Material 3. Optional text and accent colors (defaults keep Brave's #f0f2ff / #737ade). Forces Material You dynamic colors off in bytecode (the pref is non-persistent) and overrides system neutral night roles on Android 12+; leaving that force off keeps the Material You roles and low-lStar selectors untouched. Apply Dark theme in Brave to see it. Does not change web content force-dark, NTP theme collections, or add a runtime color picker. Default off. | • AMOLED background<br>• Text color<br>• Accent color<br>• Disable Material You dynamic colors<br>• Surfaces use the background color<br>• Surface / button color |
| [Brave NTP four-column tiles](#brave-ntp-four-column-tiles) | Experimental version-unpinned patch (issue #24): keeps the new-tab pinned and most-visited tiles in a four-column grid that extends downwards, the same layout Brave uses with no background image, and keeps that layout when a background image is enabled. Neutralizes the "brave.new_tab_page.show_background_image" gate in the NTP builder that otherwise forces the tiles into a single horizontally scrolling row. Default off. |  |
| [Brave Origin](#brave-origin) | Unlocks Brave Origin and enables feature toggle controls. |  |
| [Brave Startup Performance Optimization](#brave-startup-performance-optimization) | Optimizes startup time and eliminates background CPU/disk overhead by disabling unused OEM carrier partner customizations. Marks PartnerBrowserCustomizations initialized without SharedPreferences/ContentResolver/ThreadPool/timeout work, drains init callbacks immediately, and forces partner homepage and incognito lockdown gates closed. |  |
| [Custom NTP wallpaper](#custom-ntp-wallpaper) | Alpha experimental version-unpinned patch (issue #13): forces the Brave new-tab background to a custom PNG chosen at patch time. Rewrites the Java ambient wallpaper catalog (BackgroundImage drawable resource id) and makes wallpaper callbacks use it instead of native branded/URL images. IMPORTANT: crop the image to your current screen resolution first, then select that file in the patch options. Brave's New tab page settings only toggle "Show background images". Default off. | • Custom NTP wallpaper |

</details>

<details open>
<summary>📦 Brave Beta&nbsp;&nbsp;•&nbsp;&nbsp;5 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Brave AMOLED theme](#brave-amoled-theme) | Patch-time AMOLED dark theme (issue #21): rewrites Brave dark chrome surfaces to pure black (or a custom opaque hex). Raised surfaces — buttons, cards, sheets and the Material You container ladder — can take their own surface color instead of one flat black, so the UI keeps an elevation step like Material 3. Optional text and accent colors (defaults keep Brave's #f0f2ff / #737ade). Forces Material You dynamic colors off in bytecode (the pref is non-persistent) and overrides system neutral night roles on Android 12+; leaving that force off keeps the Material You roles and low-lStar selectors untouched. Apply Dark theme in Brave to see it. Does not change web content force-dark, NTP theme collections, or add a runtime color picker. Default off. | • AMOLED background<br>• Text color<br>• Accent color<br>• Disable Material You dynamic colors<br>• Surfaces use the background color<br>• Surface / button color |
| [Brave NTP four-column tiles](#brave-ntp-four-column-tiles) | Experimental version-unpinned patch (issue #24): keeps the new-tab pinned and most-visited tiles in a four-column grid that extends downwards, the same layout Brave uses with no background image, and keeps that layout when a background image is enabled. Neutralizes the "brave.new_tab_page.show_background_image" gate in the NTP builder that otherwise forces the tiles into a single horizontally scrolling row. Default off. |  |
| [Brave Origin](#brave-origin) | Unlocks Brave Origin and enables feature toggle controls. |  |
| [Brave Startup Performance Optimization](#brave-startup-performance-optimization) | Optimizes startup time and eliminates background CPU/disk overhead by disabling unused OEM carrier partner customizations. Marks PartnerBrowserCustomizations initialized without SharedPreferences/ContentResolver/ThreadPool/timeout work, drains init callbacks immediately, and forces partner homepage and incognito lockdown gates closed. |  |
| [Custom NTP wallpaper](#custom-ntp-wallpaper) | Alpha experimental version-unpinned patch (issue #13): forces the Brave new-tab background to a custom PNG chosen at patch time. Rewrites the Java ambient wallpaper catalog (BackgroundImage drawable resource id) and makes wallpaper callbacks use it instead of native branded/URL images. IMPORTANT: crop the image to your current screen resolution first, then select that file in the patch options. Brave's New tab page settings only toggle "Show background images". Default off. | • Custom NTP wallpaper |

</details>

<details open>
<summary>📦 Brave Nightly&nbsp;&nbsp;•&nbsp;&nbsp;5 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Brave AMOLED theme](#brave-amoled-theme) | Patch-time AMOLED dark theme (issue #21): rewrites Brave dark chrome surfaces to pure black (or a custom opaque hex). Raised surfaces — buttons, cards, sheets and the Material You container ladder — can take their own surface color instead of one flat black, so the UI keeps an elevation step like Material 3. Optional text and accent colors (defaults keep Brave's #f0f2ff / #737ade). Forces Material You dynamic colors off in bytecode (the pref is non-persistent) and overrides system neutral night roles on Android 12+; leaving that force off keeps the Material You roles and low-lStar selectors untouched. Apply Dark theme in Brave to see it. Does not change web content force-dark, NTP theme collections, or add a runtime color picker. Default off. | • AMOLED background<br>• Text color<br>• Accent color<br>• Disable Material You dynamic colors<br>• Surfaces use the background color<br>• Surface / button color |
| [Brave NTP four-column tiles](#brave-ntp-four-column-tiles) | Experimental version-unpinned patch (issue #24): keeps the new-tab pinned and most-visited tiles in a four-column grid that extends downwards, the same layout Brave uses with no background image, and keeps that layout when a background image is enabled. Neutralizes the "brave.new_tab_page.show_background_image" gate in the NTP builder that otherwise forces the tiles into a single horizontally scrolling row. Default off. |  |
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
| [Keep Titanium Extensions Child Processes Alive](#keep-titanium-extensions-child-processes-alive) | Experimental version-unpinned structural/data-flow patch: starts one main-process foreground service with persistent low-priority notification and forces STRONG binding plus IMPORTANT priority only for extension child processes (detected via --extension-process, matching upstream Titanium commit ff12f1c). Renderer and GPU children keep stock priority, so RAM and battery pressure stay closer to baseline. Tolerates routine signature, register, and helper-name changes; ambiguous targets fail closed. Mitigates LMK kills only. To hide the notification, use Android Settings > Apps > Titanium > Notifications (the keep-alive service stays active either way). | • Notification title<br>• Notification text |

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

### Brave AMOLED theme

Patch-time AMOLED rewrite for [issue #21](https://github.com/dh6k/morphe-patches/issues/21). Brave's dark chrome ships near-black neutrals (`#121212`, `#1e2029`, `#2e3039`) and a Material You role ladder on Android 12+; this patch flattens the chrome to a chosen background, and can keep raised surfaces on their own colour so the UI does not collapse into one flat black.

**Why the flat preset reads as Material 2.** With a single background colour a card, a sheet and the window are all the same pixel value, which is exactly what pre-M3 Android chrome looked like. Material 3 keeps an elevation step — containers are a shade brighter than the window. The two tiers restore that step without touching shape, corner radius or typography, because Brave ships no Material 2 asset set to switch to: the APK contains only `m3_*` selectors (113 files) and never an `m2_*` or `md2_*` resource, and every style name in a release build is obfuscated to `0_resource_name_obfuscated`.

**Options.**

| Option | Effect |
| --- | --- |
| `AMOLED background` | Window backgrounds and everything else in dark chrome. `#000000` for pure OLED. |
| `Surfaces use the background color` | **On (default): one flat black, identical to the previous AMOLED rewrite.** Off: raised surfaces take the surface colour below. |
| `Surface / button color` | Opaque hex for buttons, cards, sheets and the Material You container ladder. Default `#1e2029`, Brave's own dark container tone. |
| `Text color`, `Accent color` | Unchanged from earlier releases. |
| `Disable Material You dynamic colors` | Forces the non-persistent `brave_android_dynamic_colors_enabled` readers to `false` in bytecode. Turning it **off** now also skips the Material You resource rewrites — previously the role files were still pinned to the AMOLED hex, so the wallpaper palette could never show and the switch was a no-op. |

**How a surface is classified.** There is no reliable name to match, so the tier is decided by evidence, strongest first:

1. The id's role in `values-v31/v34/v35` — `system_surface_container*`, `system_surface_bright` and `system_surface_variant` are raised; `system_background`, `system_surface`, `system_surface_dim` and the API 31 `system_neutral*` tones are the window.
2. The palette tone, for `res/color-v31` selectors. On Android 12/12L/13 the popup menu, the app bar and the window background are all painted through `m3_ref_palette_dynamic_neutral_variant*` selectors whose colour is the *same* `system_neutral2_600` — so the role lookup finds nothing and only the tone separates them: 6 is the window, 12 and up are the raised steps (24 is the `surface_bright` behind the popup menu).
3. The fill's value — a raised fill is a step brighter than `#1c1c1d` (`BASE_SURFACE_MAX_CHANNEL`).
4. A denylist of ids styles paint with `android:colorBackground` / `android:windowBackground`, which are never raised whatever their value looks like.

**Validation (static, Brave stable `1.96.60` `com.brave.browser`, arm64-v8a universal APK).** The resource rewrite was run over the apktool-decoded resource tree, once with the default flat preset and once with `surfaceUsesBackground=false, surfaceColor=#1e2029`:

| Run | day | night | container | Material You roles | night-v31 overrides | lStar selectors | drawable fills |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Flat (default) | 73 | 37 | 0 | 16 | 138 | 6 | 85 |
| Two tiers | 9 | 6 | 87 | 16 | 138 | 6 | 85 |

Both runs produced exactly the same set of colour ids — the tier option re-colours ids, it never adds or drops one — and every rewritten value is an opaque hex. The role evidence classified 14 ids (`values-v34` ladder: `system_surface_container*`, `surface_bright`, `surface_variant`) plus 9 window roles; the remaining 190 raised ids came from the value heuristic. **That heuristic is the honest ceiling here: no on-device screenshot confirms those 190 are all buttons and cards rather than window fills.** The window-background denylist found 12 ids on this build and none of them would have been raised anyway, so it is insurance for other builds, not something this APK exercised.

**The popup menu, and why the window cannot follow it.** On Android 12/12L/13 the app menu background is `?attr` → `@color` → `res/color-v31/m3_ref_palette_dynamic_neutral_variant24.xml`, while the window background, `system_surface_dark` and `system_surface_dim_dark` all resolve through `…neutral_variant6.xml`. The two files name the same `system_neutral2_600` palette tone and differ only in their tone value, which is why an earlier build sent both to the flat background and the menu looked like the window. Verified after the fix on the same decoded tree: `neutral_variant6` → `#000000`, `neutral_variant22` and `neutral_variant24` → `#1e2029`.

That same aliasing puts a ceiling on the Settings screen: on API 31–33 `system_background_dark`, `system_surface_dark` and `system_surface_dim_dark` are three ids pointing at **one** selector file, and on API 34+ Material 3 defines `background` and `surface` as the same colour. The settings page is the window background by design, so the `AMOLED background` option is what moves it — the surface colour cannot.

**Not covered:** web content force-dark (`brave_night_mode_enabled_key`), the `chrome://settings` page background, NTP theme collections, Chromium `ColorProvider` / native `.pak` chrome, and a runtime colour picker. No on-device test was run for this change.

### Brave NTP four-column tiles

Experimental version-unpinned patch for [issue #24](https://github.com/dh6k/morphe-patches/issues/24). Brave's NTP tiles collapse into a single horizontally scrolling row as soon as a background image is enabled.

**Cause.** `MostVisitedTilesLayout` (a Chromium class Brave partially rewrote) keeps two layouts behind one boolean instance flag — `TilesLinearLayout` in a `HorizontalScrollView` for a single row, or a `GridLayout` with a hardcoded **4 columns** laid out downwards. Brave writes that flag in the NTP builder, and only when the `brave.new_tab_page.show_background_image` pref is **off**:

```
pref = PrefService.getBoolean("brave.new_tab_page.show_background_image")
if (pref) skip
MostVisitedTilesLayout-><flag> = true     // iput-boolean, the single write in the APK
```

The flag has exactly one writer and one reader (the measure pass), so the gate is the only thing standing between the two layouts.

**Fix.** The patch replaces the single `if-nez` skip branch after the pref read with a `nop`, so the flag is always written as true. `if-nez` is a 2-byte format-21t and `nop` is a 2-byte format-10x, so the swap is byte-identical and every branch offset in the ~6.3 kB factory method stays valid.

**Why it writes the flag instead of the read.** Rewriting the reader to force `true` makes the value provably constant, and the reassembler then folds away the surrounding four-column code — `const/4 v2, 4` and its width guard disappear, leaving `setColumnCount` with a stale register and a one-column grid. Writing the producer leaves the reader and all of its code untouched.

`HorizontalScrollView` is only used in `onLayout` to auto-scroll a focused tile into view, and both of its call sites are null-guarded, so the grid path does not depend on the scroll container.

**Validation:** statically validated on three builds — stable `1.96.60` (`com.brave.browser`), Nightly `1.98.21`, and Nightly `1.99.6` (both `com.brave.browser_nightly`, arm64-v8a universal). All three carry the same pref literal, the same single `if-nez` gate immediately after the pref read, and the same single flag write, so the fingerprint pins only the pref literal plus the structural shape.

The enclosing method differs per build — a constructor on Nightly 1.98.21, `a(TabImpl[])Lxic;` on stable 1.96.60, `a(TabImpl[])Lnrc;` on Nightly 1.99.6 — so nothing about the signature is pinned. An earlier attempt that pinned `returnType = "V"` matched one build and silently missed another. On 1.96.60 six methods carry the pref literal and only one of them also writes the flag, so resolution stays unique.

On every build the patched dex was disassembled and compared against the original: the measure pass is byte-identical and still carries `const/4 v2, 4` and `setColumnCount`; the gate site becomes `nop`; the factory method's code size is unchanged (5436 bytes on 1.99.6, 6596 on 1.96.60), so no branch offset drifts. No on-device test was run.

### Keep Titanium Extensions Child Processes Alive

Experimental version-unpinned two-layer mitigation for [issue #57](https://github.com/jqssun/android-titanium-browser/issues/57): extension child processes (detected via `--extension-process`, matching upstream Titanium commit ff12f1c) receive Chromium STRONG binding (`0x4`) and IMPORTANT priority (`0x3`), while one main-process foreground service keeps extension background runtime visible through a persistent low-priority notification. Renderer and GPU children keep stock priority, so RAM and battery pressure stay closer to baseline than the previous all-children boost. Structural and local data-flow resolution tolerates routine signature, register, helper-name, and process-launch changes, then fails closed when relevant bytecode is genuinely ambiguous. Disabled by default and version-unpinned (no pinned Titanium version). This only mitigates LMK kills; it does not guarantee survival, bypass force-stop or OEM task killers, detect or reload crashed extensions, or run a watchdog/polling loop/wake lock. A persistent low-importance foreground-service notification may appear. Future incompatible APKs may fail during patching.

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
