package br.com.recepcaosemfila.servico;

import br.com.recepcaosemfila.dados.BaseSintetica;
import br.com.recepcaosemfila.dados.ContasSinteticas;
import br.com.recepcaosemfila.dados.KitsFicticios;
import br.com.recepcaosemfila.dominio.Agendamento;
import br.com.recepcaosemfila.dominio.CheckIn;
import br.com.recepcaosemfila.dominio.Desfecho;
import br.com.recepcaosemfila.dominio.DocumentoLido;
import br.com.recepcaosemfila.dominio.Evento;
import br.com.recepcaosemfila.dominio.StatusCheckIn;
import br.com.recepcaosemfila.dominio.TipoDocumento;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.recepcaosemfila.dominio.StatusCheckIn.AGUARDANDO_CONFIRMACAO_GUIA;
import static br.com.recepcaosemfila.dominio.StatusCheckIn.AGUARDANDO_DOCUMENTOS;
import static br.com.recepcaosemfila.dominio.StatusCheckIn.APOIO_PRESENCIAL;
import static br.com.recepcaosemfila.dominio.StatusCheckIn.ATENDIMENTO_REMOTO;
import static br.com.recepcaosemfila.dominio.StatusCheckIn.CONCLUIDO;
import static br.com.recepcaosemfila.dominio.StatusCheckIn.PRONTO_PARA_CHEGADA;

/**
 * Máquina de estados do check-in:
 *
 *   AGUARDANDO_DOCUMENTOS -> (conferência ok)    AGUARDANDO_CONFIRMACAO_GUIA -> PRONTO_PARA_CHEGADA -> CONCLUIDO
 *                         -> (alguma falha)      ATENDIMENTO_REMOTO -> PRONTO_PARA_CHEGADA (liberado por uma pessoa)
 *                                                                   -> APOIO_PRESENCIAL
 *   qualquer estado aberto -> (paciente pede ajuda) APOIO_PRESENCIAL -> CONCLUIDO (registrado por uma pessoa)
 *
 * O sistema nunca "resolve" uma exceção sozinho: toda falha vai para uma pessoa, com nome e observação registrados.
 */
public final class CheckInServico {
    private final Clock relogio;
    private final LeitorDocumento leitor;
    private final Conferencia conferencia;
    private final KitsFicticios kits;
    private final ContasSinteticas contas;

    private final Map<String, CheckIn> checkIns = new LinkedHashMap<>();
    private int senhasFluxo = 0;
    private int senhasApoio = 0;

    public CheckInServico(Clock relogio, LeitorDocumento leitor, Conferencia conferencia, KitsFicticios kits,
                          ContasSinteticas contas) {
        this.relogio = relogio;
        this.leitor = leitor;
        this.conferencia = conferencia;
        this.kits = kits;
        this.contas = contas;
    }

    // ---------- Conta do paciente (login simulado) ----------

    public ContasSinteticas.Conta entrar(String email, String senha) {
        ContasSinteticas.Conta c = contas.porEmail(email).orElse(null);
        if (c == null || !ContasSinteticas.SENHA_DEMO.equals(senha)) {
            throw new RegraException(401, "E-mail ou senha não conferem.");
        }
        return c;
    }

    public ContasSinteticas.Conta conta(String contaId) {
        return contas.porId(contaId).orElseThrow(() -> new RegraException(404, "Conta não encontrada."));
    }

