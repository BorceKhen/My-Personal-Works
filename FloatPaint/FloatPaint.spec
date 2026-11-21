# -*- mode: python ; coding: utf-8 -*-

from PyInstaller.utils.hooks import collect_all

mp_datas, mp_binaries, mp_hiddenimports = collect_all("mediapipe")
cv2_datas, cv2_binaries, cv2_hiddenimports = collect_all("cv2")

datas = [
    ("frontend", "frontend"),
    ("models/hand_landmarker.task", "models"),
    ("node_modules/three/build/three.module.js", "node_modules/three/build"),
    ("node_modules/three/examples/jsm/controls/OrbitControls.js", "node_modules/three/examples/jsm/controls"),
    ("node_modules/three/examples/jsm/exporters/OBJExporter.js", "node_modules/three/examples/jsm/exporters"),
    ("node_modules/three/examples/jsm/exporters/GLTFExporter.js", "node_modules/three/examples/jsm/exporters"),
]
datas += mp_datas + cv2_datas

binaries = mp_binaries + cv2_binaries

hiddenimports = [
    "bridge",
    "tracker",
    "backend",
    "backend.depth",
    "backend.filters",
    "backend.gesture_engine",
    "backend.udp_telemetry",
] + mp_hiddenimports + cv2_hiddenimports

a = Analysis(
    ["run.py"],
    pathex=["."],
    binaries=binaries,
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=["PyQt6", "PyQt5", "PySide6", "torch", "tensorflow", "IPython", "jupyter"],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="FloatPaint",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=True,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon="assets/icon.ico",
    version="version_info.txt",
)

coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=False,
    upx_exclude=[],
    name="FloatPaint",
)
