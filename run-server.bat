@echo off
setlocal
cd /d "%~dp0"

set "CP=target\classes;lib\postgresql-jdbc.jar"

if not exist "target\classes" (
  echo [ERROR] Missing build output: target\classes
  echo Please build the project first.
  pause
  exit /b 1
)

echo [INFO] Starting server on port 5001...
java -cp "%CP%" server.ChatServer 5001
pause
