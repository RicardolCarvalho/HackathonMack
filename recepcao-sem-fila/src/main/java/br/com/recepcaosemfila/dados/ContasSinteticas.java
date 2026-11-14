package br.com.recepcaosemfila.dados;

import br.com.recepcaosemfila.dominio.Agendamento;
import br.com.recepcaosemfila.dominio.Paciente;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Contas fictícias do portal do paciente, uma por paciente da base sintética.
 * O login é simulado: senha única de demonstração, sem sessão nem autenticação real.
 * Cada conta aponta para o kit de documentos fictícios do seu cenário na demo.
 */
public final class ContasSinteticas {
    public static final String FONTE = "Conta do paciente (login simulado)";
    public static final String SENHA_DEMO = "demo";

    public record Conta(String id, String email, Paciente paciente, String kitId) {
        public String primeiroNome() { return paciente.nome().split(" ")[0]; }
    }

    private final BaseSintetica base;
    private final Map<String, Conta> porId = new LinkedHashMap<>();

    public ContasSinteticas(BaseSintetica base) {
        this.base = base;
        conta("C1", "ana.beatriz@exemplo.test", "111.222.333-00", "ana");
        conta("C2", "carlos.eduardo@exemplo.test", "222.333.444-00", "carlos");
        conta("C3", "marina.castro@exemplo.test", "333.444.555-00", "marina");
        conta("C4", "joao.pedro@exemplo.test", "444.555.666-00", "joao");
        conta("C5", "tereza.cristina@exemplo.test", "555.666.777-00", "tereza");
    }

    private void conta(String id, String email, String cpf, String kitId) {
        Paciente p = base.pacientePorCpf(cpf).orElseThrow();
        porId.put(id, new Conta(id, email, p, kitId));
    }

    public Optional<Conta> porId(String id) { return Optional.ofNullable(id == null ? null : porId.get(id)); }

    public Optional<Conta> porEmail(String email) {
        if (email == null) return Optional.empty();
        String e = email.trim().toLowerCase(Locale.ROOT);
        return porId.values().stream().filter(c -> c.email().equals(e)).findFirst();
    }

    public List<Conta> todas() { return new ArrayList<>(porId.values()); }

    /** A visita marcada pela conta, de hoje em diante (vazio enquanto o paciente não escolhe dia e horário). */
    public Optional<Agendamento> visita(Conta c, LocalDate hoje) {
        return base.proximaVisita(c.paciente().id(), hoje);
    }

    public Optional<BaseSintetica.Pedido> pedido(Conta c) {
        return base.pedido(c.paciente().id());
    }

    public List<BaseSintetica.Horario> horarios(Conta c, LocalDate dia, LocalDate hoje, LocalTime agora) {
        return base.horarios(c.paciente().id(), dia, hoje, agora);
    }

    public Agendamento agendar(Conta c, LocalDate dia, String horario) {
        return base.agendar(c.paciente().id(), dia, horario);
    }

    public void reiniciarAgenda() { base.reiniciarAgenda(); }
}
