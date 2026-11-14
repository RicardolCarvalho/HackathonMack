package br.com.recepcaosemfila.dominio;

public enum StatusCheckIn {
    AGUARDANDO_DOCUMENTOS,        // check-in aberto, documentos ainda não enviados
    PRONTO_PARA_CHEGADA,          // tudo conferiu (a guia é do hospital com o convênio); falta só confirmar a chegada
    ATENDIMENTO_REMOTO,           // algo falhou; uma pessoa do atendimento remoto assume
    APOIO_PRESENCIAL,             // paciente pediu ajuda ou o atendimento remoto encaminhou
    CONCLUIDO                     // passagem registrada
}
