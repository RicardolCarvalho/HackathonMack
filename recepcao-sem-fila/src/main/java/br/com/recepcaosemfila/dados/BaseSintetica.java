package br.com.recepcaosemfila.dados;

import br.com.recepcaosemfila.dominio.Agendamento;
import br.com.recepcaosemfila.dominio.Guia;
import br.com.recepcaosemfila.dominio.Paciente;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Base 100% sintética, em memória. Pessoas, convênios, carteirinhas e guias são inventados.
 * Os CPFs têm dígito verificador inválido de propósito, para não coincidirem com um CPF real.
 * As datas são relativas ao dia da execução, para a demo funcionar em qualquer data.
 */
public final class BaseSintetica {
    public record Hospital(String id, String nome, String endereco) {}
    public static final List<Hospital> HOSPITAIS = List.of(
            new Hospital("alberto", "Hospital Alberto Enstale", "Avenida das Palmeiras, 120 · Jardim Aurora"),
            new Hospital("sao-lucas", "Hospital São Lucas", "Rua das Acácias, 85 · Vila Serena"),
            new Hospital("12-maio", "Hospital 12 de Maio", "Avenida do Horizonte, 450 · Parque das Flores"));

    public static Hospital hospital(String id) {
        return HOSPITAIS.stream().filter(h -> h.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Escolha um hospital válido."));
    }

    public static final String FONTE_CADASTRO = "Base cadastral sintética";
    public static final String FONTE_AGENDA = "Agenda sintética da unidade";
    public static final String FONTE_GUIAS = "Base sintética de guias do convênio";

    private final Map<String, Paciente> pacientesPorCpf = new LinkedHashMap<>();
    private final Map<String, Pedido> pedidosPorPaciente = new LinkedHashMap<>();
    private final Map<String, Agendamento> agendamentosPorPaciente = new LinkedHashMap<>(); // começa vazio
    private final Map<String, Agendamento> visitasPorTipo = new LinkedHashMap<>();
    private final Map<String, Agendamento> examesPorPaciente = new LinkedHashMap<>();
    private final Map<String, Guia> guiasPorAgendamento = new LinkedHashMap<>();

    /** Grade da unidade fictícia, que funciona 24 horas: de 00:00 às 23:30, a cada 30 minutos. */
    public static final List<String> GRADE;
    static {
        List<String> g = new ArrayList<>();
        for (int i = 0; i < 48; i++) g.add(LocalTime.MIDNIGHT.plusMinutes(30L * i).toString());
        GRADE = List.copyOf(g);
    }

    /** Quantos dias a agenda abre para o paciente escolher: hoje e os 6 seguintes. */
    public static final int DIAS_DA_AGENDA = 7;

    /** Convênios fictícios da base; um cadastro novo escolhe um deles. */
    public static final List<String> CONVENIOS = List.of("Convênio Aurora (fictício)", "Plano Horizonte (fictício)");

    /** Serviços que um cadastro novo pode agendar, com o local de cada um (mostrado só depois do check-in). */
    public static final List<String> SERVICOS = List.of("Consulta eletiva", "Exame laboratorial", "Exame de imagem");
    private static final Map<String, String> LOCAL_DO_SERVICO = Map.of(
            "Consulta eletiva", "Triagem, sala 2",
            "Exame laboratorial", "Coleta, guichê 4",
            "Exame de imagem", "Imagem, recepção B");

    private final Set<String> cadastradosNoPortal = new HashSet<>();
    private int proximoCadastro = 6;

    /**
     * O que o paciente tem para agendar: o pedido que gerou a guia. O local (próxima etapa) só aparece para o
     * paciente depois do check-in. O id é o do agendamento que a guia cita, criado quando o paciente escolhe o horário.
     */
    public record Pedido(String agendamentoId, String servico, String proximaEtapa) {}

    /** Um horário da grade, na visão de um paciente: livre para ele, ocupado ou o que ele já tem. */
    public record Horario(String horario, boolean disponivel, boolean atual) {}

    public BaseSintetica(LocalDate hoje) {
        paciente(new Paciente("P1", "Ana Beatriz Lima Souza", "111.222.333-00", LocalDate.of(1988, 3, 14),
                "Convênio Aurora (fictício)", "AUR-0001-2026", hoje.plusMonths(10)));
        paciente(new Paciente("P2", "Carlos Eduardo Pereira Nunes", "222.333.444-00", LocalDate.of(1975, 11, 2),
                "Plano Horizonte (fictício)", "HOR-0002-2026", hoje.plusMonths(6)));
        paciente(new Paciente("P3", "Marina Alves Castro", "333.444.555-00", LocalDate.of(1994, 7, 29),
                "Convênio Aurora (fictício)", "AUR-0003-2025", hoje.minusDays(30)));
        paciente(new Paciente("P4", "João Pedro Martins Rocha", "444.555.666-00", LocalDate.of(2001, 1, 9),
                "Plano Horizonte (fictício)", "HOR-0004-2026", hoje.plusMonths(8)));
        paciente(new Paciente("P5", "Tereza Cristina Barbosa", "555.666.777-00", LocalDate.of(1949, 5, 21),
                "Convênio Aurora (fictício)", "AUR-0005-2026", hoje.plusMonths(3)));

        // Ninguém começa com horário marcado: cada paciente tem um pedido e escolhe o dia e o horário no portal.
        pedidosPorPaciente.put("P1", new Pedido("A1", "Consulta eletiva", "Triagem, sala 2"));
        pedidosPorPaciente.put("P2", new Pedido("A2", "Exame laboratorial", "Coleta, guichê 4"));
        pedidosPorPaciente.put("P3", new Pedido("A3", "Consulta eletiva", "Triagem, sala 2"));
        pedidosPorPaciente.put("P4", new Pedido("A4", "Exame de imagem", "Imagem, recepção B"));
        pedidosPorPaciente.put("P5", new Pedido("A5", "Consulta eletiva", "Triagem, sala 1"));

        guia(new Guia("G-2026-0001", "A1", "AUR-0001-2026", "Consulta eletiva", "AUTORIZADA", hoje.plusDays(20)));
        guia(new Guia("G-2026-0002", "A2", "HOR-0002-2026", "Exame laboratorial", "AUTORIZADA", hoje.plusDays(15)));
        guia(new Guia("G-2026-0003", "A3", "AUR-0003-2025", "Consulta eletiva", "AUTORIZADA", hoje.plusDays(10)));
        guia(new Guia("G-2026-0004", "A4", "HOR-0004-2026", "Exame de imagem", "PENDENTE", hoje.plusDays(25)));
        guia(new Guia("G-2026-0005", "A5", "AUR-0005-2026", "Consulta eletiva", "AUTORIZADA", hoje.plusDays(12)));
        int i = 0;
        for (Paciente p : pacientesPorCpf.values()) {
            Pedido pedido = pedidosPorPaciente.get(p.id());
            String servico = pedido.servico().contains("imagem") ? "Exame de imagem" : "Exame laboratorial";
            String id = "EX-" + p.id();
            Agendamento exame = new Agendamento(id, p.id(), hoje.plusDays(1), String.format("%02d:00", 9 + i),
                    servico, LOCAL_DO_SERVICO.get(servico), HOSPITAIS.get(i % HOSPITAIS.size()).id(), "EXAME_AGENDADO");
            examesPorPaciente.put(p.id(), exame);
            Guia original = guiasPorAgendamento.get(pedido.agendamentoId());
            guia(new Guia("GE-" + p.id(), id, p.numeroCarteirinha(), servico, original.status(), original.validade()));
            i++;
        }
    }

    /** Exames já marcados pela unidade fictícia; cadastrar uma conta não cria um exame. */
    public List<Agendamento> exames(String pacienteId, LocalDate hoje) {
        Agendamento exame = examesPorPaciente.get(pacienteId);
        return exame != null && !exame.data().isBefore(hoje) ? List.of(exame) : List.of();
    }

    public Agendamento selecionarExame(String pacienteId, String exameId, LocalDate hoje) {
        Agendamento exame = exames(pacienteId, hoje).stream().filter(e -> e.id().equals(exameId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Exame agendado não encontrado para esta conta."));
        registrarVisita(pacienteId, exame);
        return exame;
    }

    public Agendamento preverChegada(String pacienteId, LocalDate dia, String horario, String hospitalId) {
        hospital(hospitalId);
        Agendamento visita = new Agendamento("PS-" + pacienteId, pacienteId, dia, horario,
                "Pronto-socorro", "Triagem presencial", hospitalId, "PRONTO_SOCORRO");
        registrarVisita(pacienteId, visita);
        return visita;
    }

    private void registrarVisita(String pacienteId, Agendamento visita) {
        agendamentosPorPaciente.put(pacienteId, visita); // última visita selecionada, para clientes antigos
        visitasPorTipo.put(pacienteId + ":" + visita.tipo(), visita);
    }

    public Optional<Agendamento> visitaDoTipo(String pacienteId, String tipo, LocalDate hoje) {
        Agendamento visita = visitasPorTipo.get(pacienteId + ":" + tipo);
        return Optional.ofNullable(visita != null && !visita.data().isBefore(hoje) ? visita : null);
    }

    public List<Agendamento> visitas(String pacienteId, LocalDate hoje) {
        return visitasPorTipo.values().stream().filter(a -> a.pacienteId().equals(pacienteId) && !a.data().isBefore(hoje)).toList();
    }

    private void paciente(Paciente p) { pacientesPorCpf.put(digitos(p.cpf()), p); }
    private void guia(Guia g) { guiasPorAgendamento.put(g.agendamentoId(), g); }

    public Optional<Paciente> pacientePorCpf(String cpf) {
        return Optional.ofNullable(cpf == null ? null : pacientesPorCpf.get(digitos(cpf)));
    }

    public Optional<Pedido> pedido(String pacienteId) {
        return Optional.ofNullable(pedidosPorPaciente.get(pacienteId));
    }

    /** A visita marcada do paciente, de hoje em diante (vazio se ele ainda não escolheu dia e horário). */
    public Optional<Agendamento> proximaVisita(String pacienteId, LocalDate hoje) {
        Agendamento a = agendamentosPorPaciente.get(pacienteId);
        return Optional.ofNullable(a != null && !a.data().isBefore(hoje) ? a : null);
    }

    public Optional<Guia> guiaDoAgendamento(String agendamentoId) {
        return Optional.ofNullable(guiasPorAgendamento.get(agendamentoId));
    }

    /** Dias em que a agenda está aberta para o paciente escolher. */
    public static List<LocalDate> diasDaAgenda(LocalDate hoje) {
        List<LocalDate> dias = new ArrayList<>();
        for (int i = 0; i < DIAS_DA_AGENDA; i++) dias.add(hoje.plusDays(i));
        return dias;
    }

    /**
     * Horários de um dia na agenda do serviço do pedido do paciente. Em hoje, só os que ainda vão acontecer.
     * Ocupados: os de outros pacientes da base no mesmo serviço e uma ocupação fictícia fixa por dia e serviço.
     */
    public List<Horario> horarios(String pacienteId, LocalDate dia, LocalDate hoje, LocalTime agora) {
        return horarios(pacienteId, dia, hoje, agora, "alberto");
    }

    public List<Horario> horarios(String pacienteId, LocalDate dia, LocalDate hoje, LocalTime agora, String hospitalId) {
        hospital(hospitalId);
        Pedido pedido = pedidosPorPaciente.get(pacienteId);
        if (pedido == null || dia.isBefore(hoje)) return List.of();
        Agendamento proprio = agendamentosPorPaciente.get(pacienteId);
        Set<String> ocupados = new HashSet<>();
        for (Agendamento a : visitasPorTipo.values()) {
            if (!"PRONTO_SOCORRO".equals(a.tipo()) && !a.pacienteId().equals(pacienteId) && a.servico().equals(pedido.servico()) && a.data().equals(dia) && a.hospitalId().equals(hospitalId)) {
                ocupados.add(a.horario());
            }
        }
        List<Horario> l = new ArrayList<>();
        for (int i = 0; i < GRADE.size(); i++) {
            String h = GRADE.get(i);
            if (dia.equals(hoje) && !LocalTime.parse(h).isAfter(agora)) continue;
            boolean atual = proprio != null && proprio.hospitalId().equals(hospitalId) && proprio.data().equals(dia) && h.equals(proprio.horario());
            boolean livre = !atual && !ocupados.contains(h) && !ocupadoFicticio(pedido.servico(), dia, i);
            l.add(new Horario(h, livre, atual));
        }
        return l;
    }

    /** Ocupação fictícia, sempre a mesma para o mesmo dia e serviço (cerca de um em cada quatro horários). */
    private static boolean ocupadoFicticio(String servico, LocalDate dia, int indice) {
        return Math.floorMod(indice * 7 + dia.getDayOfYear() * 3 + servico.length(), 4) == 0;
    }

    /** Marca (ou troca) a visita do paciente. Quem chama valida antes, com {@link #horarios}. */
    public Agendamento agendar(String pacienteId, LocalDate dia, String horario) {
        return agendar(pacienteId, dia, horario, "alberto");
    }

    public Agendamento agendar(String pacienteId, LocalDate dia, String horario, String hospitalId) {
        hospital(hospitalId);
        Pedido p = pedidosPorPaciente.get(pacienteId);
        Agendamento novo = new Agendamento(p.agendamentoId(), pacienteId, dia, horario, p.servico(), p.proximaEtapa(), hospitalId);
        registrarVisita(pacienteId, novo);
        return novo;
    }

    /** Apaga os horários marcados (usado ao zerar a demo): todos voltam a não ter visita marcada. */
    public void reiniciarAgenda() {
        agendamentosPorPaciente.clear();
        visitasPorTipo.clear();
    }

    /**
     * Cadastro feito pelo próprio paciente no portal (simulado, só em memória). Entra com um pedido do serviço
     * escolhido e sem guia: a guia vem do convênio, e a conferência aponta a falta para uma pessoa resolver.
     * Quem chama valida os dados antes.
     */
    public Paciente cadastrar(String nome, String cpf, LocalDate nascimento, String convenio,
                              String numeroCarteirinha, LocalDate validadeCarteirinha, String servico) {
        int n = proximoCadastro++;
        Paciente p = new Paciente("P" + n, nome, cpf, nascimento, convenio, numeroCarteirinha, validadeCarteirinha);
        paciente(p);
        pedidosPorPaciente.put(p.id(), new Pedido("A" + n, servico, LOCAL_DO_SERVICO.get(servico)));
        cadastradosNoPortal.add(p.id());
        return p;
    }

    /** Apaga os cadastros feitos no portal (usado ao zerar a demo). */
    public void reiniciarCadastros() {
        pacientesPorCpf.values().removeIf(p -> cadastradosNoPortal.contains(p.id()));
        for (String id : cadastradosNoPortal) {
            pedidosPorPaciente.remove(id);
            agendamentosPorPaciente.remove(id);
        }
        visitasPorTipo.values().removeIf(a -> cadastradosNoPortal.contains(a.pacienteId()));
        cadastradosNoPortal.clear();
    }

    /**
     * CPF com dígitos verificadores válidos, como um CPF real. A base só aceita CPF fictício (dígito inválido),
     * para um cadastro da demo nunca coincidir com o de uma pessoa.
     */
    public static boolean cpfComDigitoValido(String cpf) {
        String d = digitos(cpf == null ? "" : cpf);
        if (d.length() != 11) return false;
        for (int j = 9; j <= 10; j++) {
            int soma = 0;
            for (int i = 0; i < j; i++) soma += (d.charAt(i) - '0') * (j + 1 - i);
            if ((soma * 10) % 11 % 10 != d.charAt(j) - '0') return false;
        }
        return true;
    }

    public Iterable<Paciente> pacientes() { return pacientesPorCpf.values(); }

    public static String digitos(String s) { return s.replaceAll("\\D", ""); }
}
