param(
    [Parameter(Mandatory)][string]$CandidateJar,
    [Parameter(Mandatory)][string]$EvidenceDirectory
)
. (Join-Path $PSScriptRoot 'release-evidence-common.ps1')
$project=Split-Path -Parent $PSScriptRoot
$candidate=(Resolve-Path -LiteralPath $CandidateJar).Path
$evidence=(Resolve-Path -LiteralPath $EvidenceDirectory).Path
$record=Get-Content -LiteralPath (Join-Path $evidence 'regressions.json') -Raw | ConvertFrom-Json
$hash=(Get-FileHash -LiteralPath $candidate).Hash
if ($record.status -ne 'PASS' -or $record.candidateSha256 -ne $hash -or $record.sourceFingerprint -ne (Get-ReleaseSourceFingerprint $project)) { throw 'Failed, stale or wrong-candidate regression evidence' }
if ($record.sourceRevision -ne (& git -C $project rev-parse HEAD)) { throw 'Evidence source revision differs' }
foreach ($file in $record.files) {
    if ((Get-FileHash -LiteralPath (Join-Path $evidence $file.path)).Hash -ne $file.sha256) { throw "Evidence file changed: $($file.path)" }
}
$unit=@(Get-ChildItem -LiteralPath (Join-Path $evidence 'junit') -Filter 'TEST-*.xml')
if (-not $unit.Count) { throw 'Missing JUnit suites' }
$unitTests=0
foreach ($file in $unit) {
    if ('junit/'+$file.Name -notin @($record.files.path)) { throw 'Unbound JUnit report' }
    [xml]$xml=Get-Content -LiteralPath $file.FullName -Raw
    if ([int]$xml.testsuite.tests -eq 0 -or [int]$xml.testsuite.failures -or [int]$xml.testsuite.errors -or [int]$xml.testsuite.skipped) { throw 'Failed/empty/skipped JUnit suite' }
    $unitTests += [int]$xml.testsuite.tests
}
foreach ($source in Get-ChildItem -LiteralPath (Join-Path $project 'src/test/java') -Filter '*Test.java' -Recurse) {
    $suiteName=[IO.Path]::GetRelativePath((Join-Path $project 'src/test/java'),$source.FullName).Replace('\','.').Replace('/','.').Replace('.java','')
    if ('TEST-'+$suiteName+'.xml' -notin @($unit.Name)) { throw "Missing required JUnit suite $suiteName" }
}
[xml]$game=Get-Content -LiteralPath (Join-Path $evidence 'gametest-report.xml') -Raw
$gameNames=@($game.SelectNodes('//testcase') | ForEach-Object name)
foreach ($test in Get-ChildItem -LiteralPath (Join-Path $project 'src/gametest/resources/data/telepads/test_instance') -Filter '*.json') {
    if ('telepads:'+$test.BaseName -notin $gameNames) { throw "Missing required GameTest $($test.BaseName)" }
}
if (@($game.SelectNodes('//failure | //error | //skipped')).Count) { throw 'GameTests failed or skipped' }
$production=Get-ReleaseZipHashes $candidate
$acceptance=Get-ReleaseZipHashes (Join-Path $evidence 'instrumented.jar')
$hookNames=@(Get-ChildItem -LiteralPath (Join-Path $project 'src/gametest/java') -Filter '*.java' -Recurse | ForEach-Object { [regex]::Escape($_.BaseName) })
$hookPattern='(^|/)('+($hookNames -join '|')+')(\$|\.class)'
$hooks=@($production.Keys | Where-Object { $_ -match $hookPattern -or $_ -like 'telepads/fixture/*' -or $_ -like 'data/telepads/test_instance/*' })
if ($hooks.Count) { throw "Acceptance hooks shipped: $($hooks -join ', ')" }
$runtime=@($production.Keys | Where-Object { $_ -like '*.class' -or $_ -like 'assets/*' -or $_ -like 'data/*' -or $_ -in @('logo.png','pack.mcmeta','META-INF/mods.toml','META-INF/MANIFEST.MF','META-INF/LICENSE.md','META-INF/NOTICE.md','META-INF/FORGE-LICENSE.txt') })
$different=@($runtime | Where-Object { -not $acceptance.ContainsKey($_) -or $production[$_] -ne $acceptance[$_] })
if ($different.Count) { throw "Instrumented runtime differs: $($different -join ', ')" }
foreach ($name in @('logo.png','pack.mcmeta','META-INF/MANIFEST.MF','META-INF/mods.toml','META-INF/LICENSE.md','META-INF/NOTICE.md','META-INF/FORGE-LICENSE.txt')) {
    if (-not $production.ContainsKey($name)) { throw "Missing package entry $name" }
}
foreach ($directory in @('src/main/resources','src/generated/resources')) {
    $resourceRoot=Join-Path $project $directory
    if (Test-Path -LiteralPath $resourceRoot) {
        foreach ($file in Get-ChildItem -LiteralPath $resourceRoot -Recurse -File | Where-Object FullName -NotMatch '[/\\]\.cache[/\\]') {
            $relative=[IO.Path]::GetRelativePath($resourceRoot,$file.FullName).Replace('\','/')
            if (-not $production.ContainsKey($relative) -or $production[$relative] -ne (Get-FileHash -LiteralPath $file.FullName).Hash) { throw "Missing/altered source resource $relative" }
        }
    }
}
foreach ($name in @('LICENSE.md','NOTICE.md','FORGE-LICENSE.txt')) {
    if ($production['META-INF/'+$name] -ne (Get-FileHash -LiteralPath (Join-Path $project $name)).Hash) { throw "Notice differs: $name" }
}
$version=[regex]::Match([IO.File]::ReadAllText((Join-Path $project 'build.gradle')),"(?m)^version = '([^']+)'").Groups[1].Value
if ([IO.Path]::GetFileName($candidate) -ne "telepads-26.3-$version.jar") { throw 'Candidate artifact name differs from build version' }
$zip=[IO.Compression.ZipFile]::OpenRead($candidate)
try {
    foreach ($name in @('META-INF/mods.toml','META-INF/MANIFEST.MF')) {
        $reader=[IO.StreamReader]::new($zip.GetEntry($name).Open()); try { $text=$reader.ReadToEnd() } finally { $reader.Dispose() }
        $pattern=if ($name.EndsWith('.toml')) { '(?m)^version="'+[regex]::Escape($version)+'"' } else { '(?m)^Implementation-Version: '+[regex]::Escape($version)+'\s*$' }
        if ($text -notmatch $pattern) { throw 'Inconsistent internal version' }
    }
    foreach ($entry in $zip.Entries | Where-Object { $_.FullName -like 'assets/telepads/lang/*.json' }) {
        $reader=[IO.StreamReader]::new($entry.Open()); try { $language=$reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        $label=$language.'screen.telepads.share'
        if (($label -and $label -ne 'Share with server') -or ($entry.FullName -eq 'assets/telepads/lang/en_us.json' -and $label -ne 'Share with server')) { throw 'Incorrect sharing translation' }
    }
} finally { $zip.Dispose() }
if (@($production.Keys | Where-Object { $_ -like 'assets/telepads/items/*.json' }).Count -ne 7 -or @($production.Keys | Where-Object { $_ -like 'data/telepads/recipe/*.json' }).Count -ne 5) { throw 'Missing item definitions/recipes' }
$result=[ordered]@{ checkedUtc=[DateTime]::UtcNow.ToString('o'); version=$version; candidateSha256=$hash; sourceRevision=$record.sourceRevision; sourceFingerprint=$record.sourceFingerprint; package='PASS'; comparedRuntimeEntries=$runtime.Count; junit=$unitTests; gameTests=$gameNames.Count; classification='Alpha'; releaseEligibility='PENDING: compatibility, actual upgrades, four-hour endurance, ordinary installations and corresponding source gates require candidate-bound evidence' }
$result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'artifact-check.json') -Encoding utf8
Write-Output "RELEASE_PACKAGE_PASS: $($runtime.Count) identical production entries; $unitTests JUnit; $($gameNames.Count) GameTests; release eligibility pending"
