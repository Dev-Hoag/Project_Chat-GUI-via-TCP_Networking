@echo off
chcp 65001 > nul
cd /d "%~dp0"

if not exist out (
    echo Folder out not found. Running compile first...
    call compile.bat
)

echo Starting TCPChatGUI client...
java -cp "out;lib\postgresql-jdbc.jar" client.ChatClient 127.0.0.1 5000
