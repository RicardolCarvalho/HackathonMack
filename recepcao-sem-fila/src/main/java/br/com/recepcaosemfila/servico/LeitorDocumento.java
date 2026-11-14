package br.com.recepcaosemfila.servico;

import br.com.recepcaosemfila.dominio.DocumentoLido;
import br.com.recepcaosemfila.dominio.TipoDocumento;

import java.util.List;

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

    /** Campos que precisam ser lidos em cada tipo de documento, na ordem em que aparecem para o paciente. */
    static List<String> obrigatorios(TipoDocumento tipo) {
        return tipo == TipoDocumento.IDENTIDADE
                ? List.of(NOME, CPF, NASCIMENTO)
                : List.of(CONVENIO, NUMERO_CARTEIRINHA, VALIDADE);
    }

    static String rotulo(String campo) {
        return switch (campo) {
            case NOME -> "Nome";
            case CPF -> "CPF";
            case NASCIMENTO -> "Data de nascimento";
            case CONVENIO -> "Convênio";
            case NUMERO_CARTEIRINHA -> "Número da carteirinha";
            case VALIDADE -> "Validade";
            default -> campo;
        };
    }
}
