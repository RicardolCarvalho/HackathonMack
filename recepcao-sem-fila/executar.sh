#!/bin/sh
# Compila e sobe a aplicação. Uso: ./executar.sh [porta]
# Sem JDK (javac), roda o jar pronto de dist/.
set -e
cd "$(dirname "$0")"
if ! command -v javac >/dev/null 2>&1; then
  echo "javac não encontrado; rodando o jar pronto de dist/."
  exec java -jar dist/recepcao-sem-fila.jar "$@"
fi
rm -rf out && mkdir -p out
find src/main/java -name '*.java' > out/fontes.txt
javac -encoding UTF-8 -d out @out/fontes.txt
cp -r src/main/resources/. out/
exec java -cp out br.com.recepcaosemfila.App "$@"
