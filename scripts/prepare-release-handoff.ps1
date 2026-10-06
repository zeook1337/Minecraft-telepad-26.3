param(
    [Parameter(Mandatory)][string]$JavaHome,
    [Parameter(Mandatory)][string]$CandidateJar,
    [Parameter(Mandatory)][string]$RegressionEvidenceDirectory,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [switch]$VerifySourceBuild
)
. (Join-Path $PSScriptRoot 'release-evidence-common.ps1')
$project=Split-Path -Parent $PSScriptRoot
$candidate=(Resolve-Path -LiteralPath $CandidateJar).Path
$output=[IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $output) { throw 'Use a new handoff directory' }
if ($output.StartsWith($project+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Keep handoff archives outside publishable source' }
& (Join-Path $PSScriptRoot 'verify-release-artifact.ps1') -CandidateJar $candidate -EvidenceDirectory $RegressionEvidenceDirectory
New-Item -ItemType Directory -Path $output | Out-Null
$hash=(Get-FileHash -LiteralPath $candidate).Hash
$fingerprint=Get-ReleaseSourceFingerprint $project
$version=[regex]::Match([IO.File]::ReadAllText((Join-Path $project 'build.gradle')),"(?m)^version = '([^']+)'").Groups[1].Value
$base=(& git -C $project rev-parse HEAD)
$previousIndex=$env:GIT_INDEX_FILE
$index=Join-Path $output 'snapshot-index'
try {
    # Build an immutable Git snapshot using an independent index. Include the
    # working source and new helpers selected by Git; ignored instances/evidence
    # are excluded. This does not stage the user's working index.
    $env:GIT_INDEX_FILE=$index
    & git -C $project read-tree HEAD
    if ($LASTEXITCODE) { throw 'Cannot initialize snapshot index' }
    & git -C $project -c core.autocrlf=false add --all
    if ($LASTEXITCODE) { throw 'Cannot select source files' }
    $files=@(& git -C $project ls-files)
    if (@($files | Where-Object { $_ -match '^(build|run|runs|logs|\.gradle|\.tools|docs/evidence)/|(^|/)(server\.properties|eula\.txt|options\.txt|\.env)$|\.(log|hprof|jfr)$' }).Count) { throw 'Local runtime/evidence files selected for source archive' }
    foreach ($required in @('gradlew','gradlew.bat','gradle/wrapper/gradle-wrapper.jar','build.gradle','LICENSE.md','NOTICE.md','FORGE-LICENSE.txt')) { if ($required -notin $files) { throw "Source missing $required" } }
    $tree=(& git -C $project write-tree)
    if ($LASTEXITCODE) { throw 'Cannot write source snapshot tree' }
    $message=Join-Path $output 'snapshot-message.txt'
    "Prepare Telepads $version Alpha acceptance snapshot`n`nProduction SHA-256: $hash`nBuild/test input fingerprint: $fingerprint`nManual acceptance gates remain pending.`n" | Set-Content -LiteralPath $message -Encoding utf8
    $ref="refs/telepads/candidates/$version/$($tree.Substring(0,12))"
    $existing=(& git -C $project show-ref --verify --hash $ref)
    if ($existing) {
        if ((& git -C $project rev-parse "$existing^{tree}") -ne $tree) { throw 'Source snapshot reference collision' }
        $revision=$existing
    } else {
        $revision=(& git -C $project commit-tree $tree -p $base -F $message)
        if ($LASTEXITCODE) { throw 'Cannot create immutable source revision with configured Git identity' }
        & git -C $project update-ref $ref $revision ('0'*40)
        if ($LASTEXITCODE) { throw 'Cannot retain source snapshot reference' }
    }
    $archive=Join-Path $output "telepads-26.3-$version-sources.zip"
    & git -C $project archive --format=zip --output $archive $revision
    if ($LASTEXITCODE) { throw 'Source archive failed' }
} finally { $env:GIT_INDEX_FILE=$previousIndex }
Copy-Item -LiteralPath $candidate -Destination $output
$sourceBuild='NOT RUN'
if ($VerifySourceBuild) {
    $extracted=Join-Path $output 'source-build-check'
    Expand-Archive -LiteralPath $archive -DestinationPath $extracted
    $javaPath=(Resolve-Path -LiteralPath $JavaHome).Path
    $oldJava=$env:JAVA_HOME; $oldPath=$env:PATH
    try {
        $env:JAVA_HOME=$javaPath; $env:PATH="$javaPath/bin;$env:PATH"
        Push-Location $extracted
        try {
            & ./gradlew.bat clean build --no-build-cache --rerun-tasks --console=plain *> (Join-Path $output 'source-build.log')
            if ($LASTEXITCODE) { throw 'Corresponding source archive did not build' }
        } finally { Pop-Location }
        $rebuilt=Get-ReleaseZipHashes (Join-Path $extracted "build/libs/telepads-26.3-$version.jar")
        $original=Get-ReleaseZipHashes $candidate
        if ($rebuilt.Count -ne $original.Count -or @($original.Keys | Where-Object { $rebuilt[$_] -ne $original[$_] }).Count) { throw 'Source rebuild differs from the candidate JAR entries' }
        $sourceBuild='PASS: clean build and all JAR entry contents match'
    } finally { $env:JAVA_HOME=$oldJava; $env:PATH=$oldPath }
}
if ($fingerprint -ne (Get-ReleaseSourceFingerprint $project) -or $hash -ne (Get-FileHash -LiteralPath $candidate).Hash) { throw 'Candidate/source changed during handoff preparation' }
$sourceHash=(Get-FileHash -LiteralPath $archive).Hash
"$($hash.ToLowerInvariant())  $([IO.Path]::GetFileName($candidate))`n$($sourceHash.ToLowerInvariant())  $([IO.Path]::GetFileName($archive))" | Set-Content -LiteralPath (Join-Path $output 'SHA256SUMS.txt') -Encoding utf8
$record=[ordered]@{version=$version;classification='Alpha';publication='UNPUBLISHED';candidateSha256=$hash;sourceRevision=$revision;sourceBaseRevision=$base;sourceRef=$ref;sourceTree=$tree;sourceFingerprint=$fingerprint;sourceArchive=[IO.Path]::GetFileName($archive);sourceArchiveSha256=$sourceHash;sourceBuild=$sourceBuild;environment=@{minecraft='26.3';forge='66.0.9';java='25'};acceptance='docs/acceptance.md';manualChecklist='docs/release-checklist.md';releaseEligibility='PENDING: compatibility, reconnects, ordinary installations, four-client short run and full endurance/restart require passing candidate-bound evidence'}
$record | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $output 'handoff.json') -Encoding utf8
@"
# Telepads $version — unpublished Alpha candidate

JAR: $([IO.Path]::GetFileName($candidate))
SHA-256: $hash
Corresponding source revision: $revision
Source archive: $([IO.Path]::GetFileName($archive))
Source archive SHA-256: $sourceHash
Source reconstruction: $sourceBuild
Environment: Minecraft 26.3, Forge 66.0.9, Java 25; network protocol 3.

Supported vanilla hazards now reject before movement or charging. Update server
and all clients together; generations 1 and 2 reject. Supported development-world
save codecs are retained. Back up the complete world/server configuration and
matching old package; rollback restores both. See the archived README, CHANGELOG,
docs/acceptance.md and docs/release-checklist.md.

Recommendation: retain Alpha. Manual/graphical compatibility, ordinary clean-JAR
installations and the qualifying four-client/four-hour workload remain pending.
No hosted upload or stable download is asserted. Rebuild this handoff after any
source, resource, metadata or acceptance-summary update before publishing.
"@ | Set-Content -LiteralPath (Join-Path $output 'HANDOFF.md') -Encoding utf8
Write-Output "RELEASE_HANDOFF_PREPARED Alpha: $revision; $hash; $sourceBuild"
