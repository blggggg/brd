$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$backendDir = Join-Path $projectRoot 'backend'
$buildDir = Join-Path $backendDir 'target-deploy'
$mavenHome = Join-Path $projectRoot 'apache-maven-3.9.11'
$maven = Join-Path $mavenHome 'bin\mvn.cmd'
$mavenRepository = Join-Path $projectRoot '.maven-repository'

try {
    if (-not (Test-Path -LiteralPath $maven)) { throw "Maven not found: $maven" }
    $java17Home = 'C:\Program Files\Java\jdk-17'
    $javaHome = if (Test-Path -LiteralPath $java17Home) { $java17Home } elseif ($env:JAVA_HOME -and (Test-Path -LiteralPath $env:JAVA_HOME)) { $env:JAVA_HOME } else { $java17Home }
    $javac = Join-Path $javaHome 'bin\javac.exe'
    $compilerVersion = if (Test-Path -LiteralPath $javac) { (& $javac -version 2>&1 | Out-String).Trim() } else { '' }
    if ($compilerVersion -notmatch '^javac (17|1[89]|[2-9][0-9])\.') {
        $javaHome = 'C:\Program Files\Java\jdk-17'
        $javac = Join-Path $javaHome 'bin\javac.exe'
        $compilerVersion = if (Test-Path -LiteralPath $javac) { (& $javac -version 2>&1 | Out-String).Trim() } else { '' }
    }
    if ($compilerVersion -notmatch '^javac (17|1[89]|[2-9][0-9])\.') { throw "JDK 17 or newer is required. Selected compiler: $compilerVersion" }

    $env:JAVA_HOME = $javaHome
    $env:MAVEN_HOME = $mavenHome
    $env:PATH = "$javaHome\bin;$mavenHome\bin;$env:PATH"
    Write-Host 'Building BRD frontend and backend...'
    Push-Location $backendDir
    try { & $maven "-Dmaven.repo.local=$mavenRepository" '-Premote-deploy' clean package '-DskipTests' } finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { throw "Build failed with exit code $LASTEXITCODE" }

    $jarName = 'brd-management-0.1.0.jar'
    $jarPath = Join-Path $buildDir $jarName
    if (-not (Test-Path -LiteralPath $jarPath)) { throw "JAR not found: $jarPath" }

    $localConfig = Join-Path $projectRoot 'deploy.local.ps1'
    if (Test-Path -LiteralPath $localConfig) { . $localConfig }
    $remoteHost = if ($env:BRD_REMOTE_HOST) { $env:BRD_REMOTE_HOST } else { $script:BRD_REMOTE_HOST }
    $remoteUser = if ($env:BRD_REMOTE_USER) { $env:BRD_REMOTE_USER } else { $script:BRD_REMOTE_USER }
    $remoteDir = if ($env:BRD_REMOTE_DIR) { $env:BRD_REMOTE_DIR } else { $script:BRD_REMOTE_DIR }
    if ([string]::IsNullOrWhiteSpace($remoteHost) -or [string]::IsNullOrWhiteSpace($remoteUser) -or [string]::IsNullOrWhiteSpace($remoteDir)) {
        throw 'Deployment settings missing. Set them in deploy.local.ps1 or BRD_REMOTE_HOST/BRD_REMOTE_USER/BRD_REMOTE_DIR.'
    }

    $identityFile = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.ssh\brd_deploy'
    $ssh = Get-Command ssh.exe -ErrorAction SilentlyContinue
    $scp = Get-Command scp.exe -ErrorAction SilentlyContinue
    if (-not $ssh -or -not $scp) { throw 'OpenSSH client is required.' }
    if (-not (Test-Path -LiteralPath $identityFile)) { throw "SSH key not found: $identityFile" }

    $remote = "$remoteUser@$remoteHost"
    $remoteTemp = "$remoteDir/$jarName.uploading"
    $remoteJar = "$remoteDir/$jarName"
    Write-Host "Uploading $jarName to $remote..."
    & $scp.Source -i $identityFile $jarPath "${remote}:${remoteTemp}"
    if ($LASTEXITCODE -ne 0) { throw "SCP upload failed with exit code $LASTEXITCODE" }

    $remoteScript = @"
set -e
cd '$remoteDir'
old_pid=`$(pgrep -f '[b]rd-management-0.1.0.jar' | head -n 1 || true)
if [ -n "`$old_pid" ]; then
  kill "`$old_pid" || true
  for i in 1 2 3 4 5 6 7 8 9 10; do
    kill -0 "`$old_pid" 2>/dev/null || break
    sleep 1
  done
  if kill -0 "`$old_pid" 2>/dev/null; then kill -9 "`$old_pid" || true; fi
fi
mv -f '$remoteTemp' '$remoteJar'
nohup '$remoteDir/start.sh' >> '$remoteDir/brd-management.log' 2>&1 < /dev/null &
echo `$! > '$remoteDir/brd-management.pid'
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
  if curl -fsS http://127.0.0.1:8080/api/v1/auth/csrf -o /dev/null; then
    echo "BRD HTTP ready; PID `$(cat '$remoteDir/brd-management.pid')"
    exit 0
  fi
  sleep 2
done
echo 'BRD failed to start; recent log:'
tail -n 80 '$remoteDir/brd-management.log'
exit 1
"@

    Write-Host 'Restarting BRD on the server...'
    $remoteScript | & $ssh.Source -i $identityFile $remote 'bash -s'
    if ($LASTEXITCODE -ne 0) { throw "Remote deployment failed with exit code $LASTEXITCODE" }
    Write-Host "Deployment complete: https://$remoteHost/"
}
catch {
    Write-Error $_
    exit 1
}
