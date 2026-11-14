package br.com.recepcaosemfila;

import br.com.recepcaosemfila.dados.BaseSintetica;
import br.com.recepcaosemfila.dados.KitsFicticios;
import br.com.recepcaosemfila.dominio.CheckIn;
import br.com.recepcaosemfila.dominio.Desfecho;
import br.com.recepcaosemfila.dominio.Paciente;
import br.com.recepcaosemfila.dominio.ResultadoVerificacao;
import br.com.recepcaosemfila.dominio.StatusCheckIn;
import br.com.recepcaosemfila.dominio.Verificacao;
import br.com.recepcaosemfila.servico.CheckInServico;
import br.com.recepcaosemfila.servico.Conferencia;
import br.com.recepcaosemfila.servico.LeitorSvgSimulado;
import br.com.recepcaosemfila.servico.RegraException;
import br.com.recepcaosemfila.web.Json;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Testes dos cenários da demo, sem biblioteca externa (roda com "java -cp out br.com.recepcaosemfila.VerificaCenarios").
 * Cobre: sucesso, informação divergente, dado ausente, encaminhamento humano, transições inválidas e métricas.
 */
public final class VerificaCenarios {
    private static int ok = 0;
    private static int falhas = 0;

    private static KitsFicticios kits;
    private static CheckInServico servico;

    public static void main(String[] args) {
        teste("Sucesso: cadastro correto conclui sem intervenção humana", VerificaCenarios::sucesso);
        teste("Divergente: nascimento diferente vai ao atendimento remoto e é resolvido por uma pessoa", VerificaCenarios::divergente);
        teste("Dado ausente: documento ilegível vai ao remoto e segue para apoio presencial", VerificaCenarios::dadoAusente);
        teste("Carteirinha vencida e guia não autorizada são encaminhadas", VerificaCenarios::carteirinhaEGuia);
        teste("Atendimento humano: paciente pede ajuda e recebe senha de apoio", VerificaCenarios::ajudaHumana);
        teste("Arquivo que não é documento fictício SVG é encaminhado, não aprovado", VerificaCenarios::arquivoInvalido);
        teste("Transições inválidas são recusadas", VerificaCenarios::transicoesInvalidas);
        teste("Métricas refletem só o que aconteceu na execução", VerificaCenarios::metricas);
        teste("JSON: escrita e leitura", VerificaCenarios::json);
        teste("CPFs da base sintética têm dígito verificador inválido", VerificaCenarios::cpfsFicticios);

        System.out.println();
        System.out.println(ok + " testes passaram, " + falhas + " falharam.");
        if (falhas > 0) System.exit(1);
    }

    // ---------- Cenários ----------

    private static void sucesso() {
        CheckIn c = enviar("ana");
        igual(StatusCheckIn.AGUARDANDO_CONFIRMACAO_GUIA, c.status);
        verdade(c.verificacoes.stream().noneMatch(Verificacao::falhou));
        igual(ResultadoVerificacao.SIMULADO, verificacao(c, "IDENTIDADE_SIMULADA").resultado());

        servico.confirmarGuia(c.id);
        igual(StatusCheckIn.PRONTO_PARA_CHEGADA, c.status);
        igual(Desfecho.CONCLUIDO_AUTOMATICO, c.desfechoInicial);

        servico.registrarChegada(c.id);
        igual(StatusCheckIn.CONCLUIDO, c.status);
        igual("A-001", c.senha);
        igual(Boolean.TRUE, c.decisaoCorreta());
        verdade(c.tempoAteDesfechoSeg() != null && c.tempoTotalSeg() != null);
        verdade(c.atendidoPor == null);
    }

