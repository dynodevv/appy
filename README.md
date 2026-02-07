# Appy

Turn any website into an APK, with a simple and easy-to-use UI.

## Features

- **On-Device APK Generation**: Convert any website URL into a standalone Android APK directly on your device
- **Customizable Apps**: Set custom app name, package ID, and app icon for your generated APK
- **Material 3 Expressive Design**: Modern UI with glass-effect TopAppBar, emphasized transitions, and extra-large corner radii
- **Binary Template Modification**: Uses pre-compiled WebView template for fast APK generation without Gradle
- **Automatic Signing**: APKs are signed and ready for installation

## Known issues

- Custom icons don't work
- Offline caching is experimental and may not work correctly on some advanced or dynamic websites

## Technical Overview

### Architecture

Appy uses a "Binary Template Modification" strategy to generate APKs without running a full Gradle build:

1. **Template APK**: A pre-compiled `base-web-template.apk` containing a WebView app is bundled in assets (built automatically from the `template` module)
2. **ZIP Manipulation**: Uses [Zip4j](https://github.com/srikanth-lingala/zip4j) to modify the template APK
3. **Config Injection**: Replaces `assets/config.json` with the user's URL and app configuration
4. **Icon Injection**: Replaces launcher icons at multiple densities with user-provided icon
5. **APK Signing**: Signs the modified APK using a bundled keystore

### Project Structure

```
├── app/                                  # Main Appy application
│   ├── src/main/
│   │   ├── java/com/appy/
│   │   │   ├── MainActivity.kt           # Main entry point
│   │   │   ├── ui/
│   │   │   │   ├── theme/
│   │   │   │   │   └── AppyTheme.kt      # M3 Expressive theme
│   │   │   │   └── screens/
│   │   │   │       └── HomeScreen.kt     # Main UI with inputs
│   │   │   └── processor/
│   │   │       └── ApkProcessor.kt       # APK generation logic
│   │   ├── assets/
│   │   │   ├── debug.jks                 # Debug keystore for signing
│   │   │   └── base-web-template.apk     # WebView template (auto-generated)
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
│
├── template/                             # WebView template module
│   ├── src/main/
│   │   ├── java/com/webtemplate/
│   │   │   └── MainActivity.kt           # WebView activity
│   │   ├── assets/
│   │   │   └── config.json               # URL configuration placeholder
│   │   └── res/
│   └── build.gradle.kts
│
└── settings.gradle.kts
```

## Building

### Prerequisites

- Android Studio Arctic Fox or later
- JDK 17 or higher
- Android SDK with API 35

### Build Commands

```bash
# Debug build (automatically builds template first)
./gradlew assembleDebug

# Release build (requires signing configuration)
./gradlew assembleRelease
```

The build system automatically:
1. Compiles the `template` module into `base-web-template.apk`
2. Copies it to `app/src/main/assets/`
3. Builds the main app with the template bundled

## CI/CD

The project includes a GitHub Actions workflow (`.github/workflows/appy_release.yml`) that:

- Builds the release APK on push to version tags (e.g., `v1.0.0`)
- Signs the APK using GitHub Secrets
- Creates a GitHub Release with the APK

### Required Secrets

- `KEYSTORE_BASE64`: Base64-encoded release keystore
- `KEYSTORE_PASSWORD`: Keystore password
- `ALIAS`: Key alias name

## Permissions

The app requires the following permissions:

- `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`: For saving generated APKs (Android 10 and below)
- `REQUEST_INSTALL_PACKAGES`: For installing generated APKs

## License

This project is open source. See LICENSE for details.
