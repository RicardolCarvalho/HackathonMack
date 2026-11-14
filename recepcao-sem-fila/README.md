# Recepção Sem Fila

PoC do Desafio 1 do Hack Inova Health AI (14/11/2026, Mackenzie Higienópolis): um check-in assistido que
antecipa a conferência administrativa da recepção.

**Login no portal → envio dos documentos (com leitura na hora) → conferência do cadastro → confirmação da guia →
registro da passagem.**
Se algo falha, o caso vai para o atendimento remoto; quem precisa recebe apoio presencial.

Todos os dados são sintéticos. A leitura do documento (OCR) e a verificação de identidade são simuladas.

## Como rodar

Requisito: Java 17 ou mais novo. Não há dependências externas (nem Maven, nem internet).

```
java -jar dist/recepcao-sem-fila.jar          # porta 8080
java -jar dist/recepcao-sem-fila.jar 9090     # outra porta
```

- Paciente (portal): http://localhost:8080/
- Painel de atendimento: http://localhost:8080/atendimento

Depois de alterar o código, recompile e suba com `./executar.sh` (Linux/macOS) ou `executar.bat` (Windows).
Para rodar os testes dos cenários: `./testar.sh` ou `testar.bat`.
Rodando pelos scripts a partir desta pasta, as telas em `src/main/resources/web` são lidas do disco:
dá para editar HTML/CSS/JS e só recarregar o navegador.

O `pom.xml` é opcional (abrir na IDE, `mvn package`). O jar em `dist/` não é atualizado pelos scripts;
se mudar o código e quiser um jar novo, gere com `mvn package` (sai em `target/`).

## Roteiro da demo (três caminhos)

Abra a tela do paciente e o painel lado a lado. No painel, use "Zerar a demo" antes de começar.

O paciente entra na conta dele (login simulado; as contas fictícias aparecem na tela de login, senha `demo`),
vê a visita de hoje e pode **escolher outro horário** livre (agenda sintética de hoje, de 07:00 às 19:30, a cada
30 minutos; horários passados não aparecem e os ocupados aparecem riscados). Depois envia o documento de
identidade e a carteirinha antes de sair de casa. A cada arquivo enviado, o assistente de check-in mostra o
que leu (nome, CPF, nascimento; convênio, número, validade), o que não conseguiu ler e, na carteirinha, se ela
está vencida. Os dois documentos só vão para a conferência quando o paciente confirma.
Cada documento pode ser o exemplo fictício da conta ("Usar o documento fictício de exemplo") ou um arquivo
SVG enviado (por exemplo, salvo de `/api/kits/ana/identidade.svg`).

1. **Cadastro correto.** Conta "Ana Beatriz" → (opcional) Escolher outro horário → Começar check-in →
   exemplo nos dois → o assistente mostra os campos lidos → enviar → Confirmar guia → Cheguei ao hospital →
   senha A-001 e próxima etapa. Nenhuma pessoa da recepção foi acionada.
2. **Informação divergente.** Conta "Carlos Eduardo" → a data de nascimento lida não bate com o cadastro →
   o caso aparece no painel, em "Atendimento remoto", com o valor lido, o valor da base e a fonte de cada
   um → o atendente clica em "Resolver e liberar", informa nome e observação → a tela do paciente atualiza
   sozinha e ele confirma a chegada.
3. **Atendimento humano acionado.** Em qualquer etapa, "Falar com uma pessoa" → senha de apoio P-001 →
   o caso aparece em "Apoio presencial" → o atendente registra o atendimento no balcão.

**Reenvio do documento certo.** Quando a pendência é de documento (vencido, divergente ou ilegível), o caso
vai para o atendimento remoto como sempre, e o paciente ganha a opção "Resolver agora": envia o documento
atualizado/correto, que passa pela mesma conferência. Se nada falhar, o check-in segue para a guia sem
esperar a pessoa; se ainda falhar, continua com o atendimento. Até 2 reenvios por caso; tudo fica no
histórico e o desfecho inicial (encaminhado) não muda nas medidas. Pendência de guia ou agendamento não
tem reenvio: depende do hospital ou do convênio. Na demo, "Usar … de exemplo" no reenvio traz a versão
correta do cenário: Marina → carteirinha renovada; Carlos → documento com o nascimento certo; Tereza →
documento legível.

