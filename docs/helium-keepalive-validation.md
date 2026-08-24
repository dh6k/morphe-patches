# Helium keep-alive validation

Validation used official arm64 Helium release APKs and Morphe Desktop CLI 1.13.2. APKs are not stored in this repository.

| Helium | Patch/rebuild | Create method | Launch region | Binding argument | Priority method | Activity hook |
| --- | --- | --- | --- | --- | --- | --- |
| 149.0.7827.197 | PASS | `SEMANTIC_EXACT` | nearest TraceEvent close | structural/data-flow (field + branch) | `SEMANTIC_EXACT`, `p12` | exact Chromium activity |
| 151.0.7922.137 | PASS | `SEMANTIC_EXACT` | nearest TraceEvent close | structural/data-flow (field + branch) | `SEMANTIC_EXACT`, `p12` | exact Chromium activity |
| 152.0.7977.54 | PASS | `SEMANTIC_EXACT` | nearest TraceEvent close | structural/data-flow (field + branch) | `SEMANTIC_EXACT`, `p12` | exact Chromium activity |

Static output checks confirmed one keep-alive service (`exported=false`, main process, `specialUse`), one special-use permission/property, one injected STRONG value (`0x4`) and one injected IMPORTANT value at `p12` (`0x3`), and one activity lifecycle hook for every APK.

## Hardening notes

- Binding resolver: requires launch-region scoping, chromium owner/name hints, and multi-signal binding-state evidence (field or small-enum+branch or derived-field+branch); PID/FD hints and large constants rejected; bounded fallback requires higher confidence; ambiguous/low-confidence throws `HeliumResolutionException` with method descriptor, region, candidate descriptors, int positions, scores/evidence and rejection reasons.
- Priority resolver: verified Chromium shape is `return I, 2×I, ≥4×Z, 1×J, name=setPriority`; exact name with unverified shape falls back to strict data-flow with peak≥4 and unique winner; structural fallback requires ≥3 independent indicators and score≥8; never selects solely on return+I+2ints.
- Register safety: const/16 range (v0..255) checked, Dalvik limit checked, liveness scan after invoke rejects live registers (future work: allocate temp register and rewrite invoke range when APIs allow).
- Activity resolver: ordered preference exact → manifest launcher → hierarchy; traverses only relevant superclass chains; ambiguous launcher/browser candidates fail closed; `LauncherActivityRegistry` now keyed by packageName (not IdentityHashMap) and cleared on both success and failure.
- Manifest mutation: reviewable Kotlin, idempotent, preserves unrelated entries, normalizes `android:process`.

## Foreground service

Low-importance channel (`IMPORTANCE_LOW`), no wake lock or polling loop, `START_STICKY` justified (recreate after kill without work), Android 12+ FGS restrictions caught (`ForegroundServiceStartNotAllowedException`), start failure triggers `stopSelf`, repeated starts idempotent. Notification wording does not claim guaranteed survival. On Android 13+, the FGS may continue while the notification is not visible if `POST_NOTIFICATIONS` is denied — no blind permission request or nag dialog is added.

These are static patch/rebuild results. Device behavior under memory pressure remains a separate runtime test.

## Limitations

- Register rewrite uses a simple const insertion; if the target register is live after the invoke or near Dalvik limits, the patch fails closed instead of allocating a temp and rewriting the invoke. Supporting temp-register allocation requires builder-level invoke rewriting.
- Wide-parameter (`J`/`D`) offsets are computed correctly, but range vs five-register invoke encoding is inferred from register count; explicit opcode guard could be added.
- Real Helium APK runtime validation was not performed in this hardening pass (no APK bundled). Re-run `./gradlew :patches:test` and patch a supported APK locally per the validation steps in the task.
