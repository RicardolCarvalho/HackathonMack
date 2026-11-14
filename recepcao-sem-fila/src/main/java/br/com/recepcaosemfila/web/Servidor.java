package br.com.recepcaosemfila.web;

import br.com.recepcaosemfila.dados.ContasSinteticas;
import br.com.recepcaosemfila.dados.KitsFicticios;
import br.com.recepcaosemfila.dominio.CheckIn;
import br.com.recepcaosemfila.dominio.DocumentoLido;
import br.com.recepcaosemfila.dominio.TipoDocumento;
import br.com.recepcaosemfila.servico.CheckInServico;
import br.com.recepcaosemfila.servico.Conferencia;
import br.com.recepcaosemfila.servico.LeitorDocumento;
import br.com.recepcaosemfila.servico.RegraException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/** Servidor HTTP da PoC, feito com o HttpServer do próprio JDK. Serve a API em /api e as telas em /. */
public final class Servidor {
    private static final int LIMITE_CORPO = 2 * 1024 * 1024;
    private static final Path WEB_DEV = Path.of("src", "main", "resources", "web");

    private final CheckInServico servico;
    private final KitsFicticios kits;
    private final ContasSinteticas contas;
    private HttpServer http;

    public Servidor(CheckInServico servico, KitsFicticios kits, ContasSinteticas contas) {
        this.servico = servico;
        this.kits = kits;
        this.contas = contas;
    }

    public void iniciar(int porta) throws IOException {
        http = HttpServer.create(new InetSocketAddress(porta), 0);
        http.createContext("/", this::atender);
        http.setExecutor(Executors.newFixedThreadPool(8));
        http.start();
    }

    public void parar() {
        if (http != null) http.stop(0);
    }

