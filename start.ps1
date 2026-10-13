$ErrorActionPreference = 'Stop'
$brdBackend = Join-Path $PSScriptRoot 'backend'
$brdJar = Join-Path $brdBackend 'target\brd-management-0.1.0.jar'
if (-not (Test-Path -LiteralPath $brdJar)) { throw 'Build the project first: mvn -f backend/pom.xml clean verify' }
$brdJava = if (Test-Path 'C:\Program Files\Java\jdk-17\bin\java.exe') { 'C:\Program Files\Java\jdk-17\bin\java.exe' } else { 'java' }
Push-Location $brdBackend
try { & $brdJava -jar $brdJar; if ($LASTEXITCODE -ne 0) { throw "Application exited with code $LASTEXITCODE" } }
finally { Pop-Location }
