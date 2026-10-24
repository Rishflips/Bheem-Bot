param(
    [string] $Recordings,
    [switch] $NoStart
)
# Installs the bench app, puts the recording list (manifest.jsonl) on the phone, allows the microphone and opens the recorder.
# Recordings already on the phone are kept, so this can be run again between sessions.
# adb writes progress to stderr, which Windows PowerShell 5.1 turns into terminating errors under 'Stop'; check exit codes instead.
$ErrorActionPreference = 'Continue'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$root = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $root 'bench\build\outputs\apk\debug\bench-debug.apk'
$pkg = 'app.bheem.bench'
$base = "/sdcard/Android/data/$pkg/files"
if (-not $Recordings) { $Recordings = Join-Path $root 'eval\recordings' }
$manifest = Join-Path $Recordings 'manifest.jsonl'

if (-not (Test-Path $apk)) { throw "build the APK first: .\gradlew.bat :bench:assembleDebug" }
if (-not (Test-Path $manifest)) { throw "manifest not found: $manifest (make it with .\gradlew.bat :eval:recordingSheets)" }
$attached = & $adb devices | Select-String -Pattern "`tdevice$"
if (-not $attached) { throw 'no authorised device attached (check USB debugging)' }

& $adb install -r $apk | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'adb install failed' }
& $adb shell am force-stop $pkg
# The app must create recordings/ itself: a folder made through adb is owned by the shell user and the app cannot use it.
# A shell-owned folder can only hold files pushed from the computer, never takes recorded on the phone.
$owner = ((& $adb shell "stat -c %U $base/recordings 2>/dev/null") | Out-String).Trim()
if ($owner -eq 'shell') { & $adb shell rm -rf "$base/recordings" }
& $adb shell am start -n "$pkg/.RecordActivity" | Out-Null
Start-Sleep -Seconds 3
& $adb shell am force-stop $pkg

& $adb push $manifest "$base/recordings/manifest.jsonl" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'adb push failed (manifest)' }
& $adb shell pm grant $pkg android.permission.RECORD_AUDIO
$onPhone = @((& $adb shell "ls $base/recordings/audio 2>/dev/null") | Where-Object { $_ -like '*.wav' }).Count
Write-Host "recording list is on the phone; $onPhone recordings are already there"
if (-not $NoStart) {
    & $adb shell am start -n "$pkg/.RecordActivity" | Out-Null
    Write-Host 'the recorder is open on the phone: pick a speaker, hold the button while reading, release when done'
}
Write-Host 'when the session is over, copy the files to the computer: .\scripts\pull-recordings.ps1'
