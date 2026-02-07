# Copilot Instructions for Appy

## Project Overview

Appy is an Android application that converts any website URL into a standalone Android APK directly on-device. It uses a unique "Binary Template Modification" strategy to generate APKs without running a full Gradle build, making APK generation fast and efficient.

**Key Technologies:**
- Language: Kotlin (main app), Java (template module)
- Framework: Android with Jetpack Compose + Material 3 Expressive Design
- Build System: Gradle with Kotlin DSL
- Min SDK: 26, Target SDK: 35, Compile SDK: 35
- JDK: 17 (source compatibility), JDK 21 (CI/CD)

## Architecture

Appy uses a two-module architecture with binary template modification:

1. **`app/` module**: Main Appy application with Jetpack Compose UI
   - Generates APKs by modifying a pre-compiled template
   - Uses Zip4j library to manipulate the template APK as a ZIP file
   - Signs APKs using apksig library with a bundled debug keystore
   
2. **`template/` module**: Pure Java WebView template (zero external dependencies)
   - Pre-compiled into `base-web-template.apk` and bundled in app assets
   - Contains a WebView that loads URLs from `assets/config.json`
   - Built automatically before the main app during any build

**Binary Template Modification Process:**
1. Template APK (`base-web-template.apk`) is copied from assets to cache
2. `assets/config.json` is replaced with user's URL and app configuration
3. Custom launcher icons are injected at multiple densities (if provided)
4. Modified APK is signed using bundled keystore
5. Final APK is saved to device storage for installation

**Important Constants:**
- Template package ID: `com.appy.generated.webapp.placeholder.app` (50 chars placeholder)
- Template app name: `AppyGeneratedWebApplicationPlaceholderNameHere`
- Debug keystore: `debug.p12`, password: `android`, alias: `androiddebugkey`

## Build Instructions

### Prerequisites
- JDK 17 or higher (JDK 21 recommended for consistency with CI)
- Android SDK with API 35
- Android Studio Arctic Fox or later (optional but recommended)

### Build Commands

**ALWAYS run these commands in order:**

```bash
# Grant execute permission (first time only)
chmod +x gradlew

# Debug build (recommended for testing)
./gradlew assembleDebug --no-daemon

# Release build (requires signing configuration)
./gradlew assembleRelease --no-daemon
```

**Build Process:**
1. The `template` module is built first: `./gradlew :template:assembleRelease`
2. Template APK is copied to `app/src/main/assets/base-web-template.apk` via `copyTemplateApk` task
3. Main app is built with the template bundled in assets
4. Output APK: `app/build/outputs/apk/debug/app-debug.apk` or `app/build/outputs/apk/release/app-release.apk`

**Build Times:**
- Clean debug build: ~60-90 seconds
- Incremental build: ~20-30 seconds
- Template rebuild alone: ~15-20 seconds

**Common Build Issues:**
- If template APK is not found in assets, run `./gradlew :template:assembleRelease` explicitly first
- If getting "SDK not found" errors, ensure `ANDROID_HOME` environment variable is set
- For "duplicate class" errors, run `./gradlew clean` before building

### Testing

At the time of writing, there are no automated tests in this repository. Manual testing is required:
1. Install the debug APK on an Android device/emulator (API 26+)
2. Launch Appy app
3. Enter a website URL (e.g., `https://example.com`)
4. Set custom app name and package ID
5. Optionally select a custom icon
6. Click "Generate APK"
7. Install the generated APK and verify the WebView loads the correct URL

## Project Layout

