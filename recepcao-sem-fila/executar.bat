@echo off
rem Compila e sobe a aplicacao. Uso: executar.bat [porta]
rem Sem JDK (javac), roda o jar pronto de dist\.
cd /d "%~dp0"
where javac >nul 2>nul
if errorlevel 1 (
  echo javac nao encontrado; rodando o jar pronto de dist\.
  java -jar dist\recepcao-sem-fila.jar %*
  exit /b
)
if exist out rmdir /s /q out
mkdir out
set P=src\main\java\br\com\recepcaosemfila
javac -encoding UTF-8 -d out %P%\*.java %P%\dados\*.java %P%\dominio\*.java %P%\servico\*.java %P%\web\*.java
if errorlevel 1 exit /b 1
xcopy /e /i /q /y src\main\resources out >nul
java -cp out br.com.recepcaosemfila.App %*
