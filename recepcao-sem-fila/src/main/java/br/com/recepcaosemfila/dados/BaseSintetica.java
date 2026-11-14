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
    public static final String FONTE_CADASTRO = "Base cadastral sintética";
    public static final String FONTE_AGENDA = "Agenda sintética da unidade";
    public static final String FONTE_GUIAS = "Base sintética de guias do convênio";

    private final Map<String, Paciente> pacientesPorCpf = new LinkedHashMap<>();
    private final Map<String, Agendamento> agendamentosPorPaciente = new LinkedHashMap<>();
    private final Map<String, Guia> guiasPorAgendamento = new LinkedHashMap<>();
    private final Map<String, Agendamento> agendaOriginal = new LinkedHashMap<>();

    /** Grade da unidade fictícia: de 07:00 às 19:30, a cada 30 minutos. */
    public static final List<String> GRADE;
    static {
        List<String> g = new ArrayList<>();
        for (LocalTime t = LocalTime.of(7, 0); !t.isAfter(LocalTime.of(19, 30)); t = t.plusMinutes(30)) g.add(t.toString());
        GRADE = List.copyOf(g);
    }

    /** Horários já ocupados por outros pacientes fictícios (fora da base), por serviço. */
    private static final Map<String, Set<String>> OCUPADOS_FICTICIOS = Map.of(
            "Consulta agendada", Set.of("08:00", "09:00", "09:30", "12:00", "13:30", "16:00", "17:30", "18:30"),
            "Exame agendado", Set.of("07:30", "08:30", "10:00", "13:00", "15:00", "16:30", "18:00"));

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

        agendamento(new Agendamento("A1", "P1", hoje, "10:30", "Consulta agendada", "Triagem, sala 2"));
        agendamento(new Agendamento("A2", "P2", hoje, "11:00", "Exame agendado", "Coleta, guichê 4"));
        agendamento(new Agendamento("A3", "P3", hoje, "11:30", "Consulta agendada", "Triagem, sala 2"));
        agendamento(new Agendamento("A4", "P4", hoje, "14:00", "Exame agendado", "Imagem, recepção B"));
        agendamento(new Agendamento("A5", "P5", hoje, "15:30", "Consulta agendada", "Triagem, sala 1"));
        agendaOriginal.putAll(agendamentosPorPaciente);

        guia(new Guia("G-2026-0001", "A1", "AUR-0001-2026", "Consulta eletiva", "AUTORIZADA", hoje.plusDays(20)));
        guia(new Guia("G-2026-0002", "A2", "HOR-0002-2026", "Exame laboratorial", "AUTORIZADA", hoje.plusDays(15)));
        guia(new Guia("G-2026-0003", "A3", "AUR-0003-2025", "Consulta eletiva", "AUTORIZADA", hoje.plusDays(10)));
        guia(new Guia("G-2026-0004", "A4", "HOR-0004-2026", "Exame de imagem", "PENDENTE", hoje.plusDays(25)));
        guia(new Guia("G-2026-0005", "A5", "AUR-0005-2026", "Consulta eletiva", "AUTORIZADA", hoje.plusDays(12)));
    }

    private void paciente(Paciente p) { pacientesPorCpf.put(digitos(p.cpf()), p); }
    private void agendamento(Agendamento a) { agendamentosPorPaciente.put(a.pacienteId(), a); }
    private void guia(Guia g) { guiasPorAgendamento.put(g.agendamentoId(), g); }

    public Optional<Paciente> pacientePorCpf(String cpf) {
        return Optional.ofNullable(cpf == null ? null : pacientesPorCpf.get(digitos(cpf)));
    }

    public Optional<Agendamento> agendamentoDoDia(String pacienteId, LocalDate dia) {
        Agendamento a = agendamentosPorPaciente.get(pacienteId);
        return Optional.ofNullable(a != null && a.data().equals(dia) ? a : null);
    }

    public Optional<Guia> guiaDoAgendamento(String agendamentoId) {
        return Optional.ofNullable(guiasPorAgendamento.get(agendamentoId));
    }

    /** Horários de hoje ainda por vir, na agenda do mesmo serviço do agendamento do paciente. */
    public List<Horario> horarios(String pacienteId, LocalDate dia, LocalTime agora) {
        Agendamento proprio = agendamentoDoDia(pacienteId, dia).orElse(null);
        if (proprio == null) return List.of();
        Set<String> ocupados = new HashSet<>(OCUPADOS_FICTICIOS.getOrDefault(proprio.servico(), Set.of()));
        for (Agendamento a : agendamentosPorPaciente.values()) {
            if (!a.id().equals(proprio.id()) && a.servico().equals(proprio.servico()) && a.data().equals(dia)) {
                ocupados.add(a.horario());
            }
        }
        List<Horario> l = new ArrayList<>();
        for (String h : GRADE) {
            if (!LocalTime.parse(h).isAfter(agora)) continue;
            boolean atual = h.equals(proprio.horario());
            l.add(new Horario(h, !atual && !ocupados.contains(h), atual));
        }
        return l;
    }

    /** Troca o horário do agendamento de hoje. Quem chama valida antes, com {@link #horarios}. */
    public Agendamento remarcar(String pacienteId, LocalDate dia, String horario) {
        Agendamento a = agendamentoDoDia(pacienteId, dia).orElseThrow();
        Agendamento novo = new Agendamento(a.id(), a.pacienteId(), a.data(), horario, a.servico(), a.proximaEtapa());
        agendamentosPorPaciente.put(pacienteId, novo);
        return novo;
    }

    /** Volta a agenda para os horários iniciais (usado ao zerar a demo). */
    public void reiniciarAgenda() {
        agendamentosPorPaciente.clear();
        agendamentosPorPaciente.putAll(agendaOriginal);
    }

    public Iterable<Paciente> pacientes() { return pacientesPorCpf.values(); }

    public static String digitos(String s) { return s.replaceAll("\\D", ""); }
}
