param(
    [Parameter(Mandatory)][int]$ClientProcessId,
    [Parameter(Mandatory)][string]$OutputFile
)
$ErrorActionPreference = 'Stop'
$client = Get-CimInstance Win32_Process -Filter "ProcessId=$ClientProcessId"
if (-not $client -or $client.Name -notin @('java.exe','javaw.exe') -or
    $client.CommandLine -notmatch 'net\.minecraftforge\.bootstrap\.ForgeBootstrap' -or
    $client.CommandLine -notmatch '--launchTarget\s+forge_client') {
    throw 'Select a running ordinary Forge client process'
}
function Read-Argument([string]$Pattern) {
    $match = [regex]::Match($client.CommandLine, $Pattern + '(?:"([^"]+)"|(\S+))')
    if (-not $match.Success) { throw 'Installed client runtime argument is missing' }
    if ($match.Groups[1].Success) { return $match.Groups[1].Value }
    return $match.Groups[2].Value
}
# Extract only local runtime paths. Never store or print the launch command,
# access token, account UUID, username, game directory or user properties.
$classpath = Read-Argument '(?:^|\s)-(?:cp|classpath)\s+'
if ($classpath -notmatch '[\\/]net[\\/]minecraftforge[\\/]forge[\\/]26\.3-66\.0\.9[\\/]') {
    throw 'This acceptance helper requires Minecraft 26.3 / Forge 66.0.9'
}
$nativeJava = Read-Argument '(?:^|\s)-Djava\.library\.path='
$configuration = [ordered]@{
    java = $client.ExecutablePath
    classpath = $classpath
    libraryDirectory = (Read-Argument '(?:^|\s)-DlibraryDirectory=')
    assetsDirectory = (Read-Argument '(?:^|\s)--assetsDir\s+')
    assetIndex = (Read-Argument '(?:^|\s)--assetIndex\s+')
    nativeDirectory = (Split-Path -Parent $nativeJava)
}
$outputPath = [IO.Path]::GetFullPath($OutputFile)
if (Test-Path -LiteralPath $outputPath) { throw 'Choose a new runtime configuration path' }
New-Item -ItemType Directory -Path (Split-Path -Parent $outputPath) -Force | Out-Null
$configuration | ConvertTo-Json | Set-Content -LiteralPath $outputPath -Encoding utf8
Write-Output 'INSTALLED_FORGE_RUNTIME_EXPORTED: runtime paths only; no account credentials'
