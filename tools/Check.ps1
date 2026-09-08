param([switch]$Diagnostics)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $repoRoot
try {
    $arguments = @(':app:assembleDebug', ':app:testDebugUnitTest', ':runtime:testDebugUnitTest', ':protocol:test', ':app:lintDebug', ':runtime:lintDebug')
    $arguments += @(':windows:test', ':windows:createDistributable')
    if ($Diagnostics) { $arguments = @('-PconnectorxDiagnostics=true') + $arguments }
    & (Join-Path $repoRoot 'gradlew.bat') @arguments
    if ($LASTEXITCODE -ne 0) { throw "Gradle checks failed: $LASTEXITCODE" }
    & python.exe (Join-Path $PSScriptRoot 'VerifyApk.py') (Join-Path $repoRoot 'apps/Android/build/outputs/apk/debug/app-debug.apk')
    if ($LASTEXITCODE -ne 0) { throw 'APK class-definition verification failed' }
} finally { Pop-Location }
