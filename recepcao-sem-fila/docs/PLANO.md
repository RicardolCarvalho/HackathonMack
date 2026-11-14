# Plano da fatia demonstrável

Complementa o brief da equipe. Onde não há evidência, está marcado como hipótese.

## Microjornada

| | |
|---|---|
| **Entrada** | Paciente com visita não emergencial agendada entra na conta do portal (login simulado) e envia documento de identidade e carteirinha (fictícios). A cada arquivo, o assistente mostra o que foi lido para o paciente conferir. |
| **Decisão** | O sistema lê os documentos e confere, por regra fixa: leitura completa, cadastro pelo CPF, nome, nascimento, carteirinha (número, convênio, validade), agendamento do dia e guia (autorizada, válida, da mesma carteirinha). |
| **Ação** | Tudo confere: o paciente confirma a guia e, ao chegar, confirma a chegada. |
| **Saída** | Passagem registrada, senha emitida e próxima etapa indicada. Tempo e desfecho entram nas medidas. |
| **Exceção** | Qualquer divergência ou dado ausente: atendimento remoto, com motivo, valor lido, valor da base e fonte. O atendente libera ou encaminha ao apoio presencial. O paciente pode pedir uma pessoa a qualquer momento (o botão fica junto das opções quando um documento dá problema). O apoio no balcão é marcado para o horário da visita, não para a hora do pedido. |

## Estados

```
AGUARDANDO_DOCUMENTOS ─ok→ AGUARDANDO_CONFIRMACAO_GUIA → PRONTO_PARA_CHEGADA → CONCLUIDO
        │ falha                                                  ↑ liberado por uma pessoa
        └────────→ ATENDIMENTO_REMOTO ─────────────────────────┘
                          │ encaminhado
   pedido de ajuda ──→ APOIO_PRESENCIAL ──(paciente confirma a chegada: senha de apoio)──→ CONCLUIDO (registrado por uma pessoa)
                            │ paciente volta antes de chegar (só se foi ele quem pediu)
                            └──→ estado em que estava antes do pedido

ATENDIMENTO_REMOTO ─(pendência de documento: paciente reenvia e a nova conferência passa)→ AGUARDANDO_CONFIRMACAO_GUIA
                   (até 2 reenvios; se ainda falhar, continua com a pessoa)
```

Antes do check-in, o paciente logado pode trocar o horário da visita de hoje por um horário livre da agenda
sintética do mesmo serviço. Um check-in em aberto passa a usar o novo horário.

Estados de interface do paciente: login (simulado), início da conta com a visita de hoje, envio de documentos com a leitura de cada um, guia para confirmar, pronto para a chegada,
senha emitida, aguardando atendimento remoto, apoio presencial marcado (aguardando a chegada), senha de apoio presencial,
erro de comunicação.
Estados do painel: fila vazia, caso aguardando ação, caso concluído, erro ao registrar sem nome ou observação.

## Contratos de dados

- **DocumentoLido**: `tipo`, `campos` (nome, cpf, nascimento | convenio, numeroCarteirinha, validade), `ausentes`, `fonte`.
- **Verificacao**: `codigo`, `titulo`, `resultado` (OK, DIVERGENTE, AUSENTE, SIMULADO, NAO_AVALIADO), `valorLido`, `valorCadastro` (só para o atendente), `fonte`, `detalhe`.
- **CheckIn**: `id`, `status`, `desfechoInicial`, `tempoAteDesfechoSeg`, `tempoTotalSeg`, `motivos`, `senha`, `horarioApoio`, `verificacoes`, `agendamento`, `guia`, `eventos` (só para o atendente).
- **Evento**: `quando`, `ator` (PACIENTE, SISTEMA, ATENDENTE nome), `descricao`.

A API está no README.

## Objeções levantadas (por um agente, a responder com o time e um mentor)

1. **Quem mais sofre na fila pode ser quem menos usa o site.** Pessoas idosas ou com pouca familiaridade
   digital talvez não façam o pré-check-in. A PoC tem apoio presencial, mas não mostra que a fila diminui
   para esse público. *Hipótese a verificar.*
2. **A parte difícil está simulada.** Provar que o documento é autêntico e pertence a quem enviou é o ponto
   crítico de um check-in remoto, e envolve dado pessoal sensível (LGPD). A demo não prova isso.
3. **A fila pode só mudar de lugar.** Se muitos casos tiverem pendência, o gargalo passa para o atendimento
   remoto. A proporção de casos encaminhados em operação real é desconhecida.
4. **Integração é onde está o esforço.** Cadastro, agenda e autorização de guia vivem em sistemas do
   hospital e da operadora. A PoC usa base sintética em memória; a viabilidade de integrar não foi avaliada.
5. **OCR real erra.** Um "confere" falso é pior que um encaminhamento a mais. A regra adotada é: na dúvida,
   encaminha. Falta medir a taxa de erro com um OCR real.
6. **Já existem totens, aplicativos e portais de pré-cadastro.** Não sabemos quais já cobrem cadastro,
   conferência documental e pendências no mesmo fluxo. *Lacuna a validar.*
7. **O tempo da demo não é comparável ao da recepção.** Sem linha de base medida, não dá para afirmar
   redução de espera nem economia.

## Divisão sugerida (definir nomes com o time)

| Área | Arquivos | Responsável |
|---|---|---|
| Regras e base sintética | `dados/`, `servico/Conferencia.java` | a definir |
| Fluxo e medidas | `servico/CheckInServico.java`, `dominio/` | a definir |
| API | `web/Servidor.java` | a definir |
| Tela do paciente | `resources/web/index.html`, `paciente.js` | a definir |
| Painel | `resources/web/atendimento.html`, `atendimento.js` | a definir |
| Estilo compartilhado | `resources/web/estilo.css`, `comum.js` | um integrador só |
| Demo, README e pitch | `README.md`, `docs/` | a definir |

Quem integra roda `testar` antes de cada merge. Se houver agentes em paralelo, uma worktree por área.
