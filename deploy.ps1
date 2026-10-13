$ErrorActionPreference = 'Stop'

# Authentication is handled by OpenSSH; this script deliberately does not store a
# password. Put machine-specific deployment values in deploy.local.ps1 (ignored by Git)
# or provide BRD_REMOTE_HOST / BRD_REMOTE_USER / BRD_REMOTE_DIR environment variables.
$localConfig = Join-Path $PSScriptRoot 'deploy.local.ps1'
if (Test-Path -LiteralPath $localConfig) { . $localConfig }
$remoteHost = if ($env:BRD_REMOTE_HOST) { $env:BRD_REMOTE_HOST } else { $script:BRD_REMOTE_HOST }
$remoteUser = if ($env:BRD_REMOTE_USER) { $env:BRD_REMOTE_USER } else { $script:BRD_REMOTE_USER }
$remoteDir = if ($env:BRD_REMOTE_DIR) { $env:BRD_REMOTE_DIR } else { $script:BRD_REMOTE_DIR }
if ([string]::IsNullOrWhiteSpace($remoteHost) -or [string]::IsNullOrWhiteSpace($remoteUser) -or [string]::IsNullOrWhiteSpace($remoteDir)) {
    throw 'Deployment settings missing. Create deploy.local.ps1 or set BRD_REMOTE_HOST, BRD_REMOTE_USER, and BRD_REMOTE_DIR.'
}
$jarName = 'brd-management-0.1.0.jar'
$jarPath = Join-Path $PSScriptRoot "backend\target\$jarName"
$identityFile = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.ssh\brd_deploy'

if (-not (Test-Path -LiteralPath $jarPath)) {
    throw "JAR not found: $jarPath. Build the project first."
}

$ssh = Get-Command ssh.exe -ErrorAction SilentlyContinue
$scp = Get-Command scp.exe -ErrorAction SilentlyContinue
if (-not $ssh -or -not $scp) {
    throw 'OpenSSH client is required. Install the Windows OpenSSH Client feature first.'
}
if (-not (Test-Path -LiteralPath $identityFile)) {
    throw "SSH key not found: $identityFile"
}

$remote = "$remoteUser@$remoteHost"
$remoteTemp = "$remoteDir/$jarName.uploading"
$remoteJar = "$remoteDir/$jarName"

Write-Host "Uploading $jarName to $remote..."
& $scp.Source -i $identityFile $jarPath "${remote}:${remoteTemp}"
if ($LASTEXITCODE -ne 0) { throw "SCP upload failed with exit code $LASTEXITCODE" }

$remoteScript = @"
set -e
cd '$remoteDir'

# Stop only the BRD process. Do not touch MySQL, H2 data, or unrelated Java apps.
old_pid=`$(pgrep -f '[b]rd-management-0.1.0.jar' | head -n 1 || true)
if [ -n "`$old_pid" ]; then
  kill "`$old_pid" || true
  for i in 1 2 3 4 5 6 7 8 9 10; do
    kill -0 "`$old_pid" 2>/dev/null || break
    sleep 1
  done
  if kill -0 "`$old_pid" 2>/dev/null; then
    kill -9 "`$old_pid" || true
  fi
fi

mv -f '$remoteTemp' '$remoteJar'
nohup /usr/lib/jvm/java-17-openjdk-amd64/bin/java \
  -jar '$remoteJar' \
  --server.address=127.0.0.1 \
  --server.port=8080 \
  --server.forward-headers-strategy=framework \
  > '$remoteDir/brd-management.log' 2>&1 < /dev/null &
echo `$! > '$remoteDir/brd-management.pid'
sleep 3
if ! kill -0 `$(cat '$remoteDir/brd-management.pid') 2>/dev/null; then
  echo 'BRD failed to stay running; recent log:'
  tail -n 80 '$remoteDir/brd-management.log'
  exit 1
fi
echo "BRD started with PID `$(cat '$remoteDir/brd-management.pid')"
"@

Write-Host 'Restarting the remote BRD service...'
$remoteScript | & $ssh.Source -i $identityFile $remote 'bash -s'
if ($LASTEXITCODE -ne 0) { throw "Remote deploy failed with exit code $LASTEXITCODE" }

Write-Host "Deploy complete: https://$remoteHost/"
