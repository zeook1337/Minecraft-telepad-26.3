param(
    [Parameter(Mandatory)][string]$JavaHome,
    [Parameter(Mandatory)][string]$ForgeDirectory,
    [Parameter(Mandatory)][string]$BaselineDirectory,
    [Parameter(Mandatory)][string]$InstanceRoot,
    [ValidateSet('create','verify','restore')][string]$Mode = 'create',
    [int]$TimeoutSeconds = 240,
    [switch]$BuildHelperOnly
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$project = Split-Path -Parent $PSScriptRoot
$baseline = (Resolve-Path -LiteralPath $BaselineDirectory).Path
$root = [IO.Path]::GetFullPath($InstanceRoot)
$forge = (Resolve-Path -LiteralPath $ForgeDirectory).Path
$java = Join-Path (Resolve-Path -LiteralPath $JavaHome).Path 'bin/java.exe'
$manifest = Get-Content -LiteralPath (Join-Path $baseline 'manifest.json') -Raw | ConvertFrom-Json
New-Item -ItemType Directory -Path $root -Force | Out-Null
# Fixture and peer automation classes live only in this separate local test mod.
$helper = Join-Path $root 'telepads-fixture-helper.jar'
if (-not (Test-Path -LiteralPath $helper)) {
    $class = Join-Path $project 'build/sourceSets/main/telepads/fixture/ReleaseBaselineFixture.class'
    if (-not (Test-Path -LiteralPath $class)) { $class = Join-Path $project 'build/classes/java/main/telepads/fixture/ReleaseBaselineFixture.class' }
    if (-not (Test-Path -LiteralPath $class)) { throw 'Compile with -PtelepadsFixture before creating the helper' }
    $zip = [IO.Compression.ZipFile]::Open($helper, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($compiled in Get-ChildItem -LiteralPath (Split-Path -Parent $class) -Filter '*.class') {
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $compiled.FullName, ('telepads/fixture/' + $compiled.Name)) | Out-Null
        }
        $entry = $zip.CreateEntry('META-INF/mods.toml'); $writer = [IO.StreamWriter]::new($entry.Open())
        try { $writer.Write(@'
modLoader="javafml"
loaderVersion="[66,67)"
license="GPL-3.0-only"
[[mods]]
modId="telepads_fixture"
version="1"
displayName="Local Telepads baseline fixture helper"
[[dependencies.telepads_fixture]]
modId="telepads"
mandatory=true
versionRange="[0,)"
ordering="AFTER"
side="SERVER"
'@) } finally { $writer.Dispose() }
    } finally { $zip.Dispose() }
}
if ($BuildHelperOnly) { Write-Output $helper; return }
function Run-Fixture([string]$Instance, [string]$FixtureMode, [bool]$Public) {
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $java; $info.WorkingDirectory = $Instance
    foreach ($arg in @('-Xms512M','-Xmx2G',"-Dtelepads.fixtureMode=$FixtureMode", "-Dtelepads.fixturePublic=$($Public.ToString().ToLowerInvariant())", "-Dtelepads.fixtureManifest=$(Join-Path $Instance 'expected-state.json')", '@libraries/net/minecraftforge/forge/26.3-66.0.9/win_args.txt','nogui')) { $info.ArgumentList.Add($arg) }
    $info.UseShellExecute = $false; $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true; $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $info
    $writer = [IO.StreamWriter]::new((Join-Path $Instance "$FixtureMode.log"), $false)
    $passed = $false; $stopped = $false; $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    try {
        $null = $process.Start(); $errorRead = $process.StandardError.ReadToEndAsync(); $read = $process.StandardOutput.ReadLineAsync()
        while ([DateTime]::UtcNow -lt $deadline) {
            if (-not $read.Wait(100)) { continue }
            $line = $read.GetAwaiter().GetResult(); if ($null -eq $line) { break }
            $writer.WriteLine($line); $writer.Flush()
            if ($line.Contains('RELEASE_BASELINE_FIXTURE_PASS')) {
                $passed = $true; $stopped = $true
                $process.StandardInput.WriteLine('save-all flush'); $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush()
                $deadline = [DateTime]::UtcNow.AddSeconds(30)
            }
            $read = $process.StandardOutput.ReadLineAsync()
        }
        if (-not $process.WaitForExit(10000)) { throw 'Fixture server timeout' }
        $writer.WriteLine($errorRead.GetAwaiter().GetResult()); $writer.Flush()
        if (-not $passed -or $process.ExitCode -ne 0) { throw "Baseline fixture failed: $Instance/$FixtureMode.log" }
    } finally {
        if ($process.Id -and -not $process.HasExited) {
            if (-not $stopped) { $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush() }
            if (-not $process.WaitForExit(10000)) { $process.Kill($true); $null = $process.WaitForExit(10000) }
        }
        $writer.Dispose(); $process.Dispose()
    }
}
foreach ($package in $manifest.packages | Where-Object protocol -eq 2) {
    $jar = Join-Path $baseline $package.file
    if ((Get-FileHash -LiteralPath $jar).Hash -ne $package.sha256) { throw 'Baseline changed' }
    $instance = Join-Path $root $package.version
    $world = Join-Path $instance 'fixture-world'; $backup = Join-Path $root ($package.version + '-untouched-backup')
    if ($Mode -eq 'create') {
        if (Test-Path -LiteralPath $instance) { throw "Refusing to overwrite fixture instance $instance" }
        New-Item -ItemType Directory -Path (Join-Path $instance 'mods') -Force | Out-Null
        New-Item -ItemType Junction -Path (Join-Path $instance 'libraries') -Target (Join-Path $forge 'libraries') | Out-Null
        Copy-Item -LiteralPath (Join-Path $forge 'forge-26.3-66.0.9-shim.jar') -Destination $instance
        Copy-Item -LiteralPath $jar -Destination (Join-Path $instance 'mods/telepads-baseline.jar')
        Copy-Item -LiteralPath $helper -Destination (Join-Path $instance 'mods/telepads-fixture-helper.jar')
        @"
server-ip=127.0.0.1
server-port=25579
online-mode=false
enable-query=false
enable-rcon=false
view-distance=3
simulation-distance=3
pause-when-empty-seconds=-1
level-name=fixture-world
spawn-protection=0
"@ | Set-Content -LiteralPath (Join-Path $instance 'server.properties')
        'eula=true' | Set-Content -LiteralPath (Join-Path $instance 'eula.txt')
        Run-Fixture $instance 'create' ($package.version -eq '1.0')
        New-Item -ItemType Directory -Path $backup | Out-Null
        Copy-Item -LiteralPath $world -Destination $backup -Recurse
        Copy-Item -LiteralPath (Join-Path $instance 'expected-state.json') -Destination $backup
        Copy-Item -LiteralPath (Join-Path $world 'serverconfig') -Destination $backup -Recurse
        $files = foreach ($file in Get-ChildItem -LiteralPath $backup -Recurse -File) { @{ path=[IO.Path]::GetRelativePath($backup,$file.FullName); sha256=(Get-FileHash -LiteralPath $file.FullName).Hash } }
        $files | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $root ($package.version + '-backup-hashes.json')) -Encoding utf8
    }
    if ($Mode -eq 'restore') {
        # Restore into a new instance; never delete/overwrite the original or upgraded world.
        $restored = Join-Path $root ($package.version + '-restored-' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmss'))
        New-Item -ItemType Directory -Path $restored | Out-Null
        foreach ($name in @('mods','forge-26.3-66.0.9-shim.jar','server.properties','eula.txt')) { Copy-Item -LiteralPath (Join-Path $instance $name) -Destination $restored -Recurse }
        New-Item -ItemType Junction -Path (Join-Path $restored 'libraries') -Target (Join-Path $forge 'libraries') | Out-Null
        Copy-Item -LiteralPath (Join-Path $backup 'fixture-world') -Destination $restored -Recurse
        Copy-Item -LiteralPath (Join-Path $backup 'expected-state.json') -Destination $restored
        $instance = $restored
    }
    Run-Fixture $instance 'verify' ($package.version -eq '1.0')
    foreach ($file in (Get-Content -LiteralPath (Join-Path $root ($package.version + '-backup-hashes.json')) -Raw | ConvertFrom-Json)) {
        if ((Get-FileHash -LiteralPath (Join-Path $backup $file.path)).Hash -ne $file.sha256) { throw 'Untouched backup changed' }
    }
    Write-Output "BASELINE_WORLD_PASS $($package.version): original binary load and expected state; untouched backup verified"
}
