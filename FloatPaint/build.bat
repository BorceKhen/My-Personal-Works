@echo off
setlocal
cd /d "%~dp0"

echo ==============================================
echo [1/2] Building FloatPaint Standalone Bundle...
echo ==============================================
pyinstaller --noconfirm --clean FloatPaint.spec
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] PyInstaller build failed!
    exit /b %ERRORLEVEL%
)

echo.
echo ==============================================
echo [2/2] Compiling Windows Setup Installer...
echo ==============================================
set "ISCC=C:\Users\%USERNAME%\AppData\Local\Programs\Inno Setup 6\ISCC.exe"
if not exist "%ISCC%" (
    echo [ERROR] Inno Setup 6 ISCC.exe not found at "%ISCC%".
    exit /b 1
)
"%ISCC%" installer.iss
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Inno Setup compilation failed!
    exit /b %ERRORLEVEL%
)

echo.
echo [SUCCESS] Build complete!
echo - App Folder       : dist\FloatPaint\FloatPaint.exe
echo - Windows Installer: installer_output\FloatPaint_Setup.exe
echo.
echo Tip: To remove Windows SmartScreen "Unknown publisher" warnings on
echo other PCs, sign the installer with a trusted CA .pfx certificate:
echo   powershell -ExecutionPolicy Bypass -File sign_installer.ps1 -PfxPath your_cert.pfx
