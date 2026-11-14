// Tela do paciente: desenha a etapa atual a partir do estado do check-in devolvido pela API.

const ETAPAS = ['Documento', 'Cadastro', 'Guia', 'Passagem'];
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

document.getElementById('abrir-ajuda').addEventListener('click', () => dialogoAjuda.showModal());
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

  if (erro) partes.push(el('div', { class: 'quadro pendencia', role: 'alert' }, el('p', { class: 'erro' }, erro)));

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
    return el('li', { class: classe, 'aria-current': i === atual ? 'step' : null }, nome);
  }));
}

function telaInicio() {
  return el('section', { class: 'quadro' },
    el('h1', {}, 'Faça seu check-in antes de chegar'),
    el('p', {}, 'Envie seu documento e sua carteirinha, confira a guia e, ao chegar, retire sua senha sem passar pela fila da recepção.'),
    el('p', { class: 'suave pequeno' }, 'Se algo não conferir, uma pessoa do atendimento assume o seu caso. Você pode pedir ajuda a qualquer momento.'),
    el('button', { class: 'largo', onclick: comecar, disabled: ocupado }, 'Começar check-in'));
}

function telaDocumentos() {
  return el('section', { class: 'quadro' },
    el('h1', {}, 'Envie seu documento e sua carteirinha'),
    el('h2', {}, 'Documentos fictícios da demonstração'),
    el('p', { class: 'suave pequeno' }, 'Escolha um par de documentos. O texto ao lado é o roteiro da demo e não aparece para um paciente real.'),
    el('div', { class: 'kits' }, kits.map((kit) =>
      el('button', { class: 'kit', onclick: () => enviarKit(kit), disabled: ocupado },
        el('span', { class: 'miniaturas' },
          el('img', { src: kit.identidadeUrl, alt: 'Documento de identidade fictício' }),
          el('img', { src: kit.carteirinhaUrl, alt: 'Carteirinha fictícia' })),
        el('span', {}, el('strong', {}, kit.titulo), el('span', { class: 'suave pequeno' }, kit.resumo))))),
    el('h2', {}, 'Ou envie seus arquivos'),
    el('p', { class: 'suave pequeno' }, 'O leitor desta demonstração entende os documentos fictícios em SVG. Para testar o envio, salve um dos modelos (por exemplo /api/kits/ana/identidade.svg e /api/kits/ana/carteirinha.svg). Outro tipo de arquivo é encaminhado para uma pessoa conferir.'),
    el('div', { class: 'campo' },
      el('label', { for: 'arquivo-identidade' }, 'Documento de identidade'),
      el('input', { type: 'file', id: 'arquivo-identidade', accept: '.svg,image/svg+xml' })),
    el('div', { class: 'campo' },
      el('label', { for: 'arquivo-carteirinha' }, 'Carteirinha do convênio'),
      el('input', { type: 'file', id: 'arquivo-carteirinha', accept: '.svg,image/svg+xml' })),
    el('button', { class: 'secundario largo', onclick: enviarArquivos, disabled: ocupado }, 'Enviar arquivos'));
}

function telaGuia() {
  const g = checkIn.guia;
  const a = checkIn.agendamento;
  return el('section', { class: 'quadro livre' },
    el('h1', {}, 'Tudo conferido. Confirme a guia'),
    el('p', {}, `${checkIn.nomeLido}, seus dados conferem com o cadastro. Veja se a guia abaixo é a do atendimento de hoje.`),
    el('dl', { class: 'dados' },
      el('dt', {}, 'Guia'), el('dd', {}, g.numero),
      el('dt', {}, 'Procedimento'), el('dd', {}, g.procedimento),
      el('dt', {}, 'Convênio'), el('dd', {}, g.convenio),
      el('dt', {}, 'Válida até'), el('dd', {}, dataBr(g.validade)),
      el('dt', {}, 'Agendamento'), el('dd', {}, `${a.servico}, hoje às ${a.horario}`)),
    el('button', { class: 'livre largo', onclick: confirmarGuia, disabled: ocupado }, 'Confirmar guia'),
    el('p', { class: 'suave pequeno' }, 'A guia não é essa? Use "Falar com uma pessoa" aqui embaixo.'));
}

