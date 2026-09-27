@echo off
@REM OpenShift Operations Portal Maven Wrapper
set MAVEN_CMD="C:\Users\ULTRA PC\.m2\wrapper\dists\apache-maven-3.9.11\a2d47e15\bin\mvn.cmd"
if exist %MAVEN_CMD% (
    %MAVEN_CMD% %*
) else (
    mvn %*
)
