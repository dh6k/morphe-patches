# Brave Origin, Quetta and Universal Patches

Morphe patch bundle for Brave Browser, Quetta browser plus app-independent Android resource patches.

## Quetta Browser support

Supports Quetta Browser (`net.quetta.browser`) and Quetta Browser Official (`net.quetta.browser.official`) through the **Block Quetta bundled extension installation** patch. Compatibility is version-unpinned and experimental, intended for arm64-v8a APKs; the framework does not enforce ABI.

Patch blocks bundled installation/reinstallation for these exact extensions:

- `nnedfbcpeenmccjbdcnlnhogapndfeoa` — Q30 from Quetta Translator
- `gadlcodpkkelmagfhkldjlobfncbkbmd` — Q30 from Quetta

Analyzed manifests show these extensions have broad page access and background behavior, and can communicate with remote services. Blocking bundled installation/reinstallation reduces bundled background code and remote-service exposure, giving users more privacy and control without claiming what those services collect.

This does not block all Quetta telemetry or every Quetta network connection, and does not remove copies already installed in existing profiles. Remove existing copies through Quetta's extension-management UI, or use a clean profile as appropriate. Static validation is anchored to supplied Quetta `2.0.2-530` Official arm64-v8a APK; this is not broad runtime proof. Future versions may change fingerprints; patch should fail safely rather than modify unrelated methods.

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
> **[v1.2.0](https://github.com/dh6k/morphe-patches/releases/tag/v1.2.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;5 patches total
<details open>
<summary>📦 Quetta Browser&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Block Quetta bundled extension installation](#block-quetta-bundled-extension-installation) | Blocks bundled extension installation/reinstallation on arm64-v8a APKs (framework does not enforce ABI). Does not remove copies already present in existing profiles. |  |

</details>

<details open>
<summary>📦 Quetta Browser Official&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Block Quetta bundled extension installation](#block-quetta-bundled-extension-installation) | Blocks bundled extension installation/reinstallation on arm64-v8a APKs (framework does not enforce ABI). Does not remove copies already present in existing profiles. |  |

</details>

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
<summary>📦 Helium Browser&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Keep Helium Child Processes Alive](#keep-helium-child-processes-alive) | Experimental: applies to all Helium child processes; forces STRONG binding at launch and IMPORTANT/STRONG on priority updates. May increase RAM, battery, and process pressure; only reduces LMK probability. Does not detect, reload, or back off crashed extensions. |  |

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
