#requires -Version 7.0
param([string]$OutputRoot=(Join-Path $PSScriptRoot '../../.local/energy-tests'))
$ErrorActionPreference='Stop'
if([IO.Path]::GetFullPath($OutputRoot) -notmatch '^E:[\\/]'){throw 'Test data must stay on E:'}
$root=Join-Path $OutputRoot ([guid]::NewGuid().ToString('N'))
$paths=@()
foreach($name in @('a','b')){
 $path=Join-Path $root $name
 New-Item -ItemType Directory -Force $path | Out-Null
 @{measurementChecksPassed=$true;cpuSeconds=10;wholePhoneChargeMah=20;temperatureStartC=30} | ConvertTo-Json | Set-Content (Join-Path $path 'report.json')
 @{appVersion="fixture-$name";requestedSeconds=300;brightness='100';autoBrightness='0'} | ConvertTo-Json | Set-Content (Join-Path $path 'manifest.json')
 'synthetic-test-config' | Set-Content (Join-Path $path 'config.pbtxt')
 $paths += $path
}
function Expect-Rejection([scriptblock]$Action,[string]$Expected){
 try {& $Action | Out-Null} catch {if($_.Exception.Message -like $Expected){return};throw}
 throw "Expected rejection: $Expected"
}
$compare=Join-Path $PSScriptRoot 'Compare-Runs.ps1'
& $compare -Baseline $paths[0] -Candidate $paths[1] -OutputDirectory (Join-Path $root 'valid') | Out-Null
$result=Get-Content (Join-Path $root 'valid/comparison.json') -Raw | ConvertFrom-Json
if($result.minimumThreePerVersion -or $result.groups.Count -ne 2 -or $result.groups[0].chargeMeanMah -ne 20){throw 'Invalid descriptive summary'}
Expect-Rejection {& $compare -Baseline $paths[0] -Candidate $paths[0] -OutputDirectory (Join-Path $root 'duplicate')} 'A run cannot be reused*'
'different-config' | Set-Content (Join-Path $paths[1] 'config.pbtxt')
Expect-Rejection {& $compare -Baseline $paths[0] -Candidate $paths[1] -OutputDirectory (Join-Path $root 'mismatch')} 'Mismatched condition: config'
'synthetic-test-config' | Set-Content (Join-Path $paths[1] 'config.pbtxt')
$report=Get-Content (Join-Path $paths[1] 'report.json') -Raw | ConvertFrom-Json
$report.measurementChecksPassed=$false
$report | ConvertTo-Json | Set-Content (Join-Path $paths[1] 'report.json')
Expect-Rejection {& $compare -Baseline $paths[0] -Candidate $paths[1] -OutputDirectory (Join-Path $root 'bad-quality')} 'Unsuitable run*'
'PASS: valid summary, repeat count, duplicate rejection, config mismatch, invalid-data rejection. Synthetic fixtures only.'
