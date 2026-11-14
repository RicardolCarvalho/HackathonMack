# Recepção Sem Fila

PoC do Desafio 1 do Hack Inova Health AI (14/11/2026, Mackenzie Higienópolis): um check-in assistido que
antecipa a conferência administrativa da recepção.

**Login no portal → envio dos documentos (com leitura na hora) → conferência do cadastro, da visita e da guia →
registro da passagem.**
Se algo falha, o caso vai para o atendimento remoto; quem precisa recebe apoio presencial.

Todos os dados são sintéticos. A leitura do documento (OCR) e a verificação de identidade são simuladas.

## Atendimentos independentes

Exames e pronto-socorro podem coexistir na mesma conta. Cada pré-cadastro guarda sua própria visita,
hospital, documentos, guia, situação e protocolo. O resumo inclui `visitas` e `checkinsAtivos` por tipo;
`POST /api/checkins` recebe `tipo` para retomar ou iniciar o atendimento escolhido. Alterar uma previsão
de chegada não altera o exame. A existência de um exame nunca bloqueia o pronto-socorro.

## Tipos de atendimento

O portal usa o nome **Adiantamento de Triagem** e oferece dois caminhos antes da seleção do hospital:
- **Exames agendados:** as contas de demonstração têm um exame fictício marcado para o dia seguinte,
  com hospital e horário definidos. O paciente acessa diretamente o pré-cadastro; alterações são feitas pela unidade.
  Contas novas não recebem um exame agendado automaticamente.
- **Pronto-socorro:** o paciente escolhe hospital e informa data e horário como **previsão de chegada**.
  Isso não ocupa vaga, não garante horário de atendimento nem define prioridade. O cadastro é administrativo;
  avaliação e triagem são presenciais. A guia não é uma condição de liberação desse pré-cadastro.

As rotas `POST /api/contas/{id}/exame` (`exameId`) e `/previsao` (`dia`, `horario`, `hospitalId`)
persistem o caminho escolhido. O resumo da conta inclui `exames`, `previsoes` e o `tipo` da visita.

## Hospitais da demonstração

Antes de escolher a data e o horário, o paciente seleciona um hospital: Hospital Alberto Enstale,
Hospital São Lucas ou Hospital 12 de Maio. Os nomes e endereços são fictícios. A disponibilidade é
separada por hospital e serviço. O hospital escolhido acompanha a visita, o check-in e o painel do
atendimento; ao trocar de unidade, é preciso escolher novamente data e horário.

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

O paciente entra na conta dele (login simulado; as contas fictícias aparecem na tela de login, senha `demo`)
e vê o pedido que tem para agendar (por exemplo, "consulta eletiva"), **sem horário marcado e sem o local**.
Ele **escolhe o dia e o horário** na agenda sintética do serviço: hoje e os próximos 6 dias, 24 horas por dia
(o hospital fictício funciona 24 horas): de 00:00 às 23:30, a cada 30 minutos (horários passados não aparecem; os ocupados aparecem riscados). Dá para trocar o dia ou o
horário depois, inclusive com o check-in feito. O portal não mostra o local do atendimento (sala, guichê).
Com a visita marcada, envia o documento de identidade e a carteirinha antes de sair de casa. A cada arquivo enviado, o assistente de check-in mostra o
que leu (nome, CPF, nascimento; convênio, número, validade), o que não conseguiu ler e, na carteirinha, se ela
está vencida. Os dois documentos só vão para a conferência quando o paciente confirma.
Cada documento pode ser o exemplo fictício da conta ("Usar o documento fictício de exemplo") ou um arquivo
SVG enviado (por exemplo, salvo de `/api/kits/ana/identidade.svg`).

1. **Cadastro correto.** Conta "Ana Beatriz" → escolher o dia (hoje) e um horário → Agendar → Começar
   check-in → exemplo nos dois → o assistente mostra os campos lidos → enviar → tudo confere e o
   pré-check-in fica pronto → Cheguei ao hospital → senha A-001. Nenhuma pessoa da recepção foi acionada. Para a demo terminar na
   senha, agende para hoje: com a visita em outro dia, o check-in vale, mas "Cheguei ao hospital" só libera no
   dia da visita.
2. **Informação divergente.** Conta "Carlos Eduardo" → a data de nascimento lida não bate com o cadastro →
   o caso aparece no painel, em "Atendimento remoto", com o valor lido, o valor da base e a fonte de cada
   um → o atendente clica em "Resolver e liberar", informa nome e observação → a tela do paciente atualiza
   sozinha e ele confirma a chegada.
3. **Atendimento humano acionado.** Em qualquer etapa, "Falar com uma pessoa". Quando um documento dá
   problema (na leitura ou na conferência), o botão aparece junto das outras opções da tela;
   nas demais etapas, no canto → o apoio no balcão fica marcado para o **dia e o horário da visita**, não para a
   hora do pedido → o caso aparece em "Apoio presencial", com o dia e o horário do balcão e nessa ordem,
   aguardando a chegada → como no fluxo normal, ao chegar o paciente toca em "Cheguei ao hospital" e só então
   recebe a senha de apoio P-001 → o atendente registra o atendimento no balcão (só depois da chegada).

