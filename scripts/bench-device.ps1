param(
    [Parameter(Mandatory)] [string] $Device,
    [Parameter(Mandatory)] [string] $ModelId,
    [Parameter(Mandatory)] [string] $ModelFile,
    [ValidateSet('cpu', 'gpu')] [string] $Backend = 'cpu',
    [ValidateSet('off', 'default')] [string] $Thinking = 'off',
    [int] $Limit = 0,
    [int] $TimeoutMinutes = 120
)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$root = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $root 'bench\build\outputs\apk\debug\bench-debug.apk'
$pkg = 'app.bheem.bench'
$base = "/sdcard/Android/data/$pkg/files"
$name = "${Device}__${ModelId}__${Backend}__${Thinking}"

if (-not (Test-Path $ModelFile)) { throw "model file not found: $ModelFile" }
if (-not (Test-Path $apk)) { throw "build the APK first: .\gradlew.bat :bench:assembleDebug" }
$attached = & $adb devices | Select-String -Pattern "`tdevice$"
if (-not $attached) { throw 'no authorised device attached (check USB debugging)' }

& $adb install -r $apk | Out-Null
& $adb shell am force-stop $pkg
& $adb shell mkdir -p "$base/models" "$base/results"
$remoteModel = "$base/models/$(Split-Path -Leaf $ModelFile)"
$have = & $adb shell "ls $remoteModel 2>/dev/null"
if (-not $have) { & $adb push $ModelFile $remoteModel }

& $adb shell rm -f "$base/results/$name.done"
& $adb shell am start -n "$pkg/.BenchActivity" `
    --es model_id $ModelId --es model_file (Split-Path -Leaf $ModelFile) --es device_label $Device `
    --es backend $Backend --es thinking $Thinking --ei limit $Limit | Out-Null

$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 30
    if (& $adb shell "ls $base/results/$name.done 2>/dev/null") { break }
    if (-not (& $adb shell pidof $pkg)) { Write-Warning 'app process is gone; pulling partial results'; break }
}

$out = Join-Path $root 'eval\results'
New-Item -ItemType Directory -Force $out | Out-Null
& $adb pull "$base/results/$name.json" (Join-Path $out "$name.json")
Write-Host "result: $(Join-Path $out "$name.json")"
