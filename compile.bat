@echo off
chcp 65001 > nul
cd /d "%~dp0"

echo Compiling TCPChatGUI...
if not exist out mkdir out

javac -encoding UTF-8 -cp ".;lib\postgresql-jdbc.jar" -d out src\common\*.java src\server\*.java src\client\*.java

if errorlevel 1 (
    echo.
    echo Compile failed. Make sure JDK is installed and lib\postgresql-jdbc.jar exists.
    pause
    exit /b 1
)

echo Compile successful.
pause
