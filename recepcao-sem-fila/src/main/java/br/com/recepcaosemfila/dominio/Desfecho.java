package br.com.recepcaosemfila.dominio;

/** Primeiro desfecho do check-in: é nele que o tempo é medido e a decisão do sistema é avaliada. */
public enum Desfecho {
    CONCLUIDO_AUTOMATICO,       // passou em todas as conferências sem intervenção humana
    ESCALADO_REMOTO,            // o sistema encontrou falha e encaminhou para uma pessoa
    APOIO_PRESENCIAL_A_PEDIDO   // o paciente pediu ajuda antes de o sistema decidir
}
