@echo off
setlocal

set "BASE_DIR=%~dp0"
for /d %%D in ("%BASE_DIR%.tools\jdk-*") do set "JAVA_HOME=%%~fD"
for /d %%D in ("%BASE_DIR%.tools\apache-maven-*") do set "MAVEN_HOME=%%~fD"

if not defined JAVA_HOME (
  echo Portable JDK not found under .tools
  exit /b 1
)

if not defined MAVEN_HOME (
  echo Portable Maven not found under .tools
  exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"
call "%MAVEN_HOME%\bin\mvn.cmd" %*
