# Design: resumo legível dos check-ins

## Intenção

Facilitar a leitura dos cards fechados no painel de atendimento, destacando a
identificação do paciente, o motivo do encaminhamento e a senha sem exigir a
abertura do dropdown.

## Abordagem escolhida

Manter a estrutura atual de `<details>` e alterar somente a composição visual
do `summary` em `src/main/resources/web/atendimento.js`, com estilos em
`estilo.css`. A identificação deixará de ser uma string única separada por
vírgulas; cada informação terá seu próprio bloco. O motivo ficará no resumo,
a senha será destacada e o texto de tempo “Desfecho em” será removido.

## Limites

Não alterar API, estados, regras do check-in, dados exibidos no conteúdo
expandido ou comportamento de abertura/fechamento dos cards.
