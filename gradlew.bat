@echo off
set GRADLE_USER_HOME=%~dp0.gradle-home
call "%~dp0.gradle-dist\gradle-8.10.2\bin\gradle.bat" %*
