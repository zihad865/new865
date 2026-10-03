# Anymaker SP0: Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Gradle + Kotlin + Jetpack Compose app named Anymaker that builds a signed release AAB, shows the tool home screen, and resizes/compresses one photo with the existing `Shrinker` algorithm at parity with Photo Shrink 1.2.

**Architecture:** New Gradle project in `anymaker-android/`, single `:app` module, packages split into `core/*` (engines, no UI) and `feature/*` (Compose screens + ViewModels). `Shrinker.java` moves unchanged except for its package. `photo-shrink-android/` stays as reference until SP1 reaches parity, then is deleted.

**Tech Stack:** Gradle 8.14.3 wrapper, Android Gradle Plugin 8.13.x, Kotlin 2.2.x, Compose BOM (latest stable at Task 1), Material 3, Navigation Compose, Lifecycle ViewModel, Coroutines, DataStore Preferences, JUnit 4, Robolectric, Compose UI Test.

**Spec:** `docs/superpowers/specs/2026-10-03-photo-shrink-2-design.md`

## Global Constraints

- applicationId and namespace: `com.vx.anymaker` (app never published, so the package is free to change; it is permanent after the first Play upload)
- App name: `Anymaker`; Play title `Anymaker: PDF, Photo & Scan`
- minSdk 24, targetSdk 36, compileSdk 36, Java/Kotlin JVM target 17
- versionCode 1, versionName `0.1.0` for SP0; first public release is `1.0.0` after SP1 + SP5
- Default UI language English; every user-visible string in `res/values/strings.xml`
- No runtime permissions in SP0; saving uses MediaStore (API 29+) and `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="28"`
- Release signing reads `keystore.properties` (git-ignored) or env vars `ANYMAKER_KEYSTORE`, `ANYMAKER_KEYSTORE_PASS`, `ANYMAKER_KEY_ALIAS`; never commit a keystore
- Every task ends with `./gradlew testDebugUnitTest lintDebug` passing
- Requires network access to `dl.google.com` (Google Maven) and `repo.maven.apache.org`

## Review Focus

1. A 48 MP photo with EXIF rotation 90° must come out upright and not crash (OOM retry path).
2. A KB limit smaller than the minimum possible output must show the "Raise the size limit" error, not save a file.
3. A photo shared from another app while a conversion runs must be queued, not overwrite the running job.
4. Rotating the screen mid-conversion must keep the job and the result.
5. Non-ASCII file names (Arabic, Bengali, emoji) must produce a valid, shareable output name.

---

### Task 1: Gradle project skeleton

**Files:**
- Create: `anymaker-android/settings.gradle.kts`, `anymaker-android/build.gradle.kts`, `anymaker-android/gradle.properties`, `anymaker-android/gradle/libs.versions.toml`, `anymaker-android/gradle/wrapper/*` (via `gradle wrapper --gradle-version 8.14.3`), `anymaker-android/.gitignore`
- Create: `anymaker-android/app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/vx/anymaker/MainActivity.kt`, `app/src/main/java/com/vx/anymaker/AnymakerApp.kt`
- Test: `app/src/test/java/com/vx/anymaker/MainActivityTest.kt`

**Interfaces:**
- Produces: `MainActivity : ComponentActivity` hosting `AnymakerApp()` composable

- [ ] **Step 1:** Resolve the latest stable AGP 8.13.x, Kotlin 2.2.x and Compose BOM from Google Maven and write them into `libs.versions.toml`.
- [ ] **Step 2: Write the failing test** `MainActivityTest.launches_and_shows_app_name` (Robolectric + `createAndroidComposeRule<MainActivity>()`): `onNodeWithText("Anymaker").assertIsDisplayed()`.
- [ ] **Step 3:** Run `./gradlew testDebugUnitTest` → FAIL (no MainActivity).
- [ ] **Step 4:** Implement `MainActivity` (edge-to-edge, `setContent { AnymakerApp() }`) and a placeholder `AnymakerApp()` showing the app name.
- [ ] **Step 5:** Run `./gradlew testDebugUnitTest assembleDebug lintDebug` → PASS.
- [ ] **Step 6:** Commit `feat(anymaker): gradle compose skeleton`.

### Task 2: Move Shrinker into core/image

**Files:**
- Create: `app/src/main/java/com/vx/anymaker/core/image/Shrinker.java` (copy of `photo-shrink-android/.../Shrinker.java`, package changed, class and members made `public`)
- Test: `app/src/test/java/com/vx/anymaker/core/image/ShrinkerTest.kt`

**Interfaces:**
- Produces: `Shrinker.targetSize(w:Int,h:Int,o:Shrinker.Options):IntArray`, `Shrinker.run(source:File,o:Options,p:Progress):Shrinker.Result`, `Shrinker.formatKb(kb:Double):String`, `Shrinker.ShrinkException`

