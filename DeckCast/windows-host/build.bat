@echo off
setlocal
cd /d "%~dp0"

echo ==============================================
echo [1/2] Building DeckCast Windows Application...
echo ==============================================
pyinstaller --noconfirm --clean DeckCast.spec
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] PyInstaller build failed!
    exit /b %ERRORLEVEL%
)

echo.
echo ==============================================
echo [2/2] Compiling DeckCast_Setup.exe Installer...
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
echo [SUCCESS] DeckCast Windows build complete!
echo - App Folder       : dist\DeckCast\DeckCast.exe
echo - Windows Installer: installer_output\DeckCast_Setup.exe
