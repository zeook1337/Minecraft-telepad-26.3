param([int]$TimeoutSeconds = 300, [switch]$Restart)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$root = Split-Path -Parent $project
$env:JAVA_HOME = Join-Path $root '.tools/jdk/jdk-25.0.4.1+1'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$instance = Join-Path $root '.tools/integration-server'
New-Item -ItemType Directory -Path $instance -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $root '.tools/test-server/libraries') -Destination $instance -Recurse -Force
Copy-Item -LiteralPath (Join-Path $root '.tools/test-server/forge-26.3-66.0.9-shim.jar') -Destination $instance -Force
New-Item -ItemType Directory -Path (Join-Path $instance 'mods') -Force | Out-Null
Push-Location $project
try { & .\gradlew.bat -PtelepadsGameTests jar --console=plain; if ($LASTEXITCODE) { throw 'Acceptance JAR build failed' } } finally { Pop-Location }
$packaged = Join-Path $instance 'mods/telepads-acceptance.jar'
Copy-Item -LiteralPath (Join-Path $project 'build/libs/telepads-26.3-1.0.jar') -Destination $packaged -Force
@'
server-ip=127.0.0.1
server-port=25577
online-mode=false
white-list=false
enforce-whitelist=false
enable-query=false
enable-rcon=false
view-distance=3
simulation-distance=3
pause-when-empty-seconds=-1
level-name=integration-world
spawn-protection=0
'@ | Set-Content -LiteralPath (Join-Path $instance 'server.properties')
Set-Content -LiteralPath (Join-Path $instance 'eula.txt') -Value 'eula=true'
function Start-Server {
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = Join-Path $env:JAVA_HOME 'bin/java.exe'; $info.WorkingDirectory = $instance
    $info.Arguments = '-Xms512M -Xmx2G -Dtelepads.integration=true -Dtelepads.integrationRestart=' + $Restart.ToString().ToLowerInvariant() + ' @libraries/net/minecraftforge/forge/26.3-66.0.9/win_args.txt nogui'
    $info.UseShellExecute = $false; $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true; $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $info; $null = $process.Start(); return $process
}
$server = Start-Server
$clients = @()
$logName = if ($Restart) { 'integration-restart-console.log' } else { 'integration-console.log' }
$writer = [IO.StreamWriter]::new((Join-Path $instance $logName), $false)
$stderr = $server.StandardError.ReadToEndAsync()
$read = $server.StandardOutput.ReadLineAsync()
$started = [DateTime]::UtcNow
$ready = $false; $passed = $false
try {
    while (([DateTime]::UtcNow - $started).TotalSeconds -lt $TimeoutSeconds) {
        if ($read.Wait(100)) {
            $line = $read.GetAwaiter().GetResult(); if ($null -eq $line) { break }
            $writer.WriteLine($line); $writer.Flush()
            if ($line.Contains('Done (') -and -not $ready) {
                $ready = $true
                foreach ($name in @('TelepadAlice','TelepadBob')) {
                    $clientInfo = [Diagnostics.ProcessStartInfo]::new()
                    $clientInfo.FileName = 'cmd.exe'; $clientInfo.WorkingDirectory = $project
                    $restartArgument = if ($Restart) { ' -PtelepadsIntegrationRestart ' } else { ' ' }
                    $clientInfo.Arguments = '/c "gradlew.bat -PtelepadsIntegration' + $restartArgument + '-Pnet.minecraftforge.gradle.merge-source-sets=false -PtelepadsUser=' + $name + ' "-PtelepadsPackagedJar=' + $packaged + '" -x compileJava -x processResources runClient --console=plain --info > "build/integration-' + $name + '.log" 2>&1"'
                    $clientInfo.UseShellExecute = $false; $clientInfo.CreateNoWindow = $true
                    $client = [Diagnostics.Process]::new(); $client.StartInfo = $clientInfo; $null = $client.Start(); $clients += $client
                }
            }
            if (($line.Contains('INTEGRATION_SERVER_PASS') -or $line.Contains('INTEGRATION_RESTART_PASS')) -and -not $passed) {
                $passed = $true; $passedAt = [DateTime]::UtcNow
            }
            $read = $server.StandardOutput.ReadLineAsync()
        }
        if ($passed -and -not $stopping -and ([DateTime]::UtcNow - $passedAt).TotalSeconds -gt 8) { $stopping = $true; $server.StandardInput.WriteLine('save-all flush'); $server.StandardInput.WriteLine('stop'); $server.StandardInput.Flush() }
    }
    if (-not $server.WaitForExit(10000)) { throw 'Integration server timeout' }
    $writer.WriteLine($stderr.GetAwaiter().GetResult()); $writer.Flush()
    if (-not $passed -or $server.ExitCode) { throw "Integration failed; inspect $instance/integration-console.log and build/integration-*.log" }
    foreach ($client in $clients) { if (-not $client.WaitForExit(60000)) { $client.Kill($true) }; $client.Dispose() }
    Write-Output "Two-client packaged-JAR acceptance passed. Log: $instance/$logName"
} finally {
    if (-not $server.HasExited) { $server.StandardInput.WriteLine('stop'); $server.StandardInput.Flush(); if (-not $server.WaitForExit(10000)) { $server.Kill($true) } }
    foreach ($client in $clients) { try { if (-not $client.HasExited) { $client.Kill($true) } } catch {} }
    $writer.Dispose(); $server.Dispose()
}
