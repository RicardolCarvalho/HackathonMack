// Funções compartilhadas pelas duas telas.

/** Cria um elemento. Texto sempre entra como textContent (nunca innerHTML), porque nomes vêm de arquivos enviados. */
function el(tag, atributos, ...filhos) {
  const no = document.createElement(tag);
  for (const [chave, valor] of Object.entries(atributos || {})) {
    if (valor === null || valor === undefined || valor === false) continue;
    if (chave === 'class') no.className = valor;
    else if (chave.startsWith('on')) no.addEventListener(chave.slice(2), valor);
    else no.setAttribute(chave, valor === true ? '' : valor);
  }
  for (const filho of filhos.flat()) {
    if (filho === null || filho === undefined || filho === false) continue;
    no.append(filho instanceof Node ? filho : document.createTextNode(String(filho)));
  }
  return no;
}

/** Ícones de traço (24x24). Desenhos fixos daqui, nunca texto vindo de fora. */
const ICONES = {
  mais: '<path d="M12 5v14M5 12h14"/>',
  calendario: '<rect x="3.5" y="5" width="17" height="15.5" rx="3"/><path d="M3.5 10h17M8.5 3v4M15.5 3v4"/>',
  relogio: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
  local: '<path d="M12 21s-6.5-5.8-6.5-11a6.5 6.5 0 0 1 13 0c0 5.2-6.5 11-6.5 11z"/><circle cx="12" cy="10" r="2.3"/>',
  identidade: '<rect x="2.5" y="5" width="19" height="14" rx="2.5"/><circle cx="8.5" cy="11" r="2.2"/><path d="M5.3 16.2c.6-1.6 1.7-2.4 3.2-2.4s2.6.8 3.2 2.4M14.5 10h4M14.5 13.5h2.8"/>',
  cartao: '<rect x="2.5" y="5" width="19" height="14" rx="2.5"/><path d="M2.5 9.5h19M6 15h4.5"/>',
  upload: '<path d="M12 15.5V4.5M7.5 9 12 4.5 16.5 9"/><path d="M4.5 15v3a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-3"/>',
  check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
  alerta: '<path d="M10.3 4.3 2.8 17.5A2 2 0 0 0 4.5 20.5h15a2 2 0 0 0 1.7-3L13.7 4.3a2 2 0 0 0-3.4 0z"/><path d="M12 9.5v4M12 17v.01"/>',
  pessoa: '<circle cx="12" cy="8" r="3.8"/><path d="M4.8 20c1.1-3.4 3.8-5.3 7.2-5.3s6.1 1.9 7.2 5.3"/>',
  sair: '<path d="M14.5 4.5h3a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2h-3M10 16l-4-4 4-4M6 12h9.5"/>',
  brilho: '<path d="M12 3.5l1.7 4.6 4.6 1.7-4.6 1.7L12 16.1l-1.7-4.6L5.7 9.8l4.6-1.7L12 3.5z"/><path d="M18.3 15.2l.7 1.9 1.9.7-1.9.7-.7 1.9-.7-1.9-1.9-.7 1.9-.7.7-1.9z"/>',
  escudo: '<path d="M12 3 5 6v5.5c0 4.4 3 8 7 9.5 4-1.5 7-5.1 7-9.5V6l-7-3z"/><path d="m9 12 2.2 2.2L15.3 10"/>',
  recarregar: '<path d="M19.5 11A7.5 7.5 0 0 0 6.2 7L4.5 9M4.5 4.5V9H9M4.5 13a7.5 7.5 0 0 0 13.3 4l1.7-2M19.5 19.5V15H15"/>',
  seta: '<path d="M5 12h14M13 6l6 6-6 6"/>',
  chevron: '<path d="m9.5 6 6 6-6 6"/>',
  guia: '<path d="M7 3h7l5 5v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z"/><path d="M14 3v5h5M8.5 13h7M8.5 16.5h4.5"/>',
  senha: '<path d="M4 7.5a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2V10a2 2 0 0 0 0 4v2.5a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V14a2 2 0 0 0 0-4V7.5z"/><path d="M14 5.5v13" stroke-dasharray="2 2"/>',
  conversa: '<path d="M20 12a8 8 0 0 1-11.6 7.1L4 20l1-4.1A8 8 0 1 1 20 12z"/><path d="M8.5 11h.01M12 11h.01M15.5 11h.01"/>',
};

function icone(nome, classe) {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('class', 'icone' + (classe ? ' ' + classe : ''));
  svg.setAttribute('aria-hidden', 'true');
  svg.innerHTML = ICONES[nome] || '';
  return svg;
}

function iniciais(nome) {
  const partes = (nome || '').trim().split(/\s+/);
  return ((partes[0] || '')[0] || '') + ((partes.length > 1 ? partes[partes.length - 1] : '')[0] || '');
}

async function api(metodo, caminho, corpo) {
  const resposta = await fetch(caminho, {
    method: metodo,
    headers: corpo ? { 'Content-Type': 'application/json' } : {},
    body: corpo ? JSON.stringify(corpo) : undefined,
  });
  const dados = await resposta.json().catch(() => ({}));
  if (!resposta.ok) throw new Error(dados.erro || 'Não foi possível concluir a ação.');
  return dados;
}

function segundos(valor) {
  if (valor === null || valor === undefined) return 'Sem dado';
  if (valor < 60) return valor.toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + ' s';
  const min = Math.floor(valor / 60);
  return min + ' min ' + Math.round(valor - min * 60) + ' s';
}

function dataBr(iso) {
  if (!iso) return '';
  const [a, m, d] = iso.split('-');
  return d + '/' + m + '/' + a;
}

const ROTULO_STATUS = {
  AGUARDANDO_DOCUMENTOS: 'Aguardando documentos',
  AGUARDANDO_CONFIRMACAO_GUIA: 'Aguardando confirmação da guia',
  PRONTO_PARA_CHEGADA: 'Pronto para a chegada',
  ATENDIMENTO_REMOTO: 'Atendimento remoto',
  APOIO_PRESENCIAL: 'Apoio presencial',
  CONCLUIDO: 'Chegada registrada',
};

const ROTULO_RESULTADO = {
  OK: 'Confere',
  DIVERGENTE: 'Divergente',
  AUSENTE: 'Dado ausente',
  SIMULADO: 'Simulado',
  NAO_AVALIADO: 'Não avaliado',
};

const SIMBOLO_RESULTADO = { OK: '✓', DIVERGENTE: '!', AUSENTE: '?', SIMULADO: 'S', NAO_AVALIADO: '–' };
