package br.com.recepcaosemfila;

import br.com.recepcaosemfila.dados.BaseSintetica;
import br.com.recepcaosemfila.dados.ContasSinteticas;
import br.com.recepcaosemfila.dados.KitsFicticios;
import br.com.recepcaosemfila.dominio.CheckIn;
import br.com.recepcaosemfila.dominio.Desfecho;
import br.com.recepcaosemfila.dominio.DocumentoLido;
import br.com.recepcaosemfila.dominio.Guia;
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
        teste("Atendimento humano: paciente pede ajuda e recebe a senha de apoio ao chegar", VerificaCenarios::ajudaHumana);
        teste("Arquivo que não é documento fictício SVG é encaminhado, não aprovado", VerificaCenarios::arquivoInvalido);
        teste("Transições inválidas são recusadas", VerificaCenarios::transicoesInvalidas);
        teste("Métricas refletem só o que aconteceu na execução", VerificaCenarios::metricas);
        teste("Conta: login simulado e resumo da conta", VerificaCenarios::contaLogin);
        teste("Leitura de um documento mostra o que foi extraído, sem conferir", VerificaCenarios::leituraDocumento);
        teste("Conta: documento do titular conclui; de outra pessoa é encaminhado", VerificaCenarios::contaTitular);
        teste("Agenda: paciente começa sem horário e escolhe o dia e o horário da visita", VerificaCenarios::horarioDaVisita);
        teste("Reenvio: carteirinha vencida é trocada pelo paciente e o check-in segue", VerificaCenarios::reenvioResolve);
        teste("Reenvio: só para pendência de documento e com limite", VerificaCenarios::reenvioLimites);
        teste("Apoio presencial fica marcado para o horário da visita, não para a hora do pedido", VerificaCenarios::apoioNoHorarioDaVisita);
        teste("Voltar: paciente desiste do apoio antes de chegar e volta para onde estava", VerificaCenarios::voltarDoApoio);
        teste("Limites: campos de atendimento e motivo são recusados", VerificaCenarios::limitesDosCampos);
        teste("Cadastro: conta nova só com dados fictícios, sem guia vai para uma pessoa e some ao zerar", VerificaCenarios::cadastroNoPortal);
        teste("JSON: escrita e leitura", VerificaCenarios::json);
        teste("CPFs da base sintética têm dígito verificador inválido", VerificaCenarios::cpfsFicticios);

        System.out.println();
        System.out.println(ok + " testes passaram, " + falhas + " falharam.");
        if (falhas > 0) System.exit(1);
    }

    // ---------- Cenários ----------

    private static void sucesso() {
        CheckIn c = enviar("ana");
        // A guia é do hospital com o convênio: com tudo conferido, o paciente não confirma nada e só falta chegar.
        igual(StatusCheckIn.PRONTO_PARA_CHEGADA, c.status);
        verdade(c.verificacoes.stream().noneMatch(Verificacao::falhou));
        igual(ResultadoVerificacao.SIMULADO, verificacao(c, "IDENTIDADE_SIMULADA").resultado());
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
        igual(null, c.senha); // a senha de apoio só sai na chegada
        verdade(!c.ajudaPedidaPeloPaciente);
        servico.registrarChegada(c.id);
        verdade(c.senha.startsWith("P-"));

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
        igual(null, c.decisaoCorreta()); // escolha do paciente não é avaliada como decisão do sistema

        // A senha de apoio não sai no pedido: sai quando o paciente confirma a chegada, como no fluxo normal.
        igual(null, c.senha);
        recusa(409, () -> servico.concluirPresencial(c.id, "Rafa", "Paciente ainda não chegou."));
        servico.registrarChegada(c.id);
        igual("P-001", c.senha);
        igual(StatusCheckIn.APOIO_PRESENCIAL, c.status); // uma pessoa ainda conclui no balcão
        recusa(409, () -> servico.registrarChegada(c.id));

        // Pedir ajuda no meio do fluxo também funciona.
        CheckIn d = enviar("ana");
        servico.pedirAjuda(d.id, null);
        igual(StatusCheckIn.APOIO_PRESENCIAL, d.status);
        servico.registrarChegada(d.id);
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

        enviar("ana");
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
        verdade(resumo.get("visita") != null);
        verdade(!Json.texto(resumo).contains("G-2026")); // a guia não aparece antes da conferência
        verdade(!Json.texto(resumo).contains("Triagem")); // nem o local da visita

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
        igual(StatusCheckIn.PRONTO_PARA_CHEGADA, proprio.status);
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
        // Base sem visitas marcadas (como na demo) e relógio fixo às 08:10.
        LocalDate hoje = LocalDate.now();
        BaseSintetica base = new BaseSintetica(hoje);
        KitsFicticios k = new KitsFicticios(hoje);
        CheckInServico s = new CheckInServico(relogio(hoje, 8, 10), new LeitorSvgSimulado(), new Conferencia(base), k, new ContasSinteticas(base));

        // Ninguém começa com horário: o paciente vê o que tem para agendar, sem o local, e a agenda de 7 dias.
        ContasSinteticas.Conta ana = s.entrar("ana.beatriz@exemplo.test", ContasSinteticas.SENHA_DEMO);
        Map<String, Object> resumo = s.resumoConta(ana.id());
        igual(null, resumo.get("visita"));
        igual("Consulta eletiva", resumo.get("servico"));
        verdade(!Json.texto(resumo).contains("Triagem"));
        igual(BaseSintetica.DIAS_DA_AGENDA, ((List<?>) resumo.get("agenda")).size());
        verdade(horariosDoDia(resumo, 0).stream().noneMatch(h -> h.get("horario").equals("08:00"))); // já passou hoje
        List<Map<String, Object>> amanha = horariosDoDia(resumo, 1);
        igual(BaseSintetica.GRADE.size(), amanha.size());                                            // amanhã: grade inteira
        recusa(409, () -> s.iniciar(ana.id()));                                                      // sem visita, sem check-in

        String dia = hoje.plusDays(1).toString();
        String livre = primeiroLivre(amanha);
        String ocupado = amanha.stream().filter(h -> Boolean.FALSE.equals(h.get("disponivel")))
                .map(h -> (String) h.get("horario")).findFirst().orElseThrow();
        recusa(409, () -> s.agendar(ana.id(), dia, ocupado));
        recusa(400, () -> s.agendar(ana.id(), hoje.plusDays(BaseSintetica.DIAS_DA_AGENDA).toString(), livre)); // fora da agenda
        recusa(400, () -> s.agendar(ana.id(), hoje.minusDays(1).toString(), livre));
        recusa(400, () -> s.agendar(ana.id(), hoje.toString(), "07:30"));                            // já passou
        recusa(400, () -> s.agendar(ana.id(), dia, "10:45"));                                        // fora da grade
        recusa(400, () -> s.agendar(ana.id(), "amanhã", livre));

        Map<String, Object> r = s.agendar(ana.id(), dia, livre);
        Map<String, Object> visita = (Map<String, Object>) r.get("visita");
        igual(hoje.plusDays(1), visita.get("data"));
        igual(livre, visita.get("horario"));
        igual(true, horario(horariosDoDia(r, 1), livre).get("atual"));
        verdade(!Json.texto(r).contains("Triagem"));

        // Quem é do mesmo serviço vê o horário ocupado.
        ContasSinteticas.Conta marina = s.entrar("marina.castro@exemplo.test", ContasSinteticas.SENHA_DEMO);
        igual(false, horario(horariosDoDia(s.resumoConta(marina.id()), 1), livre).get("disponivel"));

        // Check-in antecipado de uma visita de amanhã: a conferência vale; a chegada só no dia da visita.
        CheckIn c = s.iniciar(ana.id());
        s.enviarDocumentos(c.id, "ana", k.svgIdentidade(k.kit("ana")), k.svgCarteirinha(k.kit("ana")));
        igual(StatusCheckIn.PRONTO_PARA_CHEGADA, c.status);
        igual(hoje.plusDays(1), c.agendamento.data());
        recusa(409, () -> s.registrarChegada(c.id));

        // Depois da conferência, trocar para um dia em que a guia já venceu é recusado; dentro da validade, vale.
        Guia g = c.guia;
        c.guia = new Guia(g.numero(), g.agendamentoId(), g.numeroCarteirinha(), g.procedimento(), g.status(), hoje.plusDays(2));
        String depois = hoje.plusDays(3).toString();
        recusa(409, () -> s.agendar(ana.id(), depois, primeiroLivre(horariosDoDia(s.resumoConta(ana.id()), 3))));
        igual(hoje.plusDays(1), c.agendamento.data());
        String dois = hoje.plusDays(2).toString();
        s.agendar(ana.id(), dois, primeiroLivre(horariosDoDia(s.resumoConta(ana.id()), 2)));
        igual(hoje.plusDays(2), c.agendamento.data());

        // Zerar a demo apaga os horários marcados.
        s.reiniciar();
        igual(null, s.resumoConta(ana.id()).get("visita"));
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
        igual(StatusCheckIn.PRONTO_PARA_CHEGADA, c.status);
        verdade(c.corrigidoPeloPaciente && c.motivos.isEmpty());
        igual(1, c.reenvios);
        igual(Desfecho.ESCALADO_REMOTO, c.desfechoInicial); // o primeiro desfecho não muda
        igual(Boolean.TRUE, c.decisaoCorreta());
        verdade(c.eventos.stream().anyMatch(e -> e.descricao().contains("saiu do atendimento remoto")));

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
        igual(StatusCheckIn.PRONTO_PARA_CHEGADA, tereza.status);
    }

    private static void apoioNoHorarioDaVisita() {
        LocalDate hoje = LocalDate.now();
        BaseSintetica base = new BaseSintetica(hoje);
        agendaDeHoje(base, hoje);
        KitsFicticios k = new KitsFicticios(hoje);
        CheckInServico s = new CheckInServico(relogio(hoje, 8, 10), new LeitorSvgSimulado(), new Conferencia(base), k, new ContasSinteticas(base));

        // Pedido do paciente às 08:10: o balcão fica para o horário da visita da Ana (10:30).
        ContasSinteticas.Conta ana = s.entrar("ana.beatriz@exemplo.test", ContasSinteticas.SENHA_DEMO);
        CheckIn c = s.iniciar(ana.id());
        s.pedirAjuda(c.id, "Prefiro falar com alguém");
        igual("10:30", c.horarioApoio);
        igual(hoje, c.diaApoio);
        igual("10:30", c.paraMapa(false).get("horarioApoio"));
        verdade(c.eventos.stream().anyMatch(e -> e.descricao().contains("hoje às 10:30, horário da visita")));
        // Trocar a visita para outro dia leva o apoio junto; a chegada só vale nesse dia.
        String livre = primeiroLivre(horariosDoDia(s.resumoConta(ana.id()), 2));
        s.agendar(ana.id(), hoje.plusDays(2).toString(), livre);
        igual(hoje.plusDays(2), c.diaApoio);
        igual(livre, c.horarioApoio);
        recusa(409, () -> s.registrarChegada(c.id));

        // Encaminhado pelo atendimento: também no horário da visita (Carlos, 11:00).
        ContasSinteticas.Conta carlos = s.entrar("carlos.eduardo@exemplo.test", ContasSinteticas.SENHA_DEMO);
        CheckIn d = s.iniciar(carlos.id());
        s.enviarDocumentos(d.id, "carlos", k.svgIdentidade(k.kit("carlos")), k.svgCarteirinha(k.kit("carlos")));
        s.encaminharPresencial(d.id, "Bia", "Conferir o documento original no balcão.");
        igual("11:00", d.horarioApoio);
        // Depois de confirmar a chegada, a visita não muda pelo portal: só com a recepção.
        s.registrarChegada(d.id);
        String amanha = primeiroLivre(horariosDoDia(s.resumoConta(carlos.id()), 1));
        recusa(409, () -> s.agendar(carlos.id(), hoje.plusDays(1).toString(), amanha));
        igual(hoje, d.diaApoio);

        // Visita que já passou: o apoio é assim que o paciente chegar.
        BaseSintetica base2 = new BaseSintetica(hoje);
        agendaDeHoje(base2, hoje);
        CheckInServico noite = new CheckInServico(relogio(hoje, 19, 50), new LeitorSvgSimulado(), new Conferencia(base2), k, new ContasSinteticas(base2));
        CheckIn e = noite.iniciar(noite.entrar("ana.beatriz@exemplo.test", ContasSinteticas.SENHA_DEMO).id());
        noite.pedirAjuda(e.id, null);
        igual(null, e.horarioApoio);
        verdade(e.eventos.stream().anyMatch(x -> x.descricao().contains("assim que chegar")));
    }

    private static void voltarDoApoio() {
        // Pediu ajuda antes de enviar e voltou: o pedido desfeito não conta como desfecho.
        CheckIn lia = servico.iniciar();
        servico.pedirAjuda(lia.id, "dúvida");
        servico.cancelarAjuda(lia.id);
        igual(StatusCheckIn.AGUARDANDO_DOCUMENTOS, lia.status);
        igual(null, lia.desfechoInicial);

        // Pediu ajuda já pronto para a chegada e voltou: segue o fluxo normal, com o desfecho da conferência.
        CheckIn ana = enviar("ana");
        servico.pedirAjuda(ana.id, "dúvida");
        servico.cancelarAjuda(ana.id);
        igual(StatusCheckIn.PRONTO_PARA_CHEGADA, ana.status);
        verdade(!ana.ajudaPedidaPeloPaciente && ana.horarioApoio == null && ana.motivos.isEmpty());
        verdade(ana.eventos.stream().anyMatch(e -> e.descricao().contains("Desistiu do apoio")));
        igual(Desfecho.CONCLUIDO_AUTOMATICO, ana.desfechoInicial);

        // Com pendência: volta para o atendimento remoto, com o mesmo motivo e o mesmo desfecho.
        CheckIn carlos = enviar("carlos");
        servico.pedirAjuda(carlos.id, null);
        servico.cancelarAjuda(carlos.id);
        igual(StatusCheckIn.ATENDIMENTO_REMOTO, carlos.status);
        igual(Desfecho.ESCALADO_REMOTO, carlos.desfechoInicial);
        verdade(carlos.motivos.size() == 1 && !carlos.motivos.get(0).startsWith("Paciente pediu ajuda"));

        // Depois da chegada, ou quando foi o atendimento que encaminhou, não dá para voltar.
        CheckIn d = servico.iniciar();
        servico.pedirAjuda(d.id, null);
        servico.registrarChegada(d.id);
        recusa(409, () -> servico.cancelarAjuda(d.id));
        CheckIn tereza = enviar("tereza");
        servico.encaminharPresencial(tereza.id, "Bia", "Conferir o original no balcão.");
        recusa(409, () -> servico.cancelarAjuda(tereza.id));
        recusa(409, () -> servico.cancelarAjuda(ana.id)); // não está no apoio presencial
    }

    /** Visitas de hoje para os cinco pacientes fictícios (como se cada um já tivesse escolhido o horário). */
    private static void agendaDeHoje(BaseSintetica base, LocalDate hoje) {
        base.agendar("P1", hoje, "10:30");
        base.agendar("P2", hoje, "11:00");
        base.agendar("P3", hoje, "11:30");
        base.agendar("P4", hoje, "14:00");
        base.agendar("P5", hoje, "15:30");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> horariosDoDia(Map<String, Object> resumo, int dia) {
        List<Map<String, Object>> agenda = (List<Map<String, Object>>) resumo.get("agenda");
        return (List<Map<String, Object>>) agenda.get(dia).get("horarios");
    }

    private static String primeiroLivre(List<Map<String, Object>> hs) {
        return hs.stream().filter(h -> Boolean.TRUE.equals(h.get("disponivel"))).map(h -> (String) h.get("horario"))
                .findFirst().orElseThrow(() -> new AssertionError("nenhum horário livre"));
    }

    private static Clock relogio(LocalDate dia, int hora, int minuto) {
        ZoneId zona = ZoneId.systemDefault();
        return Clock.fixed(dia.atTime(hora, minuto).atZone(zona).toInstant(), zona);
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

    private static void limitesDosCampos() {
        CheckIn divergente = enviar("carlos");
        recusa(400, () -> servico.resolverRemoto(divergente.id, "A".repeat(61), "observação válida"));
        recusa(400, () -> servico.resolverRemoto(divergente.id, "Atendente", "A".repeat(401)));

        CheckIn ajuda = servico.iniciar();
        recusa(400, () -> servico.pedirAjuda(ajuda.id, "A".repeat(201)));
    }

    private static void cadastroNoPortal() {
        LocalDate hoje = LocalDate.now();
        CheckInServico.NovoCadastro ok = new CheckInServico.NovoCadastro("  Lia   Teste Ficticia ", "123.456.789-00",
                "1990-05-10", "Lia@Exemplo.test", "segredo", "Plano Horizonte (fictício)", "hor-9999-2027",
                hoje.plusYears(1).toString(), "Exame laboratorial");

        // Só dados fictícios e completos.
        recusa(400, () -> servico.cadastrar(com(ok, "cpf", "123.456.789-09")));            // dígito válido: poderia ser real
        recusa(409, () -> servico.cadastrar(com(ok, "cpf", "111.222.333-00")));            // CPF da Ana
        recusa(400, () -> servico.cadastrar(com(ok, "cpf", "123")));
        recusa(400, () -> servico.cadastrar(com(ok, "email", "lia@gmail.com")));           // e-mail que pode ser real
        recusa(409, () -> servico.cadastrar(com(ok, "email", "ana.beatriz@exemplo.test")));
        recusa(400, () -> servico.cadastrar(com(ok, "nome", "Lia")));
        recusa(400, () -> servico.cadastrar(com(ok, "senha", "123")));
        recusa(400, () -> servico.cadastrar(com(ok, "nascimento", hoje.plusDays(1).toString())));
        recusa(400, () -> servico.cadastrar(com(ok, "convenio", "Convênio de verdade")));
        recusa(400, () -> servico.cadastrar(com(ok, "servico", "Cirurgia")));
        recusa(400, () -> servico.cadastrar(com(ok, "validadeCarteirinha", "amanhã")));

        ContasSinteticas.Conta lia = servico.cadastrar(ok);
        igual("lia@exemplo.test", lia.email());
        igual("Lia Teste Ficticia", lia.paciente().nome());
        igual("123.456.789-00", lia.paciente().cpf());
        igual("HOR-9999-2027", lia.paciente().numeroCarteirinha());
        verdade(!lia.demo());
        igual(lia.id(), servico.entrar("lia@exemplo.test", "segredo").id());
        recusa(401, () -> servico.entrar("lia@exemplo.test", ContasSinteticas.SENHA_DEMO));
        recusa(409, () -> servico.cadastrar(com(ok, "cpf", "123.456.789-01")));            // e-mail já usado

        // Começa como as outras contas: sem visita e sem o local.
        Map<String, Object> resumo = servico.resumoConta(lia.id());
        igual(null, resumo.get("visita"));
        igual("Exame laboratorial", resumo.get("servico"));
        verdade(!Json.texto(resumo).contains("Coleta"));

        // Com os documentos gerados do próprio cadastro tudo confere, menos a guia, que não existe: vai para uma pessoa.
        servico.agendar(lia.id(), hoje.plusDays(1).toString(), primeiroLivre(horariosDoDia(resumo, 1)));
        KitsFicticios.Kit kit = kits.kit(lia.kitId());
        CheckIn c = servico.enviarDocumentos(servico.iniciar(lia.id()).id, kit.id(), kits.svgIdentidade(kit), kits.svgCarteirinha(kit));
        igual(StatusCheckIn.ATENDIMENTO_REMOTO, c.status);
        for (String codigo : List.of("NOME", "NASCIMENTO", "CARTEIRINHA", "AGENDAMENTO")) {
            igual(ResultadoVerificacao.OK, verificacao(c, codigo).resultado());
        }
        igual(ResultadoVerificacao.AUSENTE, verificacao(c, "GUIA").resultado());
        igual(null, c.decisaoCorreta()); // sem gabarito: não entra na avaliação das decisões

        // Zerar a demo apaga a conta, o cadastro e os documentos gerados.
        servico.reiniciar();
        recusa(401, () -> servico.entrar("lia@exemplo.test", "segredo"));
        recusa(404, () -> servico.resumoConta(lia.id()));
        igual(null, kits.kit(kit.id()));
        verdade(servico.cadastrar(ok) != null); // CPF e e-mail livres de novo
    }

    private static CheckInServico.NovoCadastro com(CheckInServico.NovoCadastro d, String campo, String v) {
        return new CheckInServico.NovoCadastro(
                campo.equals("nome") ? v : d.nome(), campo.equals("cpf") ? v : d.cpf(),
                campo.equals("nascimento") ? v : d.nascimento(), campo.equals("email") ? v : d.email(),
                campo.equals("senha") ? v : d.senha(), campo.equals("convenio") ? v : d.convenio(),
                campo.equals("numeroCarteirinha") ? v : d.numeroCarteirinha(),
                campo.equals("validadeCarteirinha") ? v : d.validadeCarteirinha(), campo.equals("servico") ? v : d.servico());
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
        // Relógio fixo às 08:00 de hoje e todos os pacientes com visita marcada para hoje, para os cenários
        // não dependerem da hora em que o teste roda. Os testes de agenda montam a base sem visitas.
        LocalDate hoje = LocalDate.now();
        kits = new KitsFicticios(hoje);
        BaseSintetica base = new BaseSintetica(hoje);
        agendaDeHoje(base, hoje);
        servico = new CheckInServico(relogio(hoje, 8, 0), new LeitorSvgSimulado(), new Conferencia(base), kits, new ContasSinteticas(base));
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