Variações prontas: "Marina" (carteirinha vencida), "João Pedro" (guia não autorizada) e "Tereza Cristina"
(CPF e nascimento ilegíveis: o assistente mostra "não consegui ler", e o caso vai para uma pessoa).
Documento de outra pessoa numa conta (por exemplo, o SVG da Ana enviado na conta do Carlos) é encaminhado:
a conferência é feita contra o cadastro do titular da conta, não contra o do CPF lido.

## Como medimos

O painel calcula as medidas só com os check-ins da execução atual. Nada é fixo no código.

- **Tempo de check-in:** do início do check-in até o primeiro desfecho (pré-check-in concluído,
  encaminhamento ao remoto ou pedido de apoio). Também guardamos o tempo total até a passagem registrada.
- **Proporção de casos concluídos ou escalados corretamente:** cada kit fictício tem um gabarito
  (concluir ou encaminhar). Decisão correta = desfecho do sistema igual ao gabarito. Pedidos de ajuda do
  paciente e arquivos enviados sem kit não entram na conta e são mostrados à parte.

Essas medidas descrevem cenários sintéticos operados pela equipe na demo. Não são tempo de fila real nem
comparação com a recepção atual: não temos linha de base medida (hipótese a verificar com um mentor).

## Como funciona

```
src/main/java/br/com/recepcaosemfila
  App.java                    monta tudo e sobe o servidor
  dados/BaseSintetica.java    pacientes, agendamentos e guias inventados (datas relativas a hoje)
  dados/KitsFicticios.java    documentos fictícios da demo, desenhados em SVG, com gabarito
  dados/ContasSinteticas.java contas do portal (login simulado), uma por paciente, ligada ao kit do cenário
  servico/LeitorDocumento     contrato do OCR; LeitorSvgSimulado é a implementação da PoC
  servico/Conferencia.java    regras administrativas, cada uma com a fonte do dado
  servico/CheckInServico.java máquina de estados, histórico e medidas
  web/Servidor.java           API em /api e telas (HttpServer do JDK); web/Json.java
src/main/resources/web        tela do paciente e painel (HTML, CSS e JS puros)
src/test/java                 VerificaCenarios: sucesso, divergência, dado ausente, apoio humano, horário, reenvio
docs/PLANO.md                 microjornada, contratos, estados, objeções e divisão de tarefas
```

Regras que valem em todo o fluxo:

- Qualquer divergência ou dado ausente encaminha o caso para uma pessoa. O sistema não corrige cadastro
  nem aprova exceção. O reenvio não muda isso: o documento novo passa pelas mesmas regras, e só segue se
  nenhuma falhar.
- Toda ação do atendimento exige nome e observação e fica no histórico do caso.
- Cada conferência registra a fonte (OCR simulado, conta do paciente, base cadastral sintética, agenda, base de guias).
- Com conta, o documento precisa ser do titular (mesmo CPF); sem CPF legível ou com CPF de outra pessoa, vai para uma pessoa.
- A leitura de cada documento só mostra ao paciente o que foi lido do arquivo que ele mesmo enviou; não confere
  nada nem muda o estado. A conferência acontece quando os dois documentos são enviados juntos.
- O paciente não recebe o valor que está no cadastro quando há divergência, nem os dados da guia antes de
  a conferência passar.
- Não há decisão clínica: as regras são só administrativas.

### Trocar o OCR simulado por um real

`LeitorSvgSimulado` extrai os textos de um SVG sintético; ele não lê foto. Para usar OCI Document
Understanding ou Vision, crie outra classe que implemente `LeitorDocumento` (mesmos campos de saída) e
troque a instância em `App.java`. Antes de depender do serviço, confirme acesso e limites da conta.

### API

