param([Parameter(Mandatory = $true)][string]$RuntimeApk)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$apkPath = [IO.Path]::GetFullPath($RuntimeApk)
if (-not $apkPath.StartsWith('E:\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Runtime APK must be on E:.' }
$apkHash = 'DA3F9792C73FA8A328CF8209BC3227908AACBB3AD86D09E67F1B8540B560BE5F'
if ((Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash -ne $apkHash) { throw 'Expected debug.59.9_old APK with 16 KiB UDP send buffer.' }
$manifest = Get-Content -LiteralPath "$projectRoot/app/src/main/libclient.provenance.json" -Raw | ConvertFrom-Json
$artifact = @($manifest.artifacts | Where-Object abi -eq 'arm64-v8a')
if ($artifact.Count -ne 1 -or $artifact[0].sha256 -ne '47A00629CDEF2A5BCCA3AC68F6C8D9690063A2441DAAB9A27CE98355C178FD92') { throw 'Unexpected runtime provenance.' }
$turnSource = [IO.File]::ReadAllText("$projectRoot/rust-client/turn.rs")
if ($turnSource -notmatch 'UDP_SEND_BUFFER_BYTES: usize = 16 \* 1024;' -or $turnSource -notmatch 'UDP_RECEIVE_BUFFER_BYTES: usize = 32 \* 1024;') { throw 'Unexpected UDP buffer source constants.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($apkPath)
try {
    $entry = $zip.GetEntry('lib/arm64-v8a/libclient.so')
    if ($null -eq $entry) { throw 'Missing arm64 core.' }
    $stream = $entry.Open(); $memory = [IO.MemoryStream]::new()
    try { $stream.CopyTo($memory); $bytes = $memory.ToArray() }
    finally { $stream.Dispose(); $memory.Dispose() }
    $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))
    if ($hash -ne $artifact[0].sha256 -or $bytes.Length -ne $artifact[0].size) { throw 'Native binary provenance mismatch.' }
    $destination = "$projectRoot/.local/runtime16/jniLibs/arm64-v8a/libclient.so"
    if (Test-Path -LiteralPath $destination) {
        if ((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash -ne $hash) { throw 'Preserving different existing runtime.' }
    } else {
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
        [IO.File]::WriteAllBytes($destination, $bytes)
    }
    Write-Output "Verified debug.59.9_old runtime: $hash; UDP send 16 KiB / receive 32 KiB."
} finally { $zip.Dispose() }