    /** Resumo da conta para a tela inicial do portal: quem é, a visita de hoje e os horários que dá para escolher. */
    public synchronized Map<String, Object> resumoConta(String contaId) {
        ContasSinteticas.Conta c = conta(contaId);
        LocalDate hoje = LocalDate.now(relogio);
        LocalTime agora = LocalTime.now(relogio);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id());
        m.put("email", c.email());
        m.put("nome", c.paciente().nome());
        m.put("primeiroNome", c.primeiroNome());
        m.put("kitId", c.kitId());
        m.put("visitaHoje", contas.agendamentoDeHoje(c, hoje).map(a -> {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("data", a.data());
            v.put("horario", a.horario());
            v.put("servico", a.servico());
            v.put("local", a.proximaEtapa());
            v.put("horarioPassou", !LocalTime.parse(a.horario()).isAfter(agora));
            return v;
        }).orElse(null));
        List<Map<String, Object>> horarios = new ArrayList<>();
        for (BaseSintetica.Horario h : contas.horarios(c, hoje, agora)) {
            Map<String, Object> hm = new LinkedHashMap<>();
            hm.put("horario", h.horario());
            hm.put("disponivel", h.disponivel());
            hm.put("atual", h.atual());
            horarios.add(hm);
        }
        m.put("horarios", horarios);
        return m;
    }

    /**
     * O paciente escolhe outro horário livre para a visita de hoje (agenda sintética do mesmo serviço).
     * Um check-in em aberto da conta passa a usar o novo horário.
     */
    public synchronized Map<String, Object> remarcar(String contaId, String horario) {
        ContasSinteticas.Conta conta = conta(contaId);
        LocalDate hoje = LocalDate.now(relogio);
        String escolhido = horario == null ? "" : horario.trim();
        BaseSintetica.Horario h = contas.horarios(conta, hoje, LocalTime.now(relogio)).stream()
                .filter(x -> x.horario().equals(escolhido)).findFirst()
                .orElseThrow(() -> new RegraException(400, "Horário indisponível: fora da agenda de hoje ou já passou."));
        if (!h.atual()) {
            if (!h.disponivel()) throw new RegraException(409, "Esse horário acabou de ser ocupado. Escolha outro.");
            Agendamento novo = contas.remarcar(conta, hoje, escolhido);
            for (CheckIn c : checkIns.values()) {
                if (conta.id().equals(c.contaId) && c.status != CONCLUIDO) {
                    if (c.agendamento != null) c.agendamento = novo;
                    evento(c, "PACIENTE", "Trocou o horário da visita para " + escolhido + ".");
                }
            }
        }
        return resumoConta(contaId);
    }

    // ---------- Ações do paciente ----------

    public synchronized CheckIn iniciar() {
        return iniciar(null);
    }

    public synchronized CheckIn iniciar(String contaId) {
        ContasSinteticas.Conta conta = contaId == null ? null : conta(contaId);
        CheckIn c = new CheckIn(UUID.randomUUID().toString(), relogio.instant());
        if (conta != null) {
            c.contaId = conta.id();
            c.contaEmail = conta.email();
            c.titular = conta.paciente();
        }
        checkIns.put(c.id, c);
        String visita = conta == null ? "" : contas.agendamentoDeHoje(conta, LocalDate.now(relogio))
                .map(a -> " Visita de hoje às " + a.horario() + ".").orElse("");
        evento(c, "PACIENTE", conta == null ? "Check-in iniciado."
                : "Check-in iniciado pela conta " + conta.email() + " (login simulado)." + visita);
        return c;
    }

    /** O que foi lido de um documento e os avisos que dá para dar só com ele (por exemplo, carteirinha vencida). */
    public record Leitura(DocumentoLido documento, List<Conferencia.Alerta> alertas) {}

    /**
     * Lê um documento assim que o paciente envia o arquivo, para mostrar o que foi extraído antes da conferência.
     * Não confere nada nem muda o estado: a conferência só acontece quando os documentos são enviados.
     * Vale no envio inicial e, num caso com pendência, para o documento que o paciente pode reenviar.
     */
    public synchronized Leitura lerDocumento(String id, TipoDocumento tipo, String conteudo) {
        CheckIn c = buscar(id);
        if (c.status != AGUARDANDO_DOCUMENTOS && !c.documentosParaReenviar().contains(tipo)) {
            throw new RegraException(409, "Este documento não pode ser enviado no estado atual do check-in (" + c.status + ").");
        }
        DocumentoLido d = leitor.ler(conteudo, tipo);
        int lidos = LeitorDocumento.obrigatorios(tipo).size() - d.ausentes().size();
        String nomeDoc = tipo == TipoDocumento.IDENTIDADE ? "Documento de identidade lido" : "Carteirinha lida";
        evento(c, "SISTEMA", nomeDoc + " por " + d.fonte() + ": " + lidos + " de "
                + LeitorDocumento.obrigatorios(tipo).size() + " campos"
                + (d.ausentes().isEmpty() ? "." : "; não lido: "
                + String.join(", ", d.ausentes().stream().map(LeitorDocumento::rotulo).toList()) + "."));
        return new Leitura(d, Conferencia.alertasDaLeitura(d, LocalDate.now(relogio)));
    }

    public synchronized CheckIn enviarDocumentos(String id, String kitId, String svgIdentidade, String svgCarteirinha) {
        CheckIn c = buscar(id);
        exigir(c, AGUARDANDO_DOCUMENTOS);

        KitsFicticios.Kit kit = kitId == null ? null : kits.kit(kitId);
        if (kit != null) {
            c.kitId = kit.id();
            // Com conta, o kit de outra pessoa (ou sem CPF legível) deve ser encaminhado, qualquer que seja o gabarito dele.
            boolean deOutraPessoa = c.titular != null && (kit.cpf() == null
                    || !BaseSintetica.digitos(kit.cpf()).equals(BaseSintetica.digitos(c.titular.cpf())));
            c.esperado = deOutraPessoa ? "ESCALAR" : kit.esperado();
        }

        c.identidadeLida = leitor.ler(svgIdentidade, TipoDocumento.IDENTIDADE);
        c.carteirinhaLida = leitor.ler(svgCarteirinha, TipoDocumento.CARTEIRINHA);
        evento(c, "PACIENTE", "Documentos enviados" + (kit == null ? " (arquivos próprios)." : " (kit fictício \"" + kit.titulo() + "\")."));

        Conferencia.Resultado r = conferirLidos(c);
        if (r.aprovado()) {
            c.status = AGUARDANDO_CONFIRMACAO_GUIA;
            evento(c, "SISTEMA", "Conferência sem pendências. Aguardando o paciente confirmar a guia.");
        } else {
            c.motivos.addAll(r.motivos());
            c.status = ATENDIMENTO_REMOTO;
            desfecho(c, Desfecho.ESCALADO_REMOTO);
            evento(c, "SISTEMA", "Encaminhado ao atendimento remoto. " + String.join(" ", c.motivos));
        }
        return c;
    }

    /**
     * Num caso com pendência de documento, o paciente reenvia o documento atualizado ou correto (null = mantém o anterior).
     * A conferência roda de novo, com as mesmas regras. Se nada falhar, o check-in segue para a guia; se ainda falhar,
     * continua com o atendimento remoto. O desfecho inicial (encaminhado) não muda, e tudo fica no histórico.
     */
    public synchronized CheckIn reenviar(String id, String svgIdentidade, String svgCarteirinha) {
        CheckIn c = buscar(id);
        exigir(c, ATENDIMENTO_REMOTO);
        if (c.reenvios >= CheckIn.LIMITE_REENVIOS) {
            throw new RegraException(409, "Limite de reenvios atingido. Uma pessoa do atendimento vai concluir o seu caso.");
        }
        List<TipoDocumento> permitidos = c.documentosParaReenviar();
        if (permitidos.isEmpty()) {
            throw new RegraException(409, "A pendência deste caso não se resolve com outro documento. O atendimento vai cuidar dela.");
        }
        if (svgIdentidade == null && svgCarteirinha == null) throw new RegraException(400, "Envie pelo menos um documento.");
        if (svgIdentidade != null && !permitidos.contains(TipoDocumento.IDENTIDADE)) {
            throw new RegraException(409, "O documento de identidade não tem pendência; reenvie só a carteirinha.");
        }
        if (svgCarteirinha != null && !permitidos.contains(TipoDocumento.CARTEIRINHA)) {
            throw new RegraException(409, "A carteirinha não tem pendência; reenvie só o documento de identidade.");
        }

        c.reenvios++;
        List<String> enviados = new ArrayList<>();
        if (svgIdentidade != null) {
            c.identidadeLida = leitor.ler(svgIdentidade, TipoDocumento.IDENTIDADE);
            enviados.add("documento de identidade");
        }
        if (svgCarteirinha != null) {
            c.carteirinhaLida = leitor.ler(svgCarteirinha, TipoDocumento.CARTEIRINHA);
            enviados.add("carteirinha");
        }
        evento(c, "PACIENTE", "Reenviou " + String.join(" e ", enviados) + " (reenvio " + c.reenvios + " de "
                + CheckIn.LIMITE_REENVIOS + ").");

        Conferencia.Resultado r = conferirLidos(c);
        c.motivos.clear();
        if (r.aprovado()) {
            c.status = AGUARDANDO_CONFIRMACAO_GUIA;
            c.corrigidoPeloPaciente = true;
            evento(c, "SISTEMA", "Nova conferência sem pendências após o reenvio. O caso saiu do atendimento remoto.");
        } else {
            c.motivos.addAll(r.motivos());
            evento(c, "SISTEMA", "Nova conferência ainda com pendência. Segue no atendimento remoto. " + String.join(" ", c.motivos));
        }
        return c;
    }

    private Conferencia.Resultado conferirLidos(CheckIn c) {
        c.nomeLido = c.identidadeLida.campo(LeitorDocumento.NOME);
        c.cpfLido = c.identidadeLida.campo(LeitorDocumento.CPF);
        Conferencia.Resultado r = conferencia.conferir(c.identidadeLida, c.carteirinhaLida, LocalDate.now(relogio), c.titular);
        c.verificacoes = r.verificacoes();
        c.paciente = r.paciente();
        c.agendamento = r.agendamento();
        c.guia = r.guia();
        return r;
    }

    public synchronized CheckIn confirmarGuia(String id) {
        CheckIn c = buscar(id);
        exigir(c, AGUARDANDO_CONFIRMACAO_GUIA);
        c.status = PRONTO_PARA_CHEGADA;
        desfecho(c, Desfecho.CONCLUIDO_AUTOMATICO);
        evento(c, "PACIENTE", "Guia " + c.guia.numero() + " confirmada. Pré-check-in concluído.");
        return c;
    }

    public synchronized CheckIn registrarChegada(String id) {
        CheckIn c = buscar(id);
        exigir(c, PRONTO_PARA_CHEGADA);
        c.status = CONCLUIDO;
        c.concluidoEm = relogio.instant();
        c.senha = String.format("A-%03d", ++senhasFluxo);
        evento(c, "PACIENTE", "Chegada confirmada. Passagem registrada, senha " + c.senha + ".");
        return c;
    }

    public synchronized CheckIn pedirAjuda(String id, String motivo) {
        CheckIn c = buscar(id);
        if (c.status == CONCLUIDO) throw new RegraException(409, "Este check-in já foi concluído.");
        if (c.status == APOIO_PRESENCIAL) return c;
        c.ajudaPedidaPeloPaciente = true;
        c.status = APOIO_PRESENCIAL;
        c.senha = String.format("P-%03d", ++senhasApoio);
        desfecho(c, Desfecho.APOIO_PRESENCIAL_A_PEDIDO);
        String texto = motivo == null || motivo.isBlank() ? "sem motivo informado" : motivo.trim();
        c.motivos.add("Paciente pediu ajuda de uma pessoa: " + texto + ".");
        evento(c, "PACIENTE", "Pediu apoio presencial (" + texto + "). Senha de apoio " + c.senha + ".");
        return c;
    }

    // ---------- Ações do atendimento (sempre com nome e observação) ----------

    public synchronized CheckIn resolverRemoto(String id, String atendente, String observacao) {
        CheckIn c = buscar(id);
        exigir(c, ATENDIMENTO_REMOTO);
        exigirRegistro(atendente, observacao);
        c.status = PRONTO_PARA_CHEGADA;
        c.atendidoPor = atendente.trim();
        evento(c, "ATENDENTE " + c.atendidoPor, "Pendência resolvida e check-in liberado. Observação: " + observacao.trim());
        return c;
    }

    public synchronized CheckIn encaminharPresencial(String id, String atendente, String observacao) {
        CheckIn c = buscar(id);
        exigir(c, ATENDIMENTO_REMOTO);
        exigirRegistro(atendente, observacao);
        c.status = APOIO_PRESENCIAL;
        c.atendidoPor = atendente.trim();
        c.senha = String.format("P-%03d", ++senhasApoio);
        evento(c, "ATENDENTE " + c.atendidoPor, "Encaminhado ao apoio presencial, senha " + c.senha + ". Observação: " + observacao.trim());
        return c;
    }

    public synchronized CheckIn concluirPresencial(String id, String atendente, String observacao) {
        CheckIn c = buscar(id);
        exigir(c, APOIO_PRESENCIAL);
        exigirRegistro(atendente, observacao);
        c.status = CONCLUIDO;
        c.concluidoEm = relogio.instant();
        c.atendidoPor = atendente.trim();
        evento(c, "ATENDENTE " + c.atendidoPor, "Atendimento presencial concluído e passagem registrada. Observação: " + observacao.trim());
        return c;
    }

    // ---------- Consultas ----------

    public synchronized CheckIn buscar(String id) {
        CheckIn c = checkIns.get(id);
        if (c == null) throw new RegraException(404, "Check-in não encontrado.");
        return c;
    }

    public synchronized List<CheckIn> listar() {
        List<CheckIn> l = new ArrayList<>(checkIns.values());
        Collections.reverse(l); // mais recentes primeiro
        return l;
    }

    public synchronized void reiniciar() {
        checkIns.clear();
        contas.reiniciarAgenda();
        senhasFluxo = 0;
        senhasApoio = 0;
    }

    /** Métricas calculadas só com os check-ins desta execução. Nada aqui é estimado ou fixo. */
    public synchronized Map<String, Object> metricas() {
        int total = checkIns.size(), emAndamento = 0, auto = 0, remoto = 0, aPedido = 0, passagens = 0;
        int avaliados = 0, corretos = 0, semGabarito = 0, corrigidos = 0, reenvios = 0;
        List<Double> tempos = new ArrayList<>(), temposAuto = new ArrayList<>(), temposEscalados = new ArrayList<>();

        for (CheckIn c : checkIns.values()) {
            if (c.status == CONCLUIDO) passagens++;
            if (c.corrigidoPeloPaciente) corrigidos++;
            reenvios += c.reenvios;
            if (c.desfechoInicial == null) { emAndamento++; continue; }
            Double t = c.tempoAteDesfechoSeg();
            tempos.add(t);
            switch (c.desfechoInicial) {
                case CONCLUIDO_AUTOMATICO -> { auto++; temposAuto.add(t); }
                case ESCALADO_REMOTO -> { remoto++; temposEscalados.add(t); }
                case APOIO_PRESENCIAL_A_PEDIDO -> aPedido++;
            }
            Boolean correta = c.decisaoCorreta();
            if (correta != null) {
                avaliados++;
                if (correta) corretos++;
            } else if (c.desfechoInicial != Desfecho.APOIO_PRESENCIAL_A_PEDIDO) {
                semGabarito++;
            }
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("emAndamento", emAndamento);
        m.put("concluidosAutomaticamente", auto);
        m.put("escaladosRemoto", remoto);
        m.put("apoioPresencialAPedido", aPedido);
        m.put("passagensRegistradas", passagens);
        m.put("tempoMedioAteDesfechoSeg", media(tempos));
        m.put("tempoMedianoAteDesfechoSeg", mediana(tempos));
        m.put("tempoMedioConcluidoAutomaticoSeg", media(temposAuto));
        m.put("tempoMedioAteEscalarSeg", media(temposEscalados));
        m.put("decisoesAvaliadas", avaliados);
        m.put("decisoesCorretas", corretos);
        m.put("proporcaoDecisoesCorretas", avaliados == 0 ? null : Math.round(1000.0 * corretos / avaliados) / 10.0);
        m.put("semGabarito", semGabarito);
        m.put("reenvios", reenvios);
        m.put("corrigidosPeloPaciente", corrigidos);
        m.put("comoLer", "Tempo medido do início do check-in até o primeiro desfecho (pré-check-in concluído ou encaminhamento). "
                + "Decisão correta = desfecho do sistema igual ao gabarito do kit fictício. "
                + "Pedidos de ajuda do paciente e arquivos sem gabarito não entram na proporção. "
                + "Um caso encaminhado que o paciente resolveu reenviando o documento continua contado como encaminhado. "
                + "Valores de cenários sintéticos desta execução; não representam operação real.");
        return m;
    }

    // ---------- Apoio ----------

    private void desfecho(CheckIn c, Desfecho d) {
        if (c.desfechoInicial == null) {
            c.desfechoInicial = d;
            c.desfechoEm = relogio.instant();
        }
    }

    private void evento(CheckIn c, String ator, String descricao) {
        c.eventos.add(new Evento(relogio.instant(), ator, descricao));
    }

    private static void exigir(CheckIn c, StatusCheckIn esperado) {
        if (c.status != esperado) {
            throw new RegraException(409, "Ação não permitida no estado atual do check-in (" + c.status + ").");
        }
    }

    private static void exigirRegistro(String atendente, String observacao) {
        if (atendente == null || atendente.isBlank() || observacao == null || observacao.isBlank()) {
            throw new RegraException(400, "Informe o nome de quem atendeu e uma observação: toda exceção fica registrada.");
        }
    }

    private static Double media(List<Double> v) {
        if (v.isEmpty()) return null;
        double soma = 0;
        for (double x : v) soma += x;
        return Math.round(10.0 * soma / v.size()) / 10.0;
    }

    private static Double mediana(List<Double> v) {
        if (v.isEmpty()) return null;
        List<Double> o = new ArrayList<>(v);
        Collections.sort(o);
        int n = o.size();
        double med = n % 2 == 1 ? o.get(n / 2) : (o.get(n / 2 - 1) + o.get(n / 2)) / 2;
        return Math.round(10.0 * med) / 10.0;
    }
}
