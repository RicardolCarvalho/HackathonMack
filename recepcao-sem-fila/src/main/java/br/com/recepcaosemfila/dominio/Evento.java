package br.com.recepcaosemfila.dominio;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Linha do histórico do check-in: quem fez o quê e quando. */
public record Evento(Instant quando, String ator, String descricao) {
    public Map<String, Object> paraMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("quando", quando);
        m.put("ator", ator);
        m.put("descricao", descricao);
        return m;
    }
}
