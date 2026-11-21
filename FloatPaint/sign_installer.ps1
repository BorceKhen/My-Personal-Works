<#
.SYNOPSIS
    Authenticode-signs FloatPaint.exe and FloatPaint_Setup.exe using a .pfx certificate.
.EXAMPLE
    .\sign_installer.ps1 -PfxPath "C:\path\to\codesign.pfx" -Password "your-password"
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$PfxPath,

    [Parameter(Mandatory = $false)]
    [string]$Password = "",

    [Parameter(Mandatory = $false)]
    [string]$TimestampUrl = "http://timestamp.digicert.com"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $PfxPath)) {
    throw "Certificate file not found: $PfxPath"
}

$cert = if ($Password) {
    $secPwd = ConvertTo-SecureString -String $Password -Force -AsPlainText
    New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($PfxPath, $secPwd)
} else {
    New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($PfxPath)
}

$targets = @(
    "dist\FloatPaint\FloatPaint.exe",
    "installer_output\FloatPaint_Setup.exe"
)

foreach ($file in $targets) {
    if (Test-Path $file) {
        Write-Host "Signing $file ..."
        $result = Set-AuthenticodeSignature -FilePath $file -Certificate $cert -TimestampServer $TimestampUrl -HashAlgorithm SHA256
        Write-Host "  Status: $($result.Status) ($($result.StatusMessage))"
    } else {
        Write-Warning "Skipping missing file: $file"
    }
}
