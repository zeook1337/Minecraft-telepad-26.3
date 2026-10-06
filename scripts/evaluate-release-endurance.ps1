param(
    [Parameter(Mandatory)][string]$EvidenceDirectory,
    [Parameter(Mandatory)][string]$CandidateJar,
    [switch]$AllowSynthetic
)
. (Join-Path $PSScriptRoot 'release-evidence-common.ps1')
$evidence=(Resolve-Path -LiteralPath $EvidenceDirectory).Path
$identity=Get-Content -LiteralPath (Join-Path $evidence 'scale-identity.json') -Raw | ConvertFrom-Json
$work=Get-Content -LiteralPath (Join-Path $evidence 'scale-workload.json') -Raw | ConvertFrom-Json
$incomplete=[Collections.Generic.List[string]]::new(); $failures=[Collections.Generic.List[string]]::new()
if ($identity.synthetic -and -not $AllowSynthetic) { throw 'Synthetic evaluator fixtures are not acceptance evidence' }
if ($identity.candidateSha256 -ne (Get-FileHash -LiteralPath $CandidateJar).Hash -or
    $identity.sourceFingerprint -ne (Get-ReleaseSourceFingerprint (Split-Path -Parent $PSScriptRoot))) { $failures.Add('Candidate/source identity differs') }
