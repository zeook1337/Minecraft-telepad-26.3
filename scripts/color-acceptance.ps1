param([ValidateSet('initial','restart','old-client','old-server')][string]$Mode = 'initial', [int]$TimeoutSeconds = 360)
$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
$taskRoot = Split-Path -Parent $projectDir
$env:JAVA_HOME = Join-Path $taskRoot '.tools/jdk/jdk-25.0.4.1+1'
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
$instance = Join-Path $taskRoot '.tools/color-server'
$evidenceDir = Join-Path $projectDir "docs/evidence/dye-colors/$Mode"
New-Item -ItemType Directory -Path $instance,$evidenceDir,(Join-Path $instance 'mods') -Force | Out-Null
if (-not (Test-Path -LiteralPath (Join-Path $instance 'libraries'))) {
    Copy-Item -LiteralPath (Join-Path $taskRoot '.tools/test-server/libraries') -Destination $instance -Recurse
    Copy-Item -LiteralPath (Join-Path $taskRoot '.tools/test-server/forge-26.3-66.0.9-shim.jar') -Destination $instance
}
$newJar = Join-Path $taskRoot '.tools/telepads-colors-acceptance.jar'
if ($Mode -eq 'initial') {
    Push-Location $projectDir
    try { & ./gradlew.bat -PtelepadsGameTests jar --console=plain; if ($LASTEXITCODE) { throw 'Acceptance build failed' } } finally { Pop-Location }
    Copy-Item -LiteralPath (Join-Path $projectDir 'build/libs/telepads-26.3-7.1.0-dev.jar') -Destination $newJar -Force
}
$oldJar = Join-Path $taskRoot '.tools/telepads-protocol-1-acceptance.jar'
$serverJar = if ($Mode -eq 'old-server') { $oldJar } else { $newJar }
$clientJar = if ($Mode -eq 'old-client') { $oldJar } else { $newJar }
Copy-Item -LiteralPath $serverJar -Destination (Join-Path $instance 'mods/telepads-acceptance.jar') -Force
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
level-name=colors-world
spawn-protection=0
'@ | Set-Content -LiteralPath (Join-Path $instance 'server.properties')
if ($Mode -in @('old-client','old-server')) {
    $propertiesPath = Join-Path $instance 'server.properties'
    [IO.File]::WriteAllText($propertiesPath, [IO.File]::ReadAllText($propertiesPath).Replace('level-name=colors-world','level-name=protocol-world'))
}
Set-Content -LiteralPath (Join-Path $instance 'eula.txt') -Value 'eula=true'
$info = [Diagnostics.ProcessStartInfo]::new()
$info.FileName = Join-Path $env:JAVA_HOME 'bin/java.exe'; $info.WorkingDirectory = $instance
$info.Arguments = '-Xms512M -Xmx2G -Dtelepads.colorAcceptance=true -Dtelepads.colorRestart=' + ($Mode -ne 'initial').ToString().ToLowerInvariant() + ' @libraries/net/minecraftforge/forge/26.3-66.0.9/win_args.txt nogui'
$info.UseShellExecute = $false; $info.CreateNoWindow = $true
$info.RedirectStandardInput = $true; $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
$server = [Diagnostics.Process]::new(); $server.StartInfo = $info; $null = $server.Start()
$writer = [IO.StreamWriter]::new((Join-Path $evidenceDir 'server.log'), $false)
$stderr = $server.StandardError.ReadToEndAsync(); $read = $server.StandardOutput.ReadLineAsync()
$clients = @(); $round = 0; $ready = $false; $passed = $false; $started = [DateTime]::UtcNow
function Read-Shared([string]$Path) {
    $stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
    $reader = [IO.StreamReader]::new($stream)
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}
function Start-Clients([int]$Round) {
    $names = if ($Mode -in @('old-client','old-server')) { @('TelepadAlice') } else { @('TelepadAlice','TelepadBob') }
    foreach ($name in $names) {
        $clientInfo = [Diagnostics.ProcessStartInfo]::new(); $clientInfo.FileName = 'cmd.exe'; $clientInfo.WorkingDirectory = $projectDir
        $clientFlags = if ($Mode -eq 'old-client') { '-PtelepadsIntegration' } elseif ($Mode -eq 'old-server') { '-PtelepadsColorAcceptance -PtelepadsColorReject' } else { '-PtelepadsColorAcceptance' }
        $clientInfo.Arguments = '/c "gradlew.bat ' + $clientFlags + ' -Pnet.minecraftforge.gradle.merge-source-sets=false -PtelepadsUser=' + $name + ' "-PtelepadsPackagedJar=' + $clientJar + '" -x compileJava -x processResources runClient --console=plain > "' + (Join-Path $evidenceDir "$name-$Round.log") + '" 2>&1"'
        $clientInfo.UseShellExecute = $false; $clientInfo.CreateNoWindow = $true
        $client = [Diagnostics.Process]::new(); $client.StartInfo = $clientInfo; $null = $client.Start(); $script:clients += $client
    }
}
try {
    while (([DateTime]::UtcNow - $started).TotalSeconds -lt $TimeoutSeconds) {
        if ($read.Wait(100)) {
            $line = $read.GetAwaiter().GetResult(); if ($null -eq $line) { break }
            $writer.WriteLine($line); $writer.Flush()
            if ($line.Contains('Done (') -and -not $ready) { $ready = $true; Start-Clients 0 }
            $read = $server.StandardOutput.ReadLineAsync()
        }
        if ($ready -and $Mode -in @('old-client','old-server')) {
            $serverText = Read-Shared (Join-Path $evidenceDir 'server.log')
            $clientText = if (Test-Path -LiteralPath (Join-Path $evidenceDir 'TelepadAlice-0.log')) { Read-Shared (Join-Path $evidenceDir 'TelepadAlice-0.log') } else { '' }
            if ($serverText -match 'ServerConfigurationPacketListenerImpl.*lost connection' -and $clientText.Contains('Channels [telepads:main] rejected their server side version number') -and $clientText.Contains('mismatched mod channel list')) {
                if ($serverText -match 'TelepadAlice.*logged in with entity' -or $clientText -match 'COLOR_PREVIEW_PASS|INTEGRATION_FRIENDS') { throw 'Incompatible peer reached play state' }
                if ($Mode -eq 'old-server' -and -not $clientText.Contains('COLOR_PROTOCOL_REJECTION_PASS')) { continue }
                $passed = $true; break
            }
        } elseif ($ready -and $clients.Count -gt 0 -and @($clients | Where-Object { -not $_.HasExited }).Count -eq 0) {
            foreach ($name in @('TelepadAlice','TelepadBob')) {
                $clientText = [IO.File]::ReadAllText((Join-Path $evidenceDir "$name-$round.log"))
                if (-not $clientText.Contains("COLOR_CLIENT_PASS $name")) { throw "Color client failed: $name round $round" }
                $screenshots = Join-Path $projectDir "run/colors-$name/screenshots"
                Get-ChildItem -LiteralPath $screenshots -Filter 'colors-*.png' | Copy-Item -Destination $evidenceDir -Force
                foreach ($shot in Get-ChildItem -LiteralPath $screenshots -Filter 'colors-*.png') { Copy-Item -LiteralPath $shot.FullName -Destination (Join-Path $evidenceDir "$name-$round-$($shot.Name)") -Force }
            }
            if ($Mode -eq 'initial' -and $round -eq 0) { $round = 1; Start-Clients 1 }
            else { $passed = $true; break }
        }
    }
    if (-not $passed) { throw "Color acceptance timed out: $Mode" }
    $server.StandardInput.WriteLine('save-all flush'); $server.StandardInput.WriteLine('stop'); $server.StandardInput.Flush()
    while ($null -ne ($line = $read.GetAwaiter().GetResult())) { $writer.WriteLine($line); $writer.Flush(); $read = $server.StandardOutput.ReadLineAsync() }
    if (-not $server.WaitForExit(10000)) { throw 'Server shutdown timed out' }
    $writer.WriteLine($stderr.GetAwaiter().GetResult()); $writer.Flush()
    if ($server.ExitCode) { throw 'Server acceptance crashed' }
    Set-Content -LiteralPath (Join-Path $evidenceDir 'PASS.txt') -Value "PASS $Mode; compatible palettes or pre-play protocol rejection checked."
    Write-Output "COLOR_ACCEPTANCE_PASS $Mode; evidence: $evidenceDir"
} finally {
    if (-not $server.HasExited) { $server.StandardInput.WriteLine('stop'); $server.StandardInput.Flush(); if (-not $server.WaitForExit(10000)) { $server.Kill($true) } }
    foreach ($client in $clients) { if (-not $client.HasExited) { $client.Kill($true) }; $client.Dispose() }
    $writer.Dispose(); $server.Dispose()
}
