package br.com.recepcaosemfila.dados;

import br.com.recepcaosemfila.dominio.Agendamento;
import br.com.recepcaosemfila.dominio.Paciente;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Contas do portal do paciente. As da demo são uma por paciente da base sintética, com senha única de
 * demonstração e o kit de documentos fictícios do seu cenário. As criadas no portal (cadastro simulado) têm
 * senha própria, guardada só como hash, e somem ao zerar a demo. Não há sessão nem autenticação real.
 */
public final class ContasSinteticas {
    public static final String FONTE = "Conta do paciente (login simulado)";
    public static final String SENHA_DEMO = "demo";

    public record Conta(String id, String email, Paciente paciente, String kitId, String senhaHash) {
        public String primeiroNome() { return paciente.nome().split(" ")[0]; }

        /** Conta do roteiro da demo (senha única); as criadas no portal têm senha própria. */
        public boolean demo() { return senhaHash == null; }
    }

    private final BaseSintetica base;
    private final Map<String, Conta> porId = new LinkedHashMap<>();
    private int proxima = 6;

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
        porId.put(id, new Conta(id, email, p, kitId, null));
    }

    public synchronized Optional<Conta> porId(String id) { return Optional.ofNullable(id == null ? null : porId.get(id)); }

    public synchronized Optional<Conta> porEmail(String email) {
        if (email == null) return Optional.empty();
        String e = email.trim().toLowerCase(Locale.ROOT);
        return porId.values().stream().filter(c -> c.email().equals(e)).findFirst();
    }

    public synchronized List<Conta> todas() { return new ArrayList<>(porId.values()); }

    public boolean senhaConfere(Conta c, String senha) {
        if (senha == null) return false;
        return c.demo() ? SENHA_DEMO.equals(senha) : c.senhaHash().equals(hash(senha));
    }

    public Optional<Paciente> pacientePorCpf(String cpf) { return base.pacientePorCpf(cpf); }

    /** Cadastro simulado: o paciente entra na base sintética e ganha uma conta. Quem chama valida os dados. */
    public synchronized Conta criar(String email, String senha, Paciente paciente, String kitId) {
        String id = "C" + proxima++;
        Conta c = new Conta(id, email.trim().toLowerCase(Locale.ROOT), paciente, kitId, hash(senha));
        porId.put(id, c);
        return c;
    }

    public Paciente novoPaciente(String nome, String cpf, LocalDate nascimento, String convenio,
                                 String numeroCarteirinha, LocalDate validade, String servico) {
        return base.cadastrar(nome, cpf, nascimento, convenio, numeroCarteirinha, validade, servico);
    }

    public synchronized long criadasNoPortal() { return porId.values().stream().filter(c -> !c.demo()).count(); }

    /** Apaga as contas criadas no portal e os cadastros delas (usado ao zerar a demo). */
    public synchronized void reiniciarCadastros() {
        porId.values().removeIf(c -> !c.demo());
        base.reiniciarCadastros();
    }

    /** A senha da conta criada no portal não fica guardada em texto. */
    private static String hash(String senha) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(senha.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

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
