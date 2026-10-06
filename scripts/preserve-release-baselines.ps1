param(
    [Parameter(Mandatory)][string]$PublishedJar,
    [Parameter(Mandatory)][string]$AlphaJar,
    [Parameter(Mandatory)][string]$ProtocolOneJar,
    [Parameter(Mandatory)][string]$BaselineDirectory
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$project = Split-Path -Parent $PSScriptRoot
$root = Split-Path -Parent $project
$target = [IO.Path]::GetFullPath($BaselineDirectory)
if ($target.StartsWith((Join-Path $project 'src') + [IO.Path]::DirectorySeparatorChar)) { throw 'Baselines must be outside sources' }
New-Item -ItemType Directory -Path $target -Force | Out-Null
$packages = @(
    @{ source=$PublishedJar; version='26.3-7.1.0-dev'; protocol=2; name='telepads-26.3-7.1.0-dev.jar'; expected='D2DAC004E188CCBDA8113F81991E9EC9F645F0F21877453B0374DC41ACC2DB58' },
    @{ source=$AlphaJar; version='1.0'; protocol=2; name='telepads-26.3-1.0.jar'; expected=$null },
    @{ source=$ProtocolOneJar; version='26.3-7.0.0-dev'; protocol=1; name='telepads-protocol-1.jar'; expected=$null }
)
$records = foreach ($package in $packages) {
    $source = (Resolve-Path -LiteralPath $package.source).Path
    $hash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
    if ($package.expected -and $hash -ne $package.expected) { throw 'Published checksum mismatch' }
    $zip = [IO.Compression.ZipFile]::OpenRead($source)
    try {
        $reader = [IO.StreamReader]::new($zip.GetEntry('META-INF/mods.toml').Open())
        try { $metadata = $reader.ReadToEnd() } finally { $reader.Dispose() }
        if ($metadata -notmatch ('(?m)^version="' + [regex]::Escape($package.version) + '"')) { throw "Unexpected baseline version: $source" }
        $javap = Join-Path $root '.tools/jdk/jdk-25.0.4.1+1/bin/javap.exe'
        $bytecode = (& $javap -c -p -classpath $source subaraki.telepads.network.TelepadNetwork) -join "`n"
        if ($LASTEXITCODE -or $bytecode -notmatch ('iconst_' + $package.protocol + '\s+\d+: invokevirtual[^\r\n]*networkProtocolVersion')) {
            throw 'Unexpected offered network protocol'
        }
    } finally { $zip.Dispose() }
    $destination = Join-Path $target $package.name
    if (Test-Path -LiteralPath $destination) {
        if ((Get-FileHash -LiteralPath $destination).Hash -ne $hash) { throw "Refusing to replace preserved baseline $destination" }
    } else { Copy-Item -LiteralPath $source -Destination $destination }
    if ((Get-FileHash -LiteralPath $destination).Hash -ne $hash) { throw 'Copy verification failed' }
    [ordered]@{ file=$package.name; version=$package.version; protocol=$package.protocol; sha256=$hash; bytes=(Get-Item -LiteralPath $destination).Length }
}
$references = foreach ($area in @('Telepad1.19.2','Telepad26.3-code')) {
    $directory = Join-Path $root $area
    foreach ($file in Get-ChildItem -LiteralPath $directory -File -Recurse -Force) {
        [ordered]@{ path=[IO.Path]::GetRelativePath($root,$file.FullName); sha256=(Get-FileHash -LiteralPath $file.FullName).Hash }
    }
}
$manifestPath = Join-Path $target 'manifest.json'
$manifest = [ordered]@{ preparedUtc=[DateTime]::UtcNow.ToString('o'); sourceRevision=(& git -C $project rev-parse HEAD); packages=@($records); protectedFiles=@($references) }
if (Test-Path -LiteralPath $manifestPath) {
    $previous = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    if (($previous.packages | ConvertTo-Json -Compress) -ne ($records | ConvertTo-Json -Compress)) { throw 'Preserved manifest differs' }
} else { $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $manifestPath -Encoding utf8 }
Write-Output "BASELINES_PASS: $($records.Count) original packages preserved and verified; $(@($references).Count) reference files recorded"
