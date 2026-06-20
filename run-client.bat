@echo off
setlocal
cd /d "%~dp0"

set "CP=target\classes;lib\postgresql-jdbc.jar"
set "JAVA_EXE=javaw.exe"

if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" (
  set "JAVA_EXE=%JAVA_HOME%\bin\javaw.exe"
)

if not exist "target\classes" (
  echo [ERROR] Missing build output: target\classes
  echo Please build the project first.
  pause
  exit /b 1
)

if not exist "lib\postgresql-jdbc.jar" (
  echo [ERROR] Missing dependency: lib\postgresql-jdbc.jar
  pause
  exit /b 1
)

echo [INFO] Starting client...
start "" /b "%JAVA_EXE%" -cp "%CP%" client.ChatClient 127.0.0.1 5001
pause
