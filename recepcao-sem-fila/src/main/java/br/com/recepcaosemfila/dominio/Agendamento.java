package br.com.recepcaosemfila.dominio;

import java.time.LocalDate;

/** Agendamento administrativo sintético (não carrega informação clínica). */
public record Agendamento(String id, String pacienteId, LocalDate data, String horario,
                          String servico, String proximaEtapa) {}
