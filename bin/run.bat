@echo off

if "%OS%" == "Windows_NT" setlocal

SET CLASSPATH=.;%CLASSPATH%

for %%i in (lib/*.jar) do call cpappend.bat lib/%%i

@REM ECHO %CLASSPATH%

java -cp %CLASSPATH% com.xdd.ct.framework.ApiTester "$1" "$2"

endlocal
