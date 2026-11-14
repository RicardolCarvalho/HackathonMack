// Tela do paciente: desenha a etapa atual a partir do estado do check-in devolvido pela API.

const ETAPAS = ['Documentos', 'Cadastro', 'Guia', 'Chegada'];
const CHAVE = 'recepcao-sem-fila.checkin';

let checkIn = null;
let kits = [];
let erro = null;
let ocupado = false;
let consulta = null;

const tela = document.getElementById('tela');
const dialogoAjuda = document.getElementById('dialogo-ajuda');

// ---------- Ações ----------

async function executar(acao) {
  if (ocupado) return;
  ocupado = true;
  erro = null;
  desenhar();
  try {
    checkIn = await acao();
    guardar();
  } catch (e) {
    erro = e.message;
  }
  ocupado = false;
  desenhar();
  window.scrollTo({ top: 0 });
}

const comecar = () => executar(() => api('POST', '/api/checkins'));

const enviarKit = (kit) => executar(async () => {
  const [identidadeSvg, carteirinhaSvg] = await Promise.all([
    fetch(kit.identidadeUrl).then((r) => r.text()),
    fetch(kit.carteirinhaUrl).then((r) => r.text()),
  ]);
  return api('POST', `/api/checkins/${checkIn.id}/documentos`, { kitId: kit.id, identidadeSvg, carteirinhaSvg });
});

const enviarArquivos = () => executar(async () => {
  const identidade = document.getElementById('arquivo-identidade').files[0];
  const carteirinha = document.getElementById('arquivo-carteirinha').files[0];
  if (!identidade || !carteirinha) throw new Error('Escolha os dois arquivos: documento de identidade e carteirinha.');
  return api('POST', `/api/checkins/${checkIn.id}/documentos`, {
    identidadeSvg: await identidade.text(),
    carteirinhaSvg: await carteirinha.text(),
  });
});

const confirmarGuia = () => executar(() => api('POST', `/api/checkins/${checkIn.id}/confirmar-guia`));
const registrarChegada = () => executar(() => api('POST', `/api/checkins/${checkIn.id}/chegada`));

function novoCheckIn() {
  checkIn = null;
  erro = null;
  guardar();
  desenhar();
}

function abrirAjuda(motivo) {
  document.getElementById('motivo-ajuda').value = motivo || '';
  dialogoAjuda.showModal();
}

document.getElementById('abrir-ajuda').addEventListener('click', () => abrirAjuda());
document.getElementById('cancelar-ajuda').addEventListener('click', () => dialogoAjuda.close());
document.getElementById('confirmar-ajuda').addEventListener('click', () => {
  const motivo = document.getElementById('motivo-ajuda').value;
  dialogoAjuda.close();
  document.getElementById('motivo-ajuda').value = '';
  executar(() => api('POST', `/api/checkins/${checkIn.id}/ajuda`, { motivo }));
});

// ---------- Desenho ----------

function desenhar() {
  desenharTrilha();
  const status = checkIn ? checkIn.status : null;
  const partes = [];

  if (erro) partes.push(el('div', { class: 'alerta', role: 'alert' }, erro));

  if (!checkIn) partes.push(telaInicio());
  else if (status === 'AGUARDANDO_DOCUMENTOS') partes.push(telaDocumentos());
  else if (status === 'AGUARDANDO_CONFIRMACAO_GUIA') partes.push(telaGuia());
  else if (status === 'PRONTO_PARA_CHEGADA') partes.push(telaPronto());
  else if (status === 'CONCLUIDO') partes.push(telaConcluido());
  else if (status === 'ATENDIMENTO_REMOTO') partes.push(telaRemoto());
  else if (status === 'APOIO_PRESENCIAL') partes.push(telaPresencial());

  if (checkIn && checkIn.verificacoes.length) partes.push(quadroConferencia());

  tela.replaceChildren(...partes);
  document.getElementById('barra-ajuda').hidden = !checkIn || status === 'CONCLUIDO' || status === 'APOIO_PRESENCIAL';
  acompanhar();
}

