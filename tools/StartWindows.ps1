param([switch]$Build)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$target = 'D:/Workspaces/Artifacts/ConnectorX/Windows-x64'
$exe = Join-Path $target 'ConnectorX.exe'
if ($Build -or !(Test-Path -LiteralPath $exe)) {
    Push-Location -LiteralPath $repoRoot
    try {
        & ./gradlew.bat :windows:test :windows:createDistributable
        if ($LASTEXITCODE -ne 0) { throw 'Windows build failed' }
        New-Item -ItemType Directory -Force -Path $target | Out-Null
        Copy-Item -Path ./apps/Windows/build/compose/binaries/main/app/ConnectorX/* -Destination $target -Recurse -Force
    } finally { Pop-Location }
}
$ffmpeg = Get-Command ffmpeg.exe -ErrorAction Stop
$env:CONNECTORX_FFMPEG = $ffmpeg.Source
Start-Process -FilePath $exe -WorkingDirectory $target -WindowStyle Normal
