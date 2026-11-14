@echo off
rem Compila tudo e roda os cenarios de teste (sem biblioteca externa).
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
set P=src\main\java\br\com\recepcaosemfila
javac -encoding UTF-8 -d out %P%\*.java %P%\dados\*.java %P%\dominio\*.java %P%\servico\*.java %P%\web\*.java src\test\java\br\com\recepcaosemfila\*.java
if errorlevel 1 exit /b 1
xcopy /e /i /q /y src\main\resources out >nul
java -cp out br.com.recepcaosemfila.VerificaCenarios
