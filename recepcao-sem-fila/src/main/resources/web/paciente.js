// Portal do paciente: login (simulado), escolha do horário, envio dos documentos com leitura na hora,
// reenvio do documento certo quando há pendência, e o check-in até a senha.

const ETAPAS = ['Documentos', 'Conferência', 'Guia', 'Chegada'];
const CHAVE_CONTA = 'recepcao-sem-fila.conta';
const CHAVE_CHECKIN = 'recepcao-sem-fila.checkin';

const DOCUMENTOS = {
  IDENTIDADE: { titulo: 'Documento de identidade', curto: 'seu documento de identidade', arquivo: 'identidade.svg', icone: 'identidade', campo: 'identidadeSvg' },
  CARTEIRINHA: { titulo: 'Carteirinha do convênio', curto: 'sua carteirinha', arquivo: 'carteirinha.svg', icone: 'cartao', campo: 'carteirinhaSvg' },
};

let conta = null;          // resumo da conta logada (com a visita de hoje e os horários)
let contasDemo = [];       // contas fictícias para o roteiro da demo
let kitsPorId = {};        // kits fictícios (para o documento de exemplo e o documento corrigido)
let checkIn = null;
let docs = vazios();       // envio inicial: arquivo e leitura de cada documento
let novos = vazios();      // reenvio: documento atualizado/correto de cada pendência
let escolhendoHorario = false;
let avisoHorario = null;
let conferenciaAberta = false;
let erro = null;
let ocupado = false;
let consulta = null;
let ultimaTela = null;

const tela = document.getElementById('tela');
const dialogoAjuda = document.getElementById('dialogo-ajuda');

function vazios() { return { IDENTIDADE: null, CARTEIRINHA: null }; }

// ---------- Ações ----------

async function executar(acao) {
  if (ocupado) return;
  ocupado = true;
  erro = null;
  desenhar();
  try {
    await acao();
    guardar();
  } catch (e) {
    erro = e.message;
  }
  ocupado = false;
  desenhar();
}

const entrar = (email, senha) => executar(async () => {
  conta = await api('POST', '/api/contas/entrar', { email, senha });
  checkIn = null;
  docs = vazios();
  escolhendoHorario = false;
  avisoHorario = null;
});

function sair() {
  conta = null;
  checkIn = null;
  docs = vazios();
  novos = vazios();
  erro = null;
  guardar();
  desenhar();
}

const trocarHorario = (horario) => executar(async () => {
  if (horario === conta.visitaHoje.horario) { escolhendoHorario = false; return; }
  try {
    conta = await api('POST', `/api/contas/${conta.id}/horario`, { horario });
    escolhendoHorario = false;
    avisoHorario = `Pronto! Sua visita agora é às ${horario}.`;
  } catch (e) {
    conta = await api('GET', `/api/contas/${conta.id}`).catch(() => conta); // atualiza os horários ocupados
    throw e;
  }
});

const comecar = () => executar(async () => {
  checkIn = await api('POST', '/api/checkins', { contaId: conta.id });
  docs = vazios();
  avisoHorario = null;
});

/** Lê um documento assim que o arquivo é escolhido e guarda o que o leitor extraiu, para o paciente conferir. */
async function lerDocumento(lugar, tipo, nome, svg, kitId) {
  const anterior = lugar[tipo];
  if (anterior && anterior.preview) URL.revokeObjectURL(anterior.preview);
  const preview = URL.createObjectURL(new Blob([svg], { type: 'image/svg+xml' }));
  lugar[tipo] = { nome, svg, kitId, preview, leitura: null, lendo: true, erro: null };
  desenhar();
  try {
    lugar[tipo].leitura = await api('POST', `/api/checkins/${checkIn.id}/leitura`, { tipo, svg });
  } catch (e) {
    lugar[tipo].erro = e.message;
  }
  lugar[tipo].lendo = false;
  desenhar();
}

async function escolherArquivo(lugar, tipo, arquivo) {
  if (!arquivo) return;
  erro = null;
  if (!/\.svg$/i.test(arquivo.name) && arquivo.type !== 'image/svg+xml') {
    erro = 'Esta demonstração só lê os documentos fictícios em SVG. Use o documento de exemplo ou um SVG baixado dele.';
    desenhar();
    return;
  }
  await lerDocumento(lugar, tipo, arquivo.name, await arquivo.text(), null);
}

