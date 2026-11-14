package br.com.recepcaosemfila.dominio;

public enum ResultadoVerificacao {
    OK,            // conferiu
    DIVERGENTE,    // o que foi lido não bate com a base
    AUSENTE,       // faltou dado para conferir
    SIMULADO,      // etapa simulada na PoC (ex.: autenticidade do documento)
    NAO_AVALIADO   // não deu para avaliar porque uma etapa anterior falhou
}
