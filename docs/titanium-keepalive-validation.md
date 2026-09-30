# Titanium keep-alive validation

Validation used official arm64 Titanium release APKs and Morphe Desktop CLI (`morphe-desktop-1.15.0-all.jar` via `MorpheLauncherKt`). APKs are not stored in this repository. Local APK paths are not recorded.

Since v1.7.0 the patch scopes STRONG binding and IMPORTANT priority to extension child processes only (detected via `--extension-process`, matching upstream Titanium commit ff12f1c). Renderer and GPU children keep stock priority.

| Titanium | Patch/rebuild | Create method | Launch region | Binding argument | Priority method | Activity hook |
| --- | --- | --- | --- | --- | --- | --- |
| 154.0.8037.57 | PASS (`--unsigned`) | `SEMANTIC_EXACT` (`createAndStart`) | `SEMANTIC_EXACT` ordered TraceEvent scope | `DATA_FLOW` → `HeliumProcessBoost.scopedBinding` at binding site + `noteHelper` on helper instance | `SEMANTIC_EXACT` `setPriority(IZZZZJZZZZI)I` → `isExtension(this)` then `const/16 0x3` only for extension | `ChromeTabbedActivity.onStart` → `HeliumKeepAliveStarter` |
| 152.0.7977.54 | PASS (`--unsigned`, zipalign 4) | `SEMANTIC_EXACT` (`createAndStart`) | `SEMANTIC_EXACT` ordered TraceEvent scope | `DATA_FLOW` small-enum `3` → `v7` before `Li92->a(La82;Ld92;I)Lx82` + `move-result-object` (field/small-enum credible, weak hints ignored) | `SEMANTIC_EXACT` `setPriority(IZZZZJZZZZI)I` → `p12` (`v29`, word offset 12) `const/16 0x3` | `ChromeTabbedActivity.onStart` @5→ `HeliumKeepAliveStarter` |
| 151.0.7922.71 | PASS (`--unsigned`, zipalign 4) | `SEMANTIC_EXACT` | `SEMANTIC_EXACT` | `DATA_FLOW` | `SEMANTIC_EXACT`, `p12` | exact Chromium activity |

Previous baseline `152.0.7977.54` at `v1.4.0-dev.3` also patched/rebuilt (`PATCHING PASS`, `REBUILDING PASS`) with `ChromeTabbedActivity.onStart @5`, `const/16 v7,4` before `Li92->a(La82;Ld92;I)Lx82`, `const/16 v29,3` at `setPriority`, and hardened resolvers now preserve that success while failing closed on generic synthetic cases.

Static output checks confirmed one keep-alive service (`HeliumProcessKeepAliveService`, `exported=false`, `foregroundServiceType=specialUse`, `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE`, no `android:process`), one `FOREGROUND_SERVICE`/`FOREGROUND_SERVICE_SPECIAL_USE` permission each (deduplicated), `HeliumProcessBoost` merged into the patched dex, and one activity lifecycle hook for every APK. Unrelated manifest entries are preserved.

154.0.8037.57 (built from upstream commit ff12f1c) already contains the source-level `--extension-process` → `mEffectiveImportance=IMPORTANT` logic. The Morphe patch layers a foreground keep-alive service plus bytecode-level `HeliumProcessBoost` scoping that is idempotent with that source patch and provides the same extension-only behavior on older APKs without it.

Hardening delta from reverted `95522d6`: the reverted binding `credible` gate required `branchUses>0` for `small-enum` and rejected the real `v7` (constant `3` via `move`, never branched, obfuscated owner `Li92` not a Chromium hint) → `binding: no credible candidates`. The fix now treats `0..8` constants as strong evidence without needing branch, uses only narrowing PID/FD rejection (`pid`/`fd`/`filedescriptor`/`callback`/`handle`), and keeps only an encodability guard (`v<256`) instead of `registerCount>240` / linear liveness false rejections.

These are static patch/rebuild results. Device behavior under memory pressure, foreground-service notification, and process importance/oom-adj adjustments remain separate runtime tests and were not performed here.

Command (validated): `java -cp "morphe-desktop-1.15.0-all.jar" app.morphe.MorpheLauncherKt patch -p <mpp> -e "Keep Titanium Extensions Child Processes Alive" --exclusive --unsigned -o <out.apk> -r <result.json> -t <scratch> <input.apk>` — signing the CLI output would require a separate `apksigner` step with a valid keystore.

APK SHA-256 (for reproducibility, validated against `aapt dump badging` package `io.github.jqssun.helium`):

- `154.0.8037.57` (~311 MB arm64-v8a): `16B7288981FD8610613E6F81F78FEB39553E432D67E689A794C44AE678187C8D`
- `152.0.7977.54` (~309 MB arm64-v8a): `f9d43f6a2a6675bab695f5aff6d0a67f27b390c469f6166f6de7bd4591af87f4`
- `151.0.7922.71` (~310 MB arm64-v8a): `6778f892ef2657f3930c1aa792cd8d487f86d7c52dcf1bd85e44226719d15a58`
