param(
    [string]$DatabaseUrl = 'jdbc:mysql://8.137.166.134:3306/BVLDB?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai',
    [string]$DatabaseUser = 'root',
    [int]$Port = 8091
)

$ErrorActionPreference = 'Stop'
$brdBackend = Join-Path $PSScriptRoot 'backend'
$brdJar = Join-Path $brdBackend 'target\brd-management-0.1.0.jar'
if (-not (Test-Path -LiteralPath $brdJar)) { throw 'Build the project first: mvn -f backend/pom.xml clean verify' }
$brdJava = if (Test-Path 'C:\Program Files\Java\jdk-17\bin\java.exe') { 'C:\Program Files\Java\jdk-17\bin\java.exe' } else { 'java' }

$securePassword = Read-Host "BVLDB MySQL password for $DatabaseUser" -AsSecureString
if ($securePassword.Length -eq 0) { throw 'A non-empty BVLDB password is required.' }
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
$plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)

$previousUrl = $env:BRD_PROJECT_DB_URL
$previousUser = $env:BRD_PROJECT_DB_USER
$previousPassword = $env:BRD_PROJECT_DB_PASSWORD
$previousPort = $env:PORT

Push-Location $brdBackend
try {
    $env:BRD_PROJECT_DB_URL = $DatabaseUrl
    $env:BRD_PROJECT_DB_USER = $DatabaseUser
    $env:BRD_PROJECT_DB_PASSWORD = $plainPassword
    $env:PORT = [string]$Port
    & $brdJava -jar $brdJar
    if ($LASTEXITCODE -ne 0) { throw "Application exited with code $LASTEXITCODE" }
}
finally {
    Pop-Location
    $env:BRD_PROJECT_DB_URL = $previousUrl
    $env:BRD_PROJECT_DB_USER = $previousUser
    $env:BRD_PROJECT_DB_PASSWORD = $previousPassword
    $env:PORT = $previousPort
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    $plainPassword = $null
}
