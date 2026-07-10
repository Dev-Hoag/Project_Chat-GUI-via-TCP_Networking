@echo off
chcp 65001 > nul
setlocal
cd /d "%~dp0"

set "APP_DIR=%~dp0Code\TCPChatApp"
set "CP=%APP_DIR%\out\production\TCPChatApp;%APP_DIR%\lib\mariadb-java-client-3.5.1.jar"
set "HOST=%~1"
set "PORT=%~2"

if "%HOST%"=="" set "HOST=127.0.0.1"
if "%PORT%"=="" set "PORT=5001"

if not exist "%APP_DIR%\out\production\TCPChatApp" (
    echo [ERROR] Missing build output: %APP_DIR%\out\production\TCPChatApp
    echo Build the project in IntelliJ first.
    pause
    exit /b 1
)

echo Starting TCP chat client on %HOST%:%PORT% ...
start "" java -cp "%CP%" client.ChatClient %HOST% %PORT%