function telaPronto() {
  const liberado = checkIn.atendidoPor;
  return el('section', { class: 'quadro livre' },
    el('h1', {}, liberado ? 'Pendência resolvida' : 'Pré-check-in concluído'),
    el('p', {}, liberado
      ? `${liberado}, do atendimento, conferiu seu caso e liberou o check-in.`
      : `Levou ${segundos(checkIn.tempoAteDesfechoSeg)} e você não precisou passar pela recepção.`),
    el('p', {}, 'Quando chegar ao hospital, confirme a chegada para receber sua senha.'),
    el('button', { class: 'livre largo', onclick: registrarChegada, disabled: ocupado }, 'Confirmar chegada'));
}

function telaConcluido() {
  const etapa = checkIn.agendamento ? checkIn.agendamento.proximaEtapa : null;
  const presencial = checkIn.senha && checkIn.senha.startsWith('P-');
  return el('section', { class: 'quadro' },
    el('h1', {}, 'Chegada confirmada'),
    presencial
      ? el('p', {}, `${checkIn.atendidoPor || 'A recepção'} registrou sua passagem no balcão.`)
      : el('div', { class: 'bilhete' },
          el('div', {}, 'Sua senha'),
          el('div', { class: 'numero' }, checkIn.senha),
          el('div', { class: 'destino' }, etapa ? `Siga para: ${etapa}` : 'Siga a orientação da recepção')),
    el('p', { class: 'suave pequeno' }, `Tempo total do check-in: ${segundos(checkIn.tempoTotalSeg)}.`),
    el('button', { class: 'secundario largo', onclick: novoCheckIn }, 'Começar outro check-in'));
}

function telaRemoto() {
  return el('section', { class: 'quadro pendencia' },
    el('h1', {}, 'Uma pessoa vai conferir seu caso'),
    el('p', {}, 'Encontramos uma pendência que o sistema não resolve sozinho. O atendimento remoto já recebeu seu check-in com tudo o que você enviou. Você não precisa refazer nada.'),
    el('ul', {}, checkIn.motivos.map((m) => el('li', {}, m))),
    el('p', { class: 'suave pequeno' }, 'Esta tela atualiza sozinha quando o atendimento responder.'));
}

function telaPresencial() {
  return el('section', { class: 'quadro' },
    el('h1', {}, 'Procure o balcão de apoio'),
    el('p', {}, checkIn.ajudaPedidaPeloPaciente
      ? 'Seu pedido foi registrado. Uma pessoa da recepção vai atender você.'
      : `${checkIn.atendidoPor || 'O atendimento'} pediu que seu caso seja concluído no balcão, com uma pessoa.`),
    el('div', { class: 'bilhete' },
      el('div', {}, 'Senha de apoio'),
      el('div', { class: 'numero' }, checkIn.senha),
      el('div', { class: 'destino' }, 'Balcão de apoio da recepção')),
    el('p', { class: 'suave pequeno' }, 'O que você já enviou está com a recepção. Esta tela atualiza sozinha.'));
}

function quadroConferencia() {
  return el('section', { class: 'quadro' },
    el('h2', {}, 'O que foi conferido'),
    el('ul', { class: 'conferencia' }, checkIn.verificacoes.map((v) =>
      el('li', {},
        el('span', { class: 'marca ' + v.resultado, title: ROTULO_RESULTADO[v.resultado] }, SIMBOLO_RESULTADO[v.resultado]),
        el('div', {},
          el('strong', {}, v.titulo), ' ', el('span', { class: 'suave pequeno' }, ROTULO_RESULTADO[v.resultado]),
          el('div', { class: 'pequeno' }, v.detalhe),
          v.valorLido ? el('div', { class: 'pequeno' }, 'Lido no documento: ' + v.valorLido) : null,
          el('div', { class: 'suave pequeno' }, 'Fonte: ' + v.fonte))))));
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
