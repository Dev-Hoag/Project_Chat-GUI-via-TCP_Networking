@echo off
chcp 65001 > nul
setlocal
cd /d "%~dp0"

set "APP_DIR=%~dp0Code\TCPChatApp"
set "CP=%APP_DIR%\out\production\TCPChatApp;%APP_DIR%\lib\mariadb-java-client-3.5.1.jar"

if not exist "%APP_DIR%\out\production\TCPChatApp" (
    echo [ERROR] Missing build output: %APP_DIR%\out\production\TCPChatApp
    echo Build the project in IntelliJ first.
    pause
    exit /b 1
)

echo Starting TCP chat server...
java -cp "%CP%" server.ChatServer 5001
