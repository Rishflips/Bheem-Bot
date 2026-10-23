param(
    [Parameter(Mandatory)] [string] $Device,
    [Parameter(Mandatory)] [string] $ModelId,
    [Parameter(Mandatory)] [string] $ModelFile,
    [ValidateSet('cpu', 'gpu')] [string] $Backend = 'cpu',
    [ValidateSet('off', 'default')] [string] $Thinking = 'off',
    [int] $Limit = 0,
    [int] $RepeatFirst = 0,
    [ValidateSet('on', 'off')] [string] $Benchmark = 'on',
    [ValidateSet('off', 'on')] [string] $FreshEngine = 'off',
    [ValidateSet('all', 'en')] [string] $Language = 'all',
    [ValidateSet('full', 'one-shot', 'zero-shot', 'compact')] [string] $Prompt = 'full',
    [int] $TimeoutMinutes = 120
)
# adb writes progress to stderr, which Windows PowerShell 5.1 turns into terminating errors under 'Stop'; check exit codes instead.
$ErrorActionPreference = 'Continue'
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
if ($LASTEXITCODE -ne 0) { throw 'adb install failed' }
& $adb shell am force-stop $pkg
# The app must create models/ and results/ itself: a folder made through adb is owned by the shell user and the app cannot use it.
$modelsOwner = ((& $adb shell "stat -c %U $base/models 2>/dev/null") | Out-String).Trim()
if ($modelsOwner -eq 'shell') { & $adb shell rm -rf "$base/models" }
& $adb shell rm -rf "$base/results"
& $adb shell am start -n "$pkg/.BenchActivity" | Out-Null
Start-Sleep -Seconds 3
& $adb shell am force-stop $pkg
$remoteModel = "$base/models/$(Split-Path -Leaf $ModelFile)"
$localSize = (Get-Item $ModelFile).Length
$remoteSize = (& $adb shell "stat -c %s $remoteModel 2>/dev/null") -as [long]
if ($remoteSize -ne $localSize) {
    & $adb push $ModelFile $remoteModel
    if ($LASTEXITCODE -ne 0) { throw 'adb push failed' }
}

$extraArgs = @()
if ($FreshEngine -eq 'on') { $extraArgs += @('--es', 'fresh_engine', 'on') }

& $adb shell rm -f "$base/results/$name.done"
& $adb shell am start -n "$pkg/.BenchActivity" `
    --es model_id $ModelId --es model_file (Split-Path -Leaf $ModelFile) --es device_label $Device `
    --es backend $Backend --es thinking $Thinking --ei limit $Limit --ei repeat_first $RepeatFirst --es benchmark $Benchmark --es lang $Language --es prompt $Prompt @extraArgs | Out-Null

$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 30
    if (& $adb shell "ls $base/results/$name.done 2>/dev/null") { break }
    if (-not (& $adb shell pidof $pkg)) { Write-Warning 'app process is gone; pulling partial results'; break }
}

$out = Join-Path $root 'eval\results'
New-Item -ItemType Directory -Force $out | Out-Null
$errorPath = Join-Path $out "$name.error"
Remove-Item $errorPath -ErrorAction SilentlyContinue
& $adb pull "$base/results/$name.error" $errorPath | Out-Null
if (Test-Path $errorPath) { Write-Warning "the run reported an error; see $errorPath" }
& $adb pull "$base/results/$name.json" (Join-Path $out "$name.json")
if ($LASTEXITCODE -ne 0) {
    throw "no result file was produced; see $name.error in eval\results if it was pulled"
}
Write-Host "result: $(Join-Path $out "$name.json")"
