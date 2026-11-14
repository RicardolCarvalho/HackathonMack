// Painel de atendimento: fila de exceções, histórico de cada caso e medidas da execução.

let casos = [];
let aba = 'remoto';
let ultimoRetrato = '';
const aberturaManual = new Map(); // id -> aberto?
let acaoPendente = null;

const dialogo = document.getElementById('dialogo-acao');

const ABAS = [
  { id: 'remoto', rotulo: 'Atendimento remoto', filtro: (c) => c.status === 'ATENDIMENTO_REMOTO', vazio: 'Nenhum caso esperando o atendimento remoto.' },
  { id: 'presencial', rotulo: 'Apoio presencial', filtro: (c) => c.status === 'APOIO_PRESENCIAL', vazio: 'Ninguém esperando no balcão de apoio.' },
  { id: 'todos', rotulo: 'Todos', filtro: () => true, vazio: 'Nenhum check-in ainda. Abra a tela do paciente e comece um.' },
];

const ACOES = {
  resolver: {
    titulo: 'Resolver pendência e liberar o check-in',
    texto: 'Use quando você conferiu o caso e ele pode seguir. O paciente recebe a liberação na tela dele.',
  },
  'encaminhar-presencial': {
    titulo: 'Encaminhar ao apoio presencial',
    texto: 'Use quando o caso precisa ser concluído no balcão, com o documento original.',
  },
  'concluir-presencial': {
    titulo: 'Registrar atendimento presencial',
    texto: 'Use depois de atender o paciente no balcão. A passagem fica registrada em seu nome.',
  },
};

// ---------- Dados ----------

async function atualizar() {
  try {
    const [novosCasos, medidas] = await Promise.all([api('GET', '/api/atendimento/casos'), api('GET', '/api/metricas')]);
    const retrato = JSON.stringify([novosCasos, medidas]);
    if (retrato === ultimoRetrato) return;
    ultimoRetrato = retrato;
    casos = novosCasos;
    desenharMedidas(medidas);
    desenharCasos();
  } catch (e) { /* servidor fora do ar: tenta de novo no próximo ciclo */ }
}

// ---------- Medidas ----------

function desenharMedidas(m) {
  const decisoes = m.decisoesAvaliadas
    ? `${m.decisoesCorretas} de ${m.decisoesAvaliadas} (${m.proporcaoDecisoesCorretas.toLocaleString('pt-BR')}%)`
    : 'sem dado';
  const itens = [
    [m.total, 'check-ins iniciados'],
    [m.concluidosAutomaticamente, 'concluídos sem intervenção'],
    [m.escaladosRemoto, 'encaminhados ao atendimento remoto'],
    [m.apoioPresencialAPedido, 'pedidos de apoio presencial'],
    [m.corrigidosPeloPaciente, 'pendências resolvidas pelo paciente com reenvio'],
    [segundos(m.tempoMedioAteDesfechoSeg), 'tempo médio até o desfecho'],
    [segundos(m.tempoMedioConcluidoAutomaticoSeg), 'tempo médio dos concluídos sem intervenção'],
    [decisoes, 'decisões do sistema iguais ao gabarito'],
  ];
  document.getElementById('medidas').replaceChildren(...itens.map(([valor, rotulo]) =>
    el('div', { class: 'medida' }, el('div', { class: 'valor' }, valor), el('div', { class: 'rotulo-medida' }, rotulo))));
  document.getElementById('como-ler').textContent = m.comoLer;
}

// ---------- Casos ----------

function desenharCasos() {
  document.getElementById('abas').replaceChildren(...ABAS.map((a) =>
    el('button', { 'aria-pressed': String(a.id === aba), onclick: () => { aba = a.id; desenharCasos(); } },
      `${a.rotulo} (${casos.filter(a.filtro).length})`)));

  const atual = ABAS.find((a) => a.id === aba);
  const lista = casos.filter(atual.filtro);
  document.getElementById('casos').replaceChildren(
    ...(lista.length ? lista.map(cartaoCaso) : [el('div', { class: 'vazio' }, atual.vazio)]));
}

