#requires -Version 7.0
param([string]$ToolsDirectory='E:/csqtt-deps/perfetto')
$ErrorActionPreference='Stop'
$directory=[IO.Path]::GetFullPath($ToolsDirectory)
if($directory -notmatch '^E:[\\/]'){throw 'Tools must stay on E:'}
$version='v59.1'
$sha='3acc5aca1c764541033a82ba3bb0a47524b5c3c0aa194eea8d9a7b0cdb346bdc'
$url="https://commondatastorage.googleapis.com/perfetto-luci-artifacts/$version/windows-amd64/trace_processor_shell.exe"
New-Item -ItemType Directory -Force $directory | Out-Null
$target=Join-Path $directory 'trace_processor_shell.exe'
if(!(Test-Path $target) -or (Get-FileHash $target -Algorithm SHA256).Hash -ne $sha){
 $download="$target.download"
 Invoke-WebRequest -Uri $url -OutFile $download
 if((Get-FileHash $download -Algorithm SHA256).Hash -ne $sha){throw 'Perfetto SHA-256 mismatch; downloaded file was not executed'}
 Move-Item -LiteralPath $download -Destination $target -Force
}
[ordered]@{version=$version;url=$url;sha256=$sha} | ConvertTo-Json | Set-Content (Join-Path $directory 'installation.json') -Encoding utf8
& $target --version
if($LASTEXITCODE -ne 0){throw 'Perfetto version check failed'}
'Installed verified Perfetto analyzer: '+$target