async function usarExemplo(lugar, tipo, kitId) {
  erro = null;
  try {
    const resposta = await fetch(`/api/kits/${kitId}/${DOCUMENTOS[tipo].arquivo}`);
    if (!resposta.ok) throw new Error();
    await lerDocumento(lugar, tipo, `exemplo-${DOCUMENTOS[tipo].arquivo}`, await resposta.text(), kitId);
  } catch (e) {
    erro = 'Não foi possível carregar o documento de exemplo.';
    desenhar();
  }
}

function trocar(lugar, tipo) {
  if (lugar[tipo] && lugar[tipo].preview) URL.revokeObjectURL(lugar[tipo].preview);
  lugar[tipo] = null;
  desenhar();
}

const enviarParaConferencia = () => executar(async () => {
  const { IDENTIDADE: id, CARTEIRINHA: cart } = docs;
  const kitId = id.kitId && id.kitId === cart.kitId ? id.kitId : null; // gabarito só quando o par é de um mesmo kit
  checkIn = await api('POST', `/api/checkins/${checkIn.id}/documentos`,
    { kitId, identidadeSvg: id.svg, carteirinhaSvg: cart.svg });
  conferenciaAberta = false;
});

const reenviar = () => executar(async () => {
  const corpo = {};
  for (const tipo of checkIn.reenvio.documentos) {
    const d = novos[tipo];
    if (d && d.leitura) corpo[DOCUMENTOS[tipo].campo] = d.svg;
  }
  checkIn = await api('POST', `/api/checkins/${checkIn.id}/reenvio`, corpo);
  novos = vazios();
});

const confirmarGuia = () => executar(async () => { checkIn = await api('POST', `/api/checkins/${checkIn.id}/confirmar-guia`); });
const registrarChegada = () => executar(async () => { checkIn = await api('POST', `/api/checkins/${checkIn.id}/chegada`); });

const voltarAoInicio = () => executar(async () => {
  checkIn = null;
  docs = vazios();
  novos = vazios();
  conta = await api('GET', `/api/contas/${conta.id}`);
});

document.getElementById('logo').append(icone('mais'));
document.getElementById('sair').addEventListener('click', sair);
document.getElementById('abrir-ajuda').prepend(icone('conversa'));
document.getElementById('abrir-ajuda').addEventListener('click', () => dialogoAjuda.showModal());
document.getElementById('cancelar-ajuda').addEventListener('click', () => dialogoAjuda.close());
document.getElementById('confirmar-ajuda').addEventListener('click', () => {
  const motivo = document.getElementById('motivo-ajuda').value;
  dialogoAjuda.close();
  document.getElementById('motivo-ajuda').value = '';
  executar(async () => { checkIn = await api('POST', `/api/checkins/${checkIn.id}/ajuda`, { motivo }); });
});

// ---------- Desenho ----------

function desenhar() {
  desenharTopo();
  desenharTrilha();
  const status = checkIn ? checkIn.status : null;
  let partes;

  if (!conta) partes = telaLogin();
  else if (!checkIn) partes = telaInicioConta();
  else if (status === 'AGUARDANDO_DOCUMENTOS') partes = telaDocumentos();
  else if (status === 'AGUARDANDO_CONFIRMACAO_GUIA') partes = telaGuia();
  else if (status === 'PRONTO_PARA_CHEGADA') partes = telaPronto();
  else if (status === 'CONCLUIDO') partes = telaConcluido();
  else if (status === 'ATENDIMENTO_REMOTO') partes = telaRemoto();
  else if (status === 'APOIO_PRESENCIAL') partes = telaPresencial();
  else partes = [];

  if (checkIn && checkIn.verificacoes.length) partes.push(quadroConferencia());
  if (erro) partes.unshift(el('div', { class: 'aviso erro', role: 'alert' }, icone('alerta'), el('span', {}, erro)));

  tela.replaceChildren(...partes.filter(Boolean));
  document.getElementById('abrir-ajuda').hidden = !checkIn || status === 'CONCLUIDO' || status === 'APOIO_PRESENCIAL';

  // Mudou de tela (não só de detalhe): volta ao topo, como numa navegação.
  const chave = [conta && conta.id, status, checkIn && checkIn.reenvio.feitos].join('|');
  if (chave !== ultimaTela) {
    ultimaTela = chave;
    window.scrollTo({ top: 0 });
  }
  acompanhar();
}

