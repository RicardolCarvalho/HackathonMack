package br.com.recepcaosemfila.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON mínimo (escrita e leitura) para a PoC rodar só com o JDK, sem bibliotecas externas. */
public final class Json {
    private Json() {}

    public static String texto(Object valor) {
        StringBuilder sb = new StringBuilder();
        escrever(valor, sb);
        return sb.toString();
    }

    private static void escrever(Object v, StringBuilder sb) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map<?, ?> mapa) {
            sb.append('{');
            boolean primeiro = true;
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                if (!primeiro) sb.append(',');
                primeiro = false;
                string(String.valueOf(e.getKey()), sb);
                sb.append(':');
                escrever(e.getValue(), sb);
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> lista) {
            sb.append('[');
            boolean primeiro = true;
            for (Object item : lista) {
                if (!primeiro) sb.append(',');
                primeiro = false;
                escrever(item, sb);
            }
            sb.append(']');
        } else {
            string(v.toString(), sb); // String, enum, datas
        }
    }

    private static void string(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    /** Lê um JSON e devolve Map, List, String, Double, Boolean ou null. */
    public static Object ler(String texto) {
        Leitor l = new Leitor(texto);
        Object v = l.valor();
        l.espacos();
        if (l.pos < texto.length()) throw new IllegalArgumentException("JSON com conteúdo sobrando");
        return v;
    }

    private static final class Leitor {
        final String s;
        int pos = 0;

        Leitor(String s) { this.s = s; }

        void espacos() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        char atual() {
            if (pos >= s.length()) throw new IllegalArgumentException("JSON incompleto");
            return s.charAt(pos);
        }

        Object valor() {
            espacos();
            char c = atual();
            if (c == '{') return objeto();
            if (c == '[') return lista();
            if (c == '"') return string();
            if (s.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (s.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            if (s.startsWith("null", pos)) { pos += 4; return null; }
            return numero();
        }

        Map<String, Object> objeto() {
            Map<String, Object> m = new LinkedHashMap<>();
            pos++;
            espacos();
            if (atual() == '}') { pos++; return m; }
            while (true) {
                espacos();
                String chave = string();
                espacos();
                if (atual() != ':') throw new IllegalArgumentException("JSON inválido: esperado ':'");
                pos++;
                m.put(chave, valor());
                espacos();
                char c = atual();
                pos++;
                if (c == '}') return m;
                if (c != ',') throw new IllegalArgumentException("JSON inválido: esperado ',' ou '}'");
            }
        }

        List<Object> lista() {
            List<Object> l = new ArrayList<>();
            pos++;
            espacos();
            if (atual() == ']') { pos++; return l; }
            while (true) {
                l.add(valor());
                espacos();
                char c = atual();
                pos++;
                if (c == ']') return l;
                if (c != ',') throw new IllegalArgumentException("JSON inválido: esperado ',' ou ']'");
            }
        }

        String string() {
            if (atual() != '"') throw new IllegalArgumentException("JSON inválido: esperado texto");
            pos++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = atual();
                pos++;
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                char e = atual();
                pos++;
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (pos + 4 > s.length()) throw new IllegalArgumentException("JSON incompleto");
                        sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> sb.append(e); // aspas, barra, barra invertida
                }
            }
        }

        Double numero() {
            int inicio = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) pos++;
            if (inicio == pos) throw new IllegalArgumentException("JSON inválido na posição " + pos);
            return Double.valueOf(s.substring(inicio, pos));
        }
    }
}
