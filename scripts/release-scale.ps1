param(
    [Parameter(Mandatory)][string]$JavaHome,
    [Parameter(Mandatory)][string]$ForgeDirectory,
    [Parameter(Mandatory)][string]$CandidateJar,
    [Parameter(Mandatory)][string]$HelperJar,
    [Parameter(Mandatory)][string]$InstanceRoot,
    [Parameter(Mandatory)][string]$EvidenceDirectory,
    [string]$InstalledClientConfiguration,
    [switch]$ManualClients,
    [switch]$PrepareOnly,
    [ValidateSet('quick','endurance')][string]$Mode='quick',
    [ValidateRange(1000,10000)][int]$Destinations=1000,
    [ValidateRange(120,14400)][int]$QuickSeconds=180,
    [string]$ServerHeap='2G'
)
. (Join-Path $PSScriptRoot 'release-evidence-common.ps1')
$project=Split-Path -Parent $PSScriptRoot
$java=Join-Path (Resolve-Path -LiteralPath $JavaHome).Path 'bin/java.exe'
$forge=(Resolve-Path -LiteralPath $ForgeDirectory).Path
$candidate=(Resolve-Path -LiteralPath $CandidateJar).Path; $helper=(Resolve-Path -LiteralPath $HelperJar).Path
$root=[IO.Path]::GetFullPath($InstanceRoot); $evidence=[IO.Path]::GetFullPath($EvidenceDirectory)
if (Test-Path -LiteralPath $root) { throw 'Use a new isolated instance root' }
if (Test-Path -LiteralPath $evidence) { throw 'Use a new evidence directory' }
if (-not $ManualClients -and -not $PrepareOnly -and -not $InstalledClientConfiguration) { throw 'Supply an installed Forge runtime configuration or explicitly use -ManualClients' }
$runtime=if ($InstalledClientConfiguration) { Get-Content -LiteralPath $InstalledClientConfiguration -Raw | ConvertFrom-Json } else { $null }
if ($runtime) {
    foreach ($path in (@($runtime.java,$runtime.assetsDirectory,$runtime.libraryDirectory,$runtime.nativeDirectory)+@($runtime.classpath -split ';'))) {
        if (-not (Test-Path -LiteralPath $path)) { throw "Runtime path missing: $path" }
    }
}
New-Item -ItemType Directory -Path $evidence | Out-Null
New-ReleaseServerInstance $root $forge $candidate $helper 25582 'scale-world'
$duration=if ($Mode -eq 'endurance') { 14400 } else { $QuickSeconds }
$warmup=if ($Mode -eq 'endurance') { 600 } else { 60 }
$identity=New-ReleaseIdentity $project $candidate $java
$identity.mode=$Mode; $identity.helperSha256=(Get-FileHash -LiteralPath $helper).Hash
$identity.hardware=@{ processors=@(Get-CimInstance Win32_Processor | Select-Object Name,NumberOfCores,NumberOfLogicalProcessors); memoryBytes=(Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory; os=(Get-CimInstance Win32_OperatingSystem).Caption }
$identity.serverHeap=$ServerHeap; $identity.clientHeap='1G'; $identity.warmupSeconds=$warmup
$identity.durationSeconds=$duration; $identity.destinations=$Destinations
$identity.clients=if ($ManualClients) { 'manual real clients, opt-in helper required' } else { 'four isolated installed Forge bootstrap clients' }
if ($runtime) {
    $identity.installedRuntimeConfigurationSha256=(Get-FileHash -LiteralPath $InstalledClientConfiguration).Hash
    $identity.clientJava=(& $runtime.java -version 2>&1 | Out-String).Trim()
    $identity.runtimeLibraries=@($runtime.classpath -split ';' | ForEach-Object { @{file=[IO.Path]::GetFileName($_); sha256=(Get-FileHash -LiteralPath $_).Hash} })
}
$owned=[Collections.Generic.List[object]]::new(); $nonce=[Guid]::NewGuid().ToString('N')
function Start-ScaleClients {
    if ($ManualClients) { Write-Output 'Connect four opted-in fixture clients to 127.0.0.1:25582 as ScaleAlice, ScaleBob, ScaleCarol, ScaleDave'; return }
    foreach ($name in @('ScaleAlice','ScaleBob','ScaleCarol','ScaleDave')) {
        $directory=Join-Path $root "clients/$name"
        New-Item -ItemType Directory -Path (Join-Path $directory 'mods') -Force | Out-Null
        Copy-Item -LiteralPath $candidate -Destination (Join-Path $directory 'mods/telepads.jar')
        Copy-Item -LiteralPath $helper -Destination (Join-Path $directory 'mods/telepads-fixture-helper.jar')
        @('enableVsync:false','fullscreen:false','maxFps:30','pauseOnLostFocus:false','renderDistance:3','simulationDistance:3') | Set-Content -LiteralPath (Join-Path $directory 'options.txt')
        $info=[Diagnostics.ProcessStartInfo]::new(); $info.FileName=$runtime.java; $info.WorkingDirectory=$directory
        foreach ($arg in @('-Xms256M','-Xmx1G','--enable-native-access=ALL-UNNAMED','--add-exports','java.base/jdk.internal.misc=ALL-UNNAMED',
            "-DlibraryDirectory=$($runtime.libraryDirectory)","-Djava.library.path=$($runtime.nativeDirectory)/java","-Djna.tmpdir=$($runtime.nativeDirectory)/jna",
            "-Dorg.lwjgl.system.SharedLibraryExtractPath=$($runtime.nativeDirectory)/lwjgl","-Dio.netty.native.workdir=$($runtime.nativeDirectory)/netty",'-Duser.language=en',
            '-Dtelepads.releaseScale=true',"-Dtelepads.peerRunId=$nonce",'-Dtelepads.peerAddress=127.0.0.1:25582',
            '-classpath',$runtime.classpath,'net.minecraftforge.bootstrap.ForgeBootstrap','--username',$name,'--version','forge-66.0.9',
            '--gameDir',$directory,'--assetsDir',$runtime.assetsDirectory,'--assetIndex',$runtime.assetIndex,'--accessToken','0',
            '--uuid',([Guid]::NewGuid().ToString('N')),'--userType','legacy','--width','640','--height','480','--launchTarget','forge_client')) { $info.ArgumentList.Add($arg) }
        $info.UseShellExecute=$false; $info.CreateNoWindow=$true; $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
        $process=[Diagnostics.Process]::new(); $process.StartInfo=$info
        $null=$process.Start()
        $owned.Add(@{ name=$name; process=$process; output=$process.StandardOutput.ReadToEndAsync(); error=$process.StandardError.ReadToEndAsync() })
    }
}
try {
    if ($PrepareOnly) {
        Invoke-ReleaseServer -Java $java -Instance $root -Log (Join-Path $evidence 'scale-server.log') -Heap $ServerHeap `
            -TimeoutSeconds 600 -PassMarker 'RELEASE_SCALE_SEEDED' `
            -JvmArguments @('-Dtelepads.releaseScale=true',"-Dtelepads.scaleEvidence=$evidence","-Dtelepads.scaleDestinations=$Destinations")
    } else {
    Invoke-ReleaseServer -Java $java -Instance $root -Log (Join-Path $evidence 'scale-server.log') -Heap $ServerHeap `
        -TimeoutSeconds ($duration+$warmup+900) -PassMarker 'RELEASE_SCALE_INTERVAL_COMPLETE' -OnReady { Start-ScaleClients } `
        -JvmArguments @('-Dtelepads.releaseScale=true',"-Dtelepads.scaleEvidence=$evidence","-Dtelepads.scaleSeconds=$duration","-Dtelepads.scaleWarmup=$warmup","-Dtelepads.scaleDestinations=$Destinations")
    }
    Invoke-ReleaseServer -Java $java -Instance $root -Log (Join-Path $evidence 'scale-restart.log') -Heap $ServerHeap `
        -PassMarker 'RELEASE_SCALE_RESTART_PASS' -TimeoutSeconds 300 `
        -JvmArguments @('-Dtelepads.releaseScale=true','-Dtelepads.scaleRestart=true',"-Dtelepads.scaleEvidence=$evidence")
    if ($identity.sourceFingerprint -ne (Get-ReleaseSourceFingerprint $project) -or $identity.candidateSha256 -ne (Get-FileHash -LiteralPath $candidate).Hash) { throw 'Source/candidate changed during workload' }
    $identity.status=if ($PrepareOnly) { 'PREPARED: seeding/restart only, no clients/workload' } else { 'COMPLETED' }
} catch { $identity.status='FAILED'; $identity.error=$_.Exception.Message; throw }
finally {
    foreach ($item in $owned) {
        if (-not $item.process.HasExited) { $item.process.Kill($true); $null=$item.process.WaitForExit(10000) }
        [IO.File]::WriteAllText((Join-Path $evidence ($item.name+'.log')),$item.output.GetAwaiter().GetResult()+$item.error.GetAwaiter().GetResult())
        $item.process.Dispose()
    }
    $identity.finishedUtc=[DateTime]::UtcNow.ToString('o')
    $identity.files=@(Get-ChildItem -LiteralPath $evidence -File | Where-Object Name -NotIn @('scale-identity.json','endurance-evaluation.json') | ForEach-Object { @{path=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName).Hash} })
    $identity | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $evidence 'scale-identity.json') -Encoding utf8
    if (Test-Path -LiteralPath (Join-Path $evidence 'scale-workload.json')) {
        & (Join-Path $PSScriptRoot 'evaluate-release-endurance.ps1') -EvidenceDirectory $evidence -CandidateJar $candidate
    }
}