if ($identity.sourceRevision -ne (& git -C (Split-Path -Parent $PSScriptRoot) rev-parse HEAD)) { $failures.Add('Base source revision differs') }
foreach ($file in $identity.files) {
    if (-not (Test-Path -LiteralPath (Join-Path $evidence $file.path)) -or (Get-FileHash -LiteralPath (Join-Path $evidence $file.path)).Hash -ne $file.sha256) { $failures.Add("Evidence file changed: $($file.path)") }
}
foreach ($required in @('scale-workload.json','expected-catalog.json','scale-server.log','scale-restart.json','scale-restart.log')) {
    if ($required -notin @($identity.files.path)) { $incomplete.Add("Unbound evidence: $required") }
}
if ($identity.status -ne 'COMPLETED') { $incomplete.Add('Launcher did not complete') }
if ($work.status -eq 'FAILED' -or $work.error) { $failures.Add('Workload reported an error') }
if ($work.status -ne 'COMPLETED') { $incomplete.Add('Workload did not complete') }
if ([double]$work.seconds -lt 14400) { $incomplete.Add('Fewer than four measured continuous hours') }
if ([int]$work.destinations -lt 1000) { $incomplete.Add('Fewer than 1,000 destinations') }
if ([int]$work.connectedClients -lt 4) { $incomplete.Add('Fewer than four real clients') }
if ([int]$work.warmupSeconds -lt 600) { $incomplete.Add('Less than ten minutes recorded warm-up') }
foreach ($family in @('travel','hazardRejection','unauthorizedRejection','necklace','bead','coldArrival','paging','reconnect','invariantChecks')) {
    if (-not $work.operations.$family -or [long]$work.operations.$family -lt 1) { $incomplete.Add("No completed $family operations") }
}
if (@($work.logins.PSObject.Properties | Where-Object { $_.Value -ge 2 }).Count -lt 4) { $incomplete.Add('Each of four clients must reconnect') }
$samples=@($work.tickSamples | Sort-Object { [double]$_.seconds }); $windows=@()
if ($samples.Count -lt 2 -or [double]$samples[0].seconds -gt 1) { $incomplete.Add('Missing initial tick sample') }
$previous=$null
foreach ($sample in $samples) {
    if ([int]$sample.clients -gt 4 -or [int]$sample.clients -lt 3) { $failures.Add('Unexpected client population during measured workload') }
    if ([int]$sample.clients -eq 3 -and -not $sample.scheduledReconnect) { $failures.Add('Unscheduled client loss') }
    if ($previous) {
        if ([double]$sample.seconds -le [double]$previous.seconds -or [long]$sample.ticks -le [long]$previous.ticks) { $failures.Add('Non-monotonic tick samples') }
        if ([double]$sample.seconds-[double]$previous.seconds -gt 15) { $incomplete.Add('Gap in continuous tick sampling') }
    }
    $previous=$sample
}
function Tick-At([double]$Second) {
    $before=$samples | Where-Object { [double]$_.seconds -le $Second } | Select-Object -Last 1
    $after=$samples | Where-Object { [double]$_.seconds -ge $Second } | Select-Object -First 1
    if (-not $before -or -not $after -or [double]$after.seconds-[double]$before.seconds -gt 15) { return $null }
    if ([double]$after.seconds -eq [double]$before.seconds) { return [double]$before.ticks }
    return [double]$before.ticks+([double]$after.ticks-[double]$before.ticks)*($Second-[double]$before.seconds)/([double]$after.seconds-[double]$before.seconds)
}
# Interpolate cumulative ticks at exact wall-clock boundaries; do not discard
# slow edge samples or substitute the average of per-sample TPS values.
$windowCount=[math]::Floor([double]$work.seconds/300)
for ($i=0; $i -lt $windowCount; $i++) {
    $start=$i*300; $end=($i+1)*300
    $first=Tick-At $start; $last=Tick-At $end
    if ($null -eq $first -or $null -eq $last) {
        $incomplete.Add("Missing full TPS window $i"); continue
    }
    $tps=[math]::Min(20,($last-$first)/300)
    $windows+=@{ index=$i; start=$start; end=$end; tps=$tps }
    if ($tps -lt 19) { $failures.Add("TPS below 19 in window $i") }
}
if ($windowCount -lt 48) { $incomplete.Add('Fewer than 48 five-minute windows') }
$hourly=@()
for ($hour=0; $hour -lt [math]::Floor([double]$work.seconds/3600); $hour++) {
    $a=$samples | Where-Object { [double]$_.seconds -ge $hour*3600 } | Select-Object -First 1
    $b=$samples | Where-Object { [double]$_.seconds -le ($hour+1)*3600 } | Select-Object -Last 1
    $delta=@{}
    foreach ($family in @('travel','hazardRejection','unauthorizedRejection','necklace','bead','coldArrival','paging','reconnect','invariantChecks')) {
        $delta[$family]=[long]$b.operations.$family-[long]$a.operations.$family
        if ($delta[$family] -lt 1) { $incomplete.Add("No sustained $family workload in hour $hour") }
    }
    $hourly+=@{hour=$hour;operations=$delta}
}
function Median($Values) {
    $sorted=@($Values | Sort-Object); $n=$sorted.Count
    if ($n%2) { return [double]$sorted[[int][math]::Floor($n/2)] }
    return ([double]$sorted[$n/2-1]+[double]$sorted[$n/2])/2
}
$comparisons=@()
$gc=@($work.postGcSamples)
foreach ($group in $gc | Group-Object collector) {
    $first=@($group.Group | Where-Object { [double]$_.seconds -ge 0 -and [double]$_.seconds -lt 3600 })
    $last=@($group.Group | Where-Object { [double]$_.seconds -ge [double]$work.seconds-3600 -and [double]$_.seconds -le [double]$work.seconds })
    if ($first.Count -lt 5 -or $last.Count -lt 5) { continue }
    if (@($group.Group | Where-Object { [long]$_.bytes -le 0 }).Count -or @($group.Group.id | Select-Object -Unique).Count -ne $group.Count) { $failures.Add('Invalid/duplicate post-GC samples'); continue }
    $a=Median $first.bytes; $b=Median $last.bytes
    $comparisons+=@{ collector=$group.Name; firstSamples=$first.Count; lastSamples=$last.Count; firstMedian=$a; lastMedian=$b; ratio=$b/$a }
    if ($b -gt $a*1.2) { $failures.Add("Post-GC heap grew over 20 percent for $($group.Name)") }
}
if (-not $comparisons.Count) { $incomplete.Add('Insufficient comparable naturally observed post-GC samples (five per hour/collector)') }
$restart=Join-Path $evidence 'scale-restart.json'
if (-not (Test-Path -LiteralPath $restart) -or (Get-Content -LiteralPath $restart -Raw | ConvertFrom-Json).status -ne 'PASS') { $incomplete.Add('Dedicated-server persistence restart missing') }
$status=if ($failures.Count) { 'FAILED' } elseif ($incomplete.Count) { 'INCOMPLETE' } else { 'PASS' }
$result=[ordered]@{ status=$status; synthetic=([bool]$identity.synthetic); classification='Alpha'; candidateSha256=$identity.candidateSha256; sourceRevision=$identity.sourceRevision; sourceFingerprint=$identity.sourceFingerprint; failures=@($failures); incomplete=@($incomplete); fiveMinuteWindows=$windows; hourlyOperations=$hourly; heapComparisons=$comparisons }
$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $evidence 'endurance-evaluation.json') -Encoding utf8
Write-Output "RELEASE_ENDURANCE_${status}: $($failures.Count) failures; $($incomplete.Count) incomplete conditions"
# A short run intentionally exits successfully after recording INCOMPLETE. The
# calling release decision must inspect the result status, never this exit code.
