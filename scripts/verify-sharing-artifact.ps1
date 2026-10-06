param([string]$AcceptanceJar = '../.tools/integration-server/mods/telepads-acceptance.jar')
$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
$taskRoot = Split-Path -Parent $projectDir
$evidenceDir = Join-Path $projectDir 'docs/evidence/server-sharing'
$jarPath = Join-Path $projectDir 'build/libs/telepads-26.3-1.0.jar'
if (-not [IO.Path]::IsPathRooted($AcceptanceJar)) { $AcceptanceJar = Join-Path $projectDir $AcceptanceJar }
function Hashes([string]$Path) {
    $result = @{}; $zip = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName.EndsWith('/')) { continue }
            $stream = $entry.Open()
            try { $result[$entry.FullName] = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)) }
            finally { $stream.Dispose() }
        }
    } finally { $zip.Dispose() }
    return $result
}
$production = Hashes $jarPath; $acceptance = Hashes $AcceptanceJar
$hooks = @($production.Keys | Where-Object { $_ -match '(^|/)(TelepadGameTests|ColorGameTests|ClientSmoke|IntegrationClient|IntegrationServer|ColorAcceptanceClient|ColorAcceptanceServer)(\$|\.class)' -or $_ -like 'data/telepads/test_instance/*' })
if ($hooks.Count) { throw 'Acceptance hooks in production JAR' }
$runtime = @($production.Keys | Where-Object { $_ -like '*.class' -or $_ -like 'assets/*' -or $_ -like 'data/*' -or $_ -in @('logo.png','pack.mcmeta','META-INF/mods.toml') })
$mismatches = @($runtime | Where-Object { -not $acceptance.ContainsKey($_) -or $production[$_] -ne $acceptance[$_] })
if ($mismatches.Count) { throw "Production differs from tested runtime: $($mismatches -join ', ')" }
foreach ($required in @('META-INF/LICENSE.md','META-INF/NOTICE.md','META-INF/FORGE-LICENSE.txt','META-INF/mods.toml')) {
    if (-not $production.ContainsKey($required)) { throw "Missing $required" }
}
$items = @($production.Keys | Where-Object { $_ -like 'assets/telepads/items/*.json' })
$recipes = @($production.Keys | Where-Object { $_ -like 'data/telepads/recipe/*.json' })
if ($items.Count -ne 7 -or $recipes.Count -ne 5) { throw 'Expected seven items and five recipes' }
$zip = [IO.Compression.ZipFile]::OpenRead($jarPath)
try {
    foreach ($entry in $zip.Entries | Where-Object { $_.FullName -like 'assets/telepads/lang/*.json' }) {
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $language = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        $label = $language.'screen.telepads.share'
        if ($label -and $label -ne 'Share with server') { throw "Unexpected sharing label: $($entry.FullName)" }
        if ($entry.FullName -eq 'assets/telepads/lang/en_us.json' -and $label -ne 'Share with server') { throw 'Missing English label' }
    }
} finally { $zip.Dispose() }
$unitTests = 0
foreach ($file in Get-ChildItem (Join-Path $projectDir 'build/test-results/test') -Filter '*.xml') {
    [xml]$report = Get-Content -LiteralPath $file.FullName -Raw
    if ([int]$report.testsuite.failures -or [int]$report.testsuite.errors) { throw 'JUnit failed' }
    $unitTests += [int]$report.testsuite.tests
}
if ($unitTests -lt 16) { throw 'Missing sharing unit tests' }
[xml]$game = Get-Content -LiteralPath (Join-Path $evidenceDir 'gametest-report.xml') -Raw
if (@($game.SelectNodes('//testcase')).Count -ne 9 -or @($game.SelectNodes('//failure | //error')).Count) { throw 'GameTests failed or missing' }
$initial = Get-Content -LiteralPath (Join-Path $evidenceDir 'integration-console.log') -Raw
foreach ($marker in @('UNLOADED','SHARED','TRAVEL','DIMENSION','FAR','NECKLACE','BEAD','SERVER')) {
    if (-not $initial.Contains("INTEGRATION_${marker}_PASS")) { throw "Missing integration marker: $marker" }
}
if (-not $initial.Contains('Bob without friendship and for future identities; original users preserved')) { throw 'Missing global sharing evidence' }
$restart = Get-Content -LiteralPath (Join-Path $evidenceDir 'integration-restart-console.log') -Raw
if (-not $restart.Contains('INTEGRATION_RESTART_PASS: public access without friendship, future identity, original users and legacy private sharing survived')) { throw 'Missing persistence evidence' }
$client = Get-Content -LiteralPath (Join-Path $evidenceDir 'client-smoke.log') -Raw
if (-not $client.Contains('TELEPADS_SHARE_FORM_PASS: Share with server; unchecked; within screen bounds') -or -not (Test-Path -LiteralPath (Join-Path $evidenceDir 'name.png'))) { throw 'Missing naming form evidence' }
$reference = @(Get-Content -LiteralPath (Join-Path $taskRoot '.tools/reference-sha256.json') -Raw | ConvertFrom-Json)
foreach ($entry in $reference) {
    if ((Get-FileHash -LiteralPath (Join-Path $taskRoot "Telepad1.19.2/$($entry.path)") -Algorithm SHA256).Hash -ne $entry.sha256) { throw 'Legacy reference changed' }
}
$result = [ordered]@{ checkedAtUtc=[DateTime]::UtcNow.ToString('o'); jar=$jarPath; sha256=(Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash; bytes=(Get-Item -LiteralPath $jarPath).Length; comparedRuntimeFiles=$runtime.Count; mismatches=$mismatches; excludedTestHooks=$hooks.Count; items=$items.Count; recipes=$recipes.Count; unitTests=$unitTests; gameTests=9; dedicatedIntegration='PASS'; restart='PASS'; namingForm='PASS'; legacyReferenceFiles=$reference.Count }
$result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $evidenceDir 'artifact-check.json') -Encoding utf8
Write-Output "SHARING_ARTIFACT_PASS: $($runtime.Count) identical runtime files, $unitTests JUnit, 9 GameTests, dedicated integration and restart, no test hooks"
