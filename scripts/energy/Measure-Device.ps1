#requires -Version 7.0
[CmdletBinding()]
param(
 [Parameter(Mandatory)][ValidatePattern('^[a-zA-Z0-9_.:-]+$')][string]$Serial,
 [ValidateSet('Doctor','Record')][string]$Mode='Doctor',
 [ValidateRange(10,1800)][int]$Seconds=300,
 [ValidateRange(0,300)][int]$WarmupSeconds=30,
 [ValidatePattern('^[a-zA-Z0-9_.-]+$')][string]$Label='run',
 [ValidatePattern('^[a-zA-Z0-9_.]+$')][string]$Package='csqtt.quic.amurcanov.happdebug',
 [string]$Adb='E:/csqtt-deps/android-sdk/platform-tools/adb.exe',
 [string]$OutputRoot=(Join-Path $PSScriptRoot '../../.local/energy'),
 [switch]$Smoke
)
$ErrorActionPreference='Stop'
foreach($path in @($Adb,$OutputRoot)){if([IO.Path]::GetFullPath($path) -notmatch '^E:[\\/]'){throw 'Host paths must remain on E:'}}
function Device([string[]]$Arguments){
 $result=& $Adb -s $Serial @Arguments 2>&1
 if($LASTEXITCODE -ne 0){throw "ADB failed: $($Arguments[0]); $($result -join ' ')"}
 $result -join "`n"
}
function Battery {
 $raw=Device @('shell','dumpsys','battery')
 $powered=[regex]::Matches($raw,'(?m)^\s*(?:AC|USB|Wireless|Dock) powered: (true|false)')
 if($powered.Count -lt 3){throw 'Cannot verify charging state'}
 $temp=[regex]::Match($raw,'(?m)^\s*temperature: (\d+)')
 $charge=[regex]::Match($raw,'(?m)^\s*Charge counter: (-?\d+)')
 [ordered]@{powered=(@($powered | Where-Object {$_.Groups[1].Value -eq 'true'}).Count -gt 0);temperatureC=$(if($temp.Success){[double]$temp.Groups[1].Value/10}else{$null});chargeUah=$(if($charge.Success){[long]$charge.Groups[1].Value}else{$null})}
}
$capabilities=Device @('shell','perfetto','--query')
foreach($source in @('android.power','linux.process_stats')){if(!$capabilities.Contains($source)){throw "Missing data source: $source"}}
$version=Device @('shell','perfetto','--version')
$major=[regex]::Match($version,'v(\d+)\.')
if(!$major.Success -or [int]$major.Groups[1].Value -lt 44){throw 'Process-runtime recording requires device Perfetto v44 or newer'}
$isEmulator=(Device @('shell','getprop','ro.kernel.qemu')).Trim() -eq '1'
$before=Battery
if($Mode -eq 'Doctor'){
 [ordered]@{perfetto=$version;emulator=$isEmulator;battery=$before;processRuntimeSupported=$true;powerSourcePresent=$true;note='A short trace is still required to verify actual counters; source presence does not prove hardware power rails.'} | ConvertTo-Json -Depth 5
 return
}
if(!$Smoke -and $Seconds -lt 300){throw 'Real comparison windows must be at least 300 seconds'}
if(!$Smoke -and ($isEmulator -or $before.powered)){throw 'Real energy measurement requires a physical unplugged phone; Smoke only verifies the recorder'}
$id=[guid]::NewGuid().ToString('N')
$out=Join-Path ([IO.Path]::GetFullPath($OutputRoot)) ((Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+$Label+'-'+$id.Substring(0,6))
New-Item -ItemType Directory -Path $out -Force | Out-Null
$remoteConfig="/data/local/tmp/csqttr-energy-$id.pbtxt"
$remoteTrace="/data/misc/perfetto-traces/csqttr-energy-$id.perfetto-trace"
$config=@"
buffers { size_kb: 16384 fill_policy: RING_BUFFER }
duration_ms: $($Seconds*1000)
write_into_file: true
file_write_period_ms: 5000
flush_period_ms: 5000
max_file_size_bytes: 67108864
data_sources { config {
 name: "linux.process_stats"
 process_stats_config {
  scan_all_processes_on_start: true
  proc_stats_poll_ms: 2000
  record_process_age: true
  record_process_runtime: true
 }
} }
data_sources { config {
 name: "android.power"
 android_power_config {
  battery_poll_ms: 1000
  battery_counters: BATTERY_COUNTER_CHARGE
  battery_counters: BATTERY_COUNTER_CAPACITY_PERCENT
  battery_counters: BATTERY_COUNTER_CURRENT
  battery_counters: BATTERY_COUNTER_VOLTAGE
  collect_power_rails: true
 }
} }
"@
[IO.File]::WriteAllText((Join-Path $out 'config.pbtxt'),$config,[Text.UTF8Encoding]::new($false))
$packageInfo=Device @('shell','dumpsys','package',$Package)
$appVersion=[regex]::Match($packageInfo,'versionName=([^\r\n]+)').Groups[1].Value
$manifest=[ordered]@{label=$Label;package=$Package;appVersion=$appVersion;requestedSeconds=$Seconds;warmupSeconds=$WarmupSeconds;smoke=[bool]$Smoke;emulator=$isEmulator;perfetto=$version;brightness=(Device @('shell','settings','get','system','screen_brightness')).Trim();autoBrightness=(Device @('shell','settings','get','system','screen_brightness_mode')).Trim();status='preparing';before=$null;after=$null;workload='Externally prepared; recorder does not assert identical video content or network bytes.'}
$manifest | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $out 'manifest.json') -Encoding utf8
try {

 for($left=$WarmupSeconds;$left -gt 0;$left-=5){Write-Output "Warm-up: $left s";Start-Sleep -Seconds ([math]::Min(5,$left))}
 $manifest.before=Battery
 if(!$Smoke -and $manifest.before.powered){throw 'Charging started during warm-up'}
 $startResult=$config | & $Adb -s $Serial shell perfetto --background-wait --txt -c - -o $remoteTrace 2>&1
 if($LASTEXITCODE -ne 0){throw "Perfetto start failed: $($startResult -join ' ')"}
 $manifest.status='recording'
 $manifest | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $out 'manifest.json') -Encoding utf8
 # The phone records autonomously. No ADB polling or screen interaction during this window.
 for($left=$Seconds+3;$left -gt 0;$left-=30){Write-Output "Recording on device: $left s remaining";Start-Sleep -Seconds ([math]::Min(30,$left))}
 $manifest.after=Battery
 Device @('pull',$remoteTrace,(Join-Path $out 'trace.perfetto-trace')) | Out-Null
 $manifest.status='captured'
 if(!$Smoke -and $manifest.after.powered){$manifest.status='invalid-charging'}
 Write-Output "Captured: $out"
} catch {
 $manifest.status='failed'
 $manifest.error=$_.Exception.Message
 throw
} finally {
 $manifest | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $out 'manifest.json') -Encoding utf8
 # Keep the remote trace on any failure, for recovery; only remove our own unique files on success.
 if($manifest.status -eq 'captured'){
  Device @('shell','rm','-f',$remoteConfig,$remoteTrace) | Out-Null
 }
}


