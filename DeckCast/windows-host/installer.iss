; Inno Setup Script for DeckCast Windows Studio
#define MyAppName "DeckCast"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "BorceKhen"
#define MyAppURL "https://github.com/BorceKhen/My-Personal-Works"
#define MyAppExeName "DeckCast.exe"

[Setup]
AppId={{6A92E31D-4C5E-4B18-9F40-2D7A8E613C21}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={localappdata}\Programs\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
InfoBeforeFile=installer_notice.txt

; Per-user install (no Admin / UAC elevation required)
PrivilegesRequired=lowest
OutputDir=installer_output
OutputBaseFilename=DeckCast_Setup
SetupIconFile=assets\icon.ico
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern

; Uninstall metadata
UninstallDisplayIcon={app}\{#MyAppExeName}
UninstallDisplayName={#MyAppName}
VersionInfoVersion={#MyAppVersion}.0
VersionInfoCompany={#MyAppPublisher}
VersionInfoDescription={#MyAppName} Windows Setup Installer
VersionInfoProductName={#MyAppName}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "dist\DeckCast\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "assets\icon.ico"; DestDir: "{app}\assets"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\assets\icon.ico"
Name: "{autoprograms}\Uninstall {#MyAppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\assets\icon.ico"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent
