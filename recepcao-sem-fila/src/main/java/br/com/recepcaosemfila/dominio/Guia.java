package br.com.recepcaosemfila.dominio;

import java.time.LocalDate;

/** Guia sintética do convênio. status: AUTORIZADA ou PENDENTE. */
public record Guia(String numero, String agendamentoId, String numeroCarteirinha,
                   String procedimento, String status, LocalDate validade) {}
