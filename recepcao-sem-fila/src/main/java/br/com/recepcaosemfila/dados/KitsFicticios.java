package br.com.recepcaosemfila.dados;

import br.com.recepcaosemfila.dominio.Paciente;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Documentos fictícios da demo, gerados como imagem SVG a partir de dados sintéticos.
 * Cada kit tem um documento de identidade e uma carteirinha, e um gabarito (o que o sistema deveria decidir).
 * Um campo null é desenhado como trecho ilegível.
 */
public final class KitsFicticios {
    public static final String ILEGIVEL = "████████";
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record Kit(String id, String titulo, String resumo, String esperado, String correcaoId,
                      String nome, String cpf, String nascimento,
                      String convenio, String numeroCarteirinha, String validade) {
        public Map<String, Object> paraMapa() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("titulo", titulo);
            m.put("resumo", resumo);
            m.put("esperado", esperado);
            m.put("correcaoId", correcaoId);
            m.put("identidadeUrl", "/api/kits/" + id + "/identidade.svg");
            m.put("carteirinhaUrl", "/api/kits/" + id + "/carteirinha.svg");
            return m;
        }
    }

    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private final Map<String, Kit> correcoes = new LinkedHashMap<>(); // versões corretas, usadas no reenvio
    private final Map<String, Kit> doCadastro = new ConcurrentHashMap<>(); // gerados para contas criadas no portal

    public KitsFicticios(LocalDate hoje) {
        add(new Kit("ana", "Ana Beatriz", "Tudo confere com o cadastro.", "CONCLUIR", null,
                "Ana Beatriz Lima Souza", "111.222.333-00", "14/03/1988",
                "Convênio Aurora (fictício)", "AUR-0001-2026", hoje.plusMonths(10).format(BR)));
        add(new Kit("carlos", "Carlos Eduardo", "Data de nascimento do documento difere do cadastro.", "ESCALAR", "carlos-corrigido",
                "Carlos Eduardo Pereira Nunes", "222.333.444-00", "02/11/1957",
                "Plano Horizonte (fictício)", "HOR-0002-2026", hoje.plusMonths(6).format(BR)));
        add(new Kit("marina", "Marina", "Carteirinha vencida.", "ESCALAR", "marina-renovada",
                "Marina Alves Castro", "333.444.555-00", "29/07/1994",
                "Convênio Aurora (fictício)", "AUR-0003-2025", hoje.minusDays(30).format(BR)));
        add(new Kit("joao", "João Pedro", "Guia ainda não autorizada pelo convênio.", "ESCALAR", null,
                "João Pedro Martins Rocha", "444.555.666-00", "09/01/2001",
                "Plano Horizonte (fictício)", "HOR-0004-2026", hoje.plusMonths(8).format(BR)));
        add(new Kit("tereza", "Tereza Cristina", "Documento com CPF e nascimento ilegíveis (dado ausente).", "ESCALAR", "tereza-legivel",
                "Tereza Cristina Barbosa", null, null,
                "Convênio Aurora (fictício)", "AUR-0005-2026", hoje.plusMonths(3).format(BR)));

        // O documento certo que o paciente reenviaria em cada cenário com pendência de documento.
        correcao(new Kit("carlos-corrigido", "Carlos Eduardo (documento correto)", "Data de nascimento igual à do cadastro.", "CONCLUIR", null,
                "Carlos Eduardo Pereira Nunes", "222.333.444-00", "02/11/1975",
                "Plano Horizonte (fictício)", "HOR-0002-2026", hoje.plusMonths(6).format(BR)));
        correcao(new Kit("marina-renovada", "Marina (carteirinha renovada)", "Carteirinha dentro da validade.", "CONCLUIR", null,
                "Marina Alves Castro", "333.444.555-00", "29/07/1994",
                "Convênio Aurora (fictício)", "AUR-0003-2025", hoje.plusYears(1).format(BR)));
        correcao(new Kit("tereza-legivel", "Tereza Cristina (documento legível)", "Todos os campos legíveis.", "CONCLUIR", null,
                "Tereza Cristina Barbosa", "555.666.777-00", "21/05/1949",
                "Convênio Aurora (fictício)", "AUR-0005-2026", hoje.plusMonths(3).format(BR)));
    }

    private void add(Kit k) { kits.put(k.id(), k); }
    private void correcao(Kit k) { correcoes.put(k.id(), k); }

    /** Kit principal (listado na demo), versão corrigida (usada no reenvio) ou gerado para um cadastro do portal. */
    public Kit kit(String id) {
        if (id == null) return null;
        if (kits.containsKey(id)) return kits.get(id);
        return correcoes.containsKey(id) ? correcoes.get(id) : doCadastro.get(id);
    }

    /**
     * Documentos fictícios de exemplo para uma conta criada no portal, com os dados que o próprio paciente
     * cadastrou. Sem gabarito: o caso não entra na avaliação das decisões do sistema.
     */
    public Kit kitDoCadastro(Paciente p) {
        Kit k = new Kit("cadastro-" + p.id().toLowerCase(), p.nome().split(" ")[0],
                "Documentos gerados a partir do cadastro feito no portal.", null, null,
                p.nome(), p.cpf(), p.nascimento().format(BR),
                p.convenio(), p.numeroCarteirinha(), p.validadeCarteirinha().format(BR));
        doCadastro.put(k.id(), k);
        return k;
    }

    /** Apaga os documentos gerados para contas do portal (usado ao zerar a demo). */
    public void reiniciarCadastros() { doCadastro.clear(); }

    public List<Map<String, Object>> lista() {
        List<Map<String, Object>> l = new ArrayList<>();
        for (Kit k : kits.values()) l.add(k.paraMapa());
        return l;
    }

    public String svgIdentidade(Kit k) {
        return """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 400" width="640" height="400" font-family="Arial, Helvetica, sans-serif">
              <rect width="640" height="400" rx="18" fill="#e9f1ee"/>
              <rect x="0" y="0" width="640" height="64" rx="18" fill="#1f5c4d"/>
              <rect x="0" y="40" width="640" height="24" fill="#1f5c4d"/>
              <text x="28" y="40" font-size="20" font-weight="bold" fill="#ffffff">DOCUMENTO DE IDENTIDADE</text>
              <text x="612" y="40" font-size="12" fill="#cfe6de" text-anchor="end">República Fictícia do Hackathon</text>
              <rect x="28" y="92" width="150" height="190" rx="8" fill="#c9d9d3"/>
              <circle cx="103" cy="160" r="38" fill="#9fb7ae"/>
              <path d="M48 282 Q103 196 158 282 Z" fill="#9fb7ae"/>
              <text x="206" y="108" font-size="11" fill="#55706a">NOME</text>
              <text x="206" y="134" font-size="20" font-weight="bold" fill="#14313d">%s</text>
              <text x="206" y="176" font-size="11" fill="#55706a">CPF</text>
              <text x="206" y="202" font-size="20" font-weight="bold" fill="#14313d">%s</text>
              <text x="206" y="244" font-size="11" fill="#55706a">DATA DE NASCIMENTO</text>
              <text x="206" y="270" font-size="20" font-weight="bold" fill="#14313d">%s</text>
              <text x="320" y="352" font-size="22" font-weight="bold" fill="#b3261e" fill-opacity="0.55" text-anchor="middle">DOCUMENTO FICTÍCIO, SEM VALOR LEGAL</text>
              <text x="320" y="378" font-size="11" fill="#55706a" text-anchor="middle">Gerado para o Hack Inova Health AI. Dados sintéticos.</text>
            </svg>
            """.formatted(valor(k.nome()), valor(k.cpf()), valor(k.nascimento()));
    }

    public String svgCarteirinha(Kit k) {
        return """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 400" width="640" height="400" font-family="Arial, Helvetica, sans-serif">
              <rect width="640" height="400" rx="18" fill="#14313d"/>
              <circle cx="560" cy="80" r="120" fill="#1d4757"/>
              <text x="28" y="48" font-size="20" font-weight="bold" fill="#ffffff">CARTEIRINHA DO CONVÊNIO</text>
              <text x="28" y="104" font-size="11" fill="#9cc3cf">CONVÊNIO</text>
              <text x="28" y="130" font-size="20" font-weight="bold" fill="#ffffff">%s</text>
              <text x="28" y="172" font-size="11" fill="#9cc3cf">NOME</text>
              <text x="28" y="198" font-size="20" font-weight="bold" fill="#ffffff">%s</text>
              <text x="28" y="240" font-size="11" fill="#9cc3cf">NÚMERO DA CARTEIRINHA</text>
              <text x="28" y="266" font-size="20" font-weight="bold" fill="#ffffff">%s</text>
              <text x="400" y="240" font-size="11" fill="#9cc3cf">VALIDADE</text>
              <text x="400" y="266" font-size="20" font-weight="bold" fill="#ffffff">%s</text>
              <text x="320" y="340" font-size="22" font-weight="bold" fill="#c7d9ff" fill-opacity="0.85" text-anchor="middle">CARTEIRINHA FICTÍCIA, SEM VALOR</text>
              <text x="320" y="372" font-size="11" fill="#9cc3cf" text-anchor="middle">Gerado para o Hack Inova Health AI. Dados sintéticos.</text>
            </svg>
            """.formatted(valor(k.convenio()), valor(k.nome()), valor(k.numeroCarteirinha()), valor(k.validade()));
    }

    private static String valor(String s) {
        if (s == null) return ILEGIVEL;
        return s.toUpperCase().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
