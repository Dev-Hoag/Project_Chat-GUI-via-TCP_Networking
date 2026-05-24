@echo off
cd /d "%~dp0"

echo Cleaning compiled output...
if exist out rmdir /s /q out
echo Done.
pause