function desenharTrilha() {
  let atual = 0;
  let parada = false;
  if (checkIn) {
    const falhas = checkIn.verificacoes.filter((v) => v.resultado === 'DIVERGENTE' || v.resultado === 'AUSENTE').map((v) => v.codigo);
    const etapaDaFalha = falhas.some((c) => c.startsWith('LEITURA')) ? 0
      : falhas.some((c) => !['GUIA', 'AGENDAMENTO'].includes(c)) ? 1
      : falhas.length ? 2 : 0;
    switch (checkIn.status) {
      case 'AGUARDANDO_CONFIRMACAO_GUIA': atual = 2; break;
      case 'PRONTO_PARA_CHEGADA': atual = 3; break;
      case 'CONCLUIDO': atual = 4; break;
      case 'ATENDIMENTO_REMOTO':
      case 'APOIO_PRESENCIAL': atual = etapaDaFalha; parada = true; break;
      default: atual = 0;
    }
  }
  document.getElementById('trilha').replaceChildren(...ETAPAS.map((nome, i) => {
    const classe = i < atual ? 'feita' : i === atual ? (parada ? 'parada' : 'atual') : '';
    const simbolo = i < atual ? '✓' : i === atual && parada ? '!' : String(i + 1);
    return el('li', { class: classe, 'aria-current': i === atual ? 'step' : null },
      el('span', { class: 'ponto', 'aria-hidden': 'true' }, simbolo),
      el('span', { class: 'nome' }, nome));
  }));
}

/** "ANA BEATRIZ LIMA SOUZA" -> "Ana". */
function primeiroNome(nome) {
  if (!nome) return '';
  const p = nome.trim().split(/\s+/)[0].toLowerCase();
  return p.charAt(0).toUpperCase() + p.slice(1);
}

function telaInicio() {
  return el('section', { class: 'quadro' },
    el('div', { class: 'rotulo' }, 'Check-in antecipado'),
    el('h1', {}, 'Faça seu check-in antes de chegar'),
    el('p', { class: 'suave' }, 'Em poucos minutos, sem fila na recepção.'),
    el('ol', { class: 'passos' },
      el('li', {}, el('div', {}, el('strong', {}, 'Envie documento e carteirinha'),
        el('span', { class: 'suave pequeno' }, 'Conferimos seus dados com o cadastro do hospital.'))),
      el('li', {}, el('div', {}, el('strong', {}, 'Confirme a guia'),
        el('span', { class: 'suave pequeno' }, 'Veja se é a guia da sua consulta de hoje.'))),
      el('li', {}, el('div', {}, el('strong', {}, 'Ao chegar, receba sua senha'),
        el('span', { class: 'suave pequeno' }, 'E siga direto para o atendimento.')))),
    el('button', { class: 'largo', onclick: comecar, disabled: ocupado }, ocupado ? 'Começando…' : 'Começar check-in'),
    el('p', { class: 'nota' }, 'Se algo não conferir, uma pessoa do atendimento assume o seu caso. Você pode pedir ajuda a qualquer momento.'));
}

function telaDocumentos() {
  return el('section', { class: 'quadro' },
    el('div', { class: 'rotulo' }, 'Etapa 1 de 4 · Documentos'),
    el('h1', {}, 'Envie seu documento e sua carteirinha'),
    el('p', { class: 'suave' }, ocupado ? 'Conferindo seus documentos…' : 'Escolha um dos pares de documentos fictícios da demonstração.'),
    el('p', { class: 'nota' }, 'O texto ao lado de cada par é o roteiro da demo e não aparece para um paciente real.'),
    el('div', { class: 'kits' }, kits.map((kit) =>
      el('button', { class: 'kit', onclick: () => enviarKit(kit), disabled: ocupado },
        el('span', { class: 'miniaturas' },
          el('img', { src: kit.identidadeUrl, alt: 'Documento de identidade fictício' }),
          el('img', { src: kit.carteirinhaUrl, alt: 'Carteirinha fictícia' })),
        el('span', {}, el('strong', {}, kit.titulo), el('span', { class: 'suave pequeno' }, kit.resumo)),
        el('span', { class: 'seta', 'aria-hidden': 'true' }, '→')))),
    el('div', { class: 'divisor' }, 'ou envie seus arquivos'),
    el('p', { class: 'suave pequeno' }, 'O leitor desta demonstração entende os documentos fictícios em SVG. Para testar o envio, salve um dos modelos (por exemplo /api/kits/ana/identidade.svg e /api/kits/ana/carteirinha.svg). Outro tipo de arquivo é encaminhado para uma pessoa conferir.'),
    el('div', {},
      el('div', { class: 'campo' },
        el('label', { for: 'arquivo-identidade' }, 'Documento de identidade'),
        el('input', { type: 'file', id: 'arquivo-identidade', accept: '.svg,image/svg+xml' })),
      el('div', { class: 'campo' },
        el('label', { for: 'arquivo-carteirinha' }, 'Carteirinha do convênio'),
        el('input', { type: 'file', id: 'arquivo-carteirinha', accept: '.svg,image/svg+xml' }))),
    el('button', { class: 'secundario largo', onclick: enviarArquivos, disabled: ocupado }, 'Enviar arquivos'));
}

