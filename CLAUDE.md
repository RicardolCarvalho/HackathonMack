# Recepção Sem Fila (Hack Inova Health AI, desafio 1)

PoC de check-in assistido em `recepcao-sem-fila/`. Leia antes de mexer:
- `recepcao-sem-fila/README.md`: execução, roteiro da demo, API, limites, pitch
- `recepcao-sem-fila/docs/PLANO.md`: microjornada, estados, contratos, objeções

## Regras do projeto
- Java 17, só JDK (HttpServer embutido). Não adicionar dependências sem pedir.
- Somente dados sintéticos. OCR e verificação de identidade são simulados.
- Toda divergência ou dado ausente vai para uma pessoa; o sistema nunca aprova exceção.
- Sem decisão clínica. Não inventar métricas, validações ou integrações.
- Cada conferência registra a fonte do dado.

## Comandos (dentro de recepcao-sem-fila/)
- Rodar: `./executar.sh` ou `executar.bat` (porta 8080)
- Testar: `./testar.sh` ou `testar.bat` — rodar antes de cada commit
- Novo pacote Java precisa ser incluído nos `.bat`
