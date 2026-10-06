param(
    [Parameter(Mandatory)][string]$JavaHome,
    [Parameter(Mandatory)][string]$CandidateJar,
    [Parameter(Mandatory)][string]$EvidenceDirectory
)
. (Join-Path $PSScriptRoot 'release-evidence-common.ps1')
$project=Split-Path -Parent $PSScriptRoot
$candidate=(Resolve-Path -LiteralPath $CandidateJar).Path
$evidence=[IO.Path]::GetFullPath($EvidenceDirectory)
if ($candidate.StartsWith((Join-Path $project 'build')+[IO.Path]::DirectorySeparatorChar)) { throw 'Preserve candidate outside build/ before clean test' }
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
$env:JAVA_HOME=(Resolve-Path -LiteralPath $JavaHome).Path; $env:PATH="$env:JAVA_HOME/bin;$env:PATH"
$record=[ordered]@{ sourceRevision=(& git -C $project rev-parse HEAD); sourceDirty=([bool](& git -C $project status --porcelain)); sourceFingerprint=(Get-ReleaseSourceFingerprint $project); candidateSha256=(Get-FileHash -LiteralPath $candidate).Hash; java=(& "$env:JAVA_HOME/bin/java.exe" -version 2>&1 | Out-String).Trim(); minecraft='26.3'; forge='66.0.9'; startedUtc=[DateTime]::UtcNow.ToString('o'); status='INCOMPLETE'; files=@(); scenarios=@() }
Push-Location $project
try {
    & ./gradlew.bat clean test --no-build-cache --rerun-tasks --console=plain *> (Join-Path $evidence 'junit.log')
    if ($LASTEXITCODE) { throw 'JUnit run failed' }
    New-Item -ItemType Directory -Path (Join-Path $evidence 'junit') -Force | Out-Null
    $unit=@(Get-ChildItem -LiteralPath (Join-Path $project 'build/test-results/test') -Filter 'TEST-*.xml')
    if (-not $unit.Count) { throw 'No JUnit reports' }
    foreach ($file in $unit) {
        [xml]$xml=Get-Content -LiteralPath $file.FullName -Raw
        if ([int]$xml.testsuite.tests -eq 0 -or [int]$xml.testsuite.failures -or [int]$xml.testsuite.errors -or [int]$xml.testsuite.skipped) { throw 'JUnit has failed, empty or skipped tests' }
        $relative='junit/'+$file.Name; Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $evidence $relative) -Force
        $record.files+=@{ path=$relative; sha256=(Get-FileHash -LiteralPath (Join-Path $evidence $relative)).Hash }
        foreach ($test in $xml.testsuite.testcase) { $record.scenarios+=,($test.classname+'.'+$test.name) }
    }
    & ./gradlew.bat -PtelepadsGameTests runGameTestServer jar --no-build-cache --rerun-tasks --console=plain *> (Join-Path $evidence 'gametest.log')
    if ($LASTEXITCODE) { throw 'GameTests or instrumented JAR failed' }
    $report=Join-Path $project 'run/gameTestServer/report.xml'
    [xml]$game=Get-Content -LiteralPath $report -Raw
    $expected=@(Get-ChildItem -LiteralPath (Join-Path $project 'src/gametest/resources/data/telepads/test_instance') -Filter '*.json' | ForEach-Object { 'telepads:'+$_.BaseName })
    $actual=@($game.SelectNodes('//testcase') | ForEach-Object name)
    if (@($game.SelectNodes('//failure | //error | //skipped')).Count -or @($expected | Where-Object { $_ -notin $actual }).Count) { throw 'Required GameTests failed or missing' }
    Copy-Item -LiteralPath $report -Destination (Join-Path $evidence 'gametest-report.xml') -Force
    $record.files+=@{ path='gametest-report.xml'; sha256=(Get-FileHash -LiteralPath (Join-Path $evidence 'gametest-report.xml')).Hash }
    $record.scenarios+= $actual
    $version=[regex]::Match([IO.File]::ReadAllText((Join-Path $project 'build.gradle')),"(?m)^version = '([^']+)'").Groups[1].Value
    Copy-Item -LiteralPath (Join-Path $project "build/libs/telepads-26.3-$version.jar") -Destination (Join-Path $evidence 'instrumented.jar') -Force
    $record.files+=@{ path='instrumented.jar'; sha256=(Get-FileHash -LiteralPath (Join-Path $evidence 'instrumented.jar')).Hash }
    if ($record.sourceFingerprint -ne (Get-ReleaseSourceFingerprint $project) -or $record.candidateSha256 -ne (Get-FileHash -LiteralPath $candidate).Hash) { throw 'Source or candidate changed while tests ran' }
    $record.status='PASS'
    Write-Output "RELEASE_REGRESSIONS_PASS: $($record.scenarios.Count) scenarios bound to $($record.candidateSha256)"
} catch { $record.status='FAILED'; $record.error=$_.Exception.Message; throw }
finally {
    Pop-Location; $record.finishedUtc=[DateTime]::UtcNow.ToString('o')
    $record | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $evidence 'regressions.json') -Encoding utf8
}