function telaGuia() {
  const g = checkIn.guia;
  const a = checkIn.agendamento;
  const nome = primeiroNome(checkIn.nomeLido);
  const guiaOk = checkIn.verificacoes.some((v) => v.codigo === 'GUIA' && v.resultado === 'OK');
  return el('section', { class: 'quadro' },
    el('div', { class: 'rotulo' }, 'Etapa 3 de 4 · Guia'),
    el('h1', {}, 'Esta é a guia da sua consulta de hoje?'),
    el('p', { class: 'suave' }, `${nome ? nome + ', seus' : 'Seus'} documentos e seu cadastro conferem. Confira a guia antes de seguir.`),
    el('div', { class: 'guia' },
      el('div', { class: 'guia-topo' },
        el('span', { class: 'rotulo' }, g.convenio || 'Convênio'),
        guiaOk ? el('span', { class: 'selo livre' }, '✓ Autorizada') : null),
      el('div', { class: 'guia-procedimento' }, g.procedimento),
      el('dl', { class: 'guia-dados' },
        el('div', {}, el('dt', {}, 'Horário'), el('dd', {}, a ? `Hoje, ${a.horario}` : '—')),
        el('div', {}, el('dt', {}, 'Nº da guia'), el('dd', {}, g.numero)),
        el('div', {}, el('dt', {}, 'Válida até'), el('dd', {}, dataBr(g.validade))))),
    el('div', { class: 'botoes' },
      el('button', { class: 'largo', onclick: confirmarGuia, disabled: ocupado }, ocupado ? 'Confirmando…' : 'Sim, é esta guia'),
      el('button', { class: 'secundario largo', onclick: () => abrirAjuda('A guia mostrada não é a da minha consulta de hoje.'), disabled: ocupado }, 'Não é esta guia')));
}

function telaPronto() {
  const liberado = checkIn.atendidoPor;
  const a = checkIn.agendamento;
  return el('section', { class: 'quadro' },
    el('div', { class: 'rotulo' }, 'Etapa 4 de 4 · Chegada'),
    el('h1', {}, 'Tudo pronto. Agora é só chegar'),
    el('p', { class: 'suave' }, liberado
      ? `${liberado}, do atendimento, conferiu seu caso e liberou o check-in.`
      : `Você fez o check-in em ${segundos(checkIn.tempoAteDesfechoSeg)} e não vai precisar passar pela fila da recepção.`),
    a ? el('div', { class: 'agenda' },
      el('div', { class: 'hora' }, el('small', {}, 'Hoje'), a.horario),
      el('div', { class: 'descricao' }, el('strong', {}, a.servico),
        checkIn.guia ? el('div', { class: 'suave pequeno' }, `Guia ${checkIn.guia.numero}`) : null)) : null,
    el('ol', { class: 'passos' },
      el('li', {}, el('div', {}, el('strong', {}, 'Vá até o hospital'))),
      el('li', {}, el('div', {}, el('strong', {}, 'Toque em “Cheguei ao hospital”'),
        el('span', { class: 'suave pequeno' }, 'Só quando você já estiver lá.'))),
      el('li', {}, el('div', {}, el('strong', {}, 'Receba sua senha'),
        el('span', { class: 'suave pequeno' }, 'Ela aparece aqui, com o local para onde seguir.')))),
    el('button', { class: 'largo', onclick: registrarChegada, disabled: ocupado }, ocupado ? 'Registrando…' : 'Cheguei ao hospital'));
}

