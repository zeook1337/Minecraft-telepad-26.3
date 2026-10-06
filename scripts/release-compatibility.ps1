param(
    [Parameter(Mandatory)][string]$JavaHome,
    [Parameter(Mandatory)][string]$ForgeDirectory,
    [Parameter(Mandatory)][string]$CandidateJar,
    [Parameter(Mandatory)][string]$BaselineDirectory,
    [Parameter(Mandatory)][string]$HelperJar,
    [Parameter(Mandatory)][string]$InstanceRoot,
    [Parameter(Mandatory)][string]$EvidenceDirectory,
    [string]$InstalledClientConfiguration,
    [ValidateSet('matching','all')][string]$Mode = 'all',
    [int]$TimeoutSeconds = 240
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'release-evidence-common.ps1')
$project = Split-Path -Parent $PSScriptRoot
$java = Join-Path (Resolve-Path -LiteralPath $JavaHome).Path 'bin/java.exe'
$forge = (Resolve-Path -LiteralPath $ForgeDirectory).Path
$candidate = (Resolve-Path -LiteralPath $CandidateJar).Path
$helper = (Resolve-Path -LiteralPath $HelperJar).Path
$baseline = (Resolve-Path -LiteralPath $BaselineDirectory).Path
$instanceRootPath = [IO.Path]::GetFullPath($InstanceRoot)
$evidence = [IO.Path]::GetFullPath($EvidenceDirectory)
$installed = if ($InstalledClientConfiguration) { Get-Content -LiteralPath $InstalledClientConfiguration -Raw | ConvertFrom-Json } else { $null }
if ($installed) {
    foreach ($path in @($installed.java, $installed.assetsDirectory, $installed.libraryDirectory, $installed.nativeDirectory)) {
        if (-not (Test-Path -LiteralPath $path)) { throw "Installed runtime path missing: $path" }
    }
    foreach ($path in $installed.classpath -split ';') {
        if (-not (Test-Path -LiteralPath $path)) { throw "Installed classpath entry missing: $path" }
    }
}
New-Item -ItemType Directory -Path $instanceRootPath,$evidence -Force | Out-Null
$identity = [ordered]@{ sourceRevision=(& git -C $project rev-parse HEAD); sourceDirty=([bool](& git -C $project status --porcelain)); sourceFingerprint=(Get-ReleaseSourceFingerprint $project); candidateSha256=(Get-FileHash -LiteralPath $candidate).Hash; candidate=$candidate; helperSha256=(Get-FileHash -LiteralPath $helper).Hash; minecraft='26.3'; forge='66.0.9'; java=(& $java -version 2>&1 | Out-String).Trim(); startedUtc=[DateTime]::UtcNow.ToString('o'); gates=@() }
$identity.clientLauncher = if ($installed) { 'installed Forge bootstrap in isolated instance' } else { 'ForgeGradle' }
if ($installed) {
    $identity.installedRuntimeConfigurationSha256 = (Get-FileHash -LiteralPath $InstalledClientConfiguration).Hash
    $identity.clientRuntimeSha256 = (Get-FileHash -LiteralPath $installed.java).Hash
    $identity.clientRuntimeLibraries = @($installed.classpath -split ';' | ForEach-Object { [ordered]@{ file=[IO.Path]::GetFileName($_); sha256=(Get-FileHash -LiteralPath $_).Hash } })
}
$pairs = @(@{ name='matching'; server=$candidate; client=$candidate; reject=$false })
if ($Mode -eq 'all') {
    foreach ($package in (Get-Content -LiteralPath (Join-Path $baseline 'manifest.json') -Raw | ConvertFrom-Json).packages) {
        $old = Join-Path $baseline $package.file
        if ((Get-FileHash -LiteralPath $old).Hash -ne $package.sha256) { throw 'Old package checksum differs' }
        $pairs += @{ name=($package.version+'-old-client'); server=$candidate; client=$old; reject=$true }
        $pairs += @{ name=($package.version+'-old-server'); server=$old; client=$candidate; reject=$true }
    }
}
foreach ($pair in $pairs) {
    $instance = Join-Path $instanceRootPath $pair.name; $runEvidence = Join-Path $evidence $pair.name
    if (Test-Path -LiteralPath $instance) { throw "Use a new isolated instance root: $instance exists" }
    New-Item -ItemType Directory -Path (Join-Path $instance 'mods'),$runEvidence -Force | Out-Null
    New-Item -ItemType Junction -Path (Join-Path $instance 'libraries') -Target (Join-Path $forge 'libraries') | Out-Null
    Copy-Item -LiteralPath (Join-Path $forge 'forge-26.3-66.0.9-shim.jar') -Destination $instance
    Copy-Item -LiteralPath $pair.server -Destination (Join-Path $instance 'mods/telepads.jar')
    Copy-Item -LiteralPath $helper -Destination (Join-Path $instance 'mods/telepads-fixture-helper.jar')
    @'
server-ip=127.0.0.1
server-port=25580
online-mode=false
enable-query=false
enable-rcon=false
view-distance=3
simulation-distance=3
pause-when-empty-seconds=-1
level-name=compatibility-world
'@ | Set-Content -LiteralPath (Join-Path $instance 'server.properties')
    'eula=true' | Set-Content -LiteralPath (Join-Path $instance 'eula.txt')
    $serverInfo = [Diagnostics.ProcessStartInfo]::new(); $serverInfo.FileName=$java; $serverInfo.WorkingDirectory=$instance
    foreach ($arg in @('-Xms512M','-Xmx2G','@libraries/net/minecraftforge/forge/26.3-66.0.9/win_args.txt','nogui')) { $serverInfo.ArgumentList.Add($arg) }
    $serverInfo.UseShellExecute=$false; $serverInfo.CreateNoWindow=$true
    $serverInfo.RedirectStandardInput=$true; $serverInfo.RedirectStandardOutput=$true; $serverInfo.RedirectStandardError=$true
    $server=[Diagnostics.Process]::new(); $server.StartInfo=$serverInfo; $client=$null; $clientOutput=$null; $clientError=$null
    $runId=[Guid]::NewGuid().ToString('N')
    $writer=[IO.StreamWriter]::new((Join-Path $runEvidence 'server.log'),$false)
    $gate=[ordered]@{ name=$pair.name; status='INCOMPLETE'; serverSha256=(Get-FileHash -LiteralPath $pair.server).Hash; clientSha256=(Get-FileHash -LiteralPath $pair.client).Hash; reject=$pair.reject; startedUtc=[DateTime]::UtcNow.ToString('o') }
    try {
        $null=$server.Start(); $stderr=$server.StandardError.ReadToEndAsync(); $read=$server.StandardOutput.ReadLineAsync()
        $deadline=[DateTime]::UtcNow.AddSeconds($TimeoutSeconds); $ready=$false
        while ([DateTime]::UtcNow -lt $deadline) {
            if ($read.Wait(100)) {
                $line=$read.GetAwaiter().GetResult(); if ($null -eq $line) { break }
                $writer.WriteLine($line); $writer.Flush(); $read=$server.StandardOutput.ReadLineAsync()
                if ($line.Contains('Done (') -and -not $ready) {
                    $ready=$true; $clientInfo=[Diagnostics.ProcessStartInfo]::new(); $clientInfo.FileName=$java; $clientInfo.WorkingDirectory=$project
                    $arguments=@('-classpath',(Join-Path $project 'gradle/wrapper/gradle-wrapper.jar'),'org.gradle.wrapper.GradleWrapperMain','--no-daemon',"-PtelepadsPeerRunId=$runId",
                        '-PtelepadsReleasePeer','-Pnet.minecraftforge.gradle.merge-source-sets=false',"-PtelepadsPeerDirectory=$(Join-Path $instance 'client')",
                        "-PtelepadsPackagedJar=$($pair.client)$([IO.Path]::PathSeparator)$helper",'-x','compileJava','-x','processResources','runClient','--console=plain')
                    if ($pair.reject) { $arguments += '-PtelepadsPeerReject' }
                    if ($installed) {
                        $clientDirectory = Join-Path $instance 'client'
                        New-Item -ItemType Directory -Path (Join-Path $clientDirectory 'mods') -Force | Out-Null
                        Copy-Item -LiteralPath $pair.client -Destination (Join-Path $clientDirectory 'mods/telepads.jar')
                        Copy-Item -LiteralPath $helper -Destination (Join-Path $clientDirectory 'mods/telepads-fixture-helper.jar')
                        @('enableVsync:false','fullscreen:false','maxFps:60','pauseOnLostFocus:false','inactivityFpsLimit:"afk"','renderDistance:3','simulationDistance:3') | Set-Content -LiteralPath (Join-Path $clientDirectory 'options.txt')
                        $clientInfo.FileName = $installed.java
                        $clientInfo.WorkingDirectory = $clientDirectory
                        $arguments = @('-Xms256M','-Xmx1G','--enable-native-access=ALL-UNNAMED','--add-exports','java.base/jdk.internal.misc=ALL-UNNAMED',
                            "-DlibraryDirectory=$($installed.libraryDirectory)","-Djava.library.path=$($installed.nativeDirectory)/java", "-Djna.tmpdir=$($installed.nativeDirectory)/jna",
                            "-Dorg.lwjgl.system.SharedLibraryExtractPath=$($installed.nativeDirectory)/lwjgl","-Dio.netty.native.workdir=$($installed.nativeDirectory)/netty",'-Duser.language=en',
                            '-Dtelepads.releasePeer=true',"-Dtelepads.peerReject=$($pair.reject.ToString().ToLowerInvariant())",'-Dtelepads.peerAddress=127.0.0.1:25580',"-Dtelepads.peerRunId=$runId",
                            '-classpath',$installed.classpath,'net.minecraftforge.bootstrap.ForgeBootstrap','--username','TelepadPeer','--version','forge-66.0.9',
                            '--gameDir',$clientDirectory,'--assetsDir',$installed.assetsDirectory,'--assetIndex',$installed.assetIndex,'--accessToken','0',
                            '--uuid','8e954597b96c3ff8a4cd7b2cf6d7e475','--userType','legacy','--width','960','--height','540','--launchTarget','forge_client')
                    }
                    foreach ($arg in $arguments) { $clientInfo.ArgumentList.Add($arg) }
                    $clientInfo.UseShellExecute=$false; $clientInfo.CreateNoWindow=$true; $clientInfo.RedirectStandardOutput=$true; $clientInfo.RedirectStandardError=$true
                    $clientInfo.Environment['JAVA_HOME']=Split-Path -Parent (Split-Path -Parent $java)
                    $client=[Diagnostics.Process]::new(); $client.StartInfo=$clientInfo; $null=$client.Start()
                    $clientOutput=$client.StandardOutput.ReadToEndAsync(); $clientError=$client.StandardError.ReadToEndAsync()
                }
            }
            if ($client -and $client.HasExited) { break }
        }
        if (-not $client -or -not $client.HasExited) { throw 'Real-client acceptance timed out' }
        $clientLog=$clientOutput.GetAwaiter().GetResult()+$clientError.GetAwaiter().GetResult()
        [IO.File]::WriteAllText((Join-Path $runEvidence 'client.log'),$clientLog)
        if ($client.ExitCode -ne 0) { throw 'Real Forge client failed' }
        $server.StandardInput.WriteLine('save-all flush'); $server.StandardInput.WriteLine('stop'); $server.StandardInput.Flush()
        $shutdown=[DateTime]::UtcNow.AddSeconds(30)
        while ([DateTime]::UtcNow -lt $shutdown) {
            if (-not $read.Wait(100)) { continue }
            $line=$read.GetAwaiter().GetResult(); if ($null -eq $line) { break }
            $writer.WriteLine($line); $writer.Flush(); $read=$server.StandardOutput.ReadLineAsync()
        }
        if (-not $server.WaitForExit(10000) -or $server.ExitCode -ne 0) { throw 'Server shutdown failed' }
        $writer.WriteLine($stderr.GetAwaiter().GetResult()); $writer.Flush()
        $serverLog=Get-Content -LiteralPath (Join-Path $runEvidence 'server.log') -Raw
        if ($pair.reject) {
            if (-not $clientLog.Contains('RELEASE_PEER_REJECTION_PASS') -or -not $clientLog.Contains('Channels [telepads:main] rejected their server side version number') -or
                $serverLog -notmatch 'ServerConfigurationPacketListenerImpl.*lost connection' -or $serverLog -match 'TelepadPeer.*logged in with entity') { throw 'Missing pre-play Telepads incompatibility evidence' }
        } elseif (-not $clientLog.Contains('RELEASE_PEER_MATCH_PASS') -or $serverLog -notmatch 'TelepadPeer.*logged in with entity') { throw 'Missing matching-generation world entry' }
        if ($identity.sourceFingerprint -ne (Get-ReleaseSourceFingerprint $project) -or $identity.candidateSha256 -ne (Get-FileHash -LiteralPath $candidate).Hash) { throw 'Source/candidate changed during compatibility acceptance' }
        $gate.status='PASS'; Write-Output "COMPATIBILITY_PASS $($pair.name)"
    } catch { $gate.status='FAILED'; $gate.error=$_.Exception.Message; throw }
    finally {
        # Gradle can launch games through a daemon. Identify only this run's JVMs,
        # never stop processes by a broad java.exe name or terminate a shared daemon.
        $pattern='(?<!\S)-Dtelepads.peerRunId='+[regex]::Escape($runId)+'(?=\s|$)'
        foreach ($owned in Get-CimInstance Win32_Process | Where-Object { $_.Name -in @('java.exe','javaw.exe') -and $_.CommandLine -match $pattern }) {
            $process=[Diagnostics.Process]::GetProcessById($owned.ProcessId)
            try { if (-not $process.HasExited) { $process.Kill($true); $null=$process.WaitForExit(10000) } } finally { $process.Dispose() }
        }
        foreach ($process in @($client,$server)) {
            if ($process) {
                if (-not $process.HasExited) { $process.Kill($true); $null=$process.WaitForExit(10000) }
                $process.Dispose()
            }
        }
        if ($clientOutput -and $clientError -and $clientOutput.IsCompleted -and $clientError.IsCompleted) {
            [IO.File]::WriteAllText((Join-Path $runEvidence 'client.log'),$clientOutput.GetAwaiter().GetResult()+$clientError.GetAwaiter().GetResult())
        }
        $writer.Dispose(); $gate.finishedUtc=[DateTime]::UtcNow.ToString('o'); $identity.gates+=,$gate
        $identity | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $evidence 'compatibility.json') -Encoding utf8
    }
}