    private void atender(HttpExchange ex) throws IOException {
        try {
            String caminho = ex.getRequestURI().getPath();
            if (caminho.startsWith("/api/")) {
                api(ex, ex.getRequestMethod(), caminho);
            } else {
                estatico(ex, caminho);
            }
        } catch (RegraException e) {
            json(ex, e.status, Map.of("erro", e.getMessage()));
        } catch (IllegalArgumentException e) {
            json(ex, 400, Map.of("erro", "Requisição inválida: " + e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            json(ex, 500, Map.of("erro", "Erro interno. Veja o log do servidor."));
        } finally {
            ex.close();
        }
    }

    private void api(HttpExchange ex, String metodo, String caminho) throws IOException {
        String[] p = caminho.substring(1).split("/"); // api, recurso, ...
        boolean get = metodo.equals("GET");
        boolean post = metodo.equals("POST");

        // Kits de documentos fictícios
        if (get && p.length == 2 && p[1].equals("kits")) {
            json(ex, 200, kits.lista());
            return;
        }
        if (get && p.length == 4 && p[1].equals("kits")) {
            KitsFicticios.Kit kit = kits.kit(p[2]);
            if (kit == null) throw new RegraException(404, "Kit não encontrado.");
            if (p[3].equals("identidade.svg")) { responder(ex, 200, "image/svg+xml", bytes(kits.svgIdentidade(kit))); return; }
            if (p[3].equals("carteirinha.svg")) { responder(ex, 200, "image/svg+xml", bytes(kits.svgCarteirinha(kit))); return; }
        }

        // Conta do paciente (login simulado)
        if (get && p.length == 2 && p[1].equals("contas")) {
            List<Map<String, Object>> l = new ArrayList<>();
            for (ContasSinteticas.Conta c : contas.todas()) {
                KitsFicticios.Kit kit = kits.kit(c.kitId());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("email", c.email());
                m.put("nome", c.paciente().nome());
                m.put("cenario", kit == null ? null : kit.resumo());
                m.put("esperado", kit == null ? null : kit.esperado());
                l.add(m);
            }
            json(ex, 200, l);
            return;
        }
        if (post && p.length == 3 && p[1].equals("contas") && p[2].equals("entrar")) {
            Map<String, Object> corpo = corpo(ex);
            ContasSinteticas.Conta c = servico.entrar(texto(corpo, "email"), texto(corpo, "senha"));
            json(ex, 200, servico.resumoConta(c.id()));
            return;
        }
        if (get && p.length == 3 && p[1].equals("contas")) {
            json(ex, 200, servico.resumoConta(p[2]));
            return;
        }
        if (post && p.length == 4 && p[1].equals("contas") && p[3].equals("horario")) {
            json(ex, 200, servico.remarcar(p[2], texto(corpo(ex), "horario")));
            return;
        }

        // Jornada do paciente
        if (post && p.length == 2 && p[1].equals("checkins")) {
            Map<String, Object> corpo = corpo(ex);
            json(ex, 201, servico.iniciar(texto(corpo, "contaId")).paraMapa(false));
            return;
        }
        if (p.length >= 3 && p[1].equals("checkins")) {
            String id = p[2];
            if (get && p.length == 3) { json(ex, 200, servico.buscar(id).paraMapa(false)); return; }
            if (post && p.length == 4 && p[3].equals("leitura")) {
                Map<String, Object> corpo = corpo(ex);
                TipoDocumento tipo = TipoDocumento.valueOf(String.valueOf(corpo.get("tipo")));
                json(ex, 200, leitura(servico.lerDocumento(id, tipo, texto(corpo, "svg"))));
                return;
            }
            if (post && p.length == 4) {
                Map<String, Object> corpo = corpo(ex);
                CheckIn c = switch (p[3]) {
                    case "documentos" -> servico.enviarDocumentos(id, texto(corpo, "kitId"),
                            texto(corpo, "identidadeSvg"), texto(corpo, "carteirinhaSvg"));
                    case "reenvio" -> servico.reenviar(id, texto(corpo, "identidadeSvg"), texto(corpo, "carteirinhaSvg"));
                    case "confirmar-guia" -> servico.confirmarGuia(id);
                    case "chegada" -> servico.registrarChegada(id);
                    case "ajuda" -> servico.pedirAjuda(id, texto(corpo, "motivo"));
                    default -> throw new RegraException(404, "Ação desconhecida.");
                };
                json(ex, 200, c.paraMapa(false));
                return;
            }
        }

        // Painel de atendimento
        if (get && p.length == 3 && p[1].equals("atendimento") && p[2].equals("casos")) {
            List<Map<String, Object>> casos = new ArrayList<>();
            for (CheckIn c : servico.listar()) casos.add(c.paraMapa(true));
            json(ex, 200, casos);
            return;
        }
        if (post && p.length == 5 && p[1].equals("atendimento") && p[2].equals("casos")) {
            Map<String, Object> corpo = corpo(ex);
            String atendente = texto(corpo, "atendente");
            String obs = texto(corpo, "observacao");
            CheckIn c = switch (p[4]) {
                case "resolver" -> servico.resolverRemoto(p[3], atendente, obs);
                case "encaminhar-presencial" -> servico.encaminharPresencial(p[3], atendente, obs);
                case "concluir-presencial" -> servico.concluirPresencial(p[3], atendente, obs);
                default -> throw new RegraException(404, "Ação desconhecida.");
            };
            json(ex, 200, c.paraMapa(true));
            return;
        }

        // Métricas e reinício da demo
        if (get && p.length == 2 && p[1].equals("metricas")) {
            json(ex, 200, servico.metricas());
            return;
        }
        if (post && p.length == 3 && p[1].equals("demo") && p[2].equals("reiniciar")) {
            servico.reiniciar();
            json(ex, 200, Map.of("ok", true));
            return;
        }

        throw new RegraException(404, "Rota não encontrada: " + metodo + " " + caminho);
    }

    /** O que o leitor extraiu de um documento, campo a campo, na ordem em que o paciente confere. */
    private static Map<String, Object> leitura(CheckInServico.Leitura l) {
        DocumentoLido d = l.documento();
        List<Map<String, Object>> campos = new ArrayList<>();
        for (String campo : LeitorDocumento.obrigatorios(d.tipo())) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("campo", campo);
            c.put("rotulo", LeitorDocumento.rotulo(campo));
            c.put("valor", d.campo(campo));
            c.put("alerta", l.alertas().stream().filter(a -> a.campo().equals(campo)).map(Conferencia.Alerta::texto)
                    .findFirst().orElse(null));
            campos.add(c);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo", d.tipo());
        m.put("campos", campos);
        m.put("completo", d.ausentes().isEmpty());
        m.put("alertas", l.alertas().stream().map(Conferencia.Alerta::texto).toList());
        m.put("fonte", d.fonte());
        return m;
    }

    // ---------- Arquivos das telas ----------

    private void estatico(HttpExchange ex, String caminho) throws IOException {
        String arquivo = switch (caminho) {
            case "/" -> "index.html";
            case "/atendimento" -> "atendimento.html";
            default -> caminho.substring(1);
        };
        if (arquivo.contains("..") || arquivo.contains("\\") || arquivo.startsWith("/")) {
            throw new RegraException(404, "Arquivo não encontrado.");
        }
        byte[] conteudo = null;
        Path dev = WEB_DEV.resolve(arquivo);
        if (Files.isRegularFile(dev)) {
            conteudo = Files.readAllBytes(dev); // em desenvolvimento, edita a tela e recarrega sem recompilar
        } else {
            try (InputStream in = Servidor.class.getResourceAsStream("/web/" + arquivo)) {
                if (in != null) conteudo = in.readAllBytes();
            }
        }
        if (conteudo == null) throw new RegraException(404, "Arquivo não encontrado.");
        responder(ex, 200, tipo(arquivo), conteudo);
    }

    private static String tipo(String arquivo) {
        if (arquivo.endsWith(".html")) return "text/html; charset=utf-8";
        if (arquivo.endsWith(".css")) return "text/css; charset=utf-8";
        if (arquivo.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (arquivo.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    // ---------- Apoio HTTP ----------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> corpo(HttpExchange ex) throws IOException {
        byte[] dados = ex.getRequestBody().readNBytes(LIMITE_CORPO + 1);
        if (dados.length > LIMITE_CORPO) throw new RegraException(413, "Arquivo grande demais para a PoC (limite de 2 MB).");
        String texto = new String(dados, StandardCharsets.UTF_8).trim();
        if (texto.isEmpty()) return new LinkedHashMap<>();
        Object v = Json.ler(texto);
        if (!(v instanceof Map)) throw new IllegalArgumentException("o corpo deve ser um objeto JSON");
        return (Map<String, Object>) v;
    }

    private static String texto(Map<String, Object> corpo, String chave) {
        Object v = corpo.get(chave);
        return v == null ? null : v.toString();
    }

    private static void json(HttpExchange ex, int status, Object valor) throws IOException {
        responder(ex, status, "application/json; charset=utf-8", bytes(Json.texto(valor)));
    }

    private static void responder(HttpExchange ex, int status, String tipo, byte[] conteudo) throws IOException {
        ex.getResponseHeaders().set("Content-Type", tipo);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        if (ex.getRequestMethod().equals("HEAD")) {
            ex.sendResponseHeaders(status, -1); // HEAD: só cabeçalhos, sem corpo
            return;
        }
        ex.sendResponseHeaders(status, conteudo.length);
        ex.getResponseBody().write(conteudo);
    }

    private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }
}
