param([int]$TimeoutSeconds = 180, [ValidateSet('write', 'read')][string]$Mode = 'write')
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$serverDir = Join-Path $taskRoot '.tools\test-server'
$javaExe = Join-Path $taskRoot '.tools\jdk\jdk-25.0.4.1+1\bin\java.exe'
$projectDir = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $projectDir 'build\libs\telepads-26.3-1.0.jar'
New-Item -ItemType Directory -Path (Join-Path $serverDir 'mods') -Force | Out-Null
$previousJar = Join-Path $serverDir 'mods/telepads-26.3-7.0.0-dev.jar'
if (Test-Path -LiteralPath $previousJar) {
    Move-Item -LiteralPath $previousJar -Destination (Join-Path $serverDir 'previous-telepads-26.3-7.0.0-dev.jar') -Force
}
Copy-Item -LiteralPath $jar -Destination (Join-Path $serverDir 'mods\telepads-26.3-1.0.jar') -Force
Set-Content -LiteralPath (Join-Path $serverDir 'eula.txt') -Value 'eula=true'
@'
server-ip=127.0.0.1
server-port=25576
online-mode=true
enable-query=false
enable-rcon=false
view-distance=3
simulation-distance=3
pause-when-empty-seconds=-1
level-name=telepads-smoke
'@ | Set-Content -LiteralPath (Join-Path $serverDir 'server.properties')
$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = $javaExe
$startInfo.WorkingDirectory = $serverDir
$startInfo.Arguments = '-Xms512M -Xmx2G @libraries/net/minecraftforge/forge/26.3-66.0.9/win_args.txt nogui'
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$startInfo.RedirectStandardInput = $true
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
$process = [System.Diagnostics.Process]::new()
$process.StartInfo = $startInfo
$logPath = Join-Path $serverDir "smoke-$Mode-console.log"
$logWriter = [System.IO.StreamWriter]::new($logPath, $false)
$started = [DateTime]::UtcNow
$ready = $false
try {
    if (-not $process.Start()) { throw 'Could not start test server' }
    $stderr = $process.StandardError.ReadToEndAsync()
    $readTask = $process.StandardOutput.ReadLineAsync()
    while (([DateTime]::UtcNow - $started).TotalSeconds -lt $TimeoutSeconds) {
        if ($readTask.Wait(100)) {
            $line = $readTask.GetAwaiter().GetResult()
            if ($null -eq $line) { break }
            $logWriter.WriteLine($line)
            $logWriter.Flush()
            if ($line.Contains('Done (') -and -not $ready) {
                $ready = $true
                $process.StandardInput.WriteLine('forceload add 0 0')
                if ($Mode -eq 'write') {
                    $process.StandardInput.WriteLine('setblock 0 100 0 minecraft:air')
                    $process.StandardInput.WriteLine('setblock 0 100 0 telepads:telepad')
                    $process.StandardInput.WriteLine('data merge block 0 100 0 {identity:[I;1,2,3,4],name:"SavedPad"}')
                }
                $process.StandardInput.WriteLine('data get block 0 100 0')
                $process.StandardInput.WriteLine('save-all flush')
                $process.StandardInput.WriteLine('stop')
                $process.StandardInput.Flush()
            }
            $readTask = $process.StandardOutput.ReadLineAsync()
        }
    }
    if (-not $process.WaitForExit(10000)) {
        $process.StandardInput.WriteLine('stop')
        $process.StandardInput.Flush()
        if (-not $process.WaitForExit(10000)) { $process.Kill($true) }
        throw 'Server smoke test timed out'
    }
    $logWriter.WriteLine($stderr.GetAwaiter().GetResult())
    $logWriter.Flush()
    if ($process.ExitCode -ne 0 -or -not $ready) { throw "Server failed to reach readiness (exit $($process.ExitCode)); see $logPath" }
} finally {
    if ($process.Id -and -not $process.HasExited) { $process.Kill($true) }
    $logWriter.Dispose()
    $process.Dispose()
}
$log = Get-Content -LiteralPath $logPath -Raw
if (-not $log.Contains('Telepads 26.3 adaptation registered') -or -not $log.Contains('id: "telepads:telepad"') -or -not $log.Contains('name: "SavedPad"') -or -not $log.Contains('identity: [I; 1, 2, 3, 4]')) {
    throw "Telepads registration/block checks failed; see $logPath"
}
Write-Output "Server smoke test passed: packaged JAR loaded, world ready, telepad placed and serialized, clean shutdown. Log: $logPath"
