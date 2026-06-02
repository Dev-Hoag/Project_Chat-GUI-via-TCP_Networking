@echo off
chcp 65001 > nul
cd /d "%~dp0"

if not exist out (
    echo Folder out not found. Running compile first...
    call compile.bat
)

echo Starting TCPChatGUI server on port 5000...
java -cp "out;lib\postgresql-jdbc.jar" server.ChatServer 5000
pause
