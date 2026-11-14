# Resumo legível dos check-ins Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Tornar o resumo fechado dos check-ins mais legível e útil no painel de atendimento.

**Architecture:** Manter o componente HTML `<details>` existente. Separar os dados do paciente em elementos semânticos no `summary`, mostrar os motivos no próprio resumo e aplicar destaque visual à senha via CSS.

**Tech Stack:** JavaScript vanilla, CSS, HTML servido pelo `HttpServer` do JDK.

**Spec:** `docs/superpowers/specs/2026-11-14-painel-resumo-checkins-design.md`

## Global Constraints

- Não alterar API, estados ou regras do check-in.
- Usar somente os arquivos web existentes, sem dependências.
- Preservar a sanitização atual baseada em `textContent`/`el`.

## Review Focus

- Caso sem CPF ou sem kit: o resumo deve continuar exibindo os fallbacks atuais.
- Caso sem senha: não deve aparecer um bloco vazio de senha.
- Caso sem motivos: não deve aparecer um título “Por que está aqui” vazio.
- Caso com vários motivos: todos devem aparecer no resumo, em lista legível.
- Tela estreita: o resumo deve reorganizar suas colunas sem cortar conteúdo.

### Task 1: Reorganizar o resumo dos cards

**Files:**
- Modify: `src/main/resources/web/atendimento.js:79-106`
- Modify: `src/main/resources/web/estilo.css:240-251`

**Interfaces:**
- Consumes: objeto de caso atual, incluindo `pacienteCadastro`, `nomeLido`, `cpfLido`, `kitId`, `id`, `motivos` e `senha`.
- Produces: resumo fechado com identificação em linhas separadas, motivo visível antes da expansão e senha destacada.

- [ ] **Step 1: Atualizar `cartaoCaso(c)` para separar a identificação**

Substituir a string única com vírgulas por elementos distintos para CPF,
kit fictício e check-in, mantendo exatamente os fallbacks existentes.

- [ ] **Step 2: Exibir os motivos no `summary`**

Adicionar uma seção “Por que está aqui” no resumo apenas quando
`c.motivos.length` for maior que zero, com cada motivo como item de lista.
Manter a seção detalhada existente no corpo expandido.

- [ ] **Step 3: Destacar a senha e remover o tempo do desfecho**

Manter a senha no resumo quando existir, usando uma classe própria para
deixá-la maior. Remover do resumo o bloco “Desfecho em ...”.

- [ ] **Step 4: Ajustar o CSS do resumo**

Criar estilos para os blocos de identificação, motivo e senha, usando a
paleta já existente. Garantir que o resumo continue responsivo em telas
estreitas e que textos longos possam quebrar linha.

- [ ] **Step 5: Verificar visualmente e por regressão**

Executar `executar.bat`, abrir `http://localhost:8080/atendimento`, gerar ou
usar um caso com motivo e senha e conferir o card fechado e aberto. Executar
`testar.bat` para confirmar que as regras existentes continuam passando.
