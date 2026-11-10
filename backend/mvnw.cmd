@REM ----------------------------------------------------------------------------
@REM Maven Wrapper script for Fairish Chat Backend
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set "DIR=%~dp0"
set "MAVEN_HOME=%DIR%apache-maven-3.9.6"
set "MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd"

if exist "%MAVEN_CMD%" (
    call "%MAVEN_CMD%" %*
) else (
    mvn %*
)

endlocal

