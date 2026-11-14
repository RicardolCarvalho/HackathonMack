package br.com.recepcaosemfila.servico;

import br.com.recepcaosemfila.dados.BaseSintetica;
import br.com.recepcaosemfila.dominio.Agendamento;
import br.com.recepcaosemfila.dominio.DocumentoLido;
import br.com.recepcaosemfila.dominio.Guia;
import br.com.recepcaosemfila.dominio.Paciente;
import br.com.recepcaosemfila.dominio.ResultadoVerificacao;
import br.com.recepcaosemfila.dominio.Verificacao;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import static br.com.recepcaosemfila.dominio.ResultadoVerificacao.AUSENTE;
import static br.com.recepcaosemfila.dominio.ResultadoVerificacao.DIVERGENTE;
import static br.com.recepcaosemfila.dominio.ResultadoVerificacao.NAO_AVALIADO;
import static br.com.recepcaosemfila.dominio.ResultadoVerificacao.OK;
import static br.com.recepcaosemfila.dominio.ResultadoVerificacao.SIMULADO;

/**
 * Regras administrativas da conferência: documento x cadastro x carteirinha x agendamento x guia.
 * São regras fixas e explicáveis; não há decisão clínica aqui. Qualquer falha vira encaminhamento para uma pessoa.
 */
public final class Conferencia {
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record Resultado(List<Verificacao> verificacoes, Paciente paciente, Agendamento agendamento, Guia guia) {
        public boolean aprovado() {
            return verificacoes.stream().noneMatch(Verificacao::falhou);
        }

        public List<String> motivos() {
            return verificacoes.stream().filter(Verificacao::falhou)
                    .map(v -> v.titulo() + ": " + v.detalhe()).toList();
        }
    }

    private final BaseSintetica base;

    public Conferencia(BaseSintetica base) { this.base = base; }

    public Resultado conferir(DocumentoLido identidade, DocumentoLido carteirinha, LocalDate hoje) {
        List<Verificacao> vs = new ArrayList<>();

        // 1. Leitura dos documentos
        vs.add(leitura("LEITURA_IDENTIDADE", "Leitura do documento de identidade", identidade));
        vs.add(leitura("LEITURA_CARTEIRINHA", "Leitura da carteirinha", carteirinha));
        vs.add(new Verificacao("IDENTIDADE_SIMULADA", "Identidade e autenticidade do documento", SIMULADO, null, null,
                "Simulação da PoC", "Etapa simulada: a PoC não comprova que o documento é autêntico nem que pertence a quem enviou."));

        // 2. Cadastro
        String cpf = identidade.campo(LeitorDocumento.CPF);
        Paciente p = base.pacientePorCpf(cpf).orElse(null);
        if (cpf == null) {
            vs.add(naoAvaliado("CADASTRO_LOCALIZADO", "Cadastro do paciente", BaseSintetica.FONTE_CADASTRO,
                    "Sem CPF legível não é possível localizar o cadastro."));
        } else if (p == null) {
            vs.add(new Verificacao("CADASTRO_LOCALIZADO", "Cadastro do paciente", AUSENTE, cpf, null,
                    BaseSintetica.FONTE_CADASTRO, "Nenhum cadastro encontrado para o CPF lido."));
        } else {
            vs.add(new Verificacao("CADASTRO_LOCALIZADO", "Cadastro do paciente", OK, cpf, p.cpf(),
                    BaseSintetica.FONTE_CADASTRO, "Cadastro localizado pelo CPF."));
        }

        if (p == null) {
            String motivo = "Depende de localizar o cadastro.";
            vs.add(naoAvaliado("NOME", "Nome", BaseSintetica.FONTE_CADASTRO, motivo));
            vs.add(naoAvaliado("NASCIMENTO", "Data de nascimento", BaseSintetica.FONTE_CADASTRO, motivo));
            vs.add(naoAvaliado("CARTEIRINHA", "Carteirinha do convênio", BaseSintetica.FONTE_CADASTRO, motivo));
            vs.add(naoAvaliado("AGENDAMENTO", "Agendamento de hoje", BaseSintetica.FONTE_AGENDA, motivo));
            vs.add(naoAvaliado("GUIA", "Guia do convênio", BaseSintetica.FONTE_GUIAS, motivo));
            return new Resultado(vs, null, null, null);
        }

        // 3. Dados pessoais
        String nome = identidade.campo(LeitorDocumento.NOME);
        vs.add(comparar("NOME", "Nome", nome, p.nome(),
                nome != null && LeitorSvgSimulado.semAcento(nome).equals(LeitorSvgSimulado.semAcento(p.nome())),
                BaseSintetica.FONTE_CADASTRO));

        String nasc = identidade.campo(LeitorDocumento.NASCIMENTO);
        vs.add(comparar("NASCIMENTO", "Data de nascimento", nasc, p.nascimento().format(BR),
                p.nascimento().equals(data(nasc)), BaseSintetica.FONTE_CADASTRO));

        // 4. Carteirinha
        vs.add(carteirinha(carteirinha, p, hoje));

        // 5. Agendamento e guia
        Agendamento ag = base.agendamentoDoDia(p.id(), hoje).orElse(null);
        Guia guia = null;
        if (ag == null) {
            vs.add(new Verificacao("AGENDAMENTO", "Agendamento de hoje", AUSENTE, null, null,
                    BaseSintetica.FONTE_AGENDA, "Não há agendamento para hoje neste cadastro."));
            vs.add(naoAvaliado("GUIA", "Guia do convênio", BaseSintetica.FONTE_GUIAS, "Depende de haver agendamento."));
        } else {
            vs.add(new Verificacao("AGENDAMENTO", "Agendamento de hoje", OK, null, ag.servico() + " às " + ag.horario(),
                    BaseSintetica.FONTE_AGENDA, "Agendamento localizado para hoje."));
            guia = base.guiaDoAgendamento(ag.id()).orElse(null);
            vs.add(guia(guia, p, hoje));
        }
        return new Resultado(vs, p, ag, guia);
    }

