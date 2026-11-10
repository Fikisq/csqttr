#requires -Version 7.0
param(
 [Parameter(Mandatory)][string]$RunDirectory,
 [string]$Analyzer='E:/csqtt-deps/perfetto/trace_processor_shell.exe'
)
$ErrorActionPreference='Stop'
foreach($path in @($RunDirectory,$Analyzer)){if([IO.Path]::GetFullPath($path) -notmatch '^E:[\\/]'){throw 'Host paths must remain on E:'}}
$run=[IO.Path]::GetFullPath($RunDirectory)
$manifest=Get-Content (Join-Path $run 'manifest.json') -Raw | ConvertFrom-Json
$trace=Join-Path $run 'trace.perfetto-trace'
$package=$manifest.package
if($package -notmatch '^[a-zA-Z0-9_.]+$'){throw 'Invalid package in manifest'}
function Query([string]$Sql,[string]$Name){
 $csv=& $Analyzer query --quiet --format csv $trace $Sql 2>(Join-Path $run "$Name-analyzer.txt")
 if($LASTEXITCODE -ne 0){throw "Trace query failed: $Name"}
 $csv | Set-Content (Join-Path $run "$Name.csv") -Encoding utf8
 @($csv | ConvertFrom-Csv)
}
$bounds=@(Query 'SELECT (end_ts-start_ts)/1e9 seconds FROM trace_bounds' 'duration')
$duration=[double]::Parse($bounds[0].seconds,[Globalization.CultureInfo]::InvariantCulture)
$cpuSql=@"
WITH parts AS (
 SELECT p.upid,p.pid,p.name,t.name counter_name,
        MIN(c.ts) first_ts,MAX(c.ts) last_ts,COUNT(*) samples,
        (MAX(c.value)-MIN(c.value))/1e9 seconds
 FROM counter c JOIN process_counter_track t ON t.id=c.track_id
 JOIN process p ON p.upid=t.upid
 WHERE t.name IN ('runtime.user_ns','runtime.kernel_ns')
 AND p.uid IN (SELECT uid FROM process WHERE name='$package')
 GROUP BY p.upid,p.pid,p.name,t.id
)
SELECT upid,pid,name,SUM(seconds) cpu_seconds,
 MIN((last_ts-first_ts)/1e9) covered_seconds,MIN(samples) samples,
 COUNT(*) runtime_counters FROM parts GROUP BY upid,pid,name
"@
$cpu=@(Query $cpuSql 'cpu')
$powerSql=@'
SELECT t.id,t.name,t.type,COUNT(*) samples,
 (MAX(c.ts)-MIN(c.ts))/1e9 covered_seconds,
 (SELECT value FROM counter WHERE track_id=t.id ORDER BY ts LIMIT 1) first_value,
 (SELECT value FROM counter WHERE track_id=t.id ORDER BY ts DESC LIMIT 1) last_value
FROM counter c JOIN counter_track t ON t.id=c.track_id
WHERE t.type IN ('battery_counter','power_rails') GROUP BY t.id,t.name,t.type
'@
$power=@(Query $powerSql 'power')
$health=@(Query "SELECT name,severity,value FROM stats WHERE value>0 AND severity IN ('error','data_loss')" 'health')
$issues=[Collections.Generic.List[string]]::new()
if($manifest.smoke){$issues.Add('smoke_only')}
if($manifest.emulator){$issues.Add('emulator_not_real_energy')}
if($manifest.before.powered -or $manifest.after.powered){$issues.Add('charging_at_boundary')}
if($manifest.status -ne 'captured'){$issues.Add('capture_not_completed')}
if($duration -lt 0.95*$manifest.requestedSeconds){$issues.Add('trace_too_short')}
if($health.Count){$issues.Add('trace_health_errors')}
if(!$cpu.Count){$issues.Add('target_cpu_missing')}
if(!@($cpu | Where-Object {$_.name -match 'libclient\.so'}).Count){$issues.Add('native_tunnel_process_missing')}
$totalCpu=0.0
foreach($row in $cpu){
 $totalCpu += [double]::Parse($row.cpu_seconds,[Globalization.CultureInfo]::InvariantCulture)
 if([int]$row.samples -lt 2 -or [int]$row.runtime_counters -ne 2 -or [double]::Parse($row.covered_seconds,[Globalization.CultureInfo]::InvariantCulture) -lt 0.8*$duration){$issues.Add('insufficient_cpu_coverage')}
}
$charge=@($power | Where-Object {$_.name -eq 'batt.charge_uah'})
$mah=$null
if($charge.Count -ne 1 -or [int]$charge[0].samples -lt 2){$issues.Add('battery_charge_missing')}
else {
 $first=[double]::Parse($charge[0].first_value,[Globalization.CultureInfo]::InvariantCulture)
 $last=[double]::Parse($charge[0].last_value,[Globalization.CultureInfo]::InvariantCulture)
 $mah=($first-$last)/1000
 if($mah -le 0){$issues.Add('charge_not_decreasing')}
 if([double]::Parse($charge[0].covered_seconds,[Globalization.CultureInfo]::InvariantCulture) -lt 0.8*$duration){$issues.Add('insufficient_battery_coverage')}
}
$report=[ordered]@{
 label=$manifest.label;appVersion=$manifest.appVersion;durationSeconds=$duration
 cpuSeconds=$(if($cpu.Count){[math]::Round($totalCpu,4)}else{$null})
 cpuPercentOfOneCore=$(if($cpu.Count -and $duration -gt 0){[math]::Round(100*$totalCpu/$duration,3)}else{$null})
 wholePhoneChargeMah=$mah;temperatureStartC=$manifest.before.temperatureC;temperatureEndC=$manifest.after.temperatureC
 measurementChecksPassed=($issues.Count -eq 0);issues=@($issues | Select-Object -Unique)
 cpu=$cpu;powerCounters=$power;traceHealth=$health
 limitations=@('CPU covers sampled intervals, not necessarily all startup/shutdown work.','Battery charge is whole-device consumption, not app-only energy.','Current-counter sign and units require device validation; current is not converted to watts here.','Passing data checks does not prove equal workload, identical network traffic, absence of mid-run charging, or an energy improvement.','Compare repeated physical-device runs with matched temperature, charge, brightness, workload and profiler config.','No application data, VPN routing, APK installation, or remote Git operations are performed by this analyzer.')
}
$report | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $run 'report.json') -Encoding utf8
[pscustomobject]@{report=(Join-Path $run 'report.json');seconds=[math]::Round($duration,2);cpuSeconds=$report.cpuSeconds;wholePhoneMah=$mah;checksPassed=$report.measurementChecksPassed;issues=($report.issues -join ', ')} | Format-List
