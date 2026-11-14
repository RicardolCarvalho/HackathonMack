package br.com.recepcaosemfila.dominio;

import java.util.List;
import java.util.Map;

/** Resultado da leitura de um documento: campos encontrados, campos ausentes e a fonte da leitura. */
public record DocumentoLido(TipoDocumento tipo, Map<String, String> campos, List<String> ausentes, String fonte) {
    public String campo(String nome) { return campos.get(nome); }
}
