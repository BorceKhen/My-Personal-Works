<div align="center">

# My Personal Works

**Offline Android & Windows Utilities · Interactive UI Systems · Creative Tools**

<p>
  <img src="https://img.shields.io/badge/Android-Kotlin_%26_Compose-132A3A?style=for-the-badge&labelColor=0F1B2E&logo=android&logoColor=38BDF8" alt="Android Kotlin & Compose" />
  <img src="https://img.shields.io/badge/Desktop-Python_%26_PyQt6-132A3A?style=for-the-badge&labelColor=0F1B2E&logo=python&logoColor=F5C542" alt="Desktop Python & PyQt6" />
  <img src="https://img.shields.io/badge/Local--First-Zero_Cloud_Required-132A3A?style=for-the-badge&labelColor=0F1B2E&logo=shield&logoColor=67E8F9" alt="Local-First" />
</p>

</div>

---

## Quick Index

| Project | Platform | Highlights | Direct Download |
| :--- | :--- | :--- | :--- |
| [**ForgeFolio**](./ForgeFolio) | Android | Offline 4-way **PDF ↔ Image ↔ DOCX** studio with Candlelit Library Bookcase UI | [ForgeFolio.apk](./ForgeFolio/ForgeFolio.apk) |
| [**DeckCast**](./DeckCast) | Android & Windows | Offline LAN WebRTC screen streaming & remote control | [APK](./DeckCast/DeckCast.apk) · [Setup.exe](./DeckCast/DeckCast_Setup.exe) |
| [**FloatPaint**](./FloatPaint) | Windows | Real-time 2D/3D hand-gesture air painting (`MediaPipe` + `Three.js`) | [FloatPaint_Setup.exe](./FloatPaint/FloatPaint_Setup.exe) |
| [**Overlay Customization**](./Overlay%20Customization) | Windows | Interactive desktop screen overlay controller | [Setup.exe](./Overlay%20Customization/OverlayController_Setup.exe) · [Portable](./Overlay%20Customization/OverlayController.exe) |
| [**Image Compressor**](./Image%20Compressor) | Android | Native C++ NDK (`libjpeg-turbo`) fast image compression | [ImageCompressor.apk](./Image%20Compressor/ImageCompressor.apk) |

---

## Projects

### 📚 [ForgeFolio](./ForgeFolio)
A 100% offline 4-way Android document converter (**PDF → Image**, **Image → PDF**, **Word `.docx` → PDF**, and **PDF → Word `.docx`**) styled with a tactile library bookcase & editorial documentation theme, including an interactive lit/unlit candle study switch.
- **Platform**: Android (`minSdk 26`, `compileSdk 35`)
- **Tech Stack**: Kotlin, Jetpack Compose, Material 3, `pdfbox-android`, Apache POI (`poi-ooxml`), `PdfRenderer`, `ExifInterface`, Storage Access Framework
- **Download APK**: [ForgeFolio.apk](./ForgeFolio/ForgeFolio.apk)

### 🖥️📱 [DeckCast](./DeckCast)
A 100% offline/local-network multi-device screen streaming and remote control system supporting Android-to-Android, Windows-to-Android, and Android-to-Windows connections over LAN WebRTC (`iceServers = emptyList()`), with independent draggable/resizable floating overlay windows on Android.
- **Platform**: Android & Windows
- **Tech Stack**: Kotlin, WebRTC (`webrtc-sdk`), Ktor CIO WebSockets, NsdManager, AccessibilityService, Python, `aiortc`, PyQt6, `SendInput`, Inno Setup
- **Download**: [DeckCast.apk](./DeckCast/DeckCast.apk) (Android) · [DeckCast_Setup.exe](./DeckCast/DeckCast_Setup.exe) (Windows Installer)

### 🎨 [FloatPaint (AERO-BRUSH)](./FloatPaint)
A real-time 2D/3D hand-gesture air painting studio combining MediaPipe hand-landmark tracking, a One-Euro stabilized UDP-to-WebSocket bridge, and a WebGL/Three.js canvas with `.PNG`, `.OBJ`, and `.GLTF` export.
- **Platform**: Windows
- **Tech Stack**: Python, OpenCV, MediaPipe, Three.js, PyInstaller, Inno Setup
- **Download**: [FloatPaint_Setup.exe](./FloatPaint/FloatPaint_Setup.exe) (Windows Installer)

### 🖥️ [Overlay Customization](./Overlay%20Customization)
A Windows desktop application for screen overlay customization with interactive graphics, animations, and custom styling.
- **Platform**: Windows
- **Tech Stack**: Python, PyQt6, ctypes, PyInstaller, Inno Setup
- **Download**: [OverlayController_Setup.exe](./Overlay%20Customization/OverlayController_Setup.exe) (Windows Installer) · [OverlayController.exe](./Overlay%20Customization/OverlayController.exe) (Portable)

### 📸 [Image Compressor](./Image%20Compressor)
An Android application for compressing and processing images efficiently using native code (`libjpeg-turbo` / C++ NDK) and Kotlin Jetpack Compose.
- **Platform**: Android
- **Tech Stack**: Kotlin, Jetpack Compose, Android NDK, CMake, `libjpeg-turbo`
- **Download APK**: [ImageCompressor.apk](./Image%20Compressor/ImageCompressor.apk)