function telaConcluido() {
  const etapa = checkIn.agendamento ? checkIn.agendamento.proximaEtapa : null;
  const presencial = checkIn.senha && checkIn.senha.startsWith('P-');
  return el('section', { class: 'quadro' },
    el('div', { class: 'rotulo' }, 'Chegada confirmada'),
    el('h1', {}, presencial ? 'Atendimento registrado' : 'Esta é a sua senha'),
    presencial
      ? el('p', {}, `${checkIn.atendidoPor || 'A recepção'} registrou sua passagem no balcão.`)
      : el('div', { class: 'bilhete' },
          el('div', { class: 'titulo' }, 'Sua senha'),
          el('div', { class: 'numero' }, checkIn.senha),
          el('div', { class: 'destino' }, 'Siga para', el('strong', {}, etapa || 'a orientação da recepção'))),
    el('p', { class: 'suave pequeno' }, `Tempo total do check-in: ${segundos(checkIn.tempoTotalSeg)}.`),
    el('button', { class: 'secundario largo', onclick: novoCheckIn }, 'Começar outro check-in'));
}

function telaRemoto() {
  return el('section', { class: 'quadro' },
    el('span', { class: 'selo pendencia' }, 'Com uma pessoa do atendimento'),
    el('h1', {}, 'Uma pessoa vai conferir seu caso'),
    el('p', { class: 'suave' }, 'Encontramos uma pendência que o sistema não resolve sozinho. O atendimento remoto já recebeu tudo o que você enviou. Você não precisa refazer nada.'),
    el('ul', { class: 'motivos' }, checkIn.motivos.map((m) => el('li', {}, m))),
    el('p', { class: 'aguardando' }, 'Esta tela atualiza sozinha quando o atendimento responder.'));
}

function telaPresencial() {
  return el('section', { class: 'quadro' },
    el('div', { class: 'rotulo' }, 'Apoio presencial'),
    el('h1', {}, 'Procure o balcão de apoio'),
    el('p', { class: 'suave' }, checkIn.ajudaPedidaPeloPaciente
      ? 'Seu pedido foi registrado. Uma pessoa da recepção vai atender você.'
      : `${checkIn.atendidoPor || 'O atendimento'} pediu que seu caso seja concluído no balcão, com uma pessoa.`),
    el('div', { class: 'bilhete apoio' },
      el('div', { class: 'titulo' }, 'Senha de apoio'),
      el('div', { class: 'numero' }, checkIn.senha),
      el('div', { class: 'destino' }, 'Siga para', el('strong', {}, 'Balcão de apoio da recepção'))),
    el('p', { class: 'aguardando' }, 'O que você já enviou está com a recepção. Esta tela atualiza sozinha.'));
}

// ---------- O que foi conferido ----------

const GRUPOS_CONFERENCIA = [
  { titulo: 'Documentos enviados', codigos: ['LEITURA_IDENTIDADE', 'LEITURA_CARTEIRINHA', 'IDENTIDADE_SIMULADA'] },
  { titulo: 'Seus dados no cadastro', codigos: ['CADASTRO_LOCALIZADO', 'NOME', 'NASCIMENTO', 'CARTEIRINHA'] },
  { titulo: 'Atendimento de hoje', codigos: ['AGENDAMENTO', 'GUIA'] },
];

const RESUMO_RESULTADO = {
  OK: ['confere', 'conferem'],
  DIVERGENTE: ['divergente', 'divergentes'],
  AUSENTE: ['dado ausente', 'dados ausentes'],
  SIMULADO: ['simulado', 'simulados'],
  NAO_AVALIADO: ['não avaliado', 'não avaliados'],
};

const falhou = (v) => v.resultado === 'DIVERGENTE' || v.resultado === 'AUSENTE';