function definirCabecalho(titulo, subtitulo) {
  document.getElementById('cabecalho').replaceChildren(
    el('h1', {}, titulo),
    subtitulo ? el('p', { class: 'sub' }, subtitulo) : null);
}

function desenharTopo() {
  document.getElementById('conta-topo').hidden = !conta;
  document.getElementById('nome-conta').textContent = conta ? conta.nome : '';
  document.getElementById('iniciais').textContent = conta ? iniciais(conta.nome) : '';
}

function desenharTrilha() {
  const trilha = document.getElementById('trilha');
  trilha.hidden = !checkIn;
  if (!checkIn) return;
  let atual = 0;
  let parada = false;
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
  trilha.replaceChildren(...ETAPAS.map((nome, i) => {
    const classe = i < atual ? 'feita' : i === atual ? (parada ? 'parada' : 'atual') : '';
    return el('li', { class: classe, 'aria-current': i === atual ? 'step' : null },
      el('span', { class: 'bolinha' }, i < atual ? icone('check') : i === atual && parada ? '!' : String(i + 1)),
      nome);
  }));
}

// Peças reutilizadas

function selo(nomeIcone, classe) { return el('span', { class: 'selo-icone' + (classe ? ' ' + classe : '') }, icone(nomeIcone)); }

function tituloComIcone(nomeIcone, titulo, subtitulo, classe) {
  return el('div', { class: 'titulo-icone' }, selo(nomeIcone, classe),
    el('div', {}, el('h2', {}, titulo), subtitulo ? el('p', { class: 'suave pequeno' }, subtitulo) : null));
}

function aviso(texto, tipo, nomeIcone) {
  return el('div', { class: 'aviso' + (tipo ? ' ' + tipo : '') }, icone(nomeIcone || 'alerta'), el('span', {}, texto));
}

function nota(texto, nomeIcone) { return el('p', { class: 'nota' }, icone(nomeIcone || 'escudo'), el('span', {}, texto)); }

function nomePaciente() { return conta ? conta.primeiroNome : checkIn.nomeLido; }

// ----- Login -----

function telaLogin() {
  definirCabecalho('Sua recepção, antes de chegar',
    'Escolha o horário, envie seus documentos pelo celular e, no hospital, só pegue a senha.');
  const enviar = (ev) => {
    ev.preventDefault();
    entrar(document.getElementById('email').value, document.getElementById('senha').value);
  };
  return [
    el('section', { class: 'cartao' },
      el('h2', {}, 'Entrar na sua conta'),
      el('form', { class: 'formulario', onsubmit: enviar },
        el('div', { class: 'campo' },
          el('label', { for: 'email' }, 'E-mail'),
          el('input', { type: 'email', id: 'email', autocomplete: 'username', placeholder: 'voce@exemplo.test', required: true })),
        el('div', { class: 'campo' },
          el('label', { for: 'senha' }, 'Senha'),
          el('input', { type: 'password', id: 'senha', autocomplete: 'current-password', required: true })),
        el('button', { class: 'primario largo', type: 'submit', disabled: ocupado }, 'Entrar', icone('seta'))),
      nota('Login simulado: nesta demonstração não há autenticação real.')),
    contasDemo.length ? el('section', { class: 'cartao discreto' },
      el('div', { class: 'cartao-topo' },
        el('h2', {}, 'Contas de demonstração'),
        el('span', { class: 'chip info' }, 'senha: demo')),
      el('p', { class: 'suave pequeno' }, 'Roteiro da demo, não aparece para um paciente real. Cada conta traz documentos fictícios de um cenário.'),
      el('div', { class: 'contas' }, contasDemo.map((c) =>
        el('button', { class: 'conta', onclick: () => entrar(c.email, 'demo'), disabled: ocupado },
          el('span', { class: 'avatar-iniciais' }, iniciais(c.nome)),
          el('span', { class: 'conta-texto' }, el('strong', {}, c.nome), el('span', { class: 'suave pequeno' }, c.cenario)),
          el('span', { class: 'chip ' + (c.esperado === 'CONCLUIR' ? 'ok' : 'atencao') },
            c.esperado === 'CONCLUIR' ? 'Tudo certo' : 'Com pendência'))))) : null,
  ];
}

// ----- Início da conta: visita de hoje e horário -----

function telaInicioConta() {
  const v = conta.visitaHoje;
  definirCabecalho(`Olá, ${conta.primeiroNome}`, v
    ? 'Sua visita é hoje. Escolha o melhor horário, adiante o check-in e chegue direto para a senha.'
    : 'Você não tem visita agendada para hoje.');
  if (!v) return [];
  return [cartaoVisita(v), cartaoCheckIn()];
}

function cartaoVisita(v) {
  const abrirSeletor = escolhendoHorario || v.horarioPassou;
  return el('section', { class: 'cartao' },
    el('div', { class: 'cartao-topo' },
      tituloComIcone('calendario', 'Sua visita de hoje'),
      el('span', { class: 'chip ' + (v.horarioPassou ? 'atencao' : 'ok') }, v.horarioPassou ? 'Horário já passou' : 'Agendada')),
    el('div', { class: 'visita' },
      el('div', { class: 'visita-hora' }, el('small', {}, 'Horário'), v.horario),
      el('div', { class: 'visita-texto' },
        el('div', { class: 'visita-servico' }, v.servico),
        el('div', { class: 'suave pequeno com-icone' }, icone('local'), v.local))),
    avisoHorario ? aviso(avisoHorario, 'ok', 'check') : null,
    v.horarioPassou ? aviso('Esse horário já passou. Escolha um novo horário para hoje.') : null,
    abrirSeletor
      ? seletorHorarios(v)
      : el('button', { class: 'secundario', onclick: () => { escolhendoHorario = true; avisoHorario = null; desenhar(); } },
          icone('relogio'), 'Escolher outro horário'));
}

function seletorHorarios(v) {
  const horarios = conta.horarios || [];
  const livres = horarios.filter((h) => h.disponivel);
  const grupos = [
    ['Manhã', (h) => h < '12:00'],
    ['Tarde', (h) => h >= '12:00' && h < '18:00'],
    ['Noite', (h) => h >= '18:00'],
  ].map(([nome, filtro]) => [nome, horarios.filter((h) => filtro(h.horario))]).filter(([, l]) => l.length);

  return el('div', { class: 'horarios' },
    el('div', { class: 'cartao-topo' },
      el('h3', {}, 'Horários disponíveis hoje'),
      !v.horarioPassou ? el('button', { class: 'fantasma compacto', onclick: () => { escolhendoHorario = false; desenhar(); } }, 'Manter ' + v.horario) : null),
    livres.length === 0 ? aviso('Não há outros horários livres hoje. Se precisar, fale com uma pessoa.', 'info', 'relogio') : null,
    ...grupos.map(([nome, lista]) => el('div', {},
      el('div', { class: 'grupo-titulo' }, nome),
      el('div', { class: 'chips-horario' }, lista.map((h) =>
        el('button', {
          class: 'horario' + (h.atual ? ' atual' : ''),
          disabled: ocupado || (!h.disponivel && !h.atual),
          'aria-pressed': String(h.atual),
          'aria-label': h.horario + (h.atual ? ' (seu horário)' : !h.disponivel ? ' (ocupado)' : ''),
          onclick: () => trocarHorario(h.horario),
        }, h.horario))))),
    el('div', { class: 'legenda' },
      el('span', {}, el('i', {}), 'Livre'),
      el('span', {}, el('i', { class: 'atual' }), 'Seu horário'),
      el('span', {}, el('i', { class: 'ocupado' }), 'Ocupado')));
}

function cartaoCheckIn() {
  const passos = [
    ['upload', 'Envie documento e carteirinha', 'O assistente mostra o que leu para você conferir.'],
    ['escudo', 'A gente confere', 'Cadastro, carteirinha, agendamento e guia do convênio.'],
    ['senha', 'No hospital, pegue a senha', 'Sem passar pela fila da recepção.'],
  ];
  return el('section', { class: 'cartao' },
    el('h2', {}, 'Check-in antecipado'),
    el('ol', { class: 'passos' }, passos.map(([ic, titulo, texto]) =>
      el('li', {}, selo(ic), el('div', {}, el('strong', {}, titulo), el('div', { class: 'suave pequeno' }, texto))))),
    el('button', { class: 'primario largo', onclick: comecar, disabled: ocupado }, 'Começar check-in', icone('seta')),
    nota('Se algo não conferir, uma pessoa do atendimento assume o seu caso. Você pode pedir ajuda a qualquer momento.', 'pessoa'));
}

