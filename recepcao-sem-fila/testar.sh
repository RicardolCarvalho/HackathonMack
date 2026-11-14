#!/bin/sh
# Compila tudo e roda os cenários de teste (sem biblioteca externa).
set -e
cd "$(dirname "$0")"
rm -rf out && mkdir -p out
find src/main/java src/test/java -name '*.java' > out/fontes.txt
javac -encoding UTF-8 -d out @out/fontes.txt
cp -r src/main/resources/. out/
java -cp out br.com.recepcaosemfila.VerificaCenarios
