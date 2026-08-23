# Helium keep-alive validation

Validation used official arm64 Helium release APKs and Morphe Desktop CLI 1.13.2. APKs are not stored in this repository.

| Helium | Patch/rebuild | Create method | Launch region | Binding argument | Priority method | Activity hook |
| --- | --- | --- | --- | --- | --- | --- |
| 149.0.7827.197 | PASS | `SEMANTIC_EXACT` | nearest TraceEvent close | structural/data-flow | `SEMANTIC_EXACT`, `p12` | exact Chromium activity |
| 151.0.7922.137 | PASS | `SEMANTIC_EXACT` | nearest TraceEvent close | structural/data-flow | `SEMANTIC_EXACT`, `p12` | exact Chromium activity |
| 152.0.7977.54 | PASS | `SEMANTIC_EXACT` | nearest TraceEvent close | structural/data-flow | `SEMANTIC_EXACT`, `p12` | exact Chromium activity |

Static output checks confirmed one keep-alive service, one special-use permission/property, one injected STRONG value (`0x4`), one injected IMPORTANT value at `p12` (`0x3`), and one activity lifecycle hook for every APK.

These are static patch/rebuild results. Device behavior under memory pressure remains a separate runtime test.