// ----- Documentos -----

function telaDocumentos() {
  definirCabecalho('Envie seus documentos', 'Uma imagem de cada. O assistente mostra o que leu para você conferir antes de enviar.');
  const exemplo = conta && conta.kitId;
  const partes = [
    slotDocumento(docs, 'IDENTIDADE', exemplo, 'Usar o documento fictício de exemplo'),
    slotDocumento(docs, 'CARTEIRINHA', exemplo, 'Usar a carteirinha fictícia de exemplo'),
  ];
  const lidos = docs.IDENTIDADE && docs.IDENTIDADE.leitura && docs.CARTEIRINHA && docs.CARTEIRINHA.leitura;
  if (lidos) {
    const atencao = [docs.IDENTIDADE, docs.CARTEIRINHA].some((d) => !d.leitura.completo || d.leitura.alertas.length);
    partes.push(el('section', { class: 'cartao acao-final' },
      atencao
        ? aviso('Algo não ficou certo em um dos documentos. Você pode trocar o arquivo agora ou enviar assim: uma pessoa do atendimento confere.')
        : aviso('Tudo lido. Se os dados estiverem certos, envie para a conferência.', 'ok', 'check'),
      el('button', { class: (atencao ? 'secundario' : 'sucesso') + ' largo', onclick: enviarParaConferencia, disabled: ocupado },
        atencao ? 'Enviar assim mesmo' : 'Os dados estão certos, enviar', icone('seta'))));
  } else {
    partes.push(nota('Os documentos são conferidos juntos, depois que os dois estiverem lidos.', 'escudo'));
  }
  return partes;
}

function estadoDoDocumento(d) {
  if (!d) return ['Pendente', ''];
  if (d.lendo) return ['Lendo…', 'info'];
  if (d.erro) return ['Erro na leitura', 'atencao'];
  if (!d.leitura.completo || d.leitura.alertas.length) return ['Precisa de atenção', 'atencao'];
  return ['Lido', 'ok'];
}

function slotDocumento(lugar, tipo, kitExemplo, rotuloExemplo, subtitulo) {
  const d = lugar[tipo];
  const info = DOCUMENTOS[tipo];
  const [estado, classeEstado] = estadoDoDocumento(d);
  const topo = el('div', { class: 'cartao-topo' },
    tituloComIcone(info.icone, info.titulo, subtitulo),
    el('span', { class: 'chip ' + classeEstado }, estado));

  if (!d) {
    const idInput = 'arquivo-' + tipo.toLowerCase();
    const zona = el('label', { class: 'zona', for: idInput },
      selo('upload'),
      el('strong', {}, 'Toque para escolher a imagem'),
      el('span', { class: 'suave pequeno' }, 'ou arraste o arquivo para cá (SVG fictício)'),
      el('input', { type: 'file', id: idInput, accept: '.svg,image/svg+xml', onchange: (ev) => escolherArquivo(lugar, tipo, ev.target.files[0]) }));
    zona.addEventListener('dragover', (ev) => { ev.preventDefault(); zona.classList.add('arrastando'); });
    zona.addEventListener('dragleave', () => zona.classList.remove('arrastando'));
    zona.addEventListener('drop', (ev) => { ev.preventDefault(); escolherArquivo(lugar, tipo, ev.dataTransfer.files[0]); });
    return el('section', { class: 'cartao' }, topo, zona,
      kitExemplo ? el('button', { class: 'secundario largo', onclick: () => usarExemplo(lugar, tipo, kitExemplo), disabled: ocupado }, icone('brilho'), rotuloExemplo) : null);
  }

  return el('section', { class: 'cartao' }, topo,
    el('div', { class: 'leitura' },
      el('div', { class: 'previa' },
        el('div', { class: 'previa-imagem' + (d.lendo ? ' lendo' : '') }, el('img', { src: d.preview, alt: `${info.titulo} enviado (fictício)` })),
        el('div', { class: 'nome-arquivo' }, d.nome),
        el('button', { class: 'secundario compacto', onclick: () => trocar(lugar, tipo), disabled: d.lendo }, icone('recarregar'), 'Trocar arquivo')),
      balaoAssistente(d, info)));
}

