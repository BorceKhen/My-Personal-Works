# DeckCast

100% offline/local-network multi-device screen streaming and remote control application supporting **Android-to-Android** and **Windows-to-Android** connections over LAN/hotspot WebRTC (`iceServers = emptyList()`).

## Repository Structure

```text
DeckCast/
├── README.md
├── shared/
│   ├── protocol-schema.json              # Unified JSON Schema for WebSocket signaling & DataChannel "input"
│   └── PROTOCOL.md                       # Wire protocol & mDNS/ICE specification
├── android-app/                          # Deliverable 3 (Kotlin, compileSdk 35, minSdk 26)
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradle.properties
│   ├── local.properties
│   ├── gradlew / gradlew.bat
│   ├── gradle/wrapper/
│   └── app/
│       ├── build.gradle.kts              # io.github.webrtc-sdk:android + Ktor CIO WebSockets
│       ├── proguard-rules.pro
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── res/
│           │   ├── drawable/
│           │   ├── layout/
│           │   │   ├── activity_main.xml
│           │   │   └── overlay_window.xml
│           │   ├── values/
│           │   │   ├── strings.xml
│           │   │   └── themes.xml
│           │   └── xml/
│           │       └── accessibility_service_config.xml
│           └── java/com/deckcast/app/
│               ├── model/
│               │   ├── DiscoveredHost.kt
│               │   └── ProtocolModels.kt
│               ├── network/
│               │   ├── LocalSignalingServer.kt
│               │   ├── NsdDiscoveryManager.kt
│               │   └── SignalingClient.kt
│               ├── webrtc/
│               │   ├── RemoteDeviceSession.kt
│               │   ├── TextureViewRenderer.kt
│               │   └── WebRtcFactoryProvider.kt
│               ├── controller/
│               │   ├── OverlayControllerService.kt
│               │   ├── OverlayManager.kt
│               │   └── SessionManager.kt
│               ├── host/
│               │   ├── RemoteAccessibilityService.kt
│               │   └── ScreenHostService.kt
│               └── ui/
│                   └── MainActivity.kt
└── windows-host/                         # Deliverable 4 (Python 3.11+ Host & Controller)
    ├── README.md
    ├── requirements.txt
    ├── main.py
    ├── deckcast_win/
    │   ├── capture.py                    # dxcam / mss ScreenVideoTrack (720p @ 60fps)
    │   ├── controller_client.py          # Windows Controller (Android -> Windows viewer)
    │   ├── discovery.py                  # zeroconf _remotedisplay._tcp. broadcaster & browser
    │   ├── host_server.py                # websockets signaling + aiortc Host (Windows -> Android)
    │   ├── input_inject.py               # Win32 SendInput mouse & keyboard injector
    │   ├── protocol.py                   # Shared JSON signaling & DataChannel helpers
    │   └── ui_app.py                     # PyQt6 Control Center & multi-window viewer
    └── tests/
        └── test_windows_host.py
```

## Implementation Phases

1. **Deliverables 1–3 (Completed & Gradle-verified)**:
   - Repository layout, shared JSON signaling & input protocol schema, and complete Android Controller + Host implementation.
2. **Deliverable 4 (Completed & Verified)**:
   - `/windows-host` in Python 3.11+ (`aiortc`, `mss`/`dxcam`, `websockets`, `zeroconf`, Win32 `SendInput`, and `PyQt6` viewer supporting both **Windows → Android** and **Android → Windows**).
