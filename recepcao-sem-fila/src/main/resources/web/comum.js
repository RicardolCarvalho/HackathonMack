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
  if (valor === null || valor === undefined) return 'sem dado';
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