function balaoAssistente(d, info) {
  const cabeca = el('div', { class: 'assistente-cabeca' },
    el('span', { class: 'avatar-assistente' }, icone('brilho')),
    el('div', {}, el('strong', {}, 'Assistente de check-in'), el('span', {}, d.lendo ? 'lendo o documento…' : 'leitura concluída')));

  if (d.lendo) {
    return el('div', { class: 'assistente', 'aria-live': 'polite' }, cabeca,
      el('p', {}, `Lendo ${info.curto}`), el('span', { class: 'digitando' }, el('i'), el('i'), el('i')));
  }
  if (d.erro) {
    return el('div', { class: 'assistente' }, cabeca, el('p', { class: 'erro' }, d.erro));
  }
  const l = d.leitura;
  const lidos = l.campos.filter((c) => c.valor).length;
  const mensagem = lidos === 0
    ? `Não consegui ler nada em ${info.curto}. Tente outro arquivo ou envie assim, e uma pessoa confere.`
    : l.completo ? `Li ${info.curto}. Confira se está tudo certo:` : `Li ${info.curto}, mas parte não ficou legível:`;
  return el('div', { class: 'assistente', 'aria-live': 'polite' }, cabeca,
    el('p', {}, mensagem),
    el('div', { class: 'campos-lidos' }, l.campos.map((c) =>
      el('div', { class: 'campo-lido' + (!c.valor ? ' faltou' : c.alerta ? ' atencao' : ''), title: c.alerta },
        el('span', { class: 'rotulo' }, c.rotulo),
        el('span', { class: 'valor' }, c.valor && !c.alerta ? icone('check') : icone('alerta'), c.valor || 'não consegui ler')))),
    ...l.alertas.map((a) => aviso(a)),
    el('p', { class: 'fonte' }, 'Fonte: ' + l.fonte));
}

// ----- Pendência: atendimento remoto e reenvio -----

function telaRemoto() {
  const r = checkIn.reenvio;
  const podeReenviar = r.documentos.length > 0;
  definirCabecalho('Encontramos uma pendência', podeReenviar
    ? 'Se for um documento vencido ou errado, envie o certo agora. Se preferir, aguarde: o atendimento já recebeu seu caso.'
    : 'Uma pessoa do atendimento já recebeu seu caso e responde por aqui.');

  const partes = [
    el('section', { class: 'cartao' },
      el('div', { class: 'cartao-topo' },
        tituloComIcone('alerta', 'O que precisa de atenção', null, 'atencao'),
        el('span', { class: 'chip atencao' }, 'Com o atendimento remoto')),
      r.feitos > 0 ? aviso(`Recebemos o documento que você reenviou, mas a nova conferência ainda encontrou pendência.`, 'info', 'recarregar') : null,
      el('ul', { class: 'motivos' }, checkIn.motivos.map((m) => el('li', {}, icone('alerta'), el('span', {}, m)))),
      nota('Esta tela atualiza sozinha quando o atendimento responder. Você não precisa refazer nada.', 'relogio')),
  ];

  if (podeReenviar) {
    const kit = conta && kitsPorId[conta.kitId];
    const correcao = kit && kit.correcaoId;
    const restantes = r.limite - r.feitos;
    partes.push(el('section', { class: 'cartao' },
      tituloComIcone('recarregar', 'Resolver agora',
        'Envie o documento atualizado ou correto. Ele passa pela mesma conferência; se tudo conferir, seu check-in segue sem esperar o atendimento.'),
      nota(restantes === 1 ? 'Você pode reenviar mais 1 vez.' : `Você pode reenviar mais ${restantes} vezes.`, 'escudo')));
    for (const tipo of r.documentos) {
      partes.push(slotDocumento(novos, tipo, correcao,
        tipo === 'IDENTIDADE' ? 'Usar o documento correto de exemplo' : 'Usar a carteirinha renovada de exemplo',
        'Reenviar este documento'));
    }
    const prontos = r.documentos.filter((t) => novos[t] && novos[t].leitura);
    const lendo = r.documentos.some((t) => novos[t] && novos[t].lendo);
    if (prontos.length) {
      partes.push(el('section', { class: 'cartao acao-final' },
        el('button', { class: 'sucesso largo', onclick: reenviar, disabled: ocupado || lendo },
          icone('recarregar'), prontos.length > 1 ? 'Reenviar documentos para conferência' : 'Reenviar para conferência')));
    }
  } else if (r.feitos >= r.limite) {
    partes.push(aviso(`Você já reenviou ${r.feitos} vezes. Agora uma pessoa do atendimento conclui o seu caso.`, 'info', 'pessoa'));
  } else {
    partes.push(aviso('Essa pendência não se resolve com outro documento (por exemplo, guia ainda não autorizada pelo convênio). O atendimento cuida disso e responde aqui.', 'info', 'pessoa'));
  }
  return partes;
}

