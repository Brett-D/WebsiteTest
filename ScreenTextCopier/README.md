# Screen Text Copier

An Android app that uses OCR (Optical Character Recognition) to copy text from anything visible on your phone screen via a floating overlay button.

## Features

- **Floating Capture Button** — A draggable overlay button that stays on top of all apps. Tap it to instantly capture and OCR the current screen content.
- **Clipboard History** — Long-press the floating button to expand a clipboard panel showing all previously captured text and image thumbnails.
- **Text Selection** — Long-press any item in the clipboard list to open a full-text view where you can highlight and copy just a portion of the recognized text.
- **Instant Copy** — Captured text is automatically copied to the system clipboard and also saved in the app's history.
- **ML Kit OCR** — Powered by Google ML Kit Text Recognition for fast, on-device text extraction.

## How It Works

1. **Start the service** from the main activity — the app requests overlay, notification, and screen-capture permissions.
2. A **floating button** appears on screen. You can drag it anywhere.
3. **Tap** the button → the app hides the overlay momentarily, captures the screen via MediaProjection, runs ML Kit OCR, and stores the result.
4. **Long-press** the button → a bottom-sheet-style clipboard panel slides up showing your history.
5. **Tap** an item in the list → copies the text to the system clipboard.
6. **Long-press** an item → opens a full-screen selectable-text overlay where you can highlight a portion and copy it.

## Project Structure

```
ScreenTextCopier/
├── app/
│   ├── build.gradle.kts          # App-level Gradle config
│   ├── proguard-rules.pro        # ProGuard rules for release builds
│   └── src/main/
│       ├── AndroidManifest.xml   # Permissions & service declarations
│       ├── java/com/screentextcopier/
│       │   ├── ScreenTextCopierApp.kt      # Application class
│       │   ├── MainActivity.kt             # Entry point & permission flow
│       │   ├── FloatingButtonService.kt    # Overlay button + clipboard UI
│       │   ├── ScreenCaptureService.kt     # MediaProjection screen capture
│       │   ├── OcrProcessor.kt             # ML Kit text recognition wrapper
│       │   ├── ClipboardAdapter.kt         # RecyclerView adapter for history
│       │   ├── ClipboardHistoryManager.kt  # Persistent clipboard storage
│       │   └── model/
│       │       └── ClipboardItem.kt        # Data class for clipboard entries
│       └── res/
│           ├── layout/                     # XML layouts
│           ├── drawable/                   # Vector icons & shape drawables
│           ├── values/                     # Colors, strings, themes
│           └── xml/                        # Network security config
├── build.gradle.kts              # Root Gradle config
├── settings.gradle.kts           # Gradle settings
├── gradle.properties             # Gradle properties
└── gradle/wrapper/               # Gradle wrapper config
```

## Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34 (compile SDK)
- Minimum SDK 26 (Android 8.0 Oreo)
- Kotlin 1.9.22+

## Building

1. Open the `ScreenTextCopier/` folder in Android Studio.
2. Sync Gradle and let dependencies download.
3. Build & run on a physical device or emulator (API 26+).

> **Note:** Screen capture (MediaProjection) works best on a physical device. Some emulators may not support it fully.

## Permissions

The app requires:
- **SYSTEM_ALERT_WINDOW** — to display the floating overlay button
- **FOREGROUND_SERVICE** — to keep the capture service alive
- **FOREGROUND_SERVICE_MEDIA_PROJECTION** — for screen capture
- **POST_NOTIFICATIONS** — for the persistent notification (Android 13+)

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| [ML Kit Text Recognition](https://developers.google.com/ml-kit/vision/text-recognition) | 16.0.0 | On-device OCR |
| [Gson](https://github.com/google/gson) | 2.10.1 | JSON serialization for clipboard history |
| [Coil](https://coil-kt.github.io/coil/) | 2.5.0 | Image loading for thumbnails |
| AndroidX Core KTX | 1.12.0 | Kotlin extensions |
| Material Components | 1.11.0 | Material Design UI components |
| RecyclerView | 1.3.2 | List UI for clipboard history |

## License

See [LICENSE](../LICENSE) for details.
