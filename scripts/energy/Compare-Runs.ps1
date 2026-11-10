#requires -Version 7.0
param(
 [Parameter(Mandatory)][string[]]$Baseline,
 [Parameter(Mandatory)][string[]]$Candidate,
 [Parameter(Mandatory)][string]$OutputDirectory
)
$ErrorActionPreference='Stop'
$items=@()
foreach($group in @(@{name='baseline';paths=$Baseline},@{name='candidate';paths=$Candidate})){
 foreach($path in $group.paths){
  $full=[IO.Path]::GetFullPath($path)
  if($full -notmatch '^E:[\\/]'){throw 'Input paths must stay on E:'}
  $r=Get-Content (Join-Path $full 'report.json') -Raw | ConvertFrom-Json
  $m=Get-Content (Join-Path $full 'manifest.json') -Raw | ConvertFrom-Json
  if(!$r.measurementChecksPassed){throw "Unsuitable run $full : $($r.issues -join ', ')"}
  $items += [pscustomobject]@{group=$group.name;directory=$full;version=$m.appVersion;seconds=$m.requestedSeconds;brightness=$m.brightness;autoBrightness=$m.autoBrightness;config=(Get-FileHash (Join-Path $full 'config.pbtxt')).Hash;cpuSeconds=[double]$r.cpuSeconds;chargeMah=[double]$r.wholePhoneChargeMah;temperatureStartC=$r.temperatureStartC}
 }
}
if(@($items.directory | Select-Object -Unique).Count -ne $items.Count){throw 'A run cannot be reused in the comparison'}
foreach($key in @('seconds','brightness','autoBrightness','config')){
 if(@($items | ForEach-Object {$_.$key} | Select-Object -Unique).Count -ne 1){throw "Mismatched condition: $key"}
}
if(@($items | Where-Object {$_.autoBrightness -ne '0'}).Count){throw 'Disable automatic brightness before comparable runs'}
$summary=@()
foreach($group in @('baseline','candidate')){
 $rows=@($items | Where-Object group -eq $group)
 if(@($rows.version | Select-Object -Unique).Count -ne 1){throw "Mixed app versions in $group"}
 $cpu=$rows.cpuSeconds | Measure-Object -Average -Minimum -Maximum
 $charge=$rows.chargeMah | Measure-Object -Average -Minimum -Maximum
 $summary += [pscustomobject]@{group=$group;version=$rows[0].version;runs=$rows.Count;cpuMeanSeconds=$cpu.Average;chargeMeanMah=$charge.Average;chargeMinMah=$charge.Minimum;chargeMaxMah=$charge.Maximum}
}
$out=[IO.Path]::GetFullPath($OutputDirectory)
if($out -notmatch '^E:[\\/]'){throw 'Output must stay on E:'}
New-Item -ItemType Directory -Force $out | Out-Null
$items | Export-Csv (Join-Path $out 'runs.csv') -NoTypeInformation -Encoding utf8
$summary | Export-Csv (Join-Path $out 'comparison.csv') -NoTypeInformation -Encoding utf8
[ordered]@{groups=$summary;minimumThreePerVersion=($Baseline.Count -ge 3 -and $Candidate.Count -ge 3);conclusion='Descriptive comparison only. Verify matched workload, bytes, starting temperature/charge and background activity; averages alone do not establish an energy improvement.'} | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $out 'comparison.json') -Encoding utf8
$summary | Format-Table -AutoSize
