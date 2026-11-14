package br.com.recepcaosemfila;

import br.com.recepcaosemfila.dados.BaseSintetica;
import br.com.recepcaosemfila.dados.ContasSinteticas;
import br.com.recepcaosemfila.dados.KitsFicticios;
import br.com.recepcaosemfila.dominio.CheckIn;
import br.com.recepcaosemfila.dominio.Desfecho;
import br.com.recepcaosemfila.dominio.DocumentoLido;
import br.com.recepcaosemfila.dominio.Paciente;
import br.com.recepcaosemfila.dominio.ResultadoVerificacao;
import br.com.recepcaosemfila.dominio.StatusCheckIn;
import br.com.recepcaosemfila.dominio.TipoDocumento;
import br.com.recepcaosemfila.dominio.Verificacao;
import br.com.recepcaosemfila.servico.CheckInServico;
import br.com.recepcaosemfila.servico.Conferencia;
import br.com.recepcaosemfila.servico.LeitorDocumento;
import br.com.recepcaosemfila.servico.LeitorSvgSimulado;
import br.com.recepcaosemfila.servico.RegraException;
import br.com.recepcaosemfila.web.Json;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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
        teste("Conta: login simulado e resumo com a visita de hoje", VerificaCenarios::contaLogin);
        teste("Leitura de um documento mostra o que foi extraído, sem conferir", VerificaCenarios::leituraDocumento);
        teste("Conta: documento do titular conclui; de outra pessoa é encaminhado", VerificaCenarios::contaTitular);
        teste("Horário: paciente escolhe um horário livre de hoje", VerificaCenarios::horarioDaVisita);
        teste("Reenvio: carteirinha vencida é trocada pelo paciente e o check-in segue", VerificaCenarios::reenvioResolve);
        teste("Reenvio: só para pendência de documento e com limite", VerificaCenarios::reenvioLimites);
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

    private static void contaLogin() {
        recusa(401, () -> servico.entrar("ana.beatriz@exemplo.test", "errada"));
        recusa(401, () -> servico.entrar("ninguem@exemplo.test", ContasSinteticas.SENHA_DEMO));
        ContasSinteticas.Conta conta = servico.entrar(" Ana.Beatriz@exemplo.test ", ContasSinteticas.SENHA_DEMO);
        igual("Ana", conta.primeiroNome());

        Map<String, Object> resumo = servico.resumoConta(conta.id());
        verdade(resumo.get("visitaHoje") != null);
        verdade(!Json.texto(resumo).contains("G-2026")); // a guia não aparece antes da conferência

        CheckIn c = servico.iniciar(conta.id());
        igual(conta.email(), c.contaEmail);
        verdade(!c.paraMapa(false).containsKey("conta"));
        igual(conta.email(), c.paraMapa(true).get("conta"));
        recusa(404, () -> servico.iniciar("C-inexistente"));
    }

    private static void leituraDocumento() {
        KitsFicticios.Kit ana = kits.kit("ana");
        CheckIn c = servico.iniciar();
        DocumentoLido id = servico.lerDocumento(c.id, TipoDocumento.IDENTIDADE, kits.svgIdentidade(ana)).documento();
        igual("ANA BEATRIZ LIMA SOUZA", id.campo(LeitorDocumento.NOME));
        igual("111.222.333-00", id.campo(LeitorDocumento.CPF));
        verdade(id.ausentes().isEmpty());
        DocumentoLido cart = servico.lerDocumento(c.id, TipoDocumento.CARTEIRINHA, kits.svgCarteirinha(ana)).documento();
        igual("AUR-0001-2026", cart.campo(LeitorDocumento.NUMERO_CARTEIRINHA));

        // Ler não confere nem muda o estado, mas fica no histórico.
        igual(StatusCheckIn.AGUARDANDO_DOCUMENTOS, c.status);
        verdade(c.verificacoes.isEmpty() && c.desfechoInicial == null);
        verdade(c.eventos.stream().anyMatch(e -> e.descricao().contains("3 de 3 campos")));

        DocumentoLido tereza = servico.lerDocumento(c.id, TipoDocumento.IDENTIDADE, kits.svgIdentidade(kits.kit("tereza"))).documento();
        igual(List.of(LeitorDocumento.CPF, LeitorDocumento.NASCIMENTO), tereza.ausentes());

        servico.enviarDocumentos(c.id, "ana", kits.svgIdentidade(ana), kits.svgCarteirinha(ana));
        recusa(409, () -> servico.lerDocumento(c.id, TipoDocumento.IDENTIDADE, kits.svgIdentidade(ana)));
    }

    private static void contaTitular() {
        ContasSinteticas.Conta ana = servico.entrar("ana.beatriz@exemplo.test", ContasSinteticas.SENHA_DEMO);
        CheckIn proprio = enviar(servico.iniciar(ana.id()), "ana");
        igual(StatusCheckIn.AGUARDANDO_CONFIRMACAO_GUIA, proprio.status);
        igual(ResultadoVerificacao.OK, verificacao(proprio, "TITULAR_CONTA").resultado());
        verdade(proprio.verificacoes.stream().noneMatch(v -> v.codigo().equals("CADASTRO_LOCALIZADO")));

        // Documento de outra pessoa, que sozinho passaria na conferência, não libera a guia dela para esta conta.
        ContasSinteticas.Conta carlos = servico.entrar("carlos.eduardo@exemplo.test", ContasSinteticas.SENHA_DEMO);
        CheckIn alheio = enviar(servico.iniciar(carlos.id()), "ana");
        igual(StatusCheckIn.ATENDIMENTO_REMOTO, alheio.status);
        igual(ResultadoVerificacao.DIVERGENTE, verificacao(alheio, "TITULAR_CONTA").resultado());
        igual("ESCALAR", alheio.esperado);
        igual(Boolean.TRUE, alheio.decisaoCorreta());
        verdade(!alheio.paraMapa(false).containsKey("guia"));
        verdade(!Json.texto(alheio.paraMapa(false)).contains("222.333.444-00")); // CPF do titular não vaza

        // Sem CPF legível, não dá para confirmar o titular: dado ausente, vai para uma pessoa.
        ContasSinteticas.Conta tereza = servico.entrar("tereza.cristina@exemplo.test", ContasSinteticas.SENHA_DEMO);
        CheckIn ilegivel = enviar(servico.iniciar(tereza.id()), "tereza");
        igual(ResultadoVerificacao.AUSENTE, verificacao(ilegivel, "TITULAR_CONTA").resultado());
        igual(StatusCheckIn.ATENDIMENTO_REMOTO, ilegivel.status);
    }

    @SuppressWarnings("unchecked")
    private static void horarioDaVisita() {
        // Relógio fixo às 08:10 para o teste não depender da hora em que roda.
        LocalDate hoje = LocalDate.now();
        ZoneId zona = ZoneId.systemDefault();
        Clock oitoEDez = Clock.fixed(hoje.atTime(8, 10).atZone(zona).toInstant(), zona);
        BaseSintetica base = new BaseSintetica(hoje);
        KitsFicticios k = new KitsFicticios(hoje);
        CheckInServico s = new CheckInServico(oitoEDez, new LeitorSvgSimulado(), new Conferencia(base), k, new ContasSinteticas(base));

        ContasSinteticas.Conta ana = s.entrar("ana.beatriz@exemplo.test", ContasSinteticas.SENHA_DEMO);
        List<Map<String, Object>> hs = (List<Map<String, Object>>) s.resumoConta(ana.id()).get("horarios");
        verdade(hs.stream().noneMatch(h -> h.get("horario").equals("08:00")));   // já passou
        igual(true, horario(hs, "10:30").get("atual"));
        igual(false, horario(hs, "09:00").get("disponivel"));                    // ocupado por outro paciente fictício
        igual(false, horario(hs, "11:30").get("disponivel"));                    // é o horário da Marina (mesmo serviço)
        igual(true, horario(hs, "14:30").get("disponivel"));

        recusa(409, () -> s.remarcar(ana.id(), "11:30"));
        recusa(400, () -> s.remarcar(ana.id(), "07:30"));
        recusa(400, () -> s.remarcar(ana.id(), "10:45"));

        CheckIn c = s.iniciar(ana.id());
        Map<String, Object> r = s.remarcar(ana.id(), "14:30");
        igual("14:30", ((Map<String, Object>) r.get("visitaHoje")).get("horario"));
        verdade(c.eventos.stream().anyMatch(e -> e.descricao().contains("14:30")));
        s.enviarDocumentos(c.id, "ana", k.svgIdentidade(k.kit("ana")), k.svgCarteirinha(k.kit("ana")));
        igual("14:30", c.agendamento.horario());

        // O 10:30 liberado fica livre para quem é do mesmo serviço.
        ContasSinteticas.Conta marina = s.entrar("marina.castro@exemplo.test", ContasSinteticas.SENHA_DEMO);
        igual(true, horario((List<Map<String, Object>>) s.resumoConta(marina.id()).get("horarios"), "10:30").get("disponivel"));

        s.reiniciar();
        igual("10:30", ((Map<String, Object>) s.resumoConta(ana.id()).get("visitaHoje")).get("horario"));
    }

    private static Map<String, Object> horario(List<Map<String, Object>> hs, String h) {
        return hs.stream().filter(x -> x.get("horario").equals(h)).findFirst()
                .orElseThrow(() -> new AssertionError("horário ausente: " + h));
    }

    private static void reenvioResolve() {
        ContasSinteticas.Conta marina = servico.entrar("marina.castro@exemplo.test", ContasSinteticas.SENHA_DEMO);
        CheckIn c = enviar(servico.iniciar(marina.id()), "marina");
        igual(StatusCheckIn.ATENDIMENTO_REMOTO, c.status);
        igual(List.of(TipoDocumento.CARTEIRINHA), c.documentosParaReenviar());

        // Ao ler a carteirinha vencida, o assistente já avisa.
        CheckInServico.Leitura vencida = servico.lerDocumento(c.id, TipoDocumento.CARTEIRINHA, kits.svgCarteirinha(kits.kit("marina")));
        verdade(vencida.alertas().size() == 1 && vencida.alertas().get(0).texto().contains("venceu"));
        igual(LeitorDocumento.VALIDADE, vencida.alertas().get(0).campo());
        // A identidade não tem pendência: não dá para ler nem reenviar.
        recusa(409, () -> servico.lerDocumento(c.id, TipoDocumento.IDENTIDADE, kits.svgIdentidade(kits.kit("marina"))));
        recusa(409, () -> servico.reenviar(c.id, kits.svgIdentidade(kits.kit("marina")), null));
        recusa(400, () -> servico.reenviar(c.id, null, null));

        KitsFicticios.Kit renovada = kits.kit(kits.kit("marina").correcaoId());
        verdade(servico.lerDocumento(c.id, TipoDocumento.CARTEIRINHA, kits.svgCarteirinha(renovada)).alertas().isEmpty());
        servico.reenviar(c.id, null, kits.svgCarteirinha(renovada));
        igual(StatusCheckIn.AGUARDANDO_CONFIRMACAO_GUIA, c.status);
        verdade(c.corrigidoPeloPaciente && c.motivos.isEmpty());
        igual(1, c.reenvios);
        igual(Desfecho.ESCALADO_REMOTO, c.desfechoInicial); // o primeiro desfecho não muda
        igual(Boolean.TRUE, c.decisaoCorreta());
        verdade(c.eventos.stream().anyMatch(e -> e.descricao().contains("saiu do atendimento remoto")));

        servico.confirmarGuia(c.id);
        servico.registrarChegada(c.id);
        igual(StatusCheckIn.CONCLUIDO, c.status);
        igual(1, servico.metricas().get("corrigidosPeloPaciente"));
        igual(1, servico.metricas().get("escaladosRemoto"));
    }

    private static void reenvioLimites() {
        // Guia não autorizada: outro documento não resolve.
        CheckIn joao = enviar("joao");
        verdade(joao.documentosParaReenviar().isEmpty());
        recusa(409, () -> servico.reenviar(joao.id, kits.svgIdentidade(kits.kit("joao")), null));

        // Reenviar o mesmo documento errado mantém o caso com a pessoa; depois do limite, só o atendimento.
        CheckIn carlos = enviar("carlos");
        String errado = kits.svgIdentidade(kits.kit("carlos"));
        servico.reenviar(carlos.id, errado, null);
        igual(StatusCheckIn.ATENDIMENTO_REMOTO, carlos.status);
        verdade(!carlos.motivos.isEmpty());
        servico.reenviar(carlos.id, errado, null);
        verdade(carlos.documentosParaReenviar().isEmpty());
        recusa(409, () -> servico.reenviar(carlos.id, kits.svgIdentidade(kits.kit("carlos-corrigido")), null));
        servico.resolverRemoto(carlos.id, "Bia", "Data de nascimento conferida por telefone.");
        igual(StatusCheckIn.PRONTO_PARA_CHEGADA, carlos.status);

        // Ilegível: o documento legível resolve.
        CheckIn tereza = enviar("tereza");
        servico.reenviar(tereza.id, kits.svgIdentidade(kits.kit("tereza-legivel")), null);
        igual(StatusCheckIn.AGUARDANDO_CONFIRMACAO_GUIA, tereza.status);
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
        return enviar(servico.iniciar(), kitId);
    }

    private static CheckIn enviar(CheckIn c, String kitId) {
        KitsFicticios.Kit kit = kits.kit(kitId);
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
        BaseSintetica base = new BaseSintetica(hoje);
        servico = new CheckInServico(relogio, new LeitorSvgSimulado(), new Conferencia(base), kits, new ContasSinteticas(base));
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
