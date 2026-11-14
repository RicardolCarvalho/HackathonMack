package br.com.recepcaosemfila.dominio;

import java.time.LocalDate;

/** Agendamento administrativo sintético (não carrega informação clínica). */
public record Agendamento(String id, String pacienteId, LocalDate data, String horario,
                          String servico, String proximaEtapa, String hospitalId, String tipo) {
    public Agendamento(String id, String pacienteId, LocalDate data, String horario, String servico, String proximaEtapa, String hospitalId) {
        this(id, pacienteId, data, horario, servico, proximaEtapa, hospitalId, "AGENDADO");
    }
    public Agendamento(String id, String pacienteId, LocalDate data, String horario, String servico, String proximaEtapa) {
        this(id, pacienteId, data, horario, servico, proximaEtapa, "alberto");
    }
}
