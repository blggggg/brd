param(
    [string]$DatabaseUrl = 'jdbc:mysql://8.137.166.134:3306/BVLDB?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai',
    [string]$DatabaseUser = 'root',
    [int]$Port = 8091
)

$ErrorActionPreference = 'Stop'
$brdBackend = Join-Path $PSScriptRoot 'backend'
$brdJar = Join-Path $brdBackend 'target\brd-management-0.1.0.jar'
$brdJava = if (Test-Path 'C:\Program Files\Java\jdk-17\bin\java.exe') { 'C:\Program Files\Java\jdk-17\bin\java.exe' } else { 'java' }
$brdJavaHome = if (Test-Path 'C:\Program Files\Java\jdk-17') { 'C:\Program Files\Java\jdk-17' } else { $null }
$brdMaven = Join-Path $PSScriptRoot 'apache-maven-3.9.11\bin\mvn.cmd'
if (-not (Test-Path -LiteralPath $brdMaven)) {
    $mvnCommand = Get-Command mvn -ErrorAction SilentlyContinue
    if (-not $mvnCommand) { throw 'Maven was not found in PATH.' }
    $brdMaven = $mvnCommand.Source
}
$brdMavenRepository = Join-Path $PSScriptRoot '.maven-repository'

$portOwners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique)
if ($portOwners.Count -gt 0) {
    $owners = $portOwners -join ', '
    throw "Port $Port is already in use by process ID(s): $owners. Stop that process or run .\start.ps1 -Port 8092."
}

$frontend = Join-Path $brdBackend 'src\main\frontend'
Push-Location $frontend
try {
    & npm run build
    if ($LASTEXITCODE -ne 0) { throw "Frontend build exited with code $LASTEXITCODE" }
}
finally { Pop-Location }

$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:PATH
if ($brdJavaHome) { $env:JAVA_HOME = $brdJavaHome; $env:PATH = "$brdJavaHome\bin;$env:PATH" }
Push-Location $brdBackend
try {
    & $brdMaven "-Dmaven.repo.local=$brdMavenRepository" -f pom.xml '-DskipTests' clean package
    if ($LASTEXITCODE -ne 0) { throw "Backend package exited with code $LASTEXITCODE" }
}
finally { Pop-Location }
if (-not (Test-Path -LiteralPath $brdJar)) { throw 'The updated application JAR was not created.' }

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
    $env:JAVA_HOME = $previousJavaHome
    $env:PATH = $previousPath
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    if ($adminPasswordPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($adminPasswordPointer) }
    $plainPassword = $null
    $plainAdminPassword = $null
}
