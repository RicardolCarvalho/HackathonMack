package br.com.recepcaosemfila.dominio;

import java.time.LocalDate;

/** Cadastro sintético de paciente. Nenhum dado aqui é real. */
public record Paciente(String id, String nome, String cpf, LocalDate nascimento,
                       String convenio, String numeroCarteirinha, LocalDate validadeCarteirinha) {}
