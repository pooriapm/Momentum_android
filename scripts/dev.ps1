# One-step dev loop: make sure a device is up, install the debug build incrementally, open the app.
#   .\scripts\dev.ps1            # uses a connected phone if any, else boots the light emulator
#   .\scripts\dev.ps1 -Logs      # also follows the app's log (errors and MomentumApi)
param(
    [string]$Avd = "Momentum_Light",
    [switch]$Logs
)
$ErrorActionPreference = "Stop"
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$HOME\Android\Sdk" }
$adb = "$sdk\platform-tools\adb.exe"
$root = Split-Path $PSScriptRoot -Parent

$devices = & $adb devices | Select-String "\tdevice$"
if (-not $devices) {
    Write-Host "No device; starting emulator $Avd (quick boot)..."
    # Quick boot resumes the saved snapshot in seconds; no audio, host GPU.
    Start-Process "$sdk\emulator\emulator.exe" -ArgumentList "-avd $Avd -no-audio -no-boot-anim -gpu host -feature -Uwb" -WindowStyle Minimized
    & $adb wait-for-device
    while ((& $adb shell getprop sys.boot_completed 2>$null) -ne "1") { Start-Sleep -Milliseconds 500 }
}

# installDebug only rebuilds what changed and pushes the APK in one go.
Push-Location $root
try { & .\gradlew.bat installDebug --console=plain -q } finally { Pop-Location }
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $adb shell am start -n com.pooriapm.momentum/.MainActivity | Out-Null
Write-Host "Momentum is running."
if ($Logs) {
    & $adb logcat -c
    & $adb logcat -v time "MomentumApi:*" "MomentumOnboarding:*" "AndroidRuntime:E" "*:S"
}
