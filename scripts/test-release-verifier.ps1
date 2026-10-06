param(
    [Parameter(Mandatory)][string]$CandidateJar,
    [Parameter(Mandatory)][string]$EvidenceDirectory,
    [Parameter(Mandatory)][string]$ScratchDirectory
)
. (Join-Path $PSScriptRoot 'release-evidence-common.ps1')
$candidate=(Resolve-Path -LiteralPath $CandidateJar).Path
$evidence=(Resolve-Path -LiteralPath $EvidenceDirectory).Path
$scratch=[IO.Path]::GetFullPath($ScratchDirectory)
if ($scratch.StartsWith($evidence+[IO.Path]::DirectorySeparatorChar)) { throw 'Scratch must be outside the evidence tree' }
if (Test-Path -LiteralPath $scratch) { throw 'Use a fresh scratch directory' }
& (Join-Path $PSScriptRoot 'verify-release-artifact.ps1') -CandidateJar $candidate -EvidenceDirectory $evidence
function Change-ZipEntry([string]$Jar,[string]$Name,[byte[]]$Bytes) {
    $zip=[IO.Compression.ZipFile]::Open($Jar,[IO.Compression.ZipArchiveMode]::Update)
    try {
        $previous=$zip.GetEntry($Name); if ($previous) { $previous.Delete() }
        $stream=$zip.CreateEntry($Name).Open(); try { $stream.Write($Bytes,0,$Bytes.Length) } finally { $stream.Dispose() }
    } finally { $zip.Dispose() }
}
foreach ($case in @('altered-runtime','shipped-hooks','failed-junit','missing-junit','missing-gametest','failed-status','stale-source','wrong-candidate')) {
    $directory=Join-Path $scratch $case; New-Item -ItemType Directory -Path $directory -Force | Out-Null
    Copy-Item -LiteralPath $candidate -Destination $directory
    Copy-Item -LiteralPath $evidence -Destination (Join-Path $directory 'evidence') -Recurse
    $jar=Join-Path $directory ([IO.Path]::GetFileName($candidate)); $reports=Join-Path $directory 'evidence'
    $recordPath=Join-Path $reports 'regressions.json'; $record=Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json
    switch ($case) {
        'altered-runtime' {
            Change-ZipEntry $jar 'subaraki/telepads/server/SafeArrival.class' ([Text.Encoding]::UTF8.GetBytes('altered class'))
            $record.candidateSha256=(Get-FileHash -LiteralPath $jar).Hash
        }
        'shipped-hooks' {
            Change-ZipEntry $jar 'subaraki/telepads/TelepadGameTests.class' ([Text.Encoding]::UTF8.GetBytes('shipped hook'))
            $record.candidateSha256=(Get-FileHash -LiteralPath $jar).Hash
        }
        'failed-junit' {
            $file=Get-ChildItem -LiteralPath (Join-Path $reports 'junit') -Filter 'TEST-*.xml' | Select-Object -First 1
            [xml]$xml=Get-Content -LiteralPath $file.FullName -Raw; $xml.testsuite.SetAttribute('failures','1'); $xml.Save($file.FullName)
            ($record.files | Where-Object path -eq ('junit/'+$file.Name)).sha256=(Get-FileHash -LiteralPath $file.FullName).Hash
        }
        'missing-junit' {
            $file=Get-ChildItem -LiteralPath (Join-Path $reports 'junit') -Filter 'TEST-*.xml' | Select-Object -First 1
            # Move only a verified scratch file; preserve all real evidence.
            $target=[IO.Path]::GetFullPath($file.FullName)
            if (-not $target.StartsWith($scratch+[IO.Path]::DirectorySeparatorChar)) { throw 'Scratch file escaped its root' }
            Move-Item -LiteralPath $target -Destination ($target+'.missing')
        }
        'missing-gametest' {
            $path=Join-Path $reports 'gametest-report.xml'; [xml]$xml=Get-Content -LiteralPath $path -Raw
            $node=$xml.SelectSingleNode('//testcase[@name="telepads:arrival_hazards"]'); $null=$node.ParentNode.RemoveChild($node); $xml.Save($path)
            ($record.files | Where-Object path -eq 'gametest-report.xml').sha256=(Get-FileHash -LiteralPath $path).Hash
        }
        'failed-status' { $record.status='FAILED' }
        'stale-source' { $record.sourceFingerprint='0'*64 }
        'wrong-candidate' { $record.candidateSha256='0'*64 }
    }
    $record | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $recordPath -Encoding utf8
    $rejected=$false
    try { & (Join-Path $PSScriptRoot 'verify-release-artifact.ps1') -CandidateJar $jar -EvidenceDirectory $reports }
    catch { $rejected=$true; Write-Output "VERIFIER_REJECTION_PASS ${case}: $($_.Exception.Message)" }
    if (-not $rejected) { throw "Verifier accepted invalid case $case" }
}
Write-Output 'RELEASE_VERIFIER_SELF_TEST_PASS: valid package and eight invalid-evidence/package cases checked'