// ----- Depois da conferência -----

function telaGuia() {
  const g = checkIn.guia;
  const a = checkIn.agendamento;
  definirCabecalho('Tudo certo com seus documentos', `${nomePaciente()}, confira a guia do atendimento de hoje.`);
  return [
    el('section', { class: 'cartao' },
      checkIn.reenvio.corrigido ? aviso('Seu documento atualizado foi conferido. Você não precisou esperar o atendimento.', 'ok', 'check') : null,
      tituloComIcone('guia', 'Guia do convênio', 'Autorizada para o atendimento de hoje', 'ok'),
      el('dl', { class: 'dados' },
        el('dt', {}, 'Guia'), el('dd', {}, g.numero),
        el('dt', {}, 'Procedimento'), el('dd', {}, g.procedimento),
        el('dt', {}, 'Convênio'), el('dd', {}, g.convenio),
        el('dt', {}, 'Válida até'), el('dd', {}, dataBr(g.validade)),
        el('dt', {}, 'Visita'), el('dd', {}, `${a.servico}, hoje às ${a.horario}`)),
      el('button', { class: 'sucesso largo', onclick: confirmarGuia, disabled: ocupado }, icone('check'), 'Confirmar guia'),
      nota('A guia não é essa? Use "Falar com uma pessoa".', 'pessoa')),
  ];
}

function telaPronto() {
  const liberado = checkIn.atendidoPor;
  const a = checkIn.agendamento;
  definirCabecalho(liberado ? 'Pendência resolvida' : 'Pré-check-in concluído', liberado
    ? `${liberado}, do atendimento, conferiu seu caso e liberou o check-in.`
    : `Levou ${segundos(checkIn.tempoAteDesfechoSeg)}. Você não precisa passar pela recepção.`);
  return [
    el('section', { class: 'cartao' },
      el('div', { class: 'resultado' }, selo('check', 'ok grande'),
        el('div', {}, el('h2', {}, 'Tudo pronto para a sua visita'),
          el('p', { class: 'suave' }, 'Quando chegar ao hospital, toque no botão abaixo para receber sua senha.'))),
      a ? el('div', { class: 'visita' },
        el('div', { class: 'visita-hora' }, el('small', {}, 'Hoje'), a.horario),
        el('div', { class: 'visita-texto' },
          el('div', { class: 'visita-servico' }, a.servico),
          el('div', { class: 'suave pequeno com-icone' }, icone('local'), a.proximaEtapa))) : null,
      el('button', { class: 'sucesso largo', onclick: registrarChegada, disabled: ocupado }, icone('local'), 'Cheguei ao hospital')),
  ];
}

function telaConcluido() {
  const etapa = checkIn.agendamento ? checkIn.agendamento.proximaEtapa : null;
  const presencial = checkIn.senha && checkIn.senha.startsWith('P-');
  definirCabecalho('Chegada confirmada', presencial
    ? `${checkIn.atendidoPor || 'A recepção'} registrou sua passagem no balcão.`
    : 'Sua passagem pela recepção foi registrada. Boa consulta!');
  return [
    el('section', { class: 'cartao' },
      presencial ? null : el('div', { class: 'bilhete' },
        el('div', { class: 'rotulo-senha' }, 'Sua senha'),
        el('div', { class: 'numero' }, checkIn.senha),
        el('div', { class: 'destino' }, icone('local'), etapa ? `Siga para: ${etapa}` : 'Siga a orientação da recepção')),
      nota(`Tempo total do check-in: ${segundos(checkIn.tempoTotalSeg)}.`, 'relogio'),
      el('button', { class: 'secundario largo', onclick: voltarAoInicio, disabled: ocupado }, 'Voltar ao início')),
  ];
}

