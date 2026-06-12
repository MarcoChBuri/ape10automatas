package rover.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;

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

        public AnalysisResult(String status, String message, Object ast) {
            this.status  = status;
            this.message = message;
            this.ast     = ast;
        }

        public String getStatus()  { return status; }
        public String getMessage() { return message; }
        public Object getAst()     { return ast; }
    }

    // ─── ClassLoader ──────────────────────────────────────────────────────

    private URLClassLoader buildRoverClassLoader() throws Exception {
        Path webappDir   = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path projectRoot = webappDir.getParent();
        URL roverSrc     = toDirectoryUrl(projectRoot.resolve("src"));
        URL cupRuntime   = toDirectoryUrl(projectRoot.resolve("lib/java-cup-runtime-11b.jar"));
        return new URLClassLoader(new URL[]{ roverSrc, cupRuntime },
                                  this.getClass().getClassLoader());
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
                    "⚠ El área de comandos está vacía.\nEscribe al menos un comando.", null));
        }

        try (URLClassLoader cl = buildRoverClassLoader()) {

            Class<?> lexerClass  = cl.loadClass("rover.Lexer");
            Class<?> parserClass = cl.loadClass("rover.Parser");

            Constructor<?> lexerCtor = findConstructor(lexerClass, 1);
            Object lexer = lexerCtor.newInstance(new StringReader(code));

            Constructor<?> parserCtor = findConstructor(parserClass, 1);
            Object parser = parserCtor.newInstance(lexer);

            Method parseMeth = parserClass.getMethod("parse");
            Object symbolResult = parseMeth.invoke(parser);

            // ─── Extraer AST del Symbol.value ────────────────────────────
            Object ast = null;
            try {
                java.lang.reflect.Field valueField =
                    symbolResult.getClass().getField("value");
                Object nodoPrograma = valueField.get(symbolResult);

                if (nodoPrograma != null) {
                    Method toJsonMeth = nodoPrograma.getClass().getMethod("toJson");
                    String jsonStr = (String) toJsonMeth.invoke(nodoPrograma);
                    ast = MAPPER.readValue(jsonStr, Object.class);
                }
            } catch (Exception astEx) {
                System.err.println("[WARN] No se pudo extraer el AST: " + astEx.getMessage());
            }

            return ResponseEntity.ok(new AnalysisResult("OK", buildSuccessReport(code), ast));

        } catch (java.lang.reflect.InvocationTargetException ite) {
            Throwable cause = ite.getCause() != null ? ite.getCause() : ite;
            return ResponseEntity.ok(new AnalysisResult("ERROR", buildErrorReport(cause), null));
        } catch (Exception e) {
            return ResponseEntity.ok(new AnalysisResult("ERROR", buildErrorReport(e), null));
        }
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
        StringBuilder sb = new StringBuilder();
        sb.append("✗ Error durante el análisis.\n");
        sb.append("─".repeat(50)).append("\n");
        String msg = e.getMessage();
        if (msg != null && !msg.isBlank()) {
            sb.append("Detalle: ").append(msg).append("\n");
        } else {
            sb.append("Tipo: ").append(e.getClass().getSimpleName()).append("\n");
        }
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String[] stackLines = sw.toString().split("\n");
        sb.append("\nTrazado del error:\n");
        int limit = Math.min(stackLines.length, 6);
        for (int i = 0; i < limit; i++) {
            sb.append("  ").append(stackLines[i].trim()).append("\n");
        }
        return sb.toString();
    }
}
