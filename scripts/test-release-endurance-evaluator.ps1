param([Parameter(Mandatory)][string]$CandidateJar,[Parameter(Mandatory)][string]$ScratchDirectory)
. (Join-Path $PSScriptRoot 'release-evidence-common.ps1')
$project=Split-Path -Parent $PSScriptRoot; $root=[IO.Path]::GetFullPath($ScratchDirectory)
if (Test-Path -LiteralPath $root) { throw 'Use a new scratch directory' }
New-Item -ItemType Directory -Path $root | Out-Null
$valid=[ordered]@{ status='COMPLETED'; error=''; seconds=14400; warmupSeconds=600; destinations=1000; connectedClients=4;
    operations=@{travel=240;hazardRejection=240;unauthorizedRejection=240;necklace=240;bead=240;coldArrival=240;paging=240;reconnect=240;invariantChecks=240};
    logins=@{ScaleAlice=2;ScaleBob=2;ScaleCarol=2;ScaleDave=2};
    tickSamples=@(0..2880 | ForEach-Object { $operations=@{};foreach($family in @('travel','hazardRejection','unauthorizedRejection','necklace','bead','coldArrival','paging','reconnect','invariantChecks')){$operations[$family]=[math]::Floor($_*5/60)};@{seconds=$_*5;ticks=$_*100;clients=4;scheduledReconnect=$false;operations=$operations} });
    postGcSamples=@(1..144 | ForEach-Object { @{seconds=$_*100-1;bytes=100000000;collector='Synthetic';id=$_} }) }
$cases=@(
    @{name='valid-synthetic';expected='PASS';change={param($w)}},
    @{name='early-exit';expected='INCOMPLETE';change={param($w) $w.status='RUNNING';$w.seconds=120}},
    @{name='small-catalog';expected='INCOMPLETE';change={param($w) $w.destinations=999}},
    @{name='three-clients';expected='INCOMPLETE';change={param($w) $w.connectedClients=3}},
    @{name='no-gc';expected='INCOMPLETE';change={param($w) $w.postGcSamples=@()}},
    @{name='low-tps';expected='FAILED';change={param($w) foreach($s in $w.tickSamples){$s.ticks=[long]($s.seconds*18)}}},
    @{name='heap-growth';expected='FAILED';change={param($w) foreach($s in $w.postGcSamples){if($s.seconds -ge 10800){$s.bytes=121000000}}}},
    @{name='invariant-failure';expected='FAILED';change={param($w) $w.status='FAILED';$w.error='duplicate charge'}},
    @{name='lost-client';expected='FAILED';change={param($w) $w.tickSamples[100].clients=3}},
    @{name='sample-gap';expected='INCOMPLETE';change={param($w) $w.tickSamples=@($w.tickSamples | Where-Object { $_.seconds -lt 100 -or $_.seconds -gt 150 })}},
    @{name='missing-operation';expected='INCOMPLETE';change={param($w) $w.operations.coldArrival=0}},
    @{name='missing-restart';expected='INCOMPLETE';change={param($w)}},
    @{name='wrong-candidate';expected='FAILED';change={param($w)}},
    @{name='altered-evidence';expected='FAILED';change={param($w)}}
)
foreach ($case in $cases) {
    $directory=Join-Path $root $case.name; New-Item -ItemType Directory -Path $directory | Out-Null
    $work=$valid | ConvertTo-Json -Depth 8 -Compress | ConvertFrom-Json
    & $case.change $work
    $work | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $directory 'scale-workload.json') -Encoding utf8
    '{}' | Set-Content -LiteralPath (Join-Path $directory 'expected-catalog.json')
    foreach ($name in @('scale-server.log','scale-restart.log')) { 'SYNTHETIC EVALUATOR FIXTURE — NOT AN ACCEPTANCE RUN' | Set-Content -LiteralPath (Join-Path $directory $name) }
    if ($case.name -ne 'missing-restart') { '{"status":"PASS"}' | Set-Content -LiteralPath (Join-Path $directory 'scale-restart.json') }
    $identity=@{ synthetic=$true;status='COMPLETED';sourceRevision=(& git -C $project rev-parse HEAD);sourceFingerprint=(Get-ReleaseSourceFingerprint $project);candidateSha256=(Get-FileHash -LiteralPath $CandidateJar).Hash;
        files=@(Get-ChildItem -LiteralPath $directory -File | ForEach-Object { @{path=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName).Hash} }) }
    if ($case.name -eq 'wrong-candidate') { $identity.candidateSha256='WRONG' }
    $identity | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $directory 'scale-identity.json') -Encoding utf8
    if ($case.name -eq 'altered-evidence') { 'changed' | Add-Content -LiteralPath (Join-Path $directory 'scale-server.log') }
    & (Join-Path $PSScriptRoot 'evaluate-release-endurance.ps1') -EvidenceDirectory $directory -CandidateJar $CandidateJar -AllowSynthetic
    $result=Get-Content -LiteralPath (Join-Path $directory 'endurance-evaluation.json') -Raw | ConvertFrom-Json
    if ($result.status -ne $case.expected -or -not $result.synthetic) { throw "Incorrect evaluator outcome for $($case.name): $($result.status)" }
    Write-Output "ENDURANCE_EVALUATOR_TEST_PASS $($case.name)"
}
Write-Output "ENDURANCE_EVALUATOR_TESTS_PASS $($cases.Count) synthetic cases; no real endurance acceptance asserted"
