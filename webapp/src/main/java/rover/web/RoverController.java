package rover.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RoverController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ─── DTOs ─────────────────────────────────────────────────────────────

    public static class AnalysisRequest {
        private String code;
        public String getCode()            { return code; }
        public void   setCode(String code) { this.code = code; }
    }

    public static class AnalysisResult {
        private final String status;
        private final String message;
        private final Object ast;
        private final List<Map<String,String>> tokens;

        public AnalysisResult(String status, String message, Object ast, List<Map<String,String>> tokens) {
            this.status  = status;
            this.message = message;
            this.ast     = ast;
            this.tokens  = tokens;
        }

        public String getStatus()  { return status; }
        public String getMessage() { return message; }
        public Object getAst()     { return ast; }
        public List<Map<String,String>> getTokens() { return tokens; }
    }

    // ─── ClassLoader ──────────────────────────────────────────────────────

    private URLClassLoader buildRoverClassLoader() throws Exception {
        Path webappDir   = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path projectRoot = webappDir.getParent();
        URL roverSrc     = toDirectoryUrl(projectRoot.resolve("src"));
        URL cupRuntime   = toDirectoryUrl(projectRoot.resolve("lib/java-cup-runtime-11b.jar"));
        // Usamos null como parent (bootstrap) para aislar completamente las clases del rover
        // y evitar conflictos de módulos entre Spring (named module) y el URLClassLoader (unnamed)
        return new URLClassLoader(new URL[]{ roverSrc, cupRuntime }, null);
    }

    private URL toDirectoryUrl(Path dir) throws Exception {
        String url = dir.toUri().toString();
        if (!url.endsWith("/")) url += "/";
        return new URL(url);
    }

    private Constructor<?> findConstructor(Class<?> clazz, int paramCount) {
        for (Constructor<?> c : clazz.getDeclaredConstructors()) {
            if (c.getParameterCount() == paramCount) {
                c.setAccessible(true);
                return c;
            }
        }
        throw new RuntimeException("No se encontró constructor con " + paramCount
            + " parámetro(s) en " + clazz.getName());
    }

    // ─── POST /api/analyze ────────────────────────────────────────────────

    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResult> analyze(@RequestBody AnalysisRequest req) {

        String code = req.getCode();
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest()
                .body(new AnalysisResult("ERROR",
                    "⚠ El área de comandos está vacía.\nEscribe al menos un comando.", null, null));
        }

        try (URLClassLoader cl = buildRoverClassLoader()) {

            // ─── 1. Escanear tokens siempre (para la tabla) ───────────────
            List<Map<String,String>> tokenList = scanTokens(cl, code);

            // ─── 2. Verificar si hay errores léxicos ──────────────────────
            boolean hasLexError = tokenList.stream()
                .anyMatch(t -> "ERROR".equals(t.get("tipo")));

            if (hasLexError) {
                String errLexema = tokenList.stream()
                    .filter(t -> "ERROR".equals(t.get("tipo")))
                    .map(t -> t.get("lexema"))
                    .findFirst().orElse("?");
                return ResponseEntity.ok(new AnalysisResult(
                    "ERROR",
                    "✗ Error léxico: el símbolo \"" + errLexema + "\" no pertenece al lenguaje del Rover.",
                    null, tokenList));
            }

            // ─── 3. Ejecutar el parser para el AST ───────────────────────
            Class<?> lexerClass  = cl.loadClass("rover.Lexer");
            Class<?> parserClass = cl.loadClass("rover.Parser");

            Constructor<?> lexerCtor = findConstructor(lexerClass, 1);
            Object lexer = lexerCtor.newInstance(new StringReader(code));

            Constructor<?> parserCtor = findConstructor(parserClass, 1);
            Object parser = parserCtor.newInstance(lexer);

            Method parseMeth = parserClass.getMethod("parse");
            parseMeth.setAccessible(true);
            Object symbolResult = parseMeth.invoke(parser);

            // ─── 4. Extraer AST del Symbol.value ──────────────────────────
            Object ast = null;
            try {
                Field valueField = symbolResult.getClass().getField("value");
                valueField.setAccessible(true); // Java 9+ module access
                Object nodoPrograma = valueField.get(symbolResult);
                if (nodoPrograma != null) {
                    Method toJsonMeth = nodoPrograma.getClass().getMethod("toJson");
                    toJsonMeth.setAccessible(true);
                    String jsonStr = (String) toJsonMeth.invoke(nodoPrograma);
                    ast = MAPPER.readValue(jsonStr, Object.class);
                }
            } catch (Exception astEx) {
                System.err.println("[WARN] No se pudo extraer el AST: " + astEx.getMessage());
            }

            return ResponseEntity.ok(new AnalysisResult("OK", buildSuccessReport(code), ast, tokenList));

        } catch (java.lang.reflect.InvocationTargetException ite) {
            Throwable cause = ite.getCause() != null ? ite.getCause() : ite;
            List<Map<String,String>> tokenList = tryGetTokens(code);
            return ResponseEntity.ok(new AnalysisResult("ERROR", buildErrorReport(cause), null, tokenList));
        } catch (Exception e) {
            List<Map<String,String>> tokenList = tryGetTokens(code);
            return ResponseEntity.ok(new AnalysisResult("ERROR", buildErrorReport(e), null, tokenList));
        }
    }

    /** Intenta escanear tokens; si falla retorna lista vacía (fallback seguro). */
    private List<Map<String,String>> tryGetTokens(String code) {
        try (URLClassLoader cl = buildRoverClassLoader()) {
            return scanTokens(cl, code);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    /**
     * Corre el lexer de forma independiente y recolecta todos los tokens.
     * Los caracteres no reconocidos se registran con tipo "ERROR".
     */
    private List<Map<String,String>> scanTokens(URLClassLoader cl, String code) throws Exception {
        List<Map<String,String>> list = new ArrayList<>();

        // Nombres de tokens por id (deben coincidir con sym.java generado)
        // Usamos reflexión para leer las constantes de sym
        Class<?> symClass = cl.loadClass("rover.sym");
        Map<Integer, String> idToName = new LinkedHashMap<>();
        for (java.lang.reflect.Field f : symClass.getFields()) {
            if (f.getType() == int.class) {
                f.setAccessible(true);
                idToName.put(f.getInt(null), f.getName());
            }
        }

        Class<?> lexerClass = cl.loadClass("rover.Lexer");
        Constructor<?> lexerCtor = findConstructor(lexerClass, 1);

        // Sustituimos el stderr del lexer para capturar errores léxicos
        java.io.PrintStream originalErr = System.err;
        List<String> lexErrors = new ArrayList<>();
        System.setErr(new java.io.PrintStream(new java.io.OutputStream() {
            private final StringBuilder sb = new StringBuilder();
            @Override public void write(int b) {
                char c = (char) b;
                if (c == '\n') { lexErrors.add(sb.toString()); sb.setLength(0); }
                else sb.append(c);
            }
        }));

        try {
            Object lexer = lexerCtor.newInstance(new StringReader(code));
            Method nextToken = lexerClass.getMethod("next_token");
            nextToken.setAccessible(true);
            Class<?> symRuntimeClass = cl.loadClass("java_cup.runtime.Symbol");
            Field symId    = symRuntimeClass.getField("sym");
            Field symLine  = symRuntimeClass.getField("left");   // left = línea
            Field symCol   = symRuntimeClass.getField("right");  // right = columna
            Field symValue = symRuntimeClass.getField("value");
            // Permitir acceso cross-module (Java 9+ module system)
            symId.setAccessible(true);
            symLine.setAccessible(true);
            symCol.setAccessible(true);
            symValue.setAccessible(true);

            // EOF id
            int eofId = -1;
            try {
                Field eofField = symClass.getField("EOF");
                eofField.setAccessible(true);
                eofId = eofField.getInt(null);
            } catch (Exception ignored) {}

            while (true) {
                Object tok = nextToken.invoke(lexer);
                int id   = symId.getInt(tok);
                int line = symLine.getInt(tok);
                int col  = symCol.getInt(tok);
                Object val = symValue.get(tok);

                if (id == eofId || id == 0) break; // 0 = EOF en CUP por defecto

                String nombre = idToName.getOrDefault(id, "DESCONOCIDO");
                String lexema = val != null ? val.toString() : nombre;

                Map<String,String> entry = new LinkedHashMap<>();
                entry.put("lexema",  lexema);
                entry.put("tipo",    nombre);
                entry.put("linea",   String.valueOf(line));
                entry.put("columna", String.valueOf(col));
                list.add(entry);
            }

            // Agregar errores léxicos capturados desde stderr
            for (String errMsg : lexErrors) {
                // "Error léxico: X" → extraer X
                String bad = errMsg.contains(":") ? errMsg.substring(errMsg.lastIndexOf(':') + 1).trim() : errMsg;
                Map<String,String> errEntry = new LinkedHashMap<>();
                errEntry.put("lexema",  bad);
                errEntry.put("tipo",    "ERROR");
                errEntry.put("linea",   "-");
                errEntry.put("columna", "-");
                list.add(errEntry);
            }
        } finally {
            System.setErr(originalErr);
        }

        return list;
    }

    // ─── GET /api/health ──────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Rover Analyzer Web — OK");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private String buildSuccessReport(String code) {
        StringBuilder sb = new StringBuilder();
        sb.append("✓ Análisis léxico y sintáctico completado exitosamente.\n");
        sb.append("─".repeat(50)).append("\n");
        sb.append("Comandos reconocidos:\n\n");
        String[] lines = code.split(";");
        int count = 1;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                sb.append(String.format("  [%d] %s;\n", count++, trimmed));
            }
        }
        sb.append("\n").append("─".repeat(50)).append("\n");
        sb.append("Total de instrucciones válidas: ").append(count - 1);
        return sb.toString();
    }

    private String buildErrorReport(Throwable e) {
        String msg = e.getMessage();
        // Filtrar mensajes internos de CUP que no son útiles para el usuario
        if (msg != null && msg.contains("Can't recover from previous error")) {
            return "✗ Error sintáctico: la secuencia de comandos no sigue la gramática del Rover.\n" +
                   "Revisa que cada instrucción sea válida y termine con punto y coma (;).";
        }
        if (msg != null && !msg.isBlank()) {
            // Limpiar mensajes típicos de CUP/JFlex
            String clean = msg.replaceAll("(?i)syntax error.*", "Error de sintaxis.").trim();
            return "✗ Error en el análisis: " + clean;
        }
        return "✗ Error inesperado durante el análisis.\nRevisa la sintaxis de tus comandos.";
    }
}
