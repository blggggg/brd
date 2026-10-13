@echo off
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"

echo ========== 1. Configure Maven ==========
set "MAVEN_HOME=E:\project\brd-management\apache-maven-3.9.11"
set "PATH=%MAVEN_HOME%\bin;%PATH%"

echo ========== 2. Check Environment ==========
call "%MAVEN_HOME%\bin\mvn.cmd" -version
node -v
call npm.cmd -v

echo ========== 3. Enter Project ==========
cd /d E:\project\brd-management\backend

echo ========== 4. Build Frontend and Backend ==========
call "%MAVEN_HOME%\bin\mvn.cmd" clean package -DskipTests

if errorlevel 1 (
    echo BUILD FAILED
    pause
    exit /b 1
)

echo ========== 5. Check JAR ==========
dir target\brd-management-0.1.0.jar

echo ========== 6. Check Wallpaper ==========
jar tf target\brd-management-0.1.0.jar | findstr /i "wallpaper.mp4"

echo ========== 7. Build Success ==========
echo Deploying BRD Management to the Linux server...

call powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy.ps1"
if errorlevel 1 (
    echo DEPLOY FAILED
    pause
    exit /b 1
)

echo ========== 8. Deploy Success ==========
pause
