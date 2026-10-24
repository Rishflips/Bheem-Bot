param(
    [Parameter(Mandatory)] [string] $Device,
    [Parameter(Mandatory)] [string] $ModelId,
    [Parameter(Mandatory)] [string] $ModelFile,
    [ValidateSet('line', 'line2')] [string] $Prompt = 'line2'
)
# Runs the full English set and the assistant set on the phone, GPU and CPU, with the shipped prompt, then rebuilds the report.
# Manual, before each tagged release. The new result files land in eval\results; copy the ones the gate should keep into
# eval\golden in a commit that says why (see eval\golden\README.md).
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
foreach ($backend in 'gpu', 'cpu') {
    foreach ($set in 'standard', 'assistant') {
        $label = "$Device-$Prompt"
        & (Join-Path $PSScriptRoot 'bench-device.ps1') -Device $label -ModelId $ModelId -ModelFile $ModelFile -Backend $backend -Prompt $Prompt -Language en -DataSet $set
    }
}
& (Join-Path $root 'gradlew.bat') :eval:report
Write-Host 'done: docs\PHASE0-REPORT.md rebuilt; copy the result files you want the gate to keep into eval\golden'