function quadroConferencia() {
  const vs = checkIn.verificacoes;
  const conta = {};
  for (const v of vs) conta[v.resultado] = (conta[v.resultado] || 0) + 1;

  // Agrupa por etapa; o que não estiver mapeado vai para "Outras conferências".
  const conhecidos = new Set(GRUPOS_CONFERENCIA.flatMap((g) => g.codigos));
  const grupos = GRUPOS_CONFERENCIA
    .map((g) => ({ titulo: g.titulo, itens: vs.filter((v) => g.codigos.includes(v.codigo)) }))
    .concat([{ titulo: 'Outras conferências', itens: vs.filter((v) => !conhecidos.has(v.codigo)) }])
    .filter((g) => g.itens.length);

  return el('details', { class: 'quadro conferido', open: true },
    el('summary', {},
      el('div', { class: 'cabeca' },
        el('div', { class: 'rotulo' }, `Conferência · ${vs.length} itens`),
        el('span', { class: 'alternar', 'aria-hidden': 'true' })),
      el('h2', {}, 'O que foi conferido'),
      el('p', { class: 'explica' }, 'Cada item mostra o que foi lido no seu documento e de onde veio o dado.'),
      el('div', { class: 'contagem' }, ['OK', 'DIVERGENTE', 'AUSENTE', 'SIMULADO', 'NAO_AVALIADO']
        .filter((r) => conta[r])
        .map((r) => el('span', { class: r, 'data-simbolo': SIMBOLO_RESULTADO[r] },
          `${conta[r]} ${RESUMO_RESULTADO[r][conta[r] === 1 ? 0 : 1]}`)))),
    el('div', { class: 'grupos' }, grupos.map((g) =>
      el('section', { class: 'grupo' },
        el('h3', { class: 'rotulo' }, g.titulo),
        el('ul', { class: 'itens' }, g.itens.map(itemConferencia))))));
}

function itemConferencia(v) {
  return el('li', { class: 'item' + (falhou(v) ? ' falhou' : '') },
    el('span', { class: 'marca ' + v.resultado, 'aria-hidden': 'true' }, SIMBOLO_RESULTADO[v.resultado]),
    el('div', { class: 'item-titulo' }, v.titulo),
    el('span', { class: 'estado ' + v.resultado }, ROTULO_RESULTADO[v.resultado]),
    el('div', { class: 'item-detalhe' }, v.detalhe),
    el('dl', { class: 'item-meta' },
      v.valorLido ? el('div', { class: 'lido' }, el('dt', {}, 'Lido'), el('dd', {}, v.valorLido)) : null,
      el('div', {}, el('dt', {}, 'Fonte'), el('dd', {}, v.fonte))));
}

// ---------- Estado ----------

/** Enquanto o caso está com uma pessoa, consulta o servidor para refletir a resposta do atendimento. */
function acompanhar() {
  const esperando = checkIn && (checkIn.status === 'ATENDIMENTO_REMOTO' || checkIn.status === 'APOIO_PRESENCIAL');
  if (esperando && !consulta) {
    consulta = setInterval(async () => {
      try {
        const novo = await api('GET', `/api/checkins/${checkIn.id}`);
        if (novo.status !== checkIn.status) {
          checkIn = novo;
          desenhar();
        }
      } catch (e) { /* servidor fora do ar: tenta de novo no próximo ciclo */ }
    }, 2000);
  } else if (!esperando && consulta) {
    clearInterval(consulta);
    consulta = null;
  }
}

function guardar() {
  try {
    if (checkIn) sessionStorage.setItem(CHAVE, checkIn.id);
    else sessionStorage.removeItem(CHAVE);
  } catch (e) { /* sem armazenamento: só não retoma após recarregar */ }
}

(async function abrir() {
  try {
    kits = await api('GET', '/api/kits');
    let id = null;
    try { id = sessionStorage.getItem(CHAVE); } catch (e) { /* ignora */ }
    if (id) checkIn = await api('GET', `/api/checkins/${id}`).catch(() => null);
  } catch (e) {
    erro = 'Não foi possível falar com o servidor. Confira se a aplicação está rodando.';
  }
  desenhar();
})();
