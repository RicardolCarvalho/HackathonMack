package br.com.recepcaosemfila;

import br.com.recepcaosemfila.dados.BaseSintetica;
import br.com.recepcaosemfila.dados.ContasSinteticas;
import br.com.recepcaosemfila.dados.KitsFicticios;
import br.com.recepcaosemfila.servico.CheckInServico;
import br.com.recepcaosemfila.servico.Conferencia;
import br.com.recepcaosemfila.servico.LeitorDocumento;
import br.com.recepcaosemfila.servico.LeitorSvgSimulado;
import br.com.recepcaosemfila.web.Servidor;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Recepção Sem Fila: PoC de check-in assistido (Hack Inova Health AI, desafio 1).
 * Execução: java -jar recepcao-sem-fila.jar [porta]   (padrão 8080, ou variável PORT)
 */
public final class App {
    public static void main(String[] args) throws Exception {
        int porta = args.length > 0 ? Integer.parseInt(args[0])
                : Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        Clock relogio = Clock.systemDefaultZone();
        LocalDate hoje = LocalDate.now(relogio);

        BaseSintetica base = new BaseSintetica(hoje);
        KitsFicticios kits = new KitsFicticios(hoje);
        LeitorDocumento leitor = new LeitorSvgSimulado(); // ponto de troca para um OCR real
        ContasSinteticas contas = new ContasSinteticas(base); // login simulado do portal do paciente
        CheckInServico servico = new CheckInServico(relogio, leitor, new Conferencia(base), kits, contas);

        new Servidor(servico, kits, contas).iniciar(porta);
        System.out.println("Recepcao Sem Fila no ar (dados 100% sinteticos).");
        System.out.println("  Paciente:    http://localhost:" + porta + "/");
        System.out.println("  Atendimento: http://localhost:" + porta + "/atendimento");
    }
}
