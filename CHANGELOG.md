## [1.8.1-dev.1](https://github.com/dh6k/morphe-patches/compare/v1.8.0...v1.8.1-dev.1) (2026-10-01)

### 🚀 Updated App Support

* upgrade morphe-patcher to 1.15.0 ([46479c7](https://github.com/dh6k/morphe-patches/commit/46479c7531b87ca7743c8503a901cda8e165a2a9))

## [1.8.0](https://github.com/dh6k/morphe-patches/compare/v1.7.0...v1.8.0) (2026-10-01)

### 🐛 Bug Fixes

* add gold tier to truecaller ([7d68ff0](https://github.com/dh6k/morphe-patches/commit/7d68ff0568c5c08eec0e5067e32b791d3854091e))
* **brave:** actually kill Material You in AMOLED and stop greying the switch ([41fb1b4](https://github.com/dh6k/morphe-patches/commit/41fb1b4d376f5e4bb8f577f5a4392c7b9e82f1d7))
* **brave:** avoid param-register clobber in NTP wallpaper prologue ([26508d8](https://github.com/dh6k/morphe-patches/commit/26508d84e33cc5c25562e66285f04cbd180680a1))
* **brave:** fit NTP catalog factory in 3 registers via R.drawable ([c7a9f2f](https://github.com/dh6k/morphe-patches/commit/c7a9f2f1ba8a5cc5145a1b686de2875fe2865b92))
* **brave:** Fixed XML resource issue for Brave Patch ([#17](https://github.com/dh6k/morphe-patches/issues/17)) ([35e9fb5](https://github.com/dh6k/morphe-patches/commit/35e9fb5256f3fc7a3683125e1edf3d33ce71dd55))
* **brave:** keep accent-coloured text when declaring night ink ([5b6f269](https://github.com/dh6k/morphe-patches/commit/5b6f2690031607d733cfb049ad428462099e0364)), closes [#cd4400](https://github.com/dh6k/morphe-patches/issues/cd4400) [#545ff8](https://github.com/dh6k/morphe-patches/issues/545ff8) [#687485](https://github.com/dh6k/morphe-patches/issues/687485)
* **brave:** keep colors the app renders text with out of the surface sweep ([0703501](https://github.com/dh6k/morphe-patches/commit/0703501ec7aecf9b1488e1185fd76afa568fe812)), closes [#1c1c1d](https://github.com/dh6k/morphe-patches/issues/1c1c1d) [#0d0f14](https://github.com/dh6k/morphe-patches/issues/0d0f14) [#202124](https://github.com/dh6k/morphe-patches/issues/202124) [#484b4e](https://github.com/dh6k/morphe-patches/issues/484b4e) [#25272b](https://github.com/dh6k/morphe-patches/issues/25272b)
* **brave:** only force the dedicated AMOLED dynamic-colors getter ([84ed3a4](https://github.com/dh6k/morphe-patches/commit/84ed3a445584981592038e82f5b29a1c0cba6365))
* **brave:** resolve NTP wallpaper via extension helper (2-reg invoke) ([8c1367b](https://github.com/dh6k/morphe-patches/commit/8c1367b7692cd1b1dc9317779df9b2afc1b3ae3c))
* **brave:** rewrite AMOLED dark surfaces in values/ not just values-night ([238a4f2](https://github.com/dh6k/morphe-patches/commit/238a4f27d78eafd4f8eab5cb5c31a33144f0af00)), closes [#121212](https://github.com/dh6k/morphe-patches/issues/121212) [#ff303030](https://github.com/dh6k/morphe-patches/issues/ff303030)
* **brave:** route NTP wallpaper through Java ambient catalog ([02bcaee](https://github.com/dh6k/morphe-patches/commit/02bcaeefc8835ac46799abfbe8fef5418a6abfbc))
* **brave:** stop declaring text colors that already have selectors ([4974aea](https://github.com/dh6k/morphe-patches/commit/4974aea31deb3b317bb6e9b557510cda4478d578))
* **brave:** stop night-v31 overrides from re-asserting text as background ([6832558](https://github.com/dh6k/morphe-patches/commit/6832558e54e676c890e4b81820b1aff14474e3b2)), closes [#000000](https://github.com/dh6k/morphe-patches/issues/000000)
* **brave:** support Brave Beta 1.94.94 ([e375d2e](https://github.com/dh6k/morphe-patches/commit/e375d2e5aadbc608e9fca3df25bed15d60187aa4))
* **brave:** support Origin 1.94.114 ([9be315a](https://github.com/dh6k/morphe-patches/commit/9be315aef52d3be053b7ba7b3d6362f5a0e88ef0))
* **brave:** tolerate optional Origin hooks ([d7db355](https://github.com/dh6k/morphe-patches/commit/d7db355ee729c579e39e317346719199e78bc0be))
* **brave:** tolerate Origin log-literal drift on 1.97.x ([a51c857](https://github.com/dh6k/morphe-patches/commit/a51c857941cfecec31cbc4761371a7734dd37d71)), closes [#1](https://github.com/dh6k/morphe-patches/issues/1) [#5](https://github.com/dh6k/morphe-patches/issues/5)
* **brave:** tolerate Origin restart-callback signature drift on 1.98.x ([3f03ab8](https://github.com/dh6k/morphe-patches/commit/3f03ab8c0bffcdeaabcda5a265698f9361b393d3)), closes [#18](https://github.com/dh6k/morphe-patches/issues/18) [#18](https://github.com/dh6k/morphe-patches/issues/18)
* **brave:** unpin stable compatibility ([975b7fe](https://github.com/dh6k/morphe-patches/commit/975b7fe80dfd5fb489a1a6b97ac92b5b02fe7159))
* **brave:** use invoke-interface for Runnable callback drain ([91a06ec](https://github.com/dh6k/morphe-patches/commit/91a06ec4ed10a80c206ec7d646d00675b6e14616))
* **build:** keep generator dependencies out of patch dex ([60f1ab8](https://github.com/dh6k/morphe-patches/commit/60f1ab84fea6463ad02cb1ea474e2b24aa9aa0b1))
* **build:** keep patchLocalApk providers lazy for CI configure ([1e75459](https://github.com/dh6k/morphe-patches/commit/1e754599009ff75ddeb3e3e4f95e48730bab8158))
* fix at4k patch ([3eae8b4](https://github.com/dh6k/morphe-patches/commit/3eae8b4b7445df25ede00aa2a6b88c6d33546745))
* fix missing file ([bce69ef](https://github.com/dh6k/morphe-patches/commit/bce69ef5b4f30d139e2cf99550f20ebdbc6813a0))
* fix notifications ([fd8f75f](https://github.com/dh6k/morphe-patches/commit/fd8f75ff43a87575a24ca49cf5eca50b717368ee))
* fix patch generator ([9f5167d](https://github.com/dh6k/morphe-patches/commit/9f5167d3e99e5624afbcd9551c849a14d19492df))
* fix patch instructions ([54984c1](https://github.com/dh6k/morphe-patches/commit/54984c1866d116e78beca2a25705c3435400fc10))
* fix patch instructions ([34edbcc](https://github.com/dh6k/morphe-patches/commit/34edbcca3736621f1dd302c07237378970cda4eb))
* fix spoofed setting ([c828f09](https://github.com/dh6k/morphe-patches/commit/c828f09b7b1f57b90af0e9d139d70cda63f28f8a))
* fix truecaller patch ([97137b7](https://github.com/dh6k/morphe-patches/commit/97137b7eeb4a85caf45ef283574eed76afd59716))
* fixed truecaller patch ([ce0a330](https://github.com/dh6k/morphe-patches/commit/ce0a3301f1e2ea9680293104ffb4df9216cbfbe5))
* **helium:** harden keep-alive structural resolver ([843c91a](https://github.com/dh6k/morphe-patches/commit/843c91a694eeaec1f309b19c92ca08c70b4984cc))
* **helium:** harden keep-alive without breaking apk patching ([5a2ae6f](https://github.com/dh6k/morphe-patches/commit/5a2ae6fc3bc03793488e28761231c03428323823))
* **helium:** use compatible patcher APIs ([edffab2](https://github.com/dh6k/morphe-patches/commit/edffab2e31fe77b2ffa06d2d9f56d258467039eb))
* inject plan code and name to properly trick flutter into premium status ([d7c5ccc](https://github.com/dh6k/morphe-patches/commit/d7c5ccc0a5fb1e50b51386f6681a18c679b8cd23))
* **medium:** add custom host option inside freedium settings dialog ([2c41bab](https://github.com/dh6k/morphe-patches/commit/2c41bab05f84bad2020a304f3311c056709d2944))
* **medium:** add onLongClickListener to hide the Unlock button ([8a7b15a](https://github.com/dh6k/morphe-patches/commit/8a7b15afac231112fdd365d01c53b21ecbfcc1f3))
* **medium:** change extension file type to .mpe for Morphe compatibility ([9fbdf50](https://github.com/dh6k/morphe-patches/commit/9fbdf5059bae54e07d43b35ea617836457f8b495))
* **medium:** correct syntax error in FreediumPatch.kt ([6047d0d](https://github.com/dh6k/morphe-patches/commit/6047d0d3abae39c0475738435e4691f8dd1239fc))
* **medium:** declare extension dependency in patch builder DSL ([ad43faf](https://github.com/dh6k/morphe-patches/commit/ad43fafca61959e3bc991cd8ebf05872012f7881))
* **medium:** implement manual reflection-based extension loader to resolve classloader issues ([218405c](https://github.com/dh6k/morphe-patches/commit/218405c7f7a0e3d8f8569d5f6384f1a1d31ee055))
* **medium:** inject at start of PostFragment.Q to prevent Verifier error ([9fddab0](https://github.com/dh6k/morphe-patches/commit/9fddab0e8d40e1ae26836acf1a1f6749ba1b2453))
* **medium:** prevent status bar overlap and adjust FAB bottom margin to 76dp ([6f6a4eb](https://github.com/dh6k/morphe-patches/commit/6f6a4eb1b538eabd11ff954a8bd7bdfe0ac4ef53))
* **medium:** refine Settings row integrations, click ripples, and floating button color/alignment ([690ced1](https://github.com/dh6k/morphe-patches/commit/690ced154d7e704bf59d8e861f1038acfc15505d))
* **medium:** set pill-shaped Unlock button with overlay and HTML loading animation inside webview ([1cd1f47](https://github.com/dh6k/morphe-patches/commit/1cd1f470f0da7d8e706d048e8ef682ebf5bed5a4))
* **morphe:** merge latest patches template improvements and update patch list generator ([7623841](https://github.com/dh6k/morphe-patches/commit/762384138b359274a417dce9f72f117859c078a8))
* mygate notifications ([7baf87c](https://github.com/dh6k/morphe-patches/commit/7baf87c70921b690974975b5efb4a456905a3923))
* mygate patch ([f37a419](https://github.com/dh6k/morphe-patches/commit/f37a4193f3730e01b3450df6c96da829d31bcc2d))
* mygate patch fixed ([78570ae](https://github.com/dh6k/morphe-patches/commit/78570aeded7ee120298e0f3e91c4a048bf9c6df8))
* mygate patch fixed ([12ee48b](https://github.com/dh6k/morphe-patches/commit/12ee48b4dd94765698a593f84eda5352adb2ce2e))
* **mygate:** make ShowUpgradeDialogFingerprint dynamic to bypass method z obfuscation ([b882e30](https://github.com/dh6k/morphe-patches/commit/b882e3009088c6262ac590a4f8478a80ec26264b))
* **mygate:** resolve notification loss and fingerprint mismatches on 7.30.1 ([d936181](https://github.com/dh6k/morphe-patches/commit/d9361819a52028fc33c21602074011ad92686cca))
* pass list to Quetta fingerprint strings ([0094812](https://github.com/dh6k/morphe-patches/commit/0094812aa952ada676b366b98283e1b54b719d43))
* **release:** publish appName option on main ([8036996](https://github.com/dh6k/morphe-patches/commit/803699618c5e25618bb2db50be58c9d871ca42fd))
* remove gpg check ([e6d22a9](https://github.com/dh6k/morphe-patches/commit/e6d22a9f243818b9779020b30910028bc0d86389))
* **titanium:** make notification toggle actually hide ([1a351cc](https://github.com/dh6k/morphe-patches/commit/1a351cc270ce208d6d96a8e1b6e5af32c6cfd17a))
* **titanium:** replace crashing D() tail hook with nearest-mode head const ([4ff557d](https://github.com/dh6k/morphe-patches/commit/4ff557df7ed212c3972a8c6e53250cd1b210dfd3))
* **titanium:** revert conditional pins that break smali parsing ([3c0b236](https://github.com/dh6k/morphe-patches/commit/3c0b23657ca38cc10147691d620352dfd49ee4e7))
* **universal:** release bundle-specific app name option ([7ecf578](https://github.com/dh6k/morphe-patches/commit/7ecf57893c5ee140f0a30477fdde328758d924c2))
* **universal:** restore appName option key ([a34a045](https://github.com/dh6k/morphe-patches/commit/a34a045670eab3acafb93e7ab3a255555873b74c))
* **workflow:** move clean task to start of build to prevent deleting release assets ([a569b96](https://github.com/dh6k/morphe-patches/commit/a569b964665ce4652950dbf756af1865bfec9d25))

### ✨ New Features

* add Helium foreground keep-alive service ([4ffe375](https://github.com/dh6k/morphe-patches/commit/4ffe37589b6b9355e524da0f0404aeb0a02b2a4c))
* add more truecaller patches ([cd6b169](https://github.com/dh6k/morphe-patches/commit/cd6b169666f139c018399b51680828801f56fb18))
* add one more patch ([9281068](https://github.com/dh6k/morphe-patches/commit/92810680893e8d0c9b4439da74ddc03f2a4b8985))
* Added patches for at4k and bounce ([747db21](https://github.com/dh6k/morphe-patches/commit/747db213d5006a3f33e53442a538ddaf13420e8c))
* Brave Browser Origin Unlocked and some MyGate fixes ([#15](https://github.com/dh6k/morphe-patches/issues/15)) ([e1a0d47](https://github.com/dh6k/morphe-patches/commit/e1a0d478fd890340221374cc86b1a11d4e6615f2))
* **brave:** add AMOLED text and accent color options ([9254614](https://github.com/dh6k/morphe-patches/commit/925461415ceb6b86bd3438aa0e9ba35b1984aef2))
* **brave:** add Brave Startup Performance Optimization ([ce71982](https://github.com/dh6k/morphe-patches/commit/ce71982e463fddc93fba353f02a0b5bdac466ea3))
* **brave:** add patch-time AMOLED theme (issue [#21](https://github.com/dh6k/morphe-patches/issues/21)) ([7d0c8ad](https://github.com/dh6k/morphe-patches/commit/7d0c8ad3665a686c6f05e94b2464052934c92526))
* **brave:** custom NTP wallpaper patch (alpha, default off) ([cc41a5b](https://github.com/dh6k/morphe-patches/commit/cc41a5b7afbc3a2bd72ef9633a96e44063771dff))
* bypass Flutter ad rendering via JSON spoofing & nuke native floating banners ([f1e67a4](https://github.com/dh6k/morphe-patches/commit/f1e67a40757e24caf957a899340bb1dc0072c1a0))
* fix mygate issue ([f51498d](https://github.com/dh6k/morphe-patches/commit/f51498d9c8152e2110232c565163928630c7d99a))
* **helium:** keep child processes strongly bound ([139d4e5](https://github.com/dh6k/morphe-patches/commit/139d4e5933beb9414802886e27a351907918317d))
* **helium:** strengthen child process survival ([87d697e](https://github.com/dh6k/morphe-patches/commit/87d697ed7b7968b4ca836efe81a73001a90cb570))
* make Helium keep-alive patch version resilient ([2e7cf9d](https://github.com/dh6k/morphe-patches/commit/2e7cf9da45c4e3d4b9ed2dbd358414da27272bb5))
* **medium:** add Freedium Mirror patch ([e971487](https://github.com/dh6k/morphe-patches/commit/e971487847dc294be116cf6d09d56d9e177557a7))
* my gate patch ([1478ec1](https://github.com/dh6k/morphe-patches/commit/1478ec1455686ba03e917df60c143341f3f9aeff))
* **mygate:** Introducing MyGate Premium Patch ([#19](https://github.com/dh6k/morphe-patches/issues/19)) ([34842f9](https://github.com/dh6k/morphe-patches/commit/34842f9c554da0e7fab3ca0314439595132b5836))
* patch splitwise app ([47d7ed6](https://github.com/dh6k/morphe-patches/commit/47d7ed6427cc3bf53f922aea70509c5061aa07ae))
* **quetta:** block bundled extension installation ([3f15e45](https://github.com/dh6k/morphe-patches/commit/3f15e455a7dcd605f9800f977de4e0126ff839ce))
* **quetta:** force highest refresh rate with local fingerprints ([9ef165d](https://github.com/dh6k/morphe-patches/commit/9ef165d0ed15b8777660f3fa5d6049496a9248be))
* **titanium:** force highest refresh rate ([78efa4c](https://github.com/dh6k/morphe-patches/commit/78efa4ce097969f170b7b834eebec074ee5a861f))
* **titanium:** notification options for keep-alive patch ([9e005c9](https://github.com/dh6k/morphe-patches/commit/9e005c988b905acb8a34dbc10b00f43f1b1ad78a))
* **titanium:** scope keep-alive boosts to extension processes ([bfb11b6](https://github.com/dh6k/morphe-patches/commit/bfb11b6012a44535bf2401e281b7afdcf065e953))
* **universal:** add custom app icon patch ([b459802](https://github.com/dh6k/morphe-patches/commit/b45980200d7cfe86db692ee247b667387415b4ea))
* **universal:** broaden Disable analytics to kondratjev parity ([d2459f7](https://github.com/dh6k/morphe-patches/commit/d2459f7f32e2c3019dbc1c7e64d55394f5d0bb0c))
* **universal:** cover Adjust v5 initSdk and Crashlytics boxed overload ([3ccc45e](https://github.com/dh6k/morphe-patches/commit/3ccc45e317e6289f5f7c56ecc9350dda499c5fce))
* **universal:** cover screen reporting, crashlytics/perf providers and Adjust init ([be24282](https://github.com/dh6k/morphe-patches/commit/be24282705c4a32fae2b22249b9b4ba99171435f))
* **universal:** disable common analytics SDKs ([4dca1f9](https://github.com/dh6k/morphe-patches/commit/4dca1f929c3ee9619aac36eaa9cbad620927fbcb))
* **universal:** harden Disable analytics manifest scope and runtime backstop ([1b01544](https://github.com/dh6k/morphe-patches/commit/1b01544f19d14b7787b7c591e6d5f8492b207e1e))

### 🚀 Updated App Support

* **universal:** backport Change app name patch ([380c461](https://github.com/dh6k/morphe-patches/commit/380c46189b53c6cda84d7f04603a0fb54d25cca4))

### 🔧 Improvements

* **titanium:** conditionalize keep-alive pins and streamline notification ([c1fa3af](https://github.com/dh6k/morphe-patches/commit/c1fa3aff55af2c40d57824173f9afd1cd4cdb7b7))

## [1.8.0-dev.10](https://github.com/dh6k/morphe-patches/compare/v1.8.0-dev.9...v1.8.0-dev.10) (2026-10-01)

### 🐛 Bug Fixes

* **brave:** keep accent-coloured text when declaring night ink ([11d8e1f](https://github.com/dh6k/morphe-patches/commit/11d8e1fa0a6d398682e19d33d81cf22d535ad635)), closes [#cd4400](https://github.com/dh6k/morphe-patches/issues/cd4400) [#545ff8](https://github.com/dh6k/morphe-patches/issues/545ff8) [#687485](https://github.com/dh6k/morphe-patches/issues/687485)

## [1.8.0-dev.9](https://github.com/dh6k/morphe-patches/compare/v1.8.0-dev.8...v1.8.0-dev.9) (2026-10-01)

### 🐛 Bug Fixes

* **brave:** stop night-v31 overrides from re-asserting text as background ([d17495b](https://github.com/dh6k/morphe-patches/commit/d17495bb353edeef95c552f0cbff181ac98c3405)), closes [#000000](https://github.com/dh6k/morphe-patches/issues/000000)

## [1.8.0-dev.8](https://github.com/dh6k/morphe-patches/compare/v1.8.0-dev.7...v1.8.0-dev.8) (2026-10-01)

### 🐛 Bug Fixes

* **brave:** keep colors the app renders text with out of the surface sweep ([fcf7630](https://github.com/dh6k/morphe-patches/commit/fcf7630819301cb0aa41f4e7912176b2793ce31e)), closes [#1c1c1d](https://github.com/dh6k/morphe-patches/issues/1c1c1d) [#0d0f14](https://github.com/dh6k/morphe-patches/issues/0d0f14) [#202124](https://github.com/dh6k/morphe-patches/issues/202124) [#484b4e](https://github.com/dh6k/morphe-patches/issues/484b4e) [#25272b](https://github.com/dh6k/morphe-patches/issues/25272b)

## [1.8.0-dev.7](https://github.com/dh6k/morphe-patches/compare/v1.8.0-dev.6...v1.8.0-dev.7) (2026-10-01)

### 🐛 Bug Fixes

* **brave:** stop declaring text colors that already have selectors ([cda4691](https://github.com/dh6k/morphe-patches/commit/cda4691484a808a2ad476ac1e24f49d5845af04c))

## [1.8.0-dev.6](https://github.com/dh6k/morphe-patches/compare/v1.8.0-dev.5...v1.8.0-dev.6) (2026-09-30)

### ✨ New Features

* **brave:** add AMOLED text and accent color options ([e0484bb](https://github.com/dh6k/morphe-patches/commit/e0484bbec22ffb990d510ca2b9ecaa3499545f81))

## [1.8.0-dev.5](https://github.com/dh6k/morphe-patches/compare/v1.8.0-dev.4...v1.8.0-dev.5) (2026-09-30)

### 🐛 Bug Fixes

* **brave:** rewrite AMOLED dark surfaces in values/ not just values-night ([b76ab33](https://github.com/dh6k/morphe-patches/commit/b76ab33ded56a444df0fa901d2c1b92d9ee81c87)), closes [#121212](https://github.com/dh6k/morphe-patches/issues/121212) [#ff303030](https://github.com/dh6k/morphe-patches/issues/ff303030)

## [1.8.0-dev.4](https://github.com/dh6k/morphe-patches/compare/v1.8.0-dev.3...v1.8.0-dev.4) (2026-09-30)

### 🐛 Bug Fixes

* **brave:** only force the dedicated AMOLED dynamic-colors getter ([535f6f2](https://github.com/dh6k/morphe-patches/commit/535f6f281c1de3464d1971f437c4abc2af1150a4))

## [1.8.0-dev.3](https://github.com/dh6k/morphe-patches/compare/v1.8.0-dev.2...v1.8.0-dev.3) (2026-09-30)

### 🐛 Bug Fixes

* **brave:** actually kill Material You in AMOLED and stop greying the switch ([2e38c35](https://github.com/dh6k/morphe-patches/commit/2e38c35636e31867c33211fdc103c076f885dec7))

## [1.8.0-dev.2](https://github.com/dh6k/morphe-patches/compare/v1.8.0-dev.1...v1.8.0-dev.2) (2026-09-30)

### ✨ New Features

* **brave:** add patch-time AMOLED theme (issue [#21](https://github.com/dh6k/morphe-patches/issues/21)) ([863a558](https://github.com/dh6k/morphe-patches/commit/863a558a8549b0dec16a8109c6c4c78e7a7330a9))

## [1.8.0-dev.1](https://github.com/dh6k/morphe-patches/compare/v1.7.0...v1.8.0-dev.1) (2026-09-30)

### ✨ New Features

* **titanium:** scope keep-alive boosts to extension processes ([a461882](https://github.com/dh6k/morphe-patches/commit/a461882a418570b36eb0531176065dc1f8006a8f))

## [1.7.0](https://github.com/dh6k/morphe-patches/compare/v1.6.0...v1.7.0) (2026-09-24)

### 🐛 Bug Fixes

* **brave:** avoid param-register clobber in NTP wallpaper prologue ([62132b9](https://github.com/dh6k/morphe-patches/commit/62132b919f54d5cd6194cb29de87fce38f12a41c))
* **brave:** fit NTP catalog factory in 3 registers via R.drawable ([bb3e4df](https://github.com/dh6k/morphe-patches/commit/bb3e4dff5a0983d1e2a4fe2d4286ddcc5d1a7a89))
* **brave:** resolve NTP wallpaper via extension helper (2-reg invoke) ([33a3c93](https://github.com/dh6k/morphe-patches/commit/33a3c934f126d76a1bf96dcd6ffb99f331767d62))
* **brave:** route NTP wallpaper through Java ambient catalog ([7c78a3c](https://github.com/dh6k/morphe-patches/commit/7c78a3cb11e44c04b1bb4fed833f1b4626c4932d))
* **brave:** use invoke-interface for Runnable callback drain ([adf562b](https://github.com/dh6k/morphe-patches/commit/adf562b790e0a994a2400ddfb49bfe84e9e7620e))

### ✨ New Features

* **brave:** add Brave Startup Performance Optimization ([9b9f1ab](https://github.com/dh6k/morphe-patches/commit/9b9f1ab25f7de74231ff78abf2efd7afe072ddf8))
* **brave:** custom NTP wallpaper patch (alpha, default off) ([387a449](https://github.com/dh6k/morphe-patches/commit/387a44977241523f2b8cab985ac76dd4e2a290a0))

## [1.7.0-dev.7](https://github.com/dh6k/morphe-patches/compare/v1.7.0-dev.6...v1.7.0-dev.7) (2026-09-23)

### 🐛 Bug Fixes

* **brave:** use invoke-interface for Runnable callback drain ([adf562b](https://github.com/dh6k/morphe-patches/commit/adf562b790e0a994a2400ddfb49bfe84e9e7620e))

## [1.7.0-dev.6](https://github.com/dh6k/morphe-patches/compare/v1.7.0-dev.5...v1.7.0-dev.6) (2026-09-23)

### ✨ New Features

* **brave:** add Brave Startup Performance Optimization ([9b9f1ab](https://github.com/dh6k/morphe-patches/commit/9b9f1ab25f7de74231ff78abf2efd7afe072ddf8))

## [1.7.0-dev.5](https://github.com/dh6k/morphe-patches/compare/v1.7.0-dev.4...v1.7.0-dev.5) (2026-09-23)

### 🐛 Bug Fixes

* **brave:** resolve NTP wallpaper via extension helper (2-reg invoke) ([33a3c93](https://github.com/dh6k/morphe-patches/commit/33a3c934f126d76a1bf96dcd6ffb99f331767d62))

## [1.7.0-dev.4](https://github.com/dh6k/morphe-patches/compare/v1.7.0-dev.3...v1.7.0-dev.4) (2026-09-23)

### 🐛 Bug Fixes

* **brave:** fit NTP catalog factory in 3 registers via R.drawable ([bb3e4df](https://github.com/dh6k/morphe-patches/commit/bb3e4dff5a0983d1e2a4fe2d4286ddcc5d1a7a89))

## [1.7.0-dev.3](https://github.com/dh6k/morphe-patches/compare/v1.7.0-dev.2...v1.7.0-dev.3) (2026-09-23)

### 🐛 Bug Fixes

* **brave:** route NTP wallpaper through Java ambient catalog ([7c78a3c](https://github.com/dh6k/morphe-patches/commit/7c78a3cb11e44c04b1bb4fed833f1b4626c4932d))

## [1.7.0-dev.2](https://github.com/dh6k/morphe-patches/compare/v1.7.0-dev.1...v1.7.0-dev.2) (2026-09-23)

### 🐛 Bug Fixes

* **brave:** avoid param-register clobber in NTP wallpaper prologue ([62132b9](https://github.com/dh6k/morphe-patches/commit/62132b919f54d5cd6194cb29de87fce38f12a41c))

## [1.7.0-dev.1](https://github.com/dh6k/morphe-patches/compare/v1.6.0...v1.7.0-dev.1) (2026-09-22)

### ✨ New Features

* **brave:** custom NTP wallpaper patch (alpha, default off) ([387a449](https://github.com/dh6k/morphe-patches/commit/387a44977241523f2b8cab985ac76dd4e2a290a0))

## [1.6.0](https://github.com/dh6k/morphe-patches/compare/v1.5.0...v1.6.0) (2026-09-20)

### 🐛 Bug Fixes

* **brave:** tolerate Origin restart-callback signature drift on 1.98.x ([63d88bc](https://github.com/dh6k/morphe-patches/commit/63d88bce30601c792c701cdc56482305189ac8b7)), closes [#18](https://github.com/dh6k/morphe-patches/issues/18) [#18](https://github.com/dh6k/morphe-patches/issues/18)
* **build:** keep patchLocalApk providers lazy for CI configure ([d8f97ee](https://github.com/dh6k/morphe-patches/commit/d8f97eea2f6c648eff6a08f5c7f984709805964b))
* **titanium:** make notification toggle actually hide ([f295b0e](https://github.com/dh6k/morphe-patches/commit/f295b0e39300248be67620713c3073c64ce62a3b))
* **titanium:** replace crashing D() tail hook with nearest-mode head const ([1432277](https://github.com/dh6k/morphe-patches/commit/1432277e053edbdddd417e529917153ee4c9ddc6))
* **titanium:** revert conditional pins that break smali parsing ([f76ab92](https://github.com/dh6k/morphe-patches/commit/f76ab92913cacc4d009f5484a3ab1be9f0ac090d))

### ✨ New Features

* **quetta:** force highest refresh rate with local fingerprints ([52f2c6f](https://github.com/dh6k/morphe-patches/commit/52f2c6f2a6b310b41dba3df99b12b8d5f713a439))
* **titanium:** force highest refresh rate ([473eaae](https://github.com/dh6k/morphe-patches/commit/473eaaeadd5b1a7cff701153cb4573353354a9ac))
* **titanium:** notification options for keep-alive patch ([c6b5620](https://github.com/dh6k/morphe-patches/commit/c6b5620da46cd9d6f33e1d7f64bb6467ab311260))

### 🔧 Improvements

* **titanium:** conditionalize keep-alive pins and streamline notification ([8ad79b1](https://github.com/dh6k/morphe-patches/commit/8ad79b1b7ca7203b1dc1479ce6fe81454502f31b))

## [1.6.0-dev.8](https://github.com/dh6k/morphe-patches/compare/v1.6.0-dev.7...v1.6.0-dev.8) (2026-09-20)

### ✨ New Features

* **quetta:** force highest refresh rate with local fingerprints ([52f2c6f](https://github.com/dh6k/morphe-patches/commit/52f2c6f2a6b310b41dba3df99b12b8d5f713a439))

## [1.6.0-dev.7](https://github.com/dh6k/morphe-patches/compare/v1.6.0-dev.6...v1.6.0-dev.7) (2026-09-20)

### 🐛 Bug Fixes

* **brave:** tolerate Origin restart-callback signature drift on 1.98.x ([63d88bc](https://github.com/dh6k/morphe-patches/commit/63d88bce30601c792c701cdc56482305189ac8b7)), closes [#18](https://github.com/dh6k/morphe-patches/issues/18) [#18](https://github.com/dh6k/morphe-patches/issues/18)

## [1.6.0-dev.6](https://github.com/dh6k/morphe-patches/compare/v1.6.0-dev.5...v1.6.0-dev.6) (2026-09-06)

### 🐛 Bug Fixes

* **titanium:** replace crashing D() tail hook with nearest-mode head const ([1432277](https://github.com/dh6k/morphe-patches/commit/1432277e053edbdddd417e529917153ee4c9ddc6))

## [1.6.0-dev.5](https://github.com/dh6k/morphe-patches/compare/v1.6.0-dev.4...v1.6.0-dev.5) (2026-09-06)

### 🐛 Bug Fixes

* **build:** keep patchLocalApk providers lazy for CI configure ([d8f97ee](https://github.com/dh6k/morphe-patches/commit/d8f97eea2f6c648eff6a08f5c7f984709805964b))

### ✨ New Features

* **titanium:** force highest refresh rate ([473eaae](https://github.com/dh6k/morphe-patches/commit/473eaaeadd5b1a7cff701153cb4573353354a9ac))

## [1.6.0-dev.4](https://github.com/dh6k/morphe-patches/compare/v1.6.0-dev.3...v1.6.0-dev.4) (2026-09-06)

### 🐛 Bug Fixes

* **titanium:** revert conditional pins that break smali parsing ([f76ab92](https://github.com/dh6k/morphe-patches/commit/f76ab92913cacc4d009f5484a3ab1be9f0ac090d))

## [1.6.0-dev.3](https://github.com/dh6k/morphe-patches/compare/v1.6.0-dev.2...v1.6.0-dev.3) (2026-09-06)

### 🔧 Improvements

* **titanium:** conditionalize keep-alive pins and streamline notification ([8ad79b1](https://github.com/dh6k/morphe-patches/commit/8ad79b1b7ca7203b1dc1479ce6fe81454502f31b))

## [1.6.0-dev.2](https://github.com/dh6k/morphe-patches/compare/v1.6.0-dev.1...v1.6.0-dev.2) (2026-09-06)

### 🐛 Bug Fixes

* **titanium:** make notification toggle actually hide ([f295b0e](https://github.com/dh6k/morphe-patches/commit/f295b0e39300248be67620713c3073c64ce62a3b))

## [1.6.0-dev.1](https://github.com/dh6k/morphe-patches/compare/v1.5.0...v1.6.0-dev.1) (2026-09-06)

### ✨ New Features

* **titanium:** notification options for keep-alive patch ([c6b5620](https://github.com/dh6k/morphe-patches/commit/c6b5620da46cd9d6f33e1d7f64bb6467ab311260))

## [1.5.0](https://github.com/dh6k/morphe-patches/compare/v1.4.0...v1.5.0) (2026-09-05)

### 🐛 Bug Fixes

* **brave:** tolerate Origin log-literal drift on 1.97.x ([86755f2](https://github.com/dh6k/morphe-patches/commit/86755f20a37a75546af646cd7c0f3acd684ca674)), closes [#1](https://github.com/dh6k/morphe-patches/issues/1) [#5](https://github.com/dh6k/morphe-patches/issues/5)

### ✨ New Features

* **universal:** broaden Disable analytics to kondratjev parity ([2cb64ac](https://github.com/dh6k/morphe-patches/commit/2cb64ac9ca097b92394b4f558ed9de8685c42656))
* **universal:** cover Adjust v5 initSdk and Crashlytics boxed overload ([aaba54a](https://github.com/dh6k/morphe-patches/commit/aaba54a195cc6adae3c3ea6aa1479b62a5ac9dd7))
* **universal:** cover screen reporting, crashlytics/perf providers and Adjust init ([42baa67](https://github.com/dh6k/morphe-patches/commit/42baa6770542275c175ac75ec6a514d10b84ea81))
* **universal:** harden Disable analytics manifest scope and runtime backstop ([1f27512](https://github.com/dh6k/morphe-patches/commit/1f275125aa6db5dcb05fccebb38474c68cd0adbd))

## [1.5.0-dev.5](https://github.com/dh6k/morphe-patches/compare/v1.5.0-dev.4...v1.5.0-dev.5) (2026-09-04)

### 🐛 Bug Fixes

* **brave:** tolerate Origin log-literal drift on 1.97.x ([86755f2](https://github.com/dh6k/morphe-patches/commit/86755f20a37a75546af646cd7c0f3acd684ca674)), closes [#1](https://github.com/dh6k/morphe-patches/issues/1) [#5](https://github.com/dh6k/morphe-patches/issues/5)

## [1.5.0-dev.4](https://github.com/dh6k/morphe-patches/compare/v1.5.0-dev.3...v1.5.0-dev.4) (2026-09-03)

### ✨ New Features

* **universal:** broaden Disable analytics to kondratjev parity ([2cb64ac](https://github.com/dh6k/morphe-patches/commit/2cb64ac9ca097b92394b4f558ed9de8685c42656))

## [1.5.0-dev.3](https://github.com/dh6k/morphe-patches/compare/v1.5.0-dev.2...v1.5.0-dev.3) (2026-09-03)

### ✨ New Features

* **universal:** cover Adjust v5 initSdk and Crashlytics boxed overload ([aaba54a](https://github.com/dh6k/morphe-patches/commit/aaba54a195cc6adae3c3ea6aa1479b62a5ac9dd7))

## [1.5.0-dev.2](https://github.com/dh6k/morphe-patches/compare/v1.5.0-dev.1...v1.5.0-dev.2) (2026-09-03)

### ✨ New Features

* **universal:** cover screen reporting, crashlytics/perf providers and Adjust init ([42baa67](https://github.com/dh6k/morphe-patches/commit/42baa6770542275c175ac75ec6a514d10b84ea81))

## [1.5.0-dev.1](https://github.com/dh6k/morphe-patches/compare/v1.4.0...v1.5.0-dev.1) (2026-09-03)

### ✨ New Features

* **universal:** harden Disable analytics manifest scope and runtime backstop ([1f27512](https://github.com/dh6k/morphe-patches/commit/1f275125aa6db5dcb05fccebb38474c68cd0adbd))

## [1.4.0](https://github.com/dh6k/morphe-patches/compare/v1.3.0...v1.4.0) (2026-08-24)

### 🐛 Bug Fixes

* **helium:** harden keep-alive structural resolver ([dfd8a0f](https://github.com/dh6k/morphe-patches/commit/dfd8a0f2ca9149a2cc35005f9cfc30ebaddbb7b7))
* **helium:** harden keep-alive without breaking apk patching ([1251a14](https://github.com/dh6k/morphe-patches/commit/1251a142658720365a037fce2f8b501d0f4e391c))

### ✨ New Features

* add Helium foreground keep-alive service ([1b6cc36](https://github.com/dh6k/morphe-patches/commit/1b6cc36a65971b89de44e8fe80bd81f13a69bd03))
* make Helium keep-alive patch version resilient ([cc93fc6](https://github.com/dh6k/morphe-patches/commit/cc93fc605bc403af756407b62600c1f8ee206595))

## [1.4.0-dev.4](https://github.com/dh6k/morphe-patches/compare/v1.4.0-dev.3...v1.4.0-dev.4) (2026-08-24)

### 🐛 Bug Fixes

* **helium:** harden keep-alive without breaking apk patching ([1251a14](https://github.com/dh6k/morphe-patches/commit/1251a142658720365a037fce2f8b501d0f4e391c))

## [1.4.0-dev.3](https://github.com/dh6k/morphe-patches/compare/v1.4.0-dev.2...v1.4.0-dev.3) (2026-08-23)

### 🐛 Bug Fixes

* **helium:** harden keep-alive structural resolver ([dfd8a0f](https://github.com/dh6k/morphe-patches/commit/dfd8a0f2ca9149a2cc35005f9cfc30ebaddbb7b7))

## [1.4.0-dev.2](https://github.com/dh6k/morphe-patches/compare/v1.4.0-dev.1...v1.4.0-dev.2) (2026-08-23)

### ✨ New Features

* make Helium keep-alive patch version resilient ([cc93fc6](https://github.com/dh6k/morphe-patches/commit/cc93fc605bc403af756407b62600c1f8ee206595))

## [1.4.0-dev.1](https://github.com/dh6k/morphe-patches/compare/v1.3.0...v1.4.0-dev.1) (2026-08-23)

### ✨ New Features

* add Helium foreground keep-alive service ([1b6cc36](https://github.com/dh6k/morphe-patches/commit/1b6cc36a65971b89de44e8fe80bd81f13a69bd03))

## [1.3.0](https://github.com/dh6k/morphe-patches/compare/v1.2.0...v1.3.0) (2026-08-21)

### 🐛 Bug Fixes

* **brave:** support Origin 1.94.114 ([6c5dfcd](https://github.com/dh6k/morphe-patches/commit/6c5dfcd4d62f42695c6e56e6b3faad50ba871bf9))
* **brave:** tolerate optional Origin hooks ([326af9d](https://github.com/dh6k/morphe-patches/commit/326af9d2f7dbd643d620bbc5efc358ff924c0dd0))
* **brave:** unpin stable compatibility ([ad21d01](https://github.com/dh6k/morphe-patches/commit/ad21d01975d3fe06e12699daad2557f863562342))

### ✨ New Features

* **universal:** disable common analytics SDKs ([5f8e982](https://github.com/dh6k/morphe-patches/commit/5f8e982cfd1e44585a3ee9802518bc4c0f0dd731))

## [1.3.0-dev.4](https://github.com/dh6k/morphe-patches/compare/v1.3.0-dev.3...v1.3.0-dev.4) (2026-08-21)

### 🐛 Bug Fixes

* **brave:** unpin stable compatibility ([ad21d01](https://github.com/dh6k/morphe-patches/commit/ad21d01975d3fe06e12699daad2557f863562342))

## [1.3.0-dev.3](https://github.com/dh6k/morphe-patches/compare/v1.3.0-dev.2...v1.3.0-dev.3) (2026-08-21)

### 🐛 Bug Fixes

* **brave:** tolerate optional Origin hooks ([326af9d](https://github.com/dh6k/morphe-patches/commit/326af9d2f7dbd643d620bbc5efc358ff924c0dd0))

## [1.3.0-dev.2](https://github.com/dh6k/morphe-patches/compare/v1.3.0-dev.1...v1.3.0-dev.2) (2026-08-21)

### 🐛 Bug Fixes

* **brave:** support Origin 1.94.114 ([6c5dfcd](https://github.com/dh6k/morphe-patches/commit/6c5dfcd4d62f42695c6e56e6b3faad50ba871bf9))

## [1.3.0-dev.1](https://github.com/dh6k/morphe-patches/compare/v1.2.0...v1.3.0-dev.1) (2026-08-15)

### ✨ New Features

* **universal:** disable common analytics SDKs ([5f8e982](https://github.com/dh6k/morphe-patches/commit/5f8e982cfd1e44585a3ee9802518bc4c0f0dd731))

## [1.2.0](https://github.com/dh6k/morphe-patches/compare/v1.1.0...v1.2.0) (2026-08-12)

### 🐛 Bug Fixes

* **helium:** use compatible patcher APIs ([a4e53db](https://github.com/dh6k/morphe-patches/commit/a4e53db1500c3697c03713dc88fb67a321af31ba))
* pass list to Quetta fingerprint strings ([dbe35a6](https://github.com/dh6k/morphe-patches/commit/dbe35a6e2fe1861f3eb0318e1acd61cecff90772))

### ✨ New Features

* **helium:** keep child processes strongly bound ([524412d](https://github.com/dh6k/morphe-patches/commit/524412d48a8898a36295593f9d875c94749e8d5d))
* **helium:** strengthen child process survival ([99d7e27](https://github.com/dh6k/morphe-patches/commit/99d7e27a3a8bd4be1ba48c1e1c10829efd88d6ed))
* **quetta:** block bundled extension installation ([e63165d](https://github.com/dh6k/morphe-patches/commit/e63165dbf5c3bff778c9f612b45fb8979af73cc2))

## [1.2.0-dev.3](https://github.com/dh6k/morphe-patches/compare/v1.2.0-dev.2...v1.2.0-dev.3) (2026-08-12)

### 🐛 Bug Fixes

* pass list to Quetta fingerprint strings ([dbe35a6](https://github.com/dh6k/morphe-patches/commit/dbe35a6e2fe1861f3eb0318e1acd61cecff90772))

### ✨ New Features

* **quetta:** block bundled extension installation ([e63165d](https://github.com/dh6k/morphe-patches/commit/e63165dbf5c3bff778c9f612b45fb8979af73cc2))

## [1.2.0-dev.2](https://github.com/dh6k/morphe-patches/compare/v1.2.0-dev.1...v1.2.0-dev.2) (2026-08-07)

### 🐛 Bug Fixes

* **helium:** use compatible patcher APIs ([a4e53db](https://github.com/dh6k/morphe-patches/commit/a4e53db1500c3697c03713dc88fb67a321af31ba))

### ✨ New Features

* **helium:** strengthen child process survival ([99d7e27](https://github.com/dh6k/morphe-patches/commit/99d7e27a3a8bd4be1ba48c1e1c10829efd88d6ed))

## [1.2.0-dev.1](https://github.com/dh6k/morphe-patches/compare/v1.1.0...v1.2.0-dev.1) (2026-08-07)

### ✨ New Features

* **helium:** keep child processes strongly bound ([524412d](https://github.com/dh6k/morphe-patches/commit/524412d48a8898a36295593f9d875c94749e8d5d))

## [1.1.0](https://github.com/dh6k/morphe-patches/compare/v1.0.2...v1.1.0) (2026-07-25)

### 🐛 Bug Fixes

* **build:** keep generator dependencies out of patch dex ([fe0a25e](https://github.com/dh6k/morphe-patches/commit/fe0a25e9767e47ffd90342e116e58c4c7e7f4e2b))
* **release:** publish appName option on main ([87f6a6e](https://github.com/dh6k/morphe-patches/commit/87f6a6ed75ced66379debe0ba89bdf66da693189))
* **universal:** release bundle-specific app name option ([dbdb4fc](https://github.com/dh6k/morphe-patches/commit/dbdb4fc184f82ddfc010e9d7f8fb1f4bde527758))
* **universal:** restore appName option key ([83f1677](https://github.com/dh6k/morphe-patches/commit/83f1677388c16073f71ed83d99a6663d63cd63a3))

### ✨ New Features

* **universal:** add custom app icon patch ([affb2d4](https://github.com/dh6k/morphe-patches/commit/affb2d4e578a91da7ce5ec6a40c56b47d7ae968f))

## [1.1.0-dev.4](https://github.com/dh6k/morphe-patches/compare/v1.1.0-dev.3...v1.1.0-dev.4) (2026-07-25)

### 🐛 Bug Fixes

* **universal:** restore appName option key ([83f1677](https://github.com/dh6k/morphe-patches/commit/83f1677388c16073f71ed83d99a6663d63cd63a3))

## [1.1.0-dev.3](https://github.com/dh6k/morphe-patches/compare/v1.1.0-dev.2...v1.1.0-dev.3) (2026-07-25)

### 🐛 Bug Fixes

* **universal:** release bundle-specific app name option ([dbdb4fc](https://github.com/dh6k/morphe-patches/commit/dbdb4fc184f82ddfc010e9d7f8fb1f4bde527758))

## [1.1.0-dev.2](https://github.com/dh6k/morphe-patches/compare/v1.1.0-dev.1...v1.1.0-dev.2) (2026-07-25)

### 🐛 Bug Fixes

* **build:** keep generator dependencies out of patch dex ([fe0a25e](https://github.com/dh6k/morphe-patches/commit/fe0a25e9767e47ffd90342e116e58c4c7e7f4e2b))

## [1.1.0-dev.1](https://github.com/dh6k/morphe-patches/compare/v1.0.2...v1.1.0-dev.1) (2026-07-25)

### ✨ New Features

* **universal:** add custom app icon patch ([affb2d4](https://github.com/dh6k/morphe-patches/commit/affb2d4e578a91da7ce5ec6a40c56b47d7ae968f))

## [1.0.2](https://github.com/dh6k/morphe-patches/compare/v1.0.1...v1.0.2) (2026-07-24)

### 🚀 Updated App Support

* **universal:** backport Change app name patch ([8b34d84](https://github.com/dh6k/morphe-patches/commit/8b34d84d0063e9ad7c065c14270c06206501ed33))

## [1.0.2-dev.1](https://github.com/dh6k/morphe-patches/compare/v1.0.1...v1.0.2-dev.1) (2026-07-24)

### 🚀 Updated App Support

* **universal:** backport Change app name patch ([8b34d84](https://github.com/dh6k/morphe-patches/commit/8b34d84d0063e9ad7c065c14270c06206501ed33))

## [1.0.1](https://github.com/dh6k/morphe-patches/compare/v1.0.0...v1.0.1) (2026-07-23)

### 🐛 Bug Fixes

* **brave:** support Brave Beta 1.94.94 ([9aeeb69](https://github.com/dh6k/morphe-patches/commit/9aeeb691ecdc724db6510e89a8c63577dbd6f6da))

## [1.0.1-dev.1](https://github.com/dh6k/morphe-patches/compare/v1.0.0...v1.0.1-dev.1) (2026-07-23)

### 🐛 Bug Fixes

* **brave:** support Brave Beta 1.94.94 ([9aeeb69](https://github.com/dh6k/morphe-patches/commit/9aeeb691ecdc724db6510e89a8c63577dbd6f6da))

## 1.0.0 (2026-07-22)

### 🐛 Bug Fixes

* add gold tier to truecaller ([f1377d5](https://github.com/dh6k/morphe-patches/commit/f1377d5f83a9e5fcd60fc7b14cd2e5e660440162))
* **brave:** Fixed XML resource issue for Brave Patch ([#17](https://github.com/dh6k/morphe-patches/issues/17)) ([0c0ada3](https://github.com/dh6k/morphe-patches/commit/0c0ada3dfeaad7e741e46e66e104795fea2e07e1))
* fix at4k patch ([a43fe1e](https://github.com/dh6k/morphe-patches/commit/a43fe1e829f5687c179f6201b6af7ee5a23ab2e6))
* fix missing file ([28e8a32](https://github.com/dh6k/morphe-patches/commit/28e8a322c66ebf291d2a297f2acd915c7f29f17f))
* fix notifications ([2afb127](https://github.com/dh6k/morphe-patches/commit/2afb1274e6519708a2580bc158b07b19eaac685b))
* fix patch generator ([29ba352](https://github.com/dh6k/morphe-patches/commit/29ba352a4e23174710b320518319a6b22f296f66))
* fix patch instructions ([1b172a6](https://github.com/dh6k/morphe-patches/commit/1b172a68a8e2df9ff5808dfe2381e2d596521021))
* fix patch instructions ([0e3f83b](https://github.com/dh6k/morphe-patches/commit/0e3f83bd0569e2dbe17b96211aa368f5c2b0ce56))
* fix spoofed setting ([19e3b5b](https://github.com/dh6k/morphe-patches/commit/19e3b5bb9a80c00f85721bd0676339bbc4d3e8b6))
* fix truecaller patch ([988cd62](https://github.com/dh6k/morphe-patches/commit/988cd625ad9edf7ed4f347b8e9eb47caa599d5b6))
* fixed truecaller patch ([18cb40f](https://github.com/dh6k/morphe-patches/commit/18cb40f329b9dc269756b1ade90830e37ebd3b03))
* inject plan code and name to properly trick flutter into premium status ([b575d70](https://github.com/dh6k/morphe-patches/commit/b575d703ec47f4cef4f430b57c92602254202d3f))
* **medium:** add custom host option inside freedium settings dialog ([c2927fb](https://github.com/dh6k/morphe-patches/commit/c2927fbef2f7b4bf2071b5fb391580d512dd2ce2))
* **medium:** add onLongClickListener to hide the Unlock button ([c4a5488](https://github.com/dh6k/morphe-patches/commit/c4a5488608181d2a1dca821f27504fb48eed2b13))
* **medium:** change extension file type to .mpe for Morphe compatibility ([144a163](https://github.com/dh6k/morphe-patches/commit/144a163ea3d50ca02223ea6c394979f498b6216d))
* **medium:** correct syntax error in FreediumPatch.kt ([a6cfb87](https://github.com/dh6k/morphe-patches/commit/a6cfb87bb2921f1e8b018d121ec981909775a8c0))
* **medium:** declare extension dependency in patch builder DSL ([e651e27](https://github.com/dh6k/morphe-patches/commit/e651e272625dde8525c2a79c2168dd6e5873a49f))
* **medium:** implement manual reflection-based extension loader to resolve classloader issues ([1d3a965](https://github.com/dh6k/morphe-patches/commit/1d3a9655ff62cd83990dae02b1f68b60a6968a9e))
* **medium:** inject at start of PostFragment.Q to prevent Verifier error ([bf12e08](https://github.com/dh6k/morphe-patches/commit/bf12e08ab17c232b8abb62fa974a55468b3dbcf7))
* **medium:** prevent status bar overlap and adjust FAB bottom margin to 76dp ([eea5176](https://github.com/dh6k/morphe-patches/commit/eea5176533819374cd4730ae29092e698de4151a))
* **medium:** refine Settings row integrations, click ripples, and floating button color/alignment ([f62a9f8](https://github.com/dh6k/morphe-patches/commit/f62a9f86a279d1c7979305264ec230dbfbe50ef6))
* **medium:** set pill-shaped Unlock button with overlay and HTML loading animation inside webview ([b3905cc](https://github.com/dh6k/morphe-patches/commit/b3905cc49aa2915e4548dff0e5aba9f33e922c6c))
* **morphe:** merge latest patches template improvements and update patch list generator ([20ec4f2](https://github.com/dh6k/morphe-patches/commit/20ec4f28bc0f90c6554fee0a7fe615b7bcbb8c10))
* mygate notifications ([0f9b162](https://github.com/dh6k/morphe-patches/commit/0f9b1626a893695e31396420ce458963612a956d))
* mygate patch ([1cb64e9](https://github.com/dh6k/morphe-patches/commit/1cb64e94b9165427ddcfedc3159bbdf29b492e37))
* mygate patch fixed ([40fe967](https://github.com/dh6k/morphe-patches/commit/40fe967a563783b98e1b9ee394d835a562488616))
* mygate patch fixed ([9a4ec7d](https://github.com/dh6k/morphe-patches/commit/9a4ec7dce9981ec7f771b7479180021a75e10be5))
* **mygate:** make ShowUpgradeDialogFingerprint dynamic to bypass method z obfuscation ([1cfb5b9](https://github.com/dh6k/morphe-patches/commit/1cfb5b9b53e430e3812da1bd951f5d4ad091c796))
* **mygate:** resolve notification loss and fingerprint mismatches on 7.30.1 ([0233b61](https://github.com/dh6k/morphe-patches/commit/0233b616e395622c8c2edb071c4f61e01ce69766))
* remove gpg check ([4fbcb01](https://github.com/dh6k/morphe-patches/commit/4fbcb018dc04a2e7bfd3d1dd3867701e0ce7a79e))
* **workflow:** move clean task to start of build to prevent deleting release assets ([05629a6](https://github.com/dh6k/morphe-patches/commit/05629a61578af627cfbe1bed535c5d30ad3ef66c))

### ✨ New Features

* add more truecaller patches ([c9cec9c](https://github.com/dh6k/morphe-patches/commit/c9cec9c54c3ce39c73ce24589c9e7b6b83b9c0c8))
* add one more patch ([6c59017](https://github.com/dh6k/morphe-patches/commit/6c5901702a029702f4c0aa90ccf6b5c8954fb5b2))
* Added patches for at4k and bounce ([5b0ca44](https://github.com/dh6k/morphe-patches/commit/5b0ca445236468fb8ab61c925354578e8dda0b12))
* Brave Browser Origin Unlocked and some MyGate fixes ([#15](https://github.com/dh6k/morphe-patches/issues/15)) ([fb1e24a](https://github.com/dh6k/morphe-patches/commit/fb1e24a838ef0dda09066a76f3d0e826090421a0))
* bypass Flutter ad rendering via JSON spoofing & nuke native floating banners ([e01ef3b](https://github.com/dh6k/morphe-patches/commit/e01ef3bdb6c23bb68d99ac5910c47489ebc417dc))
* fix mygate issue ([94c875d](https://github.com/dh6k/morphe-patches/commit/94c875d5e5d84ee2cc190bb1753e25cacb7123bf))
* **medium:** add Freedium Mirror patch ([02be3f7](https://github.com/dh6k/morphe-patches/commit/02be3f76a2d9ac6194b6cc7e1388f3ac44c0926b))
* my gate patch ([e599c68](https://github.com/dh6k/morphe-patches/commit/e599c68317bbf33cbb8d59a1381cae6b011705c0))
* **mygate:** Introducing MyGate Premium Patch ([#19](https://github.com/dh6k/morphe-patches/issues/19)) ([ff0953b](https://github.com/dh6k/morphe-patches/commit/ff0953bf3219fab9a14841c2fee258cfbb85094c))
* patch splitwise app ([df0a5a1](https://github.com/dh6k/morphe-patches/commit/df0a5a1e1e34c7dffd89a95f43188babe50d9a07))

## [1.8.0](https://github.com/bufferk/morphe-patches/compare/v1.7.1...v1.8.0) (2026-07-18)

### ✨ New Features

* **mygate:** Introducing MyGate Premium Patch ([#19](https://github.com/bufferk/morphe-patches/issues/19)) ([ff0953b](https://github.com/bufferk/morphe-patches/commit/ff0953bf3219fab9a14841c2fee258cfbb85094c))

## [1.8.0-dev.1](https://github.com/bufferk/morphe-patches/compare/v1.7.1...v1.8.0-dev.1) (2026-07-18)

### ✨ New Features

* merge mygate patches into single patch, fix workflows, update version to 7.31.0 ([f23039c](https://github.com/bufferk/morphe-patches/commit/f23039c22616311677d68acc394ead875ba91113))

## [1.7.1](https://github.com/bufferk/morphe-patches/compare/v1.7.0...v1.7.1) (2026-07-18)

### 🐛 Bug Fixes

* **brave:** Fixed XML resource issue for Brave Patch ([#17](https://github.com/bufferk/morphe-patches/issues/17)) ([0c0ada3](https://github.com/bufferk/morphe-patches/commit/0c0ada3dfeaad7e741e46e66e104795fea2e07e1))

## [1.8.0-dev.1](https://github.com/bufferk/morphe-patches/compare/v1.7.0...v1.8.0-dev.1) (2026-07-18)

### 🐛 Bug Fixes

* fix brave patch ([7127877](https://github.com/bufferk/morphe-patches/commit/7127877e3cfc44f6e7dbde17bb6675121cb298b3))
* fixed brave patch ([94e4cce](https://github.com/bufferk/morphe-patches/commit/94e4ccea7164af340ac27b19bda4a899a8b79b9e))
* fixed pipeline ([414946d](https://github.com/bufferk/morphe-patches/commit/414946d61bc30e97e9f171e298c31fa29081be36))
* **mygate:** directly query MutableLiveData class def to resolve setValue/postValue method name ([87b70cd](https://github.com/bufferk/morphe-patches/commit/87b70cd4dec5d1497a1a2c6fa239a6eafcd8a974))
* **mygate:** dynamically resolve both LiveData class name and method name to support obfuscated configurations ([afff4b4](https://github.com/bufferk/morphe-patches/commit/afff4b409fc5bbf6ae0a1a8df2e5bce5e2fc37ed))
* **mygate:** dynamically resolve obfuscated MutableLiveData.setValue method in test notification spoofing ([d6dbf80](https://github.com/bufferk/morphe-patches/commit/d6dbf800291b4b42cd5e6d8489b5b02dec2e00c1))
* **mygate:** fake app notification settings response to resolve e-intercom troubleshooting failure ([e72ca83](https://github.com/bufferk/morphe-patches/commit/e72ca83495cc2df4670a17c54a233ec2bd5feccc))
* **mygate:** make ShowUpgradeDialogFingerprint matching string-based to prevent obfuscated ViewBinding mismatch ([a192e9e](https://github.com/bufferk/morphe-patches/commit/a192e9e770a42bfb7669cd445ed47cc9ef11ff24))
* **mygate:** make ShowUpgradeDialogFingerprint parameter-agnostic for robust matching across versions ([f8970f4](https://github.com/bufferk/morphe-patches/commit/f8970f4ca3248b8d36a1ad5504a5f4bf44e562ff))
* **mygate:** remove double semicolon in dynamic invoke-virtual smali template ([dabd1fb](https://github.com/bufferk/morphe-patches/commit/dabd1fbaf34c8f12a803a83d10355faf1bdd8ca3))
* **mygate:** rewrite firebase Installations cert spoof to avoid NPEs ([4f7238f](https://github.com/bufferk/morphe-patches/commit/4f7238f5517954e9647757de147f71087a2eb48f))
* **mygate:** rewrite firebase installations cert spoof using reflection to bypass R8 optimization NPEs on ART ([f54572a](https://github.com/bufferk/morphe-patches/commit/f54572aab33a90152c88a15f0bbf2fbbc5ae2246))
* **mygate:** use robust Dialog.show methodCall filter for ShowUpgradeDialogFingerprint to bypass log stripping ([defe063](https://github.com/bufferk/morphe-patches/commit/defe063c3a7f2a36740f43adf120357b5050c8f0))

### ✨ New Features

* patch brave app ([a8ef687](https://github.com/bufferk/morphe-patches/commit/a8ef687d5a06874b6de60a7de6ddf4848f81c57f))

## [1.7.0](https://github.com/bufferk/morphe-patches/compare/v1.6.11...v1.7.0) (2026-07-18)

### 🐛 Bug Fixes

* **mygate:** make ShowUpgradeDialogFingerprint dynamic to bypass method z obfuscation ([1cfb5b9](https://github.com/bufferk/morphe-patches/commit/1cfb5b9b53e430e3812da1bd951f5d4ad091c796))

### ✨ New Features

* Brave Browser Origin Unlocked and some MyGate fixes ([#15](https://github.com/bufferk/morphe-patches/issues/15)) ([fb1e24a](https://github.com/bufferk/morphe-patches/commit/fb1e24a838ef0dda09066a76f3d0e826090421a0))

## [1.7.0-dev.3](https://github.com/bufferk/morphe-patches/compare/v1.7.0-dev.2...v1.7.0-dev.3) (2026-07-18)

### 🐛 Bug Fixes

* fixed pipeline ([414946d](https://github.com/bufferk/morphe-patches/commit/414946d61bc30e97e9f171e298c31fa29081be36))

## [1.7.0-dev.2](https://github.com/bufferk/morphe-patches/compare/v1.7.0-dev.1...v1.7.0-dev.2) (2026-07-18)

### 🐛 Bug Fixes

* fixed brave patch ([94e4cce](https://github.com/bufferk/morphe-patches/commit/94e4ccea7164af340ac27b19bda4a899a8b79b9e))

## [1.7.0-dev.1](https://github.com/bufferk/morphe-patches/compare/v1.6.15-dev.6...v1.7.0-dev.1) (2026-07-17)

### 🐛 Bug Fixes

* **mygate:** rewrite firebase Installations cert spoof to avoid NPEs ([4f7238f](https://github.com/bufferk/morphe-patches/commit/4f7238f5517954e9647757de147f71087a2eb48f))
* **mygate:** rewrite firebase installations cert spoof using reflection to bypass R8 optimization NPEs on ART ([f54572a](https://github.com/bufferk/morphe-patches/commit/f54572aab33a90152c88a15f0bbf2fbbc5ae2246))

### ✨ New Features

* patch brave app ([a8ef687](https://github.com/bufferk/morphe-patches/commit/a8ef687d5a06874b6de60a7de6ddf4848f81c57f))

## [1.6.15-dev.7](https://github.com/bufferk/morphe-patches/compare/v1.6.15-dev.6...v1.6.15-dev.7) (2026-07-10)

### 🐛 Bug Fixes

* **mygate:** rewrite firebase Installations cert spoof to avoid NPEs ([4f7238f](https://github.com/bufferk/morphe-patches/commit/4f7238f5517954e9647757de147f71087a2eb48f))
* **mygate:** rewrite firebase installations cert spoof using reflection to bypass R8 optimization NPEs on ART ([f54572a](https://github.com/bufferk/morphe-patches/commit/f54572aab33a90152c88a15f0bbf2fbbc5ae2246))

## [1.6.15-dev.7](https://github.com/bufferk/morphe-patches/compare/v1.6.15-dev.6...v1.6.15-dev.7) (2026-07-10)

### 🐛 Bug Fixes

* **mygate:** rewrite firebase Installations cert spoof to avoid NPEs ([4f7238f](https://github.com/bufferk/morphe-patches/commit/4f7238f5517954e9647757de147f71087a2eb48f))

## [1.6.15-dev.6](https://github.com/bufferk/morphe-patches/compare/v1.6.15-dev.5...v1.6.15-dev.6) (2026-07-06)

### 🐛 Bug Fixes

* **mygate:** fake app notification settings response to resolve e-intercom troubleshooting failure ([e72ca83](https://github.com/bufferk/morphe-patches/commit/e72ca83495cc2df4670a17c54a233ec2bd5feccc))

## [1.6.15-dev.5](https://github.com/bufferk/morphe-patches/compare/v1.6.15-dev.4...v1.6.15-dev.5) (2026-07-06)

### 🐛 Bug Fixes

* **mygate:** remove double semicolon in dynamic invoke-virtual smali template ([dabd1fb](https://github.com/bufferk/morphe-patches/commit/dabd1fbaf34c8f12a803a83d10355faf1bdd8ca3))

## [1.6.15-dev.4](https://github.com/bufferk/morphe-patches/compare/v1.6.15-dev.3...v1.6.15-dev.4) (2026-07-06)

### 🐛 Bug Fixes

* **mygate:** dynamically resolve both LiveData class name and method name to support obfuscated configurations ([afff4b4](https://github.com/bufferk/morphe-patches/commit/afff4b409fc5bbf6ae0a1a8df2e5bce5e2fc37ed))

## [1.6.15-dev.3](https://github.com/bufferk/morphe-patches/compare/v1.6.15-dev.2...v1.6.15-dev.3) (2026-07-06)

### 🐛 Bug Fixes

* **mygate:** directly query MutableLiveData class def to resolve setValue/postValue method name ([87b70cd](https://github.com/bufferk/morphe-patches/commit/87b70cd4dec5d1497a1a2c6fa239a6eafcd8a974))

## [1.6.15-dev.2](https://github.com/bufferk/morphe-patches/compare/v1.6.15-dev.1...v1.6.15-dev.2) (2026-07-06)

### 🐛 Bug Fixes

* **mygate:** dynamically resolve obfuscated MutableLiveData.setValue method in test notification spoofing ([d6dbf80](https://github.com/bufferk/morphe-patches/commit/d6dbf800291b4b42cd5e6d8489b5b02dec2e00c1))

## [1.6.15-dev.1](https://github.com/bufferk/morphe-patches/compare/v1.6.14...v1.6.15-dev.1) (2026-07-06)

### 🐛 Bug Fixes

* **mygate:** make ShowUpgradeDialogFingerprint parameter-agnostic for robust matching across versions ([f8970f4](https://github.com/bufferk/morphe-patches/commit/f8970f4ca3248b8d36a1ad5504a5f4bf44e562ff))

## [1.6.14](https://github.com/bufferk/morphe-patches/compare/v1.6.13...v1.6.14) (2026-07-06)

### 🐛 Bug Fixes

* **mygate:** use robust Dialog.show methodCall filter for ShowUpgradeDialogFingerprint to bypass log stripping ([7c8f763](https://github.com/bufferk/morphe-patches/commit/7c8f7637e7f3e2d11c9ff249154a36303751b755))

## [1.6.13](https://github.com/bufferk/morphe-patches/compare/v1.6.12...v1.6.13) (2026-07-05)

### 🐛 Bug Fixes

* **mygate:** make ShowUpgradeDialogFingerprint matching string-based to prevent obfuscated ViewBinding mismatch ([17eeddd](https://github.com/bufferk/morphe-patches/commit/17eeddd9708754d585976c56dc3ec02e64128f17))

## [1.6.12](https://github.com/bufferk/morphe-patches/compare/v1.6.11...v1.6.12) (2026-07-05)

### 🐛 Bug Fixes

* **mygate:** make ShowUpgradeDialogFingerprint dynamic to bypass method z obfuscation ([9fca981](https://github.com/bufferk/morphe-patches/commit/9fca981a6f7bab44aac16524806474d5d9f62381))

## [1.6.11](https://github.com/bufferk/morphe-patches/compare/v1.6.10...v1.6.11) (2026-07-05)

### 🐛 Bug Fixes

* **mygate:** resolve notification loss and fingerprint mismatches on 7.30.1 ([030d5ec](https://github.com/bufferk/morphe-patches/commit/030d5ecc0a48bf8965bde5e86991420d52ff736c))
* **workflow:** move clean task to start of build to prevent deleting release assets ([54bda4d](https://github.com/bufferk/morphe-patches/commit/54bda4d8d893af5d17abc545d73d8496e4222576))

## [1.6.11](https://github.com/bufferk/morphe-patches/compare/v1.6.10...v1.6.11) (2026-07-05)

### 🐛 Bug Fixes

* **mygate:** resolve notification loss and fingerprint mismatches on 7.30.1 ([030d5ec](https://github.com/bufferk/morphe-patches/commit/030d5ecc0a48bf8965bde5e86991420d52ff736c))

## [1.6.10](https://github.com/bufferk/morphe-patches/compare/v1.6.9...v1.6.10) (2026-07-05)

### 🐛 Bug Fixes

* **morphe:** merge latest patches template improvements and update patch list generator ([d834cd1](https://github.com/bufferk/morphe-patches/commit/d834cd1a80a6f19efa51b9da3403023856a8f313))

## [1.6.9](https://github.com/bufferk/morphe-patches/compare/v1.6.8...v1.6.9) (2026-07-05)


### Bug Fixes

* **medium:** add onLongClickListener to hide the Unlock button ([721fc87](https://github.com/bufferk/morphe-patches/commit/721fc871b369c4af04733b18545286bccf6824bb))

## [1.6.8](https://github.com/bufferk/morphe-patches/compare/v1.6.7...v1.6.8) (2026-07-05)


### Bug Fixes

* **medium:** prevent status bar overlap and adjust FAB bottom margin to 76dp ([57aca3b](https://github.com/bufferk/morphe-patches/commit/57aca3b063eeb0e15b5a1d61fb065b5cd5335875))

## [1.6.7](https://github.com/bufferk/morphe-patches/compare/v1.6.6...v1.6.7) (2026-07-05)


### Bug Fixes

* **medium:** add custom host option inside freedium settings dialog ([d5a553c](https://github.com/bufferk/morphe-patches/commit/d5a553c822370ce20c3f6263cdc0862a372f0955))

## [1.6.6](https://github.com/bufferk/morphe-patches/compare/v1.6.5...v1.6.6) (2026-07-05)


### Bug Fixes

* **medium:** set pill-shaped Unlock button with overlay and HTML loading animation inside webview ([6f2ac37](https://github.com/bufferk/morphe-patches/commit/6f2ac374c5907c357877197b61544ba0683bec04))

## [1.6.5](https://github.com/bufferk/morphe-patches/compare/v1.6.4...v1.6.5) (2026-07-05)


### Bug Fixes

* **medium:** refine Settings row integrations, click ripples, and floating button color/alignment ([a724c4f](https://github.com/bufferk/morphe-patches/commit/a724c4ffe33d033534e52de8d83e2ce13b619191))

## [1.6.4](https://github.com/bufferk/morphe-patches/compare/v1.6.3...v1.6.4) (2026-07-05)


### Bug Fixes

* **medium:** change extension file type to .mpe for Morphe compatibility ([a3400c4](https://github.com/bufferk/morphe-patches/commit/a3400c4491dca66cd907e160d57172cbb3bb5fc0))

## [1.6.3](https://github.com/bufferk/morphe-patches/compare/v1.6.2...v1.6.3) (2026-07-05)


### Bug Fixes

* **medium:** implement manual reflection-based extension loader to resolve classloader issues ([f3f536b](https://github.com/bufferk/morphe-patches/commit/f3f536bf78c015f2896ffac350573f6ce32a0959))

## [1.6.2](https://github.com/bufferk/morphe-patches/compare/v1.6.1...v1.6.2) (2026-07-05)


### Bug Fixes

* **medium:** correct syntax error in FreediumPatch.kt ([b85de4d](https://github.com/bufferk/morphe-patches/commit/b85de4ddfc46576495b607df04c9d3ccf0baba56))
* **medium:** declare extension dependency in patch builder DSL ([b9ddfb2](https://github.com/bufferk/morphe-patches/commit/b9ddfb20539840e51fb2e129cc01bd8f7069b225))

## [1.6.1](https://github.com/bufferk/morphe-patches/compare/v1.6.0...v1.6.1) (2026-07-05)


### Bug Fixes

* **medium:** inject at start of PostFragment.Q to prevent Verifier error ([de17b93](https://github.com/bufferk/morphe-patches/commit/de17b935495af8923a28becdcca99e9da428cd0f))

# [1.6.0](https://github.com/bufferk/morphe-patches/compare/v1.5.2...v1.6.0) (2026-07-05)


### Bug Fixes

* fix notifications ([fe555c8](https://github.com/bufferk/morphe-patches/commit/fe555c8d3c93fdf202b7296b4364c5a07329b61e))


### Features

* **medium:** add Freedium Mirror patch ([e0465c8](https://github.com/bufferk/morphe-patches/commit/e0465c86d101e91e10f1f79e1ce02e0036a3a805))

## [1.5.2](https://github.com/bufferk/morphe-patches/compare/v1.5.1...v1.5.2) (2026-05-09)


### Bug Fixes

* fix spoofed setting ([b1e3da0](https://github.com/bufferk/morphe-patches/commit/b1e3da0736e9bf67fd48766202b6eaedb318ede0))

## [1.5.1](https://github.com/bufferk/morphe-patches/compare/v1.5.0...v1.5.1) (2026-05-09)


### Bug Fixes

* mygate notifications ([ae55452](https://github.com/bufferk/morphe-patches/commit/ae55452a27083d3e78fe032446165a62f85cdfce))

# [1.5.0](https://github.com/bufferk/morphe-patches/compare/v1.4.1...v1.5.0) (2026-05-08)


### Features

* patch splitwise app ([1b905e8](https://github.com/bufferk/morphe-patches/commit/1b905e8021a02f19f420dd388221df0daa4c9ba9))

## [1.4.1](https://github.com/bufferk/morphe-patches/compare/v1.4.0...v1.4.1) (2026-05-07)


### Bug Fixes

* inject plan code and name to properly trick flutter into premium status ([3914f55](https://github.com/bufferk/morphe-patches/commit/3914f5501a3d2b2f6c5b7125d4e7e30763237bcc))

# [1.4.0](https://github.com/bufferk/morphe-patches/compare/v1.3.3...v1.4.0) (2026-05-07)


### Features

* bypass Flutter ad rendering via JSON spoofing & nuke native floating banners ([ac2c5d1](https://github.com/bufferk/morphe-patches/commit/ac2c5d19c9cd84006e4f92f4099751de9a602b83))

## [1.3.3](https://github.com/bufferk/morphe-patches/compare/v1.3.2...v1.3.3) (2026-05-07)


### Bug Fixes

* mygate patch ([7e2c582](https://github.com/bufferk/morphe-patches/commit/7e2c58224e2659870b68d13b56d8024299246562))

## [1.3.2](https://github.com/bufferk/morphe-patches/compare/v1.3.1...v1.3.2) (2026-05-07)


### Bug Fixes

* mygate patch fixed ([ee9a43e](https://github.com/bufferk/morphe-patches/commit/ee9a43e1dada05611173a7542dc9c1415b4898d0))

## [1.3.1](https://github.com/bufferk/morphe-patches/compare/v1.3.0...v1.3.1) (2026-05-07)


### Bug Fixes

* mygate patch fixed ([65b9206](https://github.com/bufferk/morphe-patches/commit/65b92062335a61e3dcb44ba548f89b5696c6135e))

# [1.3.0](https://github.com/bufferk/morphe-patches/compare/v1.2.0...v1.3.0) (2026-05-07)


### Features

* fix mygate issue ([a1d7ab9](https://github.com/bufferk/morphe-patches/commit/a1d7ab927f4826829296b4565b0690b7496743cf))
* my gate patch ([3df6460](https://github.com/bufferk/morphe-patches/commit/3df646006aaad405ff39878e43d7c9ec19e3970b))

# [1.2.0](https://github.com/bufferk/morphe-patches/compare/v1.1.0...v1.2.0) (2026-04-06)


### Features

* add one more patch ([5386df8](https://github.com/bufferk/morphe-patches/commit/5386df847dc86ed4c57aa36a05eb6a144a8c69c1))

# [1.1.0](https://github.com/bufferk/morphe-patches/compare/v1.0.5...v1.1.0) (2026-04-05)


### Features

* add more truecaller patches ([f91304c](https://github.com/bufferk/morphe-patches/commit/f91304cfeecd1b9ef94aaf08ed54a7f90d177644))

## [1.0.5](https://github.com/bufferk/morphe-patches/compare/v1.0.4...v1.0.5) (2026-04-05)


### Bug Fixes

* fix at4k patch ([4ccdf22](https://github.com/bufferk/morphe-patches/commit/4ccdf22c4d384518c282045ecaab96689ecadb32))

## [1.0.4](https://github.com/bufferk/morphe-patches/compare/v1.0.3...v1.0.4) (2026-04-05)


### Bug Fixes

* add gold tier to truecaller ([b15ca8e](https://github.com/bufferk/morphe-patches/commit/b15ca8ec021a2dd2e077c6d2098f0c37a7b203aa))

## [1.0.3](https://github.com/bufferk/morphe-patches/compare/v1.0.2...v1.0.3) (2026-04-05)


### Bug Fixes

* fix patch instructions ([363917d](https://github.com/bufferk/morphe-patches/commit/363917de5c944b72a8cfaa5dac18784100c240d6))

## [1.0.2](https://github.com/bufferk/morphe-patches/compare/v1.0.1...v1.0.2) (2026-04-05)


### Bug Fixes

* fix patch instructions ([7cb9773](https://github.com/bufferk/morphe-patches/commit/7cb9773c20802e12577c460e9faa6da843e8c155))

## [1.0.1](https://github.com/bufferk/morphe-patches/compare/v1.0.0...v1.0.1) (2026-04-05)


### Bug Fixes

* fix truecaller patch ([36a1c40](https://github.com/bufferk/morphe-patches/commit/36a1c4055086762e7ecc012e7b9abf660cf01770))

# 1.0.0 (2026-04-05)


### Bug Fixes

* fix missing file ([6aa5ff1](https://github.com/bufferk/morphe-patches/commit/6aa5ff13947f752a92de35158d8db04e8c536e17))
* fix patch generator ([3aa3aa6](https://github.com/bufferk/morphe-patches/commit/3aa3aa68eb6d45f73330bc4badccee01e6b8db5e))
* fixed truecaller patch ([20194d9](https://github.com/bufferk/morphe-patches/commit/20194d94cbd59759a32285ddea2620a085d42725))
* remove gpg check ([0a15d7b](https://github.com/bufferk/morphe-patches/commit/0a15d7b6a43ac1459396c48f6e3d937444781081))


### Features

* Added patches for at4k and bounce ([86886d0](https://github.com/bufferk/morphe-patches/commit/86886d0f0e42fa31b57e15862f30289579163f33))
