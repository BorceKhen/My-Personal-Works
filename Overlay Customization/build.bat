@echo off
echo ==============================================
echo Building Overlay Controller Standalone Exe...
echo ==============================================
pyinstaller --noconsole --onefile --clean --icon="icon.ico" --add-data "style.css;." --add-data "icon.png;." --name "OverlayController" main.py

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] PyInstaller compilation failed!
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ==============================================
echo Compiling Windows Setup Installer (Inno Setup)...
echo ==============================================
"C:\Users\%USERNAME%\AppData\Local\Programs\Inno Setup 6\ISCC.exe" installer.iss

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Inno Setup compilation failed!
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ==============================================
echo Creating Zip Archive for Google Drive / Web Distribution...
echo ==============================================
powershell -Command "Compress-Archive -Path '.\installer_output\OverlayController_Setup.exe' -DestinationPath '.\installer_output\OverlayController_Setup_v1.0.zip' -Force"

echo.
echo [SUCCESS] Everything built successfully!
echo - Standalone Portable Exe: dist\OverlayController.exe
echo - Windows Setup Installer: installer_output\OverlayController_Setup.exe
echo - Distribution Zip Archive: installer_output\OverlayController_Setup_v1.0.zip
pause