function cartaoCaso(c) {
  const precisaDeAcao = c.status === 'ATENDIMENTO_REMOTO' || c.status === 'APOIO_PRESENCIAL';
  const aberto = aberturaManual.has(c.id) ? aberturaManual.get(c.id) : precisaDeAcao;
  const nome = c.pacienteCadastro || c.nomeLido || 'Paciente não identificado';

  const detalhes = el('details', { class: 'caso ' + c.status, open: aberto },
    el('summary', {},
      el('div', {},
        el('h3', {}, nome, ' ', el('span', { class: 'etiqueta ' + c.status }, ROTULO_STATUS[c.status])),
        el('div', { class: 'suave pequeno' },
          [c.conta ? 'conta ' + c.conta : 'sem conta',
            c.cpfLido ? 'CPF ' + c.cpfLido : 'CPF não lido',
            c.kitId ? 'kit fictício "' + c.kitId + '"' : 'sem kit',
            c.reenvio && c.reenvio.feitos ? `${c.reenvio.feitos} reenvio(s) do paciente` + (c.reenvio.corrigido ? ', resolvido por ele' : '') : null,
            'check-in ' + c.id.slice(0, 8)].filter(Boolean).join(', '))),
      el('div', { class: 'pequeno' },
        c.senha ? el('div', {}, 'Senha ', el('strong', {}, c.senha)) : null,
        c.tempoAteDesfechoSeg !== null ? el('div', { class: 'suave' }, 'Desfecho em ' + segundos(c.tempoAteDesfechoSeg)) : null)),
    el('div', { class: 'corpo' },
      c.motivos.length ? el('div', {}, el('h3', {}, 'Por que está aqui'), el('ul', {}, c.motivos.map((m) => el('li', {}, m)))) : null,
      botoes(c),
      c.verificacoes.length ? tabelaVerificacoes(c) : null,
      el('p', { class: 'pequeno suave' }, textoGabarito(c)),
      el('div', {}, el('h3', {}, 'Histórico'),
        el('ul', { class: 'historico' }, c.eventos.map((e) =>
          el('li', {}, el('span', { class: 'suave' }, new Date(e.quando).toLocaleTimeString('pt-BR')),
            el('span', {}, el('strong', {}, e.ator), ': ', e.descricao)))))));

  detalhes.querySelector('summary').addEventListener('click', () => aberturaManual.set(c.id, !detalhes.open));
  return detalhes;
}

function botoes(c) {
  if (c.status === 'ATENDIMENTO_REMOTO') {
    return el('div', { class: 'acoes' },
      el('button', { class: 'livre', onclick: () => abrirAcao(c, 'resolver') }, 'Resolver e liberar'),
      el('button', { class: 'secundario', onclick: () => abrirAcao(c, 'encaminhar-presencial') }, 'Encaminhar ao apoio presencial'));
  }
  if (c.status === 'APOIO_PRESENCIAL') {
    return el('div', { class: 'acoes' },
      el('button', { onclick: () => abrirAcao(c, 'concluir-presencial') }, 'Registrar atendimento presencial'));
  }
  return null;
}

function tabelaVerificacoes(c) {
  return el('div', {}, el('h3', {}, 'Conferência'),
    el('div', { class: 'rolagem' },
      el('table', {},
        el('thead', {}, el('tr', {}, ['Item', 'Resultado', 'Lido no documento', 'Na base', 'Fonte', 'Detalhe'].map((t) => el('th', {}, t)))),
        el('tbody', {}, c.verificacoes.map((v) =>
          el('tr', { class: v.resultado === 'DIVERGENTE' || v.resultado === 'AUSENTE' ? 'falhou' : null },
            el('td', {}, v.titulo),
            el('td', {}, el('span', { class: 'etiqueta ' + v.resultado }, ROTULO_RESULTADO[v.resultado])),
            el('td', {}, v.valorLido || ''),
            el('td', {}, v.valorCadastro || ''),
            el('td', {}, v.fonte),
            el('td', {}, v.detalhe)))))));
}

function textoGabarito(c) {
  if (!c.desfechoInicial) return 'Ainda sem desfecho.';
  if (c.desfechoInicial === 'APOIO_PRESENCIAL_A_PEDIDO') return 'O paciente pediu ajuda antes de o sistema decidir; este caso não entra na avaliação das decisões.';
  if (c.decisaoCorreta === null) return 'Arquivos enviados sem kit: não há gabarito para avaliar a decisão do sistema.';
  const esperado = c.esperado === 'CONCLUIR' ? 'concluir sem intervenção' : 'encaminhar para uma pessoa';
  return `Gabarito do kit: ${esperado}. Decisão do sistema: ${c.decisaoCorreta ? 'igual ao gabarito' : 'DIFERENTE do gabarito'}.`;
}

// ---------- Ações do atendente ----------

function abrirAcao(caso, acao) {
  acaoPendente = { id: caso.id, acao };
  document.getElementById('titulo-acao').textContent = ACOES[acao].titulo;
  document.getElementById('texto-acao').textContent = ACOES[acao].texto;
  document.getElementById('observacao').value = '';
  document.getElementById('erro-acao').hidden = true;
  try { document.getElementById('atendente').value = localStorage.getItem('recepcao-sem-fila.atendente') || ''; } catch (e) { /* ignora */ }
  dialogo.showModal();
}

document.getElementById('cancelar-acao').addEventListener('click', () => dialogo.close());
document.getElementById('confirmar-acao').addEventListener('click', async () => {
  const atendente = document.getElementById('atendente').value;
  const observacao = document.getElementById('observacao').value;
  try {
    await api('POST', `/api/atendimento/casos/${acaoPendente.id}/${acaoPendente.acao}`, { atendente, observacao });
    try { localStorage.setItem('recepcao-sem-fila.atendente', atendente.trim()); } catch (e) { /* ignora */ }
    dialogo.close();
    atualizar();
  } catch (e) {
    const erro = document.getElementById('erro-acao');
    erro.textContent = e.message;
    erro.hidden = false;
  }
});

document.getElementById('reiniciar').addEventListener('click', async () => {
  if (!confirm('Apagar todos os check-ins e medidas desta execução?')) return;
  await api('POST', '/api/demo/reiniciar');
  aberturaManual.clear();
  atualizar();
});

atualizar();
setInterval(atualizar, 2000);