    private static void divergente() {
        CheckIn c = enviar("carlos");
        igual(StatusCheckIn.ATENDIMENTO_REMOTO, c.status);
        igual(Desfecho.ESCALADO_REMOTO, c.desfechoInicial);
        igual(ResultadoVerificacao.DIVERGENTE, verificacao(c, "NASCIMENTO").resultado());
        igual(ResultadoVerificacao.OK, verificacao(c, "NOME").resultado());
        igual(Boolean.TRUE, c.decisaoCorreta());

        // O paciente não vê o valor do cadastro nem a guia enquanto houver pendência.
        Map<String, Object> visaoPaciente = c.paraMapa(false);
        verdade(!Json.texto(visaoPaciente).contains("valorCadastro"));
        verdade(!visaoPaciente.containsKey("guia"));
        verdade(Json.texto(c.paraMapa(true)).contains("02/11/1975"));

        // Resolver exige nome e observação.
        recusa(400, () -> servico.resolverRemoto(c.id, "Bia", " "));
        servico.resolverRemoto(c.id, "Bia", "Cadastro corrigido após conferência por telefone.");
        igual(StatusCheckIn.PRONTO_PARA_CHEGADA, c.status);
        igual("Bia", c.atendidoPor);
        igual(Desfecho.ESCALADO_REMOTO, c.desfechoInicial); // o desfecho inicial não muda

        servico.registrarChegada(c.id);
        igual(StatusCheckIn.CONCLUIDO, c.status);
    }

    private static void dadoAusente() {
        CheckIn c = enviar("tereza");
        igual(StatusCheckIn.ATENDIMENTO_REMOTO, c.status);
        igual(ResultadoVerificacao.AUSENTE, verificacao(c, "LEITURA_IDENTIDADE").resultado());
        igual(ResultadoVerificacao.NAO_AVALIADO, verificacao(c, "CADASTRO_LOCALIZADO").resultado());
        verdade(c.paciente == null && c.cpfLido == null);

        servico.encaminharPresencial(c.id, "Bia", "Documento ilegível; conferir o original no balcão.");
        igual(StatusCheckIn.APOIO_PRESENCIAL, c.status);
        verdade(c.senha.startsWith("P-"));
        verdade(!c.ajudaPedidaPeloPaciente);

        servico.concluirPresencial(c.id, "Rafa", "Documento original conferido no balcão.");
        igual(StatusCheckIn.CONCLUIDO, c.status);
    }

    private static void carteirinhaEGuia() {
        CheckIn marina = enviar("marina");
        igual(StatusCheckIn.ATENDIMENTO_REMOTO, marina.status);
        igual(ResultadoVerificacao.DIVERGENTE, verificacao(marina, "CARTEIRINHA").resultado());

        CheckIn joao = enviar("joao");
        igual(StatusCheckIn.ATENDIMENTO_REMOTO, joao.status);
        igual(ResultadoVerificacao.DIVERGENTE, verificacao(joao, "GUIA").resultado());
        igual(ResultadoVerificacao.OK, verificacao(joao, "CARTEIRINHA").resultado());
    }

    private static void ajudaHumana() {
        CheckIn c = servico.iniciar();
        servico.pedirAjuda(c.id, "Não consigo enviar a foto");
        igual(StatusCheckIn.APOIO_PRESENCIAL, c.status);
        igual(Desfecho.APOIO_PRESENCIAL_A_PEDIDO, c.desfechoInicial);
        verdade(c.ajudaPedidaPeloPaciente);
        igual("P-001", c.senha);
        igual(null, c.decisaoCorreta()); // escolha do paciente não é avaliada como decisão do sistema

        // Pedir ajuda no meio do fluxo também funciona.
        CheckIn d = enviar("ana");
        servico.pedirAjuda(d.id, null);
        igual(StatusCheckIn.APOIO_PRESENCIAL, d.status);
        servico.concluirPresencial(d.id, "Rafa", "Paciente atendida no balcão.");
        recusa(409, () -> servico.pedirAjuda(d.id, "de novo"));
    }

    private static void arquivoInvalido() {
        CheckIn c = servico.iniciar();
        servico.enviarDocumentos(c.id, null, "isto não é uma imagem", null);
        igual(StatusCheckIn.ATENDIMENTO_REMOTO, c.status);
        igual(null, c.esperado);
        igual(null, c.decisaoCorreta());
    }

    private static void transicoesInvalidas() {
        CheckIn c = servico.iniciar();
        recusa(409, () -> servico.confirmarGuia(c.id));
        recusa(409, () -> servico.registrarChegada(c.id));
        recusa(409, () -> servico.resolverRemoto(c.id, "Bia", "x"));
        recusa(404, () -> servico.buscar("nao-existe"));
        CheckIn d = enviar("ana");
        recusa(409, () -> servico.enviarDocumentos(d.id, "ana", "", ""));
    }