- [ ] **Step 1: Write the failing tests:**
  - `targetSize keeps aspect inside box`: 4000×3000, box 1000×1000 keepAspect → `[1000, 750]`
  - `targetSize never enlarges`: 800×600, width 1600 → `[800, 600]`
  - `targetSize exact stretches`: 4000×3000, 300×300 keepAspect=false → `[300, 300]`
  - `formatKb`: `100.0 → "100"`, `60.5 → "60.5"`
  - `run fits budget` (Robolectric, `@Config(sdk=[34])`, `@GraphicsMode(NATIVE)`): 2000×1500 generated noise JPEG, maxKb 60 → `result.data.size <= 60*1024`
  - `run strict throws`: same image, 2000×1500 strict, maxKb 1 → throws `ShrinkException`
- [ ] **Step 2:** Run → FAIL (class missing).
- [ ] **Step 3:** Copy `Shrinker.java`, change package to `com.vx.anymaker.core.image`, make the class, `Options`, `Result`, `Progress`, `ShrinkException`, `run`, `targetSize`, `formatKb` public. No logic change.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat(core/image): move Shrinker with tests`.

### Task 3: Design system

**Files:**
- Create: `app/src/main/java/com/vx/anymaker/ui/theme/Color.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`
- Create: `app/src/main/res/font/plusjakartasans_{regular,medium,semibold,bold}.ttf` (from npm `@fontsource/plus-jakarta-sans`, OFL license file in `app/src/main/assets/licenses/`)
- Test: `app/src/test/java/com/vx/anymaker/ui/theme/ThemeTest.kt`

**Interfaces:**
- Produces: `AnymakerTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit)`; `ToolColors` object with `photo`, `scan`, `pdf` (light/dark pairs from the plan page: photo `#2563A8`/`#7FB0EA`, scan `#0F7B74`/`#4CC3B8`, pdf `#B4235A`/`#EF7EA6`)

- [ ] **Step 1: Write the failing test** `ThemeTest.light_and_dark_resolve`: inside `AnymakerTheme(darkTheme=true, dynamicColor=false)` `MaterialTheme.colorScheme.background` equals the dark token `#0E161C`; with `false` equals `#F3F6F8`.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement tokens and theme; dynamic color only on API 31+.
- [ ] **Step 4:** Run → PASS. Commit `feat(ui): theme, type, colors`.

### Task 4: Tool registry and home screen with navigation

**Files:**
- Create: `app/src/main/java/com/vx/anymaker/feature/home/Tools.kt`, `HomeScreen.kt`
- Create: `app/src/main/java/com/vx/anymaker/nav/AnymakerNavHost.kt`, `nav/Routes.kt`
- Create: `feature/files/FilesScreen.kt`, `feature/settings/SettingsScreen.kt` (empty-state screens)
- Modify: `AnymakerApp.kt`
- Test: `app/src/test/java/com/vx/anymaker/feature/home/HomeScreenTest.kt`

**Interfaces:**
- Produces: `enum class ToolSection { PHOTO, SCAN, PDF }`; `data class Tool(val id: String, @StringRes val title: Int, val icon: ImageVector, val section: ToolSection, val route: String?, val available: Boolean)`; `val allTools: List<Tool>` (every spec tool; only `resize` has `available = true` in SP0, others show a "Coming soon" chip); `object Routes { HOME, FILES, SETTINGS, RESIZE }`

- [ ] **Step 1: Write the failing tests:**
  - `shows_three_sections`: texts "Photo", "Scan", "PDF" displayed
  - `search_filters_tools`: type "pdf" in search → "Resize" not displayed, "Merge PDF" displayed
  - `tapping_resize_opens_resize`: click "Resize" → node with tag `resize_screen` exists
  - `unavailable_tool_shows_coming_soon`: click "Merge PDF" → snackbar "Coming soon"
  - `bottom_bar_switches_tabs`: click "Files" → text "No files yet" displayed
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement: `Scaffold` with `NavigationBar` (Tools, Files, Settings), `HomeScreen` with search field, preset chip row (static in SP0), `LazyVerticalGrid` sections; adaptive columns (4 on phone, 6 on ≥600dp).
- [ ] **Step 4:** Run → PASS. Commit `feat(home): tool grid and navigation`.

### Task 5: Resize engine wiring (ViewModel + repository)

**Files:**
- Create: `core/image/ImageRepository.kt`, `core/data/SettingsStore.kt`
- Create: `feature/resize/ResizeViewModel.kt`, `feature/resize/ResizeState.kt`
- Test: `feature/resize/ResizeViewModelTest.kt`, `core/image/ImageRepositoryTest.kt`

