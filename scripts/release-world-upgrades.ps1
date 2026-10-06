param(
    [Parameter(Mandatory)][string]$JavaHome,
    [Parameter(Mandatory)][string]$ForgeDirectory,
    [Parameter(Mandatory)][string]$CandidateJar,
    [Parameter(Mandatory)][string]$BaselineDirectory,
    [Parameter(Mandatory)][string]$BaselineWorldDirectory,
    [Parameter(Mandatory)][string]$HelperJar,
    [Parameter(Mandatory)][string]$InstanceRoot,
    [Parameter(Mandatory)][string]$EvidenceDirectory,
    [int]$TimeoutSeconds=240
)
. (Join-Path $PSScriptRoot 'release-evidence-common.ps1')
$project=Split-Path -Parent $PSScriptRoot
$java=Join-Path (Resolve-Path -LiteralPath $JavaHome).Path 'bin/java.exe'
$forge=(Resolve-Path -LiteralPath $ForgeDirectory).Path
$candidate=(Resolve-Path -LiteralPath $CandidateJar).Path
$helper=(Resolve-Path -LiteralPath $HelperJar).Path
$baselines=(Resolve-Path -LiteralPath $BaselineDirectory).Path
$worlds=(Resolve-Path -LiteralPath $BaselineWorldDirectory).Path
$root=[IO.Path]::GetFullPath($InstanceRoot); $evidence=[IO.Path]::GetFullPath($EvidenceDirectory)
if (Test-Path -LiteralPath $root) { throw 'Use a new isolated instance root' }
if (Test-Path -LiteralPath $evidence) { throw 'Use a new evidence directory' }
New-Item -ItemType Directory -Path $root,$evidence | Out-Null
$record=New-ReleaseIdentity $project $candidate $java
$record.helperSha256=(Get-FileHash -LiteralPath $helper).Hash
$record.playerReconnection='NOT RUN: verify with ordinary real clients'
$packages=@((Get-Content -LiteralPath (Join-Path $baselines 'manifest.json') -Raw | ConvertFrom-Json).packages | Where-Object protocol -eq 2)
if ($packages.Count -ne 2 -or '1.0' -notin $packages.version -or '26.3-7.1.0-dev' -notin $packages.version) { throw 'Both original protocol-2 baselines required' }
function Assert-Backup([string]$Version) {
    $backup=Join-Path $worlds "$Version-untouched-backup"
    $hashes=@(Get-Content -LiteralPath (Join-Path $worlds "$Version-backup-hashes.json") -Raw | ConvertFrom-Json)
    if (@(Get-ChildItem -LiteralPath $backup -File -Recurse).Count -ne $hashes.Count) { throw 'Untouched backup file set changed' }
    foreach ($file in $hashes) {
        if ((Get-FileHash -LiteralPath (Join-Path $backup $file.path)).Hash -ne $file.sha256) { throw 'Untouched backup changed' }
    }
    return $backup
}
function Check-World([string]$Name, [string]$Instance, [string]$Mode, [bool]$Public, [string]$Jar) {
    $gate=[ordered]@{ name=$Name; status='INCOMPLETE'; packageSha256=(Get-FileHash -LiteralPath $Jar).Hash }
    try {
        $log=Join-Path $evidence "$Name.log"
        $marker=if ($Mode -eq 'empty') { 'RELEASE_NEW_WORLD_PASS' } else { 'RELEASE_BASELINE_FIXTURE_PASS' }
        Invoke-ReleaseServer -Java $java -Instance $Instance -Log $log -TimeoutSeconds $TimeoutSeconds -PassMarker $marker `
            -JvmArguments @("-Dtelepads.fixtureMode=$Mode","-Dtelepads.fixturePublic=$($Public.ToString().ToLowerInvariant())","-Dtelepads.fixtureManifest=$(Join-Path $Instance 'expected-state.json')")
        $gate.status='PASS'; $gate.logSha256=(Get-FileHash -LiteralPath $log).Hash
        Write-Output "RELEASE_WORLD_PASS $Name"
    } catch { $gate.status='FAILED'; $gate.error=$_.Exception.Message; throw }
    finally { $record.gates+=,$gate }
}
try {
    foreach ($package in $packages) {
        $original=Join-Path $baselines $package.file
        if ((Get-FileHash -LiteralPath $original).Hash -ne $package.sha256) { throw 'Original package changed' }
        $backup=Assert-Backup $package.version
        $upgraded=Join-Path $root ($package.version+'-upgraded')
        New-ReleaseServerInstance $upgraded $forge $candidate $helper 25581
        Copy-Item -LiteralPath (Join-Path $backup 'fixture-world'),(Join-Path $backup 'expected-state.json') -Destination $upgraded -Recurse
        Check-World ($package.version+'-upgrade') $upgraded 'verify' ($package.version -eq '1.0') $candidate
        Check-World ($package.version+'-restart') $upgraded 'verify' ($package.version -eq '1.0') $candidate
        $restored=Join-Path $root ($package.version+'-rollback')
        New-ReleaseServerInstance $restored $forge $original $helper 25581
        Copy-Item -LiteralPath (Join-Path $backup 'fixture-world'),(Join-Path $backup 'expected-state.json') -Destination $restored -Recurse
        Check-World ($package.version+'-rollback') $restored 'verify' ($package.version -eq '1.0') $original
        $null=Assert-Backup $package.version
    }
    $empty=Join-Path $root 'candidate-new-world'
    New-ReleaseServerInstance $empty $forge $candidate $helper 25581
    Check-World 'candidate-new-world' $empty 'empty' $false $candidate
    if ($record.sourceFingerprint -ne (Get-ReleaseSourceFingerprint $project) -or $record.candidateSha256 -ne (Get-FileHash -LiteralPath $candidate).Hash) { throw 'Source/candidate changed during world checks' }
    $record.status='PASS'
} catch { $record.status='FAILED'; $record.error=$_.Exception.Message; throw }
finally {
    $record.finishedUtc=[DateTime]::UtcNow.ToString('o')
    $record | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $evidence 'world-upgrades.json') -Encoding utf8
}