**Guia.** A guia é emitida pelo hospital e enviada ao convênio, então o paciente não confirma nada sobre ela: a
conferência verifica se ela existe, está autorizada e vale até o dia da visita. Com tudo conferido, o pré-check-in
fica pronto direto; a guia aparece só para consulta, na página da conferência.

**Voltar.** Toda tela do paciente, exceto o login, tem um botão "Voltar" que leva à página anterior. A trilha
do check-in é: início da conta → documentos → conferência → chegada (pronto) → senha. Ao voltar para uma etapa
já feita, ela aparece só para consulta (nada é desfeito no servidor) com "Avançar" para seguir; antes dos
documentos vem o início da conta e, antes dele, o login. Sair com um check-in em aberto não o perde: ao entrar de
novo na mesma aba, ele é retomado. No cadastro, "Voltar" leva ao login; na agenda aberta, fecha a agenda (se foi
aberta pelo "Trocar dia ou horário" do check-in, volta para lá). Na pendência, a página anterior é a dos
documentos; no apoio marcado pelo atendimento, a da conferência. Com o apoio no balcão pedido pelo próprio
paciente e antes da chegada, "Voltar" cancela o pedido e devolve o paciente à tela em que estava (um caso com
pendência continua no atendimento remoto). Depois que o paciente confirma a chegada, o dia e o horário da visita
não podem mais ser trocados pelo portal: só com a recepção.

**Reenvio do documento certo.** Quando a pendência é de documento (vencido, divergente ou ilegível), o caso
vai para o atendimento remoto como sempre, e o paciente ganha a opção "Resolver agora": envia o documento
atualizado/correto, que passa pela mesma conferência. Se nada falhar, o pré-check-in fica pronto sem
esperar a pessoa; se ainda falhar, continua com o atendimento. Até 2 reenvios por caso; tudo fica no
histórico e o desfecho inicial (encaminhado) não muda nas medidas. Pendência de guia ou de visita não
tem reenvio: depende do hospital ou do convênio. Na demo, "Usar … de exemplo" no reenvio traz a versão
correta do cenário: Marina → carteirinha renovada; Carlos → documento com o nascimento certo; Tereza →
documento legível.

Variações prontas: "Marina" (carteirinha vencida), "João Pedro" (guia não autorizada) e "Tereza Cristina"
(CPF e nascimento ilegíveis: o assistente mostra "não consegui ler", e o caso vai para uma pessoa).
Documento de outra pessoa numa conta (por exemplo, o SVG da Ana enviado na conta do Carlos) é encaminhado:
a conferência é feita contra o cadastro do titular da conta, não contra o do CPF lido.

**Criar conta.** Na tela de login, "Criar conta" abre um cadastro simulado: nome, CPF, nascimento, e-mail,
senha, convênio fictício, número e validade da carteirinha e o serviço a agendar. Só aceita dados fictícios: CPF
com dígito verificador inválido (ex.: 123.456.789-00) e e-mail terminado em `.test`. A conta entra logada, sem
horário, e ganha documentos fictícios de exemplo gerados com os dados cadastrados. Ela não tem guia do convênio,
então a conferência aponta "Não há guia para a visita marcada" e o caso vai para o atendimento remoto, onde uma
pessoa resolve. A conta fica só na memória (a senha só como hash), não aparece na lista de contas da demo e é
apagada em "Zerar a demo".

## Como medimos

O painel calcula as medidas só com os check-ins da execução atual. Nada é fixo no código.

- **Tempo de check-in:** do início do check-in até o primeiro desfecho (pré-check-in concluído,
  encaminhamento ao remoto ou pedido de apoio). Também guardamos o tempo total até a passagem registrada.
- **Proporção de casos concluídos ou escalados corretamente:** cada kit fictício tem um gabarito
  (concluir ou encaminhar). Decisão correta = desfecho do sistema igual ao gabarito. Pedidos de ajuda do
  paciente e arquivos enviados sem kit não entram na conta e são mostrados à parte. Um pedido de ajuda que o
  paciente cancelou antes de chegar ("Voltar") deixa de contar como desfecho; o pedido continua no histórico.

Essas medidas descrevem cenários sintéticos operados pela equipe na demo. Não são tempo de fila real nem
comparação com a recepção atual: não temos linha de base medida (hipótese a verificar com um mentor).

## Como funciona

```
src/main/java/br/com/recepcaosemfila
  App.java                    monta tudo e sobe o servidor
  dados/BaseSintetica.java    pacientes, pedidos, agenda e guias inventados (datas relativas a hoje)
  dados/KitsFicticios.java    documentos fictícios da demo, desenhados em SVG, com gabarito
  dados/ContasSinteticas.java contas do portal (login simulado), uma por paciente, ligada ao kit do cenário
  servico/LeitorDocumento     contrato do OCR; LeitorSvgSimulado é a implementação da PoC
  servico/Conferencia.java    regras administrativas, cada uma com a fonte do dado
  servico/CheckInServico.java máquina de estados, histórico e medidas
  web/Servidor.java           API em /api e telas (HttpServer do JDK); web/Json.java
src/main/resources/web        tela do paciente e painel (HTML, CSS e JS puros)
src/test/java                 VerificaCenarios: sucesso, divergência, dado ausente, apoio humano, agenda, reenvio
docs/PLANO.md                 microjornada, contratos, estados, objeções e divisão de tarefas
```