**Interfaces:**
- Consumes: `Shrinker` (Task 2)
- Produces:
  - `class ImageRepository(context: Context) { suspend fun importToCache(uri: Uri): ImportedImage; suspend fun shrink(src: ImportedImage, o: Shrinker.Options, onProgress: (String) -> Unit): ShrinkOutput; suspend fun saveToGallery(out: ShrinkOutput): Uri; fun shareUri(out: ShrinkOutput): Uri }`
  - `data class ImportedImage(val file: File, val displayName: String, val bytes: Long, val width: Int, val height: Int)`
  - `data class ShrinkOutput(val file: File, val width: Int, val height: Int, val bytes: Long, val quality: Int, val webp: Boolean)`
  - `class SettingsStore(context) { val settings: Flow<ResizeSettings>; suspend fun update(transform: (ResizeSettings) -> ResizeSettings) }`
  - `data class ResizeSettings(val width: Int?, val height: Int?, val keepAspect: Boolean = true, val neverReducePixels: Boolean = false, val maxKb: Double = 100.0, val webp: Boolean = false, val locked: Boolean = false)`
  - `sealed interface ResizeState { Empty; Loaded(image); Working(image, message); Done(image, output); Error(image?, message) }`
  - `ResizeViewModel.onImagePicked(uri)`, `onShared(uris: List<Uri>)`, `onSettingsChange(ResizeSettings)`, `convert()`, `save()`, `share()`

- [ ] **Step 1: Write the failing tests:**
  - `importToCache sanitizes names`: display name `".../٢٠٢٤ ছবি 😀.jpg"` → cache file name has no `/`, no leading dot, ≤ 60 chars, ends `.jpg`
  - `convert produces Done under budget`
  - `invalid width shows Error "Width must be 16–20000"`
  - `locked settings auto-convert on pick`: locked=true, pick → state goes to `Done` without calling `convert()`
  - `shared image during Working is queued`: second `onShared` while Working → processed after first completes, first output unchanged
  - `settings survive recreation`: new ViewModel with same `SettingsStore` reads last settings
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement; work on `Dispatchers.Default`; keep last 10 outputs in `cacheDir/results` (prune oldest); MediaStore save to `Pictures/Anymaker`.
- [ ] **Step 4:** Run → PASS. Commit `feat(resize): view model and image repository`.

### Task 6: Resize screen UI

**Files:**
- Create: `feature/resize/ResizeScreen.kt`, `ui/components/ResultCard.kt`, `ui/components/SettingRow.kt`
- Modify: `nav/AnymakerNavHost.kt`, `AndroidManifest.xml` (`SEND` image/* intent filter, `androidx.core.content.FileProvider` with `res/xml/file_paths.xml`)
- Test: `feature/resize/ResizeScreenTest.kt`

**Interfaces:**
- Consumes: `ResizeViewModel` (Task 5)

- [ ] **Step 1: Write the failing tests:** empty state shows "Choose photo"; with a loaded state, entering width 1000 / height 1000 / KB 60 and pressing "Convert" shows result card with "≤ 60 KB" text and Save + Share buttons; error state shows message text; rotation (`StateRestorationTester`) keeps Done state.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement with Photo Picker (`PickVisualMedia`), grouped Material 3 cards, segmented button JPG/WEBP, switches Keep aspect / Never reduce pixels / Lock settings, progress indicator, result card with dimensions, size, quality grade (A ≥ 85, B ≥ 70, C ≥ 55, else D), Save and Share.
- [ ] **Step 4:** Run full checks → PASS. Commit `feat(resize): compose screen, share intake`.

### Task 7: Release build and assets

**Files:**
- Modify: `app/build.gradle.kts` (release: `isMinifyEnabled = true`, `isShrinkResources = true`, signingConfig from `keystore.properties` or env)
- Create: `anymaker-android/scripts/make-keystore.sh` (keytool, PKCS12, RSA 4096, 10000 days, writes `keystore.properties`, prints backup warning)
- Create: launcher icon (adaptive, `mipmap-anydpi-v26`), `store/icon-512.png`, `store/feature-graphic-1024x500.png` via `tools/make_assets.py` adapted for the Anymaker mark
- Create: `anymaker-android/PRIVACY.md`, `anymaker-android/PLAY_STORE.md`, `anymaker-android/README.md`
- Test: `./gradlew bundleRelease` + `bundletool validate`

- [ ] **Step 1:** `scripts/make-keystore.sh` then `./gradlew bundleRelease assembleRelease`.
- [ ] **Step 2:** Verify: `jarsigner -verify app/build/outputs/bundle/release/app-release.aab` → "jar verified"; `apksigner verify` on the APK → exit 0; `bundletool validate` → exit 0.
- [ ] **Step 3:** Install-smoke on Robolectric is not possible; run `./gradlew testReleaseUnitTest` to prove R8 keeps needed classes.
- [ ] **Step 4:** Commit `build: signed release bundle, store assets, docs` (keystore and properties stay git-ignored).