```
/
├── .github/
│   └── workflows/
│       ├── ci.yml              # CI build on push/PR to main
│       └── appy_release.yml    # Release build on version tags
├── app/                        # Main Appy application
│   ├── src/main/
│   │   ├── java/com/appy/
│   │   │   ├── MainActivity.kt              # Entry point, sets Compose theme
│   │   │   ├── ui/
│   │   │   │   ├── theme/AppyTheme.kt       # Material 3 Expressive theme
│   │   │   │   └── screens/HomeScreen.kt    # Main UI with form inputs
│   │   │   └── processor/
│   │   │       └── ApkProcessor.kt          # Core APK generation logic
│   │   ├── assets/
│   │   │   ├── debug.p12                    # Debug keystore (bundled)
│   │   │   └── base-web-template.apk        # WebView template (auto-copied)
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts                     # App module build config
│   └── proguard-rules.pro                   # ProGuard rules for release builds
├── template/                                # Pure Java WebView template
│   ├── src/main/
│   │   ├── java/com/webtemplate/
│   │   │   └── MainActivity.java            # WebView activity
│   │   ├── assets/
│   │   │   └── config.json                  # URL config placeholder
│   │   └── res/                             # Launcher icons and strings
│   └── build.gradle.kts                     # Template module build config
├── build.gradle.kts                         # Root project build config
├── settings.gradle.kts                      # Project modules configuration
├── gradle.properties                        # Gradle properties
└── README.md                                # Comprehensive project documentation

```

**Key Files for Changes:**
- APK generation logic: `app/src/main/java/com/appy/processor/ApkProcessor.kt`
- UI and user input: `app/src/main/java/com/appy/ui/screens/HomeScreen.kt`
- Theme customization: `app/src/main/java/com/appy/ui/theme/AppyTheme.kt`
- Template WebView: `template/src/main/java/com/webtemplate/MainActivity.java`
- Template config: `template/src/main/assets/config.json`

## CI/CD Pipelines

### CI Build (`.github/workflows/ci.yml`)
- **Trigger**: Push or PR to `main` branch
- **Job**: Builds debug APK using `./gradlew assembleDebug --no-daemon`
- **JDK**: 21 (Temurin distribution)
- **Output**: Uploads debug APK as artifact (`Appy-Debug-APK`)
- **Duration**: ~2-3 minutes

### Release Build (`.github/workflows/appy_release.yml`)
- **Trigger**: Push to version tags (`v*`) or manual workflow dispatch
- **Job**: Builds release APK using `./gradlew assembleRelease --no-daemon`
- **Signing**: Uses secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `ALIAS`)
- **Output**: Creates GitHub Release with signed APK
- **Duration**: ~3-4 minutes

**Required GitHub Secrets for Release:**
- `KEYSTORE_BASE64`: Base64-encoded release keystore file
- `KEYSTORE_PASSWORD`: Keystore and key password
- `ALIAS`: Key alias name

## Dependencies

**App Module (`app/build.gradle.kts`):**
- Jetpack Compose BOM + Material 3
- Coil (image loading)
- Zip4j (ZIP manipulation)
- apksig (APK signing)
- DataStore Preferences
- Navigation Compose

**Template Module (`template/build.gradle.kts`):**
- **ZERO external dependencies** - uses only Android framework APIs
- This is intentional to keep the template APK minimal

## Code Conventions

1. **Language**: Kotlin for app module, Java for template module
2. **Architecture**: Repository pattern (`ApkProcessor` acts as repository)
3. **UI**: Jetpack Compose with Material 3 Expressive theme
4. **Naming**: Use descriptive names, follow Android conventions
5. **Async**: Use Kotlin Coroutines and Flow for async operations
6. **Error Handling**: Return sealed class results (`ApkProcessingResult`) with Progress/Error states

## Known Issues

- Custom icons don't work properly: The icon injection feature is implemented but icons may not display correctly at all densities in the generated APKs

## Important Notes

- The template module MUST remain pure Java with zero dependencies
- Template APK is automatically built before the main app via `copyTemplateApk` task
- Template package ID is a long placeholder (50 chars) to allow custom package names up to 50 chars
- Binary manifest modification replaces the placeholder with user's package ID
- Config is injected via `assets/config.json` with manual JSON parsing (no Gson/Moshi)
- APKs are signed with debug keystore in debug builds, release keystore in release builds
- The build system uses `--no-daemon` flag in CI to avoid daemon issues

## Validation Steps

When making changes:
1. Run `./gradlew assembleDebug` to ensure code compiles
2. Install the APK on a device/emulator
3. Test the APK generation flow end-to-end
4. Verify the generated APK installs and loads the correct URL
5. Check CI build passes on GitHub Actions

Trust these instructions. Only search the codebase if information here is incomplete or found to be incorrect.
