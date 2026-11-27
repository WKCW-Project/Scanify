# Scanify

[![Build APK](https://github.com/WKCW-Project/Scanify/actions/workflows/build.yml/badge.svg)](https://github.com/WKCW-Project/Scanify/actions/workflows/build.yml)

A lightweight Android document scanner built with Kotlin and Jetpack Compose. Scan a page with your camera, adjust its corners, apply a filter, and save it as a PDF or JPG.

## Features

- **Live document detection** with a visible guide and corner overlay
- **Auto capture** that fires when the detected document is stable inside the guide
- **Auto detect / auto capture toggles** in Settings
- **Manual crop** fallback when detection fails
- **Rotate** and **Original / Gray / B&W** filters on the preview
- **Multi-page** scanning with page count tracking
- **PDF and JPG export** to `Downloads/Scanify`
- **Local scan history** with thumbnails; JPG and PDF entries open with the correct MIME type
- **Stable signed CI builds** delivered as a single debug APK to Telegram

## Tech stack

| Area | Choice |
| --- | --- |
| Language | Kotlin 2.4.10 |
| UI | Jetpack Compose, COUI 17 (ColorOS 17 design) |
| Navigation | `coui-nav` (COUI page transitions & predictive back) |
| Camera | CameraX 1.3.4 |
| Document processing | OpenCV 4.9.0 |
| Architecture | Android ViewModel + Compose state |
| Build | AGP 9.3.1, Gradle 9.6.1, JDK 21 |
| SDK | `compileSdk` / `targetSdk` 37, `minSdk` 29 (COUI 17 blur on Android 13+) |
| ABI | `arm64-v8a` only (native libs use legacy packaging to keep the APK small) |

## Getting started

### Requirements

- Android Studio with AGP 9.3.1 support
- JDK 21
- Android SDK 37
- An `arm64-v8a` device or emulator with a camera

### Build locally

```bash
git clone https://github.com/WKCW-Project/Scanify.git
cd Scanify
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

> Local builds without CI signing use the default Android debug key. CI builds use a dedicated key so updates install in place.

## CI / delivery

The [`Build APK`](.github/workflows/build.yml) workflow runs on pushes to `main` / `master`, `v*` tags, pull requests, and manual dispatch.

For non-PR events it:

1. Decodes the signing keystore from secrets.
2. Builds `:app:assembleDebug`.
3. Verifies the APK certificate contains `CN=Scanify`.
4. Uploads the APK as a GitHub Actions artifact.
5. Sends one `app-debug.apk` via Telegram `sendDocument` with the caption:

   ```text
   New CI from <owner/repo>

   Commit message:
   <commit subject>
   ```

6. Attaches the APK to a GitHub Release when the trigger is a `v*` tag.

Pull requests build with the default debug signature and skip the Telegram step because fork secrets are unavailable. The workflow also fails early if the APK exceeds 48 MB, leaving headroom under the 50 MB Bot API file limit.

### Required GitHub secrets

| Secret | Purpose |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | Base64-encoded JKS keystore |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Signing key alias |
| `SIGNING_KEY_PASSWORD` | Signing key password |
| `TELEGRAM_BOT_TOKEN` | Bot token used for `sendDocument` |
| `TELEGRAM_CHAT_ID` | Destination chat ID |

If replacing an earlier unsigned or differently signed debug build, uninstall it once before installing the CI-signed APK.

## Project structure

```text
app/src/main/java/com/wkcw/scanify/
├── MainActivity.kt          # coui-nav back stack and edge-to-edge setup
├── ScanViewModel.kt         # Screen state, capture pipeline, exports, history, prefs
├── ui/
│   ├── CouiBlur.kt          # COUI 17 page backdrop and material blur bridge
│   ├── CouiPage.kt          # Reusable COUI collapsing top bar page scaffolds
│   ├── CameraScreen.kt      # CameraX preview, live guide overlay, auto capture
│   ├── CropScreen.kt        # Draggable document corners
│   ├── ResultScreen.kt      # Rotate, filter, save, add page
│   ├── HomeScreen.kt        # Scan history, top-bar search, and filter chips
│   ├── SettingsScreen.kt    # Scanning, appearance, and COUI blur toggles
│   ├── AboutScreen.kt       # App version info and repository link
│   └── theme/Theme.kt       # COUI 17 Green theme controller
├── scanner/
│   ├── DocumentScanner.kt   # OpenCV multi-pass detection, warp, rotate, filters
│   ├── ImageExporter.kt     # JPG export via MediaStore
│   └── PdfExporter.kt       # PDF export via MediaStore
└── history/ScanHistory.kt   # Scan records and thumbnails
```

## Credits

- [**COUI**](https://github.com/suqi8/coui) by [@suqi8](https://github.com/suqi8) — ColorOS 17 / COUI 17 Compose Multiplatform UI components, preferences, material blur, icons, squircle shapes, and `coui-nav`.
- [**OpenCV**](https://opencv.org/) — Document edge detection, perspective correction, rotation, and image filters.
- [**AndroidX CameraX**](https://developer.android.com/training/camerax) — Live camera preview, frame analysis, and low-latency capture.
- [**Jetpack Compose**](https://developer.android.com/compose) — Declarative Android UI toolkit.

## Contributing

Keep changes focused and commit them separately (for example, one commit per feature or fix). CI is the verification path for builds and Telegram delivery.
