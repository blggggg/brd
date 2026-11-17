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
$secureAdminPassword = Read-Host 'Initial BRD admin password (ignored if an account already exists)' -AsSecureString
if ($secureAdminPassword.Length -gt 0 -and $secureAdminPassword.Length -lt 12) { throw 'The initial BRD admin password must be at least 12 characters.' }
$adminPasswordPointer = if ($secureAdminPassword.Length -gt 0) { [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureAdminPassword) } else { [IntPtr]::Zero }
$plainAdminPassword = if ($adminPasswordPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($adminPasswordPointer) } else { '' }

$previousUrl = $env:BRD_PROJECT_DB_URL
$previousUser = $env:BRD_PROJECT_DB_USER
$previousPassword = $env:BRD_PROJECT_DB_PASSWORD
$previousAdminPassword = $env:BRD_ADMIN_PASSWORD
$previousPort = $env:PORT

Push-Location $brdBackend
try {
    $env:BRD_PROJECT_DB_URL = $DatabaseUrl
    $env:BRD_PROJECT_DB_USER = $DatabaseUser
    $env:BRD_PROJECT_DB_PASSWORD = $plainPassword
    $env:BRD_ADMIN_PASSWORD = $plainAdminPassword
    $env:PORT = [string]$Port
    & $brdJava -jar $brdJar
    if ($LASTEXITCODE -ne 0) { throw "Application exited with code $LASTEXITCODE" }
}
finally {
    Pop-Location
    $env:BRD_PROJECT_DB_URL = $previousUrl
    $env:BRD_PROJECT_DB_USER = $previousUser
    $env:BRD_PROJECT_DB_PASSWORD = $previousPassword
    $env:BRD_ADMIN_PASSWORD = $previousAdminPassword
    $env:PORT = $previousPort
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    if ($adminPasswordPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($adminPasswordPointer) }
    $plainPassword = $null
    $plainAdminPassword = $null
}