function telaPresencial() {
  definirCabecalho('Procure o balcão de apoio', checkIn.ajudaPedidaPeloPaciente
    ? 'Seu pedido foi registrado. Uma pessoa da recepção vai atender você.'
    : `${checkIn.atendidoPor || 'O atendimento'} pediu que seu caso seja concluído no balcão, com uma pessoa.`);
  return [
    el('section', { class: 'cartao' },
      el('div', { class: 'bilhete' },
        el('div', { class: 'rotulo-senha' }, 'Senha de apoio'),
        el('div', { class: 'numero' }, checkIn.senha),
        el('div', { class: 'destino' }, icone('pessoa'), 'Balcão de apoio da recepção')),
      nota('O que você já enviou está com a recepção. Esta tela atualiza sozinha.', 'relogio')),
  ];
}

function quadroConferencia() {
  const ok = checkIn.verificacoes.filter((v) => v.resultado === 'OK').length;
  const pendentes = checkIn.verificacoes.filter((v) => v.resultado === 'DIVERGENTE' || v.resultado === 'AUSENTE').length;
  const resumo = pendentes
    ? `${pendentes} ${pendentes === 1 ? 'item com pendência' : 'itens com pendência'}, ${ok} conferem`
    : `${ok} itens conferem, nenhuma pendência`;
  return el('section', { class: 'cartao' },
    el('details', { class: 'detalhe-conferencia', open: conferenciaAberta, ontoggle: (ev) => { conferenciaAberta = ev.target.open; } },
      el('summary', {},
        el('span', { class: 'titulo-icone' }, selo('escudo'),
          el('span', {}, el('span', {}, 'O que foi conferido'), el('span', { class: 'suave pequeno', style: 'display:block;font-weight:400' }, resumo))),
        icone('chevron')),
      el('ul', { class: 'conferencia' }, checkIn.verificacoes.map((v) =>
        el('li', {},
          el('span', { class: 'marca-resultado ' + v.resultado, title: ROTULO_RESULTADO[v.resultado] }, SIMBOLO_RESULTADO[v.resultado]),
          el('div', {},
            el('strong', {}, v.titulo), ' ', el('span', { class: 'suave pequeno' }, ROTULO_RESULTADO[v.resultado]),
            el('div', { class: 'pequeno' }, v.detalhe),
            v.valorLido ? el('div', { class: 'pequeno' }, 'Lido no documento: ' + v.valorLido) : null,
            el('div', { class: 'suave pequeno' }, 'Fonte: ' + v.fonte)))))));
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
          novos = vazios();
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
    if (conta) sessionStorage.setItem(CHAVE_CONTA, conta.id);
    else sessionStorage.removeItem(CHAVE_CONTA);
    if (checkIn) sessionStorage.setItem(CHAVE_CHECKIN, checkIn.id);
    else sessionStorage.removeItem(CHAVE_CHECKIN);
  } catch (e) { /* sem armazenamento: só não retoma após recarregar */ }
}

function lerGuardado(chave) {
  try { return sessionStorage.getItem(chave); } catch (e) { return null; }
}

(async function abrir() {
  try {
    const [listaContas, listaKits] = await Promise.all([api('GET', '/api/contas'), api('GET', '/api/kits')]);
    contasDemo = listaContas;
    kitsPorId = Object.fromEntries(listaKits.map((k) => [k.id, k]));
    const contaId = lerGuardado(CHAVE_CONTA);
    if (contaId) conta = await api('GET', `/api/contas/${contaId}`).catch(() => null);
    const checkInId = conta ? lerGuardado(CHAVE_CHECKIN) : null;
    if (checkInId) checkIn = await api('GET', `/api/checkins/${checkInId}`).catch(() => null);
  } catch (e) {
    erro = 'Não foi possível falar com o servidor. Confira se a aplicação está rodando.';
  }
  desenhar();
})();