    private static Verificacao leitura(String codigo, String titulo, DocumentoLido doc) {
        if (doc.ausentes().isEmpty()) {
            return new Verificacao(codigo, titulo, OK, null, null, doc.fonte(), "Todos os campos necessários foram lidos.");
        }
        return new Verificacao(codigo, titulo, AUSENTE, null, null, doc.fonte(),
                "Não foi possível ler: " + String.join(", ", doc.ausentes().stream().map(Conferencia::rotulo).toList()) + ".");
    }

    private static Verificacao comparar(String codigo, String titulo, String lido, String cadastro, boolean igual, String fonte) {
        if (lido == null) {
            return new Verificacao(codigo, titulo, AUSENTE, null, cadastro, fonte, "Campo não lido no documento.");
        }
        return igual
                ? new Verificacao(codigo, titulo, OK, lido, cadastro, fonte, "Confere com o cadastro.")
                : new Verificacao(codigo, titulo, DIVERGENTE, lido, cadastro, fonte, "O documento não confere com o cadastro.");
    }

    private static Verificacao carteirinha(DocumentoLido doc, Paciente p, LocalDate hoje) {
        String titulo = "Carteirinha do convênio";
        String numero = doc.campo(LeitorDocumento.NUMERO_CARTEIRINHA);
        String convenio = doc.campo(LeitorDocumento.CONVENIO);
        LocalDate validade = data(doc.campo(LeitorDocumento.VALIDADE));
        String lido = numero == null ? null : numero + " (" + convenio + ", validade " + doc.campo(LeitorDocumento.VALIDADE) + ")";
        String cadastro = p.numeroCarteirinha() + " (" + p.convenio() + ", validade " + p.validadeCarteirinha().format(BR) + ")";
        String fonte = BaseSintetica.FONTE_CADASTRO + " + data do sistema";

        if (numero == null || convenio == null || validade == null) {
            return new Verificacao("CARTEIRINHA", titulo, AUSENTE, lido, cadastro, fonte, "Faltam dados legíveis na carteirinha.");
        }
        if (!numero.equalsIgnoreCase(p.numeroCarteirinha())) {
            return new Verificacao("CARTEIRINHA", titulo, DIVERGENTE, lido, cadastro, fonte, "O número não confere com o cadastro.");
        }
        if (!LeitorSvgSimulado.semAcento(convenio).equals(LeitorSvgSimulado.semAcento(p.convenio()))) {
            return new Verificacao("CARTEIRINHA", titulo, DIVERGENTE, lido, cadastro, fonte, "O convênio não confere com o cadastro.");
        }
        if (validade.isBefore(hoje)) {
            return new Verificacao("CARTEIRINHA", titulo, DIVERGENTE, lido, cadastro, fonte,
                    "Carteirinha vencida em " + validade.format(BR) + ".");
        }
        return new Verificacao("CARTEIRINHA", titulo, OK, lido, cadastro, fonte, "Número e convênio conferem; dentro da validade.");
    }

    private static Verificacao guia(Guia g, Paciente p, LocalDate hoje) {
        String titulo = "Guia do convênio";
        String fonte = BaseSintetica.FONTE_GUIAS;
        if (g == null) {
            return new Verificacao("GUIA", titulo, AUSENTE, null, null, fonte, "Não há guia para o agendamento de hoje.");
        }
        String cadastro = g.numero() + " (" + g.status() + ", validade " + g.validade().format(BR) + ")";
        if (!"AUTORIZADA".equals(g.status())) {
            return new Verificacao("GUIA", titulo, DIVERGENTE, null, cadastro, fonte, "A guia ainda não está autorizada pelo convênio.");
        }
        if (g.validade().isBefore(hoje)) {
            return new Verificacao("GUIA", titulo, DIVERGENTE, null, cadastro, fonte, "A guia está vencida.");
        }
        if (!g.numeroCarteirinha().equalsIgnoreCase(p.numeroCarteirinha())) {
            return new Verificacao("GUIA", titulo, DIVERGENTE, null, cadastro, fonte, "A guia é de outra carteirinha.");
        }
        return new Verificacao("GUIA", titulo, OK, null, cadastro, fonte, "Guia autorizada e dentro da validade.");
    }

    private static Verificacao naoAvaliado(String codigo, String titulo, String fonte, String detalhe) {
        return new Verificacao(codigo, titulo, NAO_AVALIADO, null, null, fonte, detalhe);
    }

    private static LocalDate data(String s) {
        if (s == null) return null;
        try {
            return LocalDate.parse(s.trim(), BR);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String rotulo(String campo) {
        return switch (campo) {
            case LeitorDocumento.NOME -> "nome";
            case LeitorDocumento.CPF -> "CPF";
            case LeitorDocumento.NASCIMENTO -> "data de nascimento";
            case LeitorDocumento.CONVENIO -> "convênio";
            case LeitorDocumento.NUMERO_CARTEIRINHA -> "número da carteirinha";
            case LeitorDocumento.VALIDADE -> "validade";
            default -> campo;
        };
    }
}
