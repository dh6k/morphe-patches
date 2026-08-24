# Brave Origin, Quetta and Universal Patches

Morphe patch bundle for Brave Browser, Quetta browser plus app-independent Android resource patches.

## Quetta Browser support

| Build | Package name | Support status |
| --- | --- | --- |
| Quetta Browser (Play Store edition) | `net.quetta.browser` | version-unpinned, Should work normally |
| Quetta Browser (Direct APK edition) | `net.quetta.browser.official` | version-unpinned, tested on `2.0.2 (5307)` |

Supports both versions of Quetta Browser through the **Block Quetta bundled extension installation** patch. Compatibility is version-unpinned and experimental, intended for arm64-v8a APKs (armeabi-v7a might work too, but currently not planned atm); the framework does not enforce ABI.

Patch blocks bundled installation/reinstallation for these exact extensions:

- `nnedfbcpeenmccjbdcnlnhogapndfeoa` — Q30 from Quetta Translator
- `gadlcodpkkelmagfhkldjlobfncbkbmd` — Q30 from Quetta

Analyzed manifests show these extensions have broad page access and background behavior, and can communicate with remote services (or telemetry in short). Blocking bundled installation/reinstallation reduces bundled background code and remote-service exposure, giving users more privacy and control without claiming what those services collect. **All related functions works probably fine without these extensions in the first place, so these are definitely bloated components**.

This does not block all Quetta telemetry or every Quetta network connection (for that just use DNS with blocklist instead), and does not remove copies already installed in existing profiles. Remove existing copies through ~~Quetta's extension-management UI~~ [SimpleExtManager Beta, since it's hidden from the Inbuilt Extension management UI](https://chromewebstore.google.com/detail/simpleextmanager-beta/bbgbjeiedibajiehaenkindljahjkodi) and install it from [here](https://www.crx4chrome.com/crx-downloader/) if download interrupted from the Web store, ~~or use a clean profile as appropriate~~ **Clean install already removed that, just patch it as usual and enjoy**. Static validation is anchored to supplied Quetta `2.0.2-530` Official (Direct APK from website) arm64-v8a APK; this is not broad runtime proof. Future versions may change fingerprints; patch should fail safely rather than modify unrelated methods.

## Brave Origin support

| Build | Package name | Support status |
| --- | --- | --- |
| Brave Browser | `com.brave.browser` | Tested on `1.92.140` |
| Brave Beta | `com.brave.browser_beta` | Experimental; version-unpinned |
| Brave Nightly | `com.brave.browser_nightly` | Experimental; version-unpinned |

Beta and Nightly share Brave Origin code paths, but require APK validation for each release before promotion from experimental support.

### Web app installation limitation

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

## Patches

<!-- PATCHES_START EXPANDED -->
> **[v1.4.0-dev.4](https://github.com/dh6k/morphe-patches/releases/tag/v1.4.0-dev.4)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;6 patches total
<details open>
<summary>📦 Quetta Browser&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Block Quetta bundled extension installation](#block-quetta-bundled-extension-installation) | Blocks bundled extension installation/reinstallation on arm64-v8a APKs (the framework does not enforce ABI restrictions). Does not remove copies already present in existing profiles. Takes effect immediately on clean installs. |  |

</details>

<details open>
<summary>📦 Quetta Browser Official&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Block Quetta bundled extension installation](#block-quetta-bundled-extension-installation) | Blocks bundled extension installation/reinstallation on arm64-v8a APKs (the framework does not enforce ABI restrictions). Does not remove copies already present in existing profiles. Takes effect immediately on clean installs. |  |

</details>

<details open>
<summary>📦 Brave Browser&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

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
<summary>📦 Helium Browser&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Keep Helium Child Processes Alive](#keep-helium-child-processes-alive) | Experimental version-unpinned structural/data-flow patch: starts one main-process foreground service with persistent low-priority notification and forces child STRONG binding plus IMPORTANT/STRONG priority updates. Tolerates routine signature, register, and helper-name changes; ambiguous targets fail closed. May increase RAM, battery, and process pressure; mitigates LMK kills only. |  |

</details>

<details open>
<summary>🌐 Universal&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Change app icon](#change-app-icon) | Changes the Android launcher icon using a custom PNG image. Use a square image with transparent adaptive-icon padding. | • Custom app icon |
| [Change app name](#change-app-name) | Changes the app name shown by Android launchers. Set the desired name in the patch options. | • App name |
| [Disable analytics](#disable-analytics) | Disables Firebase Analytics, Crashlytics and Performance through manifest opt-outs and exact runtime setters when present. Other SDK components are disabled only when explicitly declared; custom or server-side telemetry is not covered. |  |

</details>

<!-- PATCHES_END -->

## Keep Helium Child Processes Alive

Experimental, version-unpinned, disabled by default. Applies broadly to Helium child processes (not only extension processes) and only reduces the likelihood of LMK reclaim — it does not guarantee child survival, does not detect or reload crashed extensions, does not defeat OEM task killers, and does not bypass force-stop. Two-layer mitigation for [issue #57](https://github.com/jqssun/android-titanium-browser/issues/57): Chromium child processes are forced to STRONG binding (`0x4`) and IMPORTANT priority (`0x3`) while one main-process foreground service keeps a persistent low-importance notification ("Helium process protection active" / "Reduces likelihood of extension runtime reclaim"). Structural plus local data-flow resolution tolerates routine signature/register/helper-name changes and fails closed with actionable diagnostics when Helium internals no longer match safely. May increase RAM, battery, and process pressure; the foreground notification may be shown (on Android 13+ it may be hidden if POST_NOTIFICATIONS is denied but the service still runs). No watchdog loop, polling, or wake lock is used; START_STICKY is documented and idempotent.
