# -*- mode: python ; coding: utf-8 -*-

from PyInstaller.utils.hooks import collect_all

av_datas, av_binaries, av_hiddenimports = collect_all("av")
rtc_datas, rtc_binaries, rtc_hiddenimports = collect_all("aiortc")
zc_datas, zc_binaries, zc_hiddenimports = collect_all("zeroconf")

datas = [("assets/icon.ico", "assets")] + av_datas + rtc_datas + zc_datas
binaries = av_binaries + rtc_binaries + zc_binaries
hiddenimports = [
    "deckcast_win",
    "deckcast_win.capture",
    "deckcast_win.controller_client",
    "deckcast_win.discovery",
    "deckcast_win.host_server",
    "deckcast_win.input_inject",
    "deckcast_win.protocol",
    "deckcast_win.ui_app",
    "websockets",
    "mss",
] + av_hiddenimports + rtc_hiddenimports + zc_hiddenimports

a = Analysis(
    ["main.py"],
    pathex=["."],
    binaries=binaries,
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=["torch", "tensorflow", "mediapipe", "cv2", "scipy", "matplotlib", "tkinter", "pygame"],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="DeckCast",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=False,
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
    name="DeckCast",
)
