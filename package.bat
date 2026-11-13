@echo off
setlocal
title BRD 一键构建并部署
echo BRD 管理系统 - 一键构建并部署到服务器
echo 此操作会构建前后端并重启远程 BRD 服务。
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0package.ps1"
set "RESULT=%ERRORLEVEL%"
pause
exit /b %RESULT%