    private static void metricas() {
        Map<String, Object> vazio = servico.metricas();
        igual(0, vazio.get("total"));
        igual(null, vazio.get("proporcaoDecisoesCorretas"));
        igual(null, vazio.get("tempoMedioAteDesfechoSeg"));

        CheckIn a = enviar("ana");
        servico.confirmarGuia(a.id);
        enviar("carlos");
        enviar("tereza");
        CheckIn ajuda = servico.iniciar();
        servico.pedirAjuda(ajuda.id, "prefiro falar com alguém");
        servico.iniciar(); // fica em andamento

        Map<String, Object> m = servico.metricas();
        igual(5, m.get("total"));
        igual(1, m.get("emAndamento"));
        igual(1, m.get("concluidosAutomaticamente"));
        igual(2, m.get("escaladosRemoto"));
        igual(1, m.get("apoioPresencialAPedido"));
        igual(3, m.get("decisoesAvaliadas"));
        igual(3, m.get("decisoesCorretas"));
        igual(100.0, m.get("proporcaoDecisoesCorretas"));
        verdade(m.get("tempoMedioAteDesfechoSeg") != null);
    }

    private static void json() {
        String texto = Json.texto(Map.of("a", List.of(1, "dois \"x\"\n", true), "ç", "ação"));
        Object lido = Json.ler(texto);
        verdade(lido instanceof Map);
        igual("ação", ((Map<?, ?>) lido).get("ç"));
        igual(List.of(1.0, "dois \"x\"\n", true), ((Map<?, ?>) lido).get("a"));
        igual("é", Json.ler("\"\\u00e9\""));
        try {
            Json.ler("{\"a\":");
            throw new AssertionError("deveria recusar JSON incompleto");
        } catch (IllegalArgumentException esperado) {
            // ok
        }
    }

    private static void cpfsFicticios() {
        for (Paciente p : new BaseSintetica(LocalDate.now()).pacientes()) {
            verdade(!cpfValido(p.cpf()));
        }
    }

    // ---------- Apoio ----------

    private static CheckIn enviar(String kitId) {
        KitsFicticios.Kit kit = kits.kit(kitId);
        CheckIn c = servico.iniciar();
        return servico.enviarDocumentos(c.id, kitId, kits.svgIdentidade(kit), kits.svgCarteirinha(kit));
    }

    private static Verificacao verificacao(CheckIn c, String codigo) {
        return c.verificacoes.stream().filter(v -> v.codigo().equals(codigo)).findFirst()
                .orElseThrow(() -> new AssertionError("verificação ausente: " + codigo));
    }

    private static boolean cpfValido(String cpf) {
        String d = cpf.replaceAll("\\D", "");
        for (int n = 9; n <= 10; n++) {
            int soma = 0;
            for (int i = 0; i < n; i++) soma += (d.charAt(i) - '0') * (n + 1 - i);
            int dv = (soma * 10) % 11 % 10;
            if (dv != d.charAt(n) - '0') return false;
        }
        return true;
    }

    private static void teste(String nome, Runnable corpo) {
        Clock relogio = Clock.systemDefaultZone();
        LocalDate hoje = LocalDate.now(relogio);
        kits = new KitsFicticios(hoje);
        servico = new CheckInServico(relogio, new LeitorSvgSimulado(), new Conferencia(new BaseSintetica(hoje)), kits);
        try {
            corpo.run();
            ok++;
            System.out.println("  ok     " + nome);
        } catch (Throwable t) {
            falhas++;
            System.out.println("  FALHA  " + nome + " -> " + t);
            for (StackTraceElement e : t.getStackTrace()) {
                if (e.getClassName().contains("VerificaCenarios")) { System.out.println("         em " + e); break; }
            }
        }
    }

    private static void igual(Object esperado, Object obtido) {
        if (esperado == null ? obtido != null : !esperado.equals(obtido)) {
            throw new AssertionError("esperado <" + esperado + ">, obtido <" + obtido + ">");
        }
    }

    private static void verdade(boolean condicao) {
        if (!condicao) throw new AssertionError("condição falsa");
    }

    private static void recusa(int status, Runnable acao) {
        try {
            acao.run();
        } catch (RegraException e) {
            igual(status, e.status);
            return;
        }
        throw new AssertionError("deveria ter sido recusado com status " + status);
    }
}
