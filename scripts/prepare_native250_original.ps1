param([Parameter(Mandatory = $true)][string]$OriginalApk)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$apkPath = (Resolve-Path -LiteralPath $OriginalApk).Path
if (-not $apkPath.StartsWith('E:\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Original APK must be on the authorized E: drive.'
}
$expectedApkHash = '1AFD5F1E015ADCC23B110476543AE6D0C01F85E28A74D1598AA7CD50DA0AE735'
if ((Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash -ne $expectedApkHash) {
    throw 'Original APK checksum differs from the reviewed CSQTT 2.5.0 artifact.'
}
$serverManifestJson = & git -C $projectRoot show 'fd936ef:app/src/main/assets/csqtt.server-provenance.json'
if ($LASTEXITCODE -ne 0) { throw 'Original reference manifest is unavailable.' }
$serverManifest = ($serverManifestJson -join "`n") | ConvertFrom-Json
$targets = @()
foreach ($artifact in $serverManifest.artifacts) {
    $targets += @{
        Entry = "assets/$($artifact.assetName)"
        Relative = "assets/$($artifact.assetName)"
        Size = $artifact.binarySize
        Hash = $artifact.binarySha256
    }
}
foreach ($license in @('CSQTT-LICENSE', 'primp-LICENSE')) {
    # Source assets already contain these licenses; retain APK copies outside merged assets.
    $targets += @{ Entry = "assets/licenses/$license"; Relative = "reference-licenses/$license" }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($apkPath)
$outputRoot = [IO.Path]::GetFullPath("$projectRoot/.local/original250")
try {
    foreach ($target in $targets) {
        $entry = $zip.GetEntry($target.Entry)
        if ($null -eq $entry) { throw "Missing original artifact: $($target.Entry)" }
        $memory = [IO.MemoryStream]::new()
        $inputStream = $entry.Open()
        try { $inputStream.CopyTo($memory); $bytes = $memory.ToArray() }
        finally { $inputStream.Dispose(); $memory.Dispose() }
        $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))
        if ($target.ContainsKey('Hash') -and ($hash -ne $target.Hash -or $bytes.Length -ne $target.Size)) {
            throw "Artifact does not match source provenance: $($target.Entry)"
        }
        $destination = [IO.Path]::GetFullPath((Join-Path $outputRoot $target.Relative))
        if (-not $destination.StartsWith($outputRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Artifact path escapes the local output directory.'
        }
        if (Test-Path -LiteralPath $destination) {
            if ((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash -ne $hash) {
                throw "Preserving differing existing artifact: $destination"
            }
        } else {
            [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
            [IO.File]::WriteAllBytes($destination, $bytes)
        }
        Write-Output "$($target.Entry): $($bytes.Length) bytes, SHA256 $hash"
    }
} finally { $zip.Dispose() }
