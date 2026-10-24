param(
    [Parameter(Mandatory)] [string] $Device,
    [Parameter(Mandatory)] [string] $ModelId,
    [Parameter(Mandatory)] [string] $ModelFile,
    [ValidateSet('line', 'line2')] [string] $Prompt = 'line2'
)
# Runs the full English set and the assistant set on the phone, GPU and CPU, with the shipped prompt, then builds a report
# from only those four result files. Manual, before each tagged release. Copy the result files the gate should keep into
# eval\golden in a commit that says why (see eval\golden\README.md), and update eval\gate-data-pins.json if the data changed.
# adb and Gradle write progress to stderr, which Windows PowerShell 5.1 turns into terminating errors under 'Stop'; exit codes are checked instead.
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $root 'gradlew.bat'

& $gradle ':bench:assembleDebug'
if ($LASTEXITCODE -ne 0) { throw 'building the bench app failed' }

$label = "$Device-$Prompt"
$release = Join-Path $root "eval\results-release\$label"
Remove-Item $release -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $release | Out-Null

foreach ($backend in 'gpu', 'cpu') {
    foreach ($set in 'standard', 'assistant') {
        & (Join-Path $PSScriptRoot 'bench-device.ps1') -Device $label -ModelId $ModelId -ModelFile $ModelFile -Backend $backend -Prompt $Prompt -Language en -DataSet $set
        $suffix = if ($set -eq 'assistant') { '__assistant' } else { '' }
        $file = Join-Path $root "eval\results\${label}__${ModelId}__${backend}__off$suffix.json"
        if (-not (Test-Path $file)) { throw "no result file for $backend $set" }
        Copy-Item $file $release
    }
}

$expected = if ($Prompt -eq 'line2') { (Select-String -Path (Join-Path $root 'agent-core\src\main\kotlin\app\bheem\core\planner\PlannerPrompt.kt') -Pattern 'VERSION = "([^"]+)"').Matches[0].Groups[1].Value } else { 'proposer-v3.5-line' }
foreach ($f in Get-ChildItem $release -Filter '*.json') {
    $version = ((Get-Content $f.FullName -Raw -Encoding UTF8) | ConvertFrom-Json).promptVersion
    $complete = ((Get-Content $f.FullName -Raw -Encoding UTF8) | ConvertFrom-Json).complete
    if (-not $complete) { throw "$($f.Name) is a partial run" }
    if ($version -ne $expected) { throw "$($f.Name) was recorded with prompt $version, expected $expected (the line2 version is read from PlannerPrompt.kt; a v3.5 run expects proposer-v3.5-line)" }
}

& $gradle ':eval:report' "-PreportDir=$release" "-PreportOut=$(Join-Path $release 'REPORT.md')"
if ($LASTEXITCODE -ne 0) { throw 'building the report failed' }
Write-Host "done: $release\REPORT.md (four results, prompt $expected). Copy what the gate should keep into eval\golden."
