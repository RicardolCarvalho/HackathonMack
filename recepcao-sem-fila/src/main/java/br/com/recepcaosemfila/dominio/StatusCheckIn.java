package br.com.recepcaosemfila.dominio;

public enum StatusCheckIn {
    AGUARDANDO_DOCUMENTOS,        // check-in aberto, documentos ainda não enviados
    AGUARDANDO_CONFIRMACAO_GUIA,  // tudo conferiu; paciente precisa confirmar a guia
    PRONTO_PARA_CHEGADA,          // pré-check-in concluído; falta só confirmar a chegada
    ATENDIMENTO_REMOTO,           // algo falhou; uma pessoa do atendimento remoto assume
    APOIO_PRESENCIAL,             // paciente pediu ajuda ou o atendimento remoto encaminhou
    CONCLUIDO                     // passagem registrada
}
