package br.com.recepcaosemfila.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Estado de um check-in. Só o CheckInServico altera estes campos. */
public class CheckIn {
    /** Quantas vezes o paciente pode reenviar documento num caso com pendência antes de ficar só com o atendimento. */
    public static final int LIMITE_REENVIOS = 2;

    public final String id;
    public final Instant criadoEm;
    public StatusCheckIn status = StatusCheckIn.AGUARDANDO_DOCUMENTOS;

    public String contaId;             // conta do portal que iniciou o check-in (login simulado), se houver
    public String contaEmail;
    public Paciente titular;           // titular da conta: o cadastro contra o qual o documento é conferido

    public String kitId;               // kit fictício usado (se houver)
    public String esperado;            // gabarito do kit: CONCLUIR ou ESCALAR
    public Desfecho desfechoInicial;
    public Instant desfechoEm;
    public Instant concluidoEm;

    public String nomeLido;
    public String cpfLido;
    public Paciente paciente;
    public Agendamento agendamento;
    public Guia guia;

    public List<Verificacao> verificacoes = new ArrayList<>();
    public final List<String> motivos = new ArrayList<>();
    public final List<Evento> eventos = new ArrayList<>();

    public DocumentoLido identidadeLida;     // últimas leituras conferidas (não vão para a API)
    public DocumentoLido carteirinhaLida;
    public int reenvios;
    public boolean corrigidoPeloPaciente;  // uma pendência de documento foi resolvida com reenvio do próprio paciente

    public boolean ajudaPedidaPeloPaciente;
    public String atendidoPor;
    public String senha;

    public CheckIn(String id, Instant criadoEm) {
        this.id = id;
        this.criadoEm = criadoEm;
    }

    public Double tempoAteDesfechoSeg() { return segundos(desfechoEm); }

    public Double tempoTotalSeg() { return segundos(concluidoEm); }

    private Double segundos(Instant fim) {
        if (fim == null) return null;
        return Math.round(Duration.between(criadoEm, fim).toMillis() / 100.0) / 10.0;
    }

    /** Avaliação da decisão do sistema contra o gabarito do kit. null = não avaliável. */
    public Boolean decisaoCorreta() {
        if (esperado == null || desfechoInicial == null) return null;
        if (desfechoInicial == Desfecho.APOIO_PRESENCIAL_A_PEDIDO) return null; // escolha do paciente, não do sistema
        boolean concluiu = desfechoInicial == Desfecho.CONCLUIDO_AUTOMATICO;
        return concluiu == "CONCLUIR".equals(esperado);
    }

    /** Documentos cuja pendência o próprio paciente pode resolver reenviando; vazio quando não cabe reenvio. */
    public List<TipoDocumento> documentosParaReenviar() {
        if (status != StatusCheckIn.ATENDIMENTO_REMOTO || reenvios >= LIMITE_REENVIOS) return List.of();
        List<TipoDocumento> l = new ArrayList<>();
        for (Verificacao v : verificacoes) {
            if (!v.falhou()) continue;
            TipoDocumento t = TipoDocumento.daVerificacao(v.codigo());
            if (t != null && !l.contains(t)) l.add(t);
        }
        l.sort(Comparator.naturalOrder());
        return l;
    }

    public Map<String, Object> paraMapa(boolean visaoAtendente) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("status", status);
        m.put("criadoEm", criadoEm);
        m.put("desfechoInicial", desfechoInicial);
        m.put("tempoAteDesfechoSeg", tempoAteDesfechoSeg());
        m.put("tempoTotalSeg", tempoTotalSeg());
        m.put("motivos", motivos);
        m.put("ajudaPedidaPeloPaciente", ajudaPedidaPeloPaciente);
        m.put("atendidoPor", atendidoPor);
        m.put("senha", senha);
        m.put("nomeLido", nomeLido);
        m.put("cpfLido", mascararCpf(cpfLido));

        Map<String, Object> reenvio = new LinkedHashMap<>();
        reenvio.put("documentos", documentosParaReenviar());
        reenvio.put("feitos", reenvios);
        reenvio.put("limite", LIMITE_REENVIOS);
        reenvio.put("corrigido", corrigidoPeloPaciente);
        m.put("reenvio", reenvio);

        List<Map<String, Object>> vs = new ArrayList<>();
        for (Verificacao v : verificacoes) vs.add(v.paraMapa(visaoAtendente));
        m.put("verificacoes", vs);

        // Dados que vêm do cadastro só vão para o paciente depois que a conferência passou
        // (ou depois que uma pessoa do atendimento liberou).
        boolean liberado = status == StatusCheckIn.AGUARDANDO_CONFIRMACAO_GUIA
                || status == StatusCheckIn.PRONTO_PARA_CHEGADA
                || status == StatusCheckIn.CONCLUIDO;
        if (visaoAtendente || liberado) {
            m.put("agendamento", agendamento == null ? null : mapaAgendamento());
            m.put("guia", guia == null ? null : mapaGuia());
        }
        if (visaoAtendente) {
            m.put("conta", contaEmail);
            m.put("kitId", kitId);
            m.put("esperado", esperado);
            m.put("decisaoCorreta", decisaoCorreta());
            m.put("pacienteCadastro", paciente == null ? null : paciente.nome());
            List<Map<String, Object>> es = new ArrayList<>();
            for (Evento e : eventos) es.add(e.paraMapa());
            m.put("eventos", es);
        }
        return m;
    }

    private Map<String, Object> mapaAgendamento() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("data", agendamento.data());
        a.put("horario", agendamento.horario());
        a.put("servico", agendamento.servico());
        a.put("proximaEtapa", agendamento.proximaEtapa());
        return a;
    }

    private Map<String, Object> mapaGuia() {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("numero", guia.numero());
        g.put("procedimento", guia.procedimento());
        g.put("status", guia.status());
        g.put("validade", guia.validade());
        g.put("convenio", paciente == null ? null : paciente.convenio());
        return g;
    }

    static String mascararCpf(String cpf) {
        if (cpf == null) return null;
        String d = cpf.replaceAll("\\D", "");
        if (d.length() != 11) return "***";
        return "***.***." + d.substring(6, 9) + "-" + d.substring(9);
    }
}
