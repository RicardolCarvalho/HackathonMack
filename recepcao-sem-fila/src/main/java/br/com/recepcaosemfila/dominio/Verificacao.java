package br.com.recepcaosemfila.dominio;

import java.util.LinkedHashMap;
import java.util.Map;

/** Uma conferência feita pelo sistema, sempre com a fonte de onde veio cada lado da comparação. */
public record Verificacao(String codigo, String titulo, ResultadoVerificacao resultado,
                          String valorLido, String valorCadastro, String fonte, String detalhe) {

    public boolean falhou() {
        return resultado == ResultadoVerificacao.DIVERGENTE || resultado == ResultadoVerificacao.AUSENTE;
    }

    /** O paciente não recebe o valor que está no cadastro: se o documento não for dele, isso vazaria dado de outra pessoa. */
    public Map<String, Object> paraMapa(boolean visaoAtendente) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("codigo", codigo);
        m.put("titulo", titulo);
        m.put("resultado", resultado);
        m.put("valorLido", valorLido);
        if (visaoAtendente) m.put("valorCadastro", valorCadastro);
        m.put("fonte", fonte);
        m.put("detalhe", detalhe);
        return m;
    }
}
