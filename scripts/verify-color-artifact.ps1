param([string]$AcceptanceJar = '../.tools/telepads-colors-acceptance.jar')
$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
$taskRoot = Split-Path -Parent $projectDir
$version = '26.3-1.0'
$jarPath = Join-Path $projectDir "build/libs/telepads-$version.jar"
if (-not [IO.Path]::IsPathRooted($AcceptanceJar)) { $AcceptanceJar = Join-Path $projectDir $AcceptanceJar }
function Hashes([string]$Path) {
    $result = @{}; $zip = [IO.Compression.ZipFile]::OpenRead($Path)
    try { foreach ($entry in $zip.Entries) { if ($entry.FullName.EndsWith('/')) { continue }; $stream = $entry.Open(); try { $result[$entry.FullName] = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)) } finally { $stream.Dispose() } } }
    finally { $zip.Dispose() }; return $result
}
$production = Hashes $jarPath; $acceptance = Hashes $AcceptanceJar
$hooks = @($production.Keys | Where-Object { $_ -match '(^|/)(TelepadGameTests|ColorGameTests|ClientSmoke|IntegrationClient|IntegrationServer|ColorAcceptanceClient|ColorAcceptanceServer)(\$|\.class)' -or $_ -like 'data/telepads/test_instance/*' })
if ($hooks.Count) { throw "Acceptance hooks in distribution: $($hooks -join ', ')" }
$runtime = @($production.Keys | Where-Object { $_ -like '*.class' -or $_ -like 'assets/*' -or $_ -like 'data/*' -or $_ -in @('logo.png','pack.mcmeta','META-INF/mods.toml') })
$mismatches = @($runtime | Where-Object { -not $acceptance.ContainsKey($_) -or $production[$_] -ne $acceptance[$_] })
if ($mismatches.Count) { throw "Production differs from tested runtime: $($mismatches -join ', ')" }
foreach ($required in @('subaraki/telepads/recipe/TelepadDyeRecipe.class','data/telepads/recipe/telepad_dye.json','META-INF/LICENSE.md','META-INF/NOTICE.md','META-INF/FORGE-LICENSE.txt','assets/telepads/models/block/telepad.json','assets/telepads/textures/block/telepad_top.png','assets/telepads/textures/block/telepad_frame.png','assets/telepads/textures/block/telepad_base.png','assets/telepads/textures/block/telepad_bottom.png','assets/telepads/items/telepad.json','assets/telepads/lang/en_us.json')) { if (-not $production.ContainsKey($required)) { throw "Missing $required" } }
$items = @($production.Keys | Where-Object { $_ -like 'assets/telepads/items/*.json' }); $recipes = @($production.Keys | Where-Object { $_ -like 'data/telepads/recipe/*.json' })
if ($items.Count -ne 7 -or $recipes.Count -ne 5) { throw 'Expected seven items and five recipes' }
$zip = [IO.Compression.ZipFile]::OpenRead($jarPath)
try {
    foreach ($entryName in @('META-INF/mods.toml','META-INF/MANIFEST.MF')) { $reader = [IO.StreamReader]::new($zip.GetEntry($entryName).Open()); try { if (-not $reader.ReadToEnd().Contains($version)) { throw "Wrong version in $entryName" } } finally { $reader.Dispose() } }
    $reader = [IO.StreamReader]::new($zip.GetEntry('data/telepads/recipe/telepad_dye.json').Open()); try { if (($reader.ReadToEnd() | ConvertFrom-Json).type -ne 'telepads:telepad_dye') { throw 'Wrong recipe serializer' } } finally { $reader.Dispose() }
} finally { $zip.Dispose() }
foreach ($source in @('build.gradle','README.md','docs/development.md','docs/PUBLISHING.md','src/main/resources/META-INF/mods.toml')) { if (-not ([IO.File]::ReadAllText((Join-Path $projectDir $source))).Contains($version)) { throw "Missing release version: $source" } }
if (-not ([IO.File]::ReadAllText((Join-Path $projectDir 'src/main/java/subaraki/telepads/network/TelepadNetwork.java'))).Contains('networkProtocolVersion(2)')) { throw 'Wrong protocol' }
$unitTests = 0
foreach ($file in Get-ChildItem -LiteralPath (Join-Path $projectDir 'build/test-results/test') -Filter '*.xml') { [xml]$report = Get-Content -LiteralPath $file.FullName -Raw; if ([int]$report.testsuite.failures -or [int]$report.testsuite.errors) { throw 'Unit tests failed' }; $unitTests += [int]$report.testsuite.tests }
if ($unitTests -lt 14) { throw 'Missing color unit tests' }
$evidenceDir = Join-Path $projectDir 'docs/evidence/dye-colors'
[xml]$game = Get-Content -LiteralPath (Join-Path $evidenceDir 'gametest-report.xml') -Raw
if (@($game.SelectNodes('//testcase')).Count -ne 9 -or @($game.SelectNodes('//failure | //error')).Count) { throw 'GameTests failed or missing' }
foreach ($mode in @('initial','restart','old-client','old-server')) { if (-not (Test-Path -LiteralPath (Join-Path $evidenceDir "$mode/PASS.txt"))) { throw "Missing acceptance mode $mode" } }
$reference = @(Get-Content -LiteralPath (Join-Path $taskRoot '.tools/reference-sha256.json') -Raw | ConvertFrom-Json)
foreach ($entry in $reference) { if ((Get-FileHash -LiteralPath (Join-Path $taskRoot "Telepad1.19.2/$($entry.path)") -Algorithm SHA256).Hash -ne $entry.sha256) { throw 'Legacy reference changed' } }
$result = [ordered]@{ version=$version; protocol=2; checkedAtUtc=[DateTime]::UtcNow.ToString('o'); jar=$jarPath; sha256=(Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash; bytes=(Get-Item -LiteralPath $jarPath).Length; comparedRuntimeFiles=$runtime.Count; mismatches=$mismatches; excludedTestHooks=$hooks.Count; items=$items.Count; recipes=$recipes.Count; unitTests=$unitTests; gameTests=9; acceptanceModes=@('initial with reconnection','restart','old-client','old-server'); legacyReferenceFiles=$reference.Count }
$result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $evidenceDir 'artifact-check.json') -Encoding utf8
Write-Output "COLOR_ARTIFACT_PASS: $($runtime.Count) tested runtime files, $unitTests unit tests, 9 GameTests, no instrumentation"
