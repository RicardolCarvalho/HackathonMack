package br.com.recepcaosemfila.servico;

/** Erro de regra de negócio, já com o status HTTP que a API deve devolver. */
public class RegraException extends RuntimeException {
    public final int status;

    public RegraException(int status, String mensagem) {
        super(mensagem);
        this.status = status;
    }
}
