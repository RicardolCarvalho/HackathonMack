package br.com.recepcaosemfila.servico;

import br.com.recepcaosemfila.dominio.DocumentoLido;
import br.com.recepcaosemfila.dominio.TipoDocumento;

/**
 * Contrato da leitura de documento (OCR). Na PoC a implementação é simulada.
 * Para usar um OCR real (por exemplo OCI Document Understanding ou Vision), crie outra
 * implementação desta interface que devolva os mesmos campos e troque a instância em App.java.
 */
public interface LeitorDocumento {
    String NOME = "nome";
    String CPF = "cpf";
    String NASCIMENTO = "nascimento";
    String CONVENIO = "convenio";
    String NUMERO_CARTEIRINHA = "numeroCarteirinha";
    String VALIDADE = "validade";

    DocumentoLido ler(String conteudo, TipoDocumento tipo);
}