| Método e rota | O que faz |
|---|---|
| `GET /api/kits` | lista os kits fictícios; `GET /api/kits/{id}/identidade.svg` e `/carteirinha.svg` |
| `GET /api/contas` | contas fictícias da demo (e-mail, nome, cenário) |
| `POST /api/contas/entrar` | corpo `{email, senha}`; login simulado, devolve a conta e a visita de hoje |
| `GET /api/contas/{id}` | resumo da conta: visita de hoje e horários (livres, ocupados, o atual) |
| `POST /api/contas/{id}/horario` | corpo `{horario}`; troca o horário da visita de hoje por um livre |
| `POST /api/checkins` | corpo `{contaId?}`; inicia um check-in (começa a contar o tempo) |
| `GET /api/checkins/{id}` | estado do check-in, na visão do paciente |
| `POST /api/checkins/{id}/leitura` | corpo `{tipo: IDENTIDADE\|CARTEIRINHA, svg}`; devolve os campos lidos e alertas (ex.: vencida), sem conferir |
| `POST /api/checkins/{id}/documentos` | corpo `{kitId?, identidadeSvg, carteirinhaSvg}`; lê e confere |
| `POST /api/checkins/{id}/reenvio` | corpo `{identidadeSvg?, carteirinhaSvg?}`; com pendência de documento, confere de novo |
| `POST /api/checkins/{id}/confirmar-guia` | paciente confirma a guia |
| `POST /api/checkins/{id}/chegada` | registra a passagem e emite a senha |
| `POST /api/checkins/{id}/ajuda` | corpo `{motivo?}`; aciona apoio presencial |
| `GET /api/atendimento/casos` | todos os casos, na visão do atendente (com valores da base e histórico) |
| `POST /api/atendimento/casos/{id}/resolver` | corpo `{atendente, observacao}`; libera o check-in |
| `POST /api/atendimento/casos/{id}/encaminhar-presencial` | corpo `{atendente, observacao}` |
| `POST /api/atendimento/casos/{id}/concluir-presencial` | corpo `{atendente, observacao}` |
| `GET /api/metricas` | medidas da execução |
| `POST /api/demo/reiniciar` | apaga check-ins e medidas |

Erros voltam como `{"erro": "..."}` com status 400, 401, 404, 409 ou 413.

## Limites da PoC

- Dados, documentos, convênios e CPFs são inventados (os CPFs têm dígito verificador inválido de propósito).
- A verificação de identidade e de autenticidade do documento é simulada e aparece como "Simulado".
- O OCR é simulado e só entende os documentos fictícios em SVG.
- Sem integração com sistema hospitalar ou operadora; a base fica em memória e se perde ao reiniciar.
- O login do paciente é simulado: senha única de demonstração, sem sessão nem token; o `contaId` vai no corpo.
- O painel não tem login. Um produto real precisaria de autenticação, trilha de auditoria persistente e
  tratamento de dados pessoais e de saúde conforme a LGPD.
- Uma unidade simulada; sem teste de carga nem de acessibilidade com usuários.

## Roteiro de pitch (3 minutos)

1. **Dor.** Conferir documento, carteirinha, agendamento e guia prende a recepção em tarefa repetitiva e
   o paciente na fila. *Hipótese da equipe; volume e tempo reais a verificar com um mentor.*
2. **Proposta.** O paciente faz a parte administrativa antes de chegar; o sistema confere o que é regra e
   entrega para uma pessoa tudo o que foge da regra, com o motivo e as fontes já organizados.
3. **Demo.** Os três caminhos acima, com o painel ao lado.
4. **Evidência que temos.** O que a demo mede na hora: tempo até o desfecho em cada caminho e decisões
   iguais ao gabarito nos cenários sintéticos. Citar os números que aparecerem no painel, dizendo que são
   de cenário sintético.
5. **O que não temos.** Linha de base da recepção atual, validação com pacientes ou recepcionistas,
   integração real, OCR real e verificação de identidade. Nenhuma economia financeira foi medida.
6. **Próximo passo.** Medir o processo atual em uma unidade, validar com a recepção quais exceções são
   mais comuns e testar um OCR real com documentos fictícios impressos.