Regras que valem em todo o fluxo:

- Qualquer divergência ou dado ausente encaminha o caso para uma pessoa. O sistema não corrige cadastro
  nem aprova exceção. O reenvio não muda isso: o documento novo passa pelas mesmas regras, e só segue se
  nenhuma falhar.
- Toda ação do atendimento exige nome e observação e fica no histórico do caso.
- O paciente só começa o check-in com a visita marcada (dia e horário escolhidos por ele). A conferência vale
  para o dia da visita: carteirinha e guia precisam estar válidas até esse dia. Trocar para um dia em que a guia
  ou a carteirinha já venceram, depois da conferência, é recusado (o paciente escolhe outro dia ou fala com uma
  pessoa). A chegada ("Cheguei ao hospital") só é aceita no dia da visita.
- O portal não mostra ao paciente o local do atendimento (por exemplo, "Triagem, sala 2"): ele fica só no painel do atendimento.
- O apoio presencial (pedido pelo paciente ou encaminhado pelo atendimento) é marcado para o dia e o horário da
  visita. Sem visita à frente (por exemplo, o horário de hoje já passou), é assim que o paciente chegar. Se o
  paciente troca o dia ou o horário da visita, o apoio acompanha. A senha de apoio só sai quando o paciente
  confirma a chegada ao hospital, como a senha do fluxo normal.
- Cada conferência registra a fonte (OCR simulado, conta do paciente, base cadastral sintética, agenda, base de guias).
- Com conta, o documento precisa ser do titular (mesmo CPF); sem CPF legível ou com CPF de outra pessoa, vai para uma pessoa.
- O cadastro de conta nova só aceita dados fictícios (CPF com dígito verificador inválido, e-mail `.test`,
  convênios fictícios) e não cria guia: a falta da guia vai para uma pessoa, como qualquer dado ausente.
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
| `GET /api/contas` | contas fictícias da demo (e-mail, nome, cenário); as criadas no portal não aparecem |
| `POST /api/contas` | corpo `{nome, cpf, nascimento, email, senha, convenio, numeroCarteirinha, validadeCarteirinha, servico}` (datas AAAA-MM-DD); cadastro simulado, só com dados fictícios; devolve o resumo da conta nova |
| `GET /api/contas/opcoes` | convênios fictícios e serviços aceitos no cadastro |
| `POST /api/contas/entrar` | corpo `{email, senha}`; login simulado, devolve o resumo da conta |
| `GET /api/contas/{id}` | resumo da conta: serviço do pedido, visita marcada (ou `null`) e a agenda de 7 dias com os horários (livres, ocupados, o atual); sem o local |
| `POST /api/contas/{id}/agendamento` | corpo `{dia: AAAA-MM-DD, horario}`; marca ou troca o dia e o horário da visita por um livre |
| `POST /api/checkins` | corpo `{contaId?}`; inicia um check-in (começa a contar o tempo); com conta, exige visita marcada |
| `GET /api/checkins/{id}` | estado do check-in, na visão do paciente |
| `POST /api/checkins/{id}/leitura` | corpo `{tipo: IDENTIDADE\|CARTEIRINHA, svg}`; devolve os campos lidos e alertas (ex.: vencida), sem conferir |
| `POST /api/checkins/{id}/documentos` | corpo `{kitId?, identidadeSvg, carteirinhaSvg}`; lê e confere |
| `POST /api/checkins/{id}/reenvio` | corpo `{identidadeSvg?, carteirinhaSvg?}`; com pendência de documento, confere de novo |
| `POST /api/checkins/{id}/chegada` | registra a chegada e emite a senha (no apoio presencial, a senha de apoio); só no dia da visita |
| `POST /api/checkins/{id}/ajuda` | corpo `{motivo?}`; aciona apoio presencial no dia e horário da visita (`diaApoio`, `horarioApoio`) |
| `POST /api/checkins/{id}/cancelar-ajuda` | o paciente desiste do apoio que pediu, antes de chegar, e volta para onde estava |
| `GET /api/atendimento/casos` | todos os casos, na visão do atendente (com valores da base e histórico) |
| `POST /api/atendimento/casos/{id}/resolver` | corpo `{atendente, observacao}`; libera o check-in |
| `POST /api/atendimento/casos/{id}/encaminhar-presencial` | corpo `{atendente, observacao}` |
| `POST /api/atendimento/casos/{id}/concluir-presencial` | corpo `{atendente, observacao}` |
| `GET /api/metricas` | medidas da execução |
| `POST /api/demo/reiniciar` | apaga check-ins, medidas, horários marcados e contas criadas no portal |

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
