package br.com.recepcaosemfila.servico;

import br.com.recepcaosemfila.dominio.DocumentoLido;
import br.com.recepcaosemfila.dominio.TipoDocumento;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OCR simulado: extrai os textos de uma imagem SVG sintética e monta os campos a partir dos rótulos.
 * Não reconhece caracteres em foto. Trechos ilegíveis (blocos) ou rótulos sem valor viram campo ausente.
 */
public final class LeitorSvgSimulado implements LeitorDocumento {
    public static final String FONTE = "OCR simulado (extração de texto do documento SVG fictício)";

    private static final Pattern TEXTO = Pattern.compile("<text\\b[^>]*>(.*?)</text>", Pattern.DOTALL);

    private static final Map<String, String> ROTULOS = Map.of(
            "NOME", NOME,
            "CPF", CPF,
            "DATA DE NASCIMENTO", NASCIMENTO,
            "CONVENIO", CONVENIO,
            "NUMERO DA CARTEIRINHA", NUMERO_CARTEIRINHA,
            "VALIDADE", VALIDADE);

    @Override
    public DocumentoLido ler(String conteudo, TipoDocumento tipo) {
        Map<String, String> campos = new LinkedHashMap<>();
        if (conteudo != null && conteudo.contains("<svg")) {
            List<String> textos = new ArrayList<>();
            Matcher m = TEXTO.matcher(conteudo);
            while (m.find()) textos.add(limpar(m.group(1)));
            for (int i = 0; i + 1 < textos.size(); i++) {
                String campo = ROTULOS.get(semAcento(textos.get(i)));
                if (campo == null || campos.containsKey(campo)) continue;
                String valor = textos.get(i + 1);
                if (legivel(valor) && !ROTULOS.containsKey(semAcento(valor))) campos.put(campo, valor);
            }
        }
        List<String> obrigatorios = tipo == TipoDocumento.IDENTIDADE
                ? List.of(NOME, CPF, NASCIMENTO)
                : List.of(CONVENIO, NUMERO_CARTEIRINHA, VALIDADE);
        List<String> ausentes = new ArrayList<>();
        for (String c : obrigatorios) if (!campos.containsKey(c)) ausentes.add(c);
        return new DocumentoLido(tipo, campos, ausentes, FONTE);
    }

    private static boolean legivel(String valor) {
        return !valor.isBlank() && !valor.contains("█");
    }

    private static String limpar(String bruto) {
        return bruto.replaceAll("<[^>]+>", "")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&")
                .replaceAll("\\s+", " ").trim();
    }

    public static String semAcento(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase().replaceAll("\\s+", " ").trim();
    }
}
