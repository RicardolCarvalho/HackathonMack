package br.com.recepcaosemfila.dominio;

public enum TipoDocumento {
    IDENTIDADE, CARTEIRINHA;

    /** Documento que o paciente pode reenviar para resolver a falha desta verificação; null quando outro documento não resolve. */
    public static TipoDocumento daVerificacao(String codigo) {
        return switch (codigo) {
            case "LEITURA_IDENTIDADE", "TITULAR_CONTA", "CADASTRO_LOCALIZADO", "NOME", "NASCIMENTO" -> IDENTIDADE;
            case "LEITURA_CARTEIRINHA", "CARTEIRINHA" -> CARTEIRINHA;
            default -> null; // agendamento e guia dependem do hospital e do convênio, não de um documento novo
        };
    }
}
