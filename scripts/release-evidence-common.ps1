$ErrorActionPreference = 'Stop'
function Get-ReleaseSourceFingerprint([string]$Project) {
    $files = @()
    foreach ($name in @('build.gradle','settings.gradle','gradle.properties')) { $files += Get-Item -LiteralPath (Join-Path $Project $name) }
    foreach ($directory in @('src/main','src/generated','src/gametest','src/test','scripts','gradle')) {
        $path = Join-Path $Project $directory
        if (Test-Path -LiteralPath $path) { $files += Get-ChildItem -LiteralPath $path -Recurse -File | Where-Object FullName -NotMatch '[/\\]\.cache[/\\]' }
    }
    $records = @($files | Sort-Object FullName | ForEach-Object { [IO.Path]::GetRelativePath($Project,$_.FullName).Replace('\','/') + ':' + (Get-FileHash -LiteralPath $_.FullName).Hash })
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes(($records -join "`n"))))
}

function New-ReleaseIdentity([string]$Project, [string]$Candidate, [string]$Java) {
    return [ordered]@{
        sourceRevision=(& git -C $Project rev-parse HEAD)
        sourceDirty=([bool](& git -C $Project status --porcelain))
        sourceFingerprint=(Get-ReleaseSourceFingerprint $Project)
        candidateSha256=(Get-FileHash -LiteralPath $Candidate).Hash
        minecraft='26.3'; forge='66.0.9'
        java=(& $Java -version 2>&1 | Out-String).Trim()
        startedUtc=[DateTime]::UtcNow.ToString('o')
        status='INCOMPLETE'; gates=@()
    }
}

function New-ReleaseServerInstance([string]$Instance, [string]$Forge, [string]$Jar, [string]$Helper, [int]$Port, [string]$World='fixture-world') {
    if (Test-Path -LiteralPath $Instance) { throw "Refusing to overwrite instance: $Instance" }
    New-Item -ItemType Directory -Path (Join-Path $Instance 'mods') -Force | Out-Null
    New-Item -ItemType Junction -Path (Join-Path $Instance 'libraries') -Target (Join-Path $Forge 'libraries') | Out-Null
    Copy-Item -LiteralPath (Join-Path $Forge 'forge-26.3-66.0.9-shim.jar') -Destination $Instance
    Copy-Item -LiteralPath $Jar -Destination (Join-Path $Instance 'mods/telepads.jar')
    Copy-Item -LiteralPath $Helper -Destination (Join-Path $Instance 'mods/telepads-fixture-helper.jar')
    @"
server-ip=127.0.0.1
server-port=$Port
online-mode=false
enable-query=false
enable-rcon=false
view-distance=3
simulation-distance=3
pause-when-empty-seconds=-1
level-name=$World
spawn-protection=0
"@ | Set-Content -LiteralPath (Join-Path $Instance 'server.properties')
    'eula=true' | Set-Content -LiteralPath (Join-Path $Instance 'eula.txt')
}

function Invoke-ReleaseServer {
    param([string]$Java, [string]$Instance, [string]$Log, [string[]]$JvmArguments,
          [string]$PassMarker, [int]$TimeoutSeconds=240, [string]$Heap='2G', [scriptblock]$OnReady)
    $info=[Diagnostics.ProcessStartInfo]::new()
    $info.FileName=$Java; $info.WorkingDirectory=$Instance
    foreach ($arg in (@('-Xms512M',"-Xmx$Heap") + $JvmArguments + @('@libraries/net/minecraftforge/forge/26.3-66.0.9/win_args.txt','nogui'))) { $info.ArgumentList.Add($arg) }
    $info.UseShellExecute=$false; $info.CreateNoWindow=$true
    $info.RedirectStandardInput=$true; $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
    $process=[Diagnostics.Process]::new(); $process.StartInfo=$info
    $writer=[IO.StreamWriter]::new($Log,$false); $started=$false; $passed=$false; $ready=$false
    try {
        $started=$process.Start(); $errorRead=$process.StandardError.ReadToEndAsync(); $read=$process.StandardOutput.ReadLineAsync()
        $deadline=[DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        while ([DateTime]::UtcNow -lt $deadline) {
            if (-not $read.Wait(100)) { continue }
            $line=$read.GetAwaiter().GetResult(); if ($null -eq $line) { break }
            $writer.WriteLine($line); $writer.Flush()
            if (-not $ready -and $line.Contains('Done (')) { $ready=$true; if ($OnReady) { & $OnReady } }
            if (-not $passed -and $line.Contains($PassMarker)) {
                $passed=$true
                $process.StandardInput.WriteLine('save-all flush'); $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush()
                $deadline=[DateTime]::UtcNow.AddSeconds(30)
            }
            $read=$process.StandardOutput.ReadLineAsync()
        }
        if (-not $process.WaitForExit(10000)) { throw 'Isolated server timed out' }
        $writer.WriteLine($errorRead.GetAwaiter().GetResult()); $writer.Flush()
        if (-not $passed -or $process.ExitCode -ne 0) { throw "Isolated server check failed: $Log" }
    } finally {
        if ($started -and -not $process.HasExited) {
            try { $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush() } catch { }
            if (-not $process.WaitForExit(10000)) { $process.Kill($true); $null=$process.WaitForExit(10000) }
        }
        $writer.Dispose(); $process.Dispose()
    }
}
function Get-ReleaseZipHashes([string]$Path) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $hashes=@{}; $zip=[IO.Compression.ZipFile]::OpenRead($Path)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName.EndsWith('/')) { continue }
            if ($hashes.ContainsKey($entry.FullName)) { throw "Duplicate JAR entry $($entry.FullName)" }
            $stream=$entry.Open()
            try { $hashes[$entry.FullName]=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)) } finally { $stream.Dispose() }
        }
    } finally { $zip.Dispose() }
    return $hashes
}
