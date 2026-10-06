param(
    [string]$AcceptanceJar = '../.tools/integration-server/mods/telepads-acceptance.jar',
    [string]$ReferenceManifest = '../.tools/reference-sha256.json'
)
$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
$taskRoot = Split-Path -Parent $projectDir
$jarPath = Join-Path $projectDir 'build/libs/telepads-26.3-1.0.jar'
if (-not [IO.Path]::IsPathRooted($AcceptanceJar)) { $AcceptanceJar = Join-Path $projectDir $AcceptanceJar }
if (-not [IO.Path]::IsPathRooted($ReferenceManifest)) { $ReferenceManifest = Join-Path $projectDir $ReferenceManifest }

function Get-ZipHashes([string]$Path) {
    $hashes = @{}
    $archive = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName.EndsWith('/')) { continue }
            $stream = $entry.Open()
            try { $hashes[$entry.FullName] = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)) }
            finally { $stream.Dispose() }
        }
    } finally { $archive.Dispose() }
    return $hashes
}

$production = Get-ZipHashes $jarPath
$acceptance = Get-ZipHashes $AcceptanceJar
$hooks = @($production.Keys | Where-Object { $_ -match '(^|/)(TelepadGameTests|ColorGameTests|ClientSmoke|IntegrationClient|IntegrationServer|ColorAcceptanceClient|ColorAcceptanceServer)(\$|\.class)' -or $_ -like 'data/telepads/test_instance/*' })
if ($hooks.Count) { throw "Production JAR includes test hooks: $($hooks -join ', ')" }
$runtimePaths = @($production.Keys | Where-Object {
    $_ -like '*.class' -or $_ -like 'assets/*' -or $_ -like 'data/*' -or
    $_ -in @('logo.png', 'pack.mcmeta', 'META-INF/mods.toml')
})
$mismatches = @($runtimePaths | Where-Object { -not $acceptance.ContainsKey($_) -or $production[$_] -ne $acceptance[$_] })
if ($mismatches.Count) { throw "Acceptance runtime differs: $($mismatches -join ', ')" }
$licenses = @('META-INF/LICENSE.md', 'META-INF/NOTICE.md', 'META-INF/FORGE-LICENSE.txt')
foreach ($path in $licenses) { if (-not $production.ContainsKey($path)) { throw "Missing license/credits: $path" } }
$items = @($production.Keys | Where-Object { $_ -like 'assets/telepads/items/*.json' })
$recipes = @($production.Keys | Where-Object { $_ -like 'data/telepads/recipe/*.json' })
if ($items.Count -ne 7 -or $recipes.Count -ne 5) { throw 'Expected seven item definitions and five recipes' }

$referenceDir = Join-Path $taskRoot 'Telepad1.19.2'
$reference = @(Get-Content -LiteralPath $ReferenceManifest -Raw | ConvertFrom-Json)
$changed = @()
$missing = @()
foreach ($entry in $reference) {
    $path = Join-Path $referenceDir $entry.path
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { $missing += $entry.path; continue }
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $entry.sha256) { $changed += $entry.path }
}
$expectedPaths = @{}
foreach ($entry in $reference) { $expectedPaths[$entry.path.Replace('\', '/')] = $true }
$extra = @(Get-ChildItem -LiteralPath $referenceDir -Recurse -File -Force | ForEach-Object {
    $relative = [IO.Path]::GetRelativePath($referenceDir, $_.FullName).Replace('\', '/')
    if (-not $expectedPaths.ContainsKey($relative)) { $relative }
})
if ($changed.Count -or $missing.Count -or $extra.Count) { throw 'Original reference files changed; inspect reference manifest' }

$unitTests = 0; $unitFailures = 0; $unitErrors = 0
foreach ($file in Get-ChildItem -LiteralPath (Join-Path $projectDir 'build/test-results/test') -Filter '*.xml') {
    [xml]$report = Get-Content -LiteralPath $file.FullName -Raw
    $unitTests += [int]$report.testsuite.tests
    $unitFailures += [int]$report.testsuite.failures
    $unitErrors += [int]$report.testsuite.errors
}
[xml]$gameReport = Get-Content -LiteralPath (Join-Path $projectDir 'docs/evidence/gametest-report.xml') -Raw
$gameTests = @($gameReport.SelectNodes('//testcase')).Count
$gameFailures = @($gameReport.SelectNodes('//failure | //error')).Count
if ($unitTests -lt 14 -or $unitFailures -or $unitErrors -or $gameTests -ne 9 -or $gameFailures) { throw 'Unexpected or failing test reports' }

$integration = Get-Content -LiteralPath (Join-Path $projectDir 'docs/evidence/integration-console.log') -Raw
$restart = Get-Content -LiteralPath (Join-Path $projectDir 'docs/evidence/integration-restart-console.log') -Raw
$markers = @('UNLOADED', 'SHARED', 'TRAVEL', 'DIMENSION', 'FAR', 'NECKLACE', 'BEAD', 'SERVER')
foreach ($marker in $markers) { if (-not $integration.Contains("INTEGRATION_${marker}_PASS")) { throw "Missing integration marker: $marker" } }
if (-not $restart.Contains('INTEGRATION_RESTART_PASS')) { throw 'Missing restart marker' }
foreach ($mode in @('write', 'read')) {
    $log = Get-Content -LiteralPath (Join-Path $projectDir "docs/evidence/server-$mode.log") -Raw
    foreach ($marker in @('Telepads 26.3 adaptation registered', 'name: "SavedPad"', 'identity: [I; 1, 2, 3, 4]')) {
        if (-not $log.Contains($marker)) { throw "Missing server $mode evidence: $marker" }
    }
}

$result = [ordered]@{
    checkedAtUtc = [DateTime]::UtcNow.ToString('o')
    productionJar = [IO.Path]::GetFullPath($jarPath)
    sha256 = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash
    bytes = (Get-Item -LiteralPath $jarPath).Length
    fileEntries = $production.Count
    excludedTestHookEntries = $hooks.Count
    acceptanceComparison = [ordered]@{
        jar = [IO.Path]::GetFullPath($AcceptanceJar)
        sha256 = (Get-FileHash -LiteralPath $AcceptanceJar -Algorithm SHA256).Hash
        scope = 'All production .class, assets/, data/, logo.png, pack.mcmeta, META-INF/mods.toml; excludes archive manifest and license/credit documents'
        comparedFiles = $runtimePaths.Count
        mismatches = $mismatches
    }
    resources = @{ itemDefinitions = $items.Count; recipes = $recipes.Count; licensesAndCredits = $licenses }
    junit = @{ tests = $unitTests; failures = $unitFailures; errors = $unitErrors }
    gameTests = @{ tests = $gameTests; failuresAndErrors = $gameFailures }
    integration = @{ passedMarkers = $markers; restart = 'PASS'; dedicatedProductionWriteAndRead = 'PASS' }
    originalReference = @{ files = $reference.Count; changed = $changed; missing = $missing; extra = $extra }
}
$output = Join-Path $projectDir 'docs/evidence/artifact-check.json'
$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $output -Encoding utf8
Write-Output "Artifact verified: $($runtimePaths.Count) identical runtime files, no test hooks, $($reference.Count) original files unchanged. Evidence: $output"
