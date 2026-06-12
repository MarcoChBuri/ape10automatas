package rover.web;

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

/**
 * REST controller que expone el endpoint de análisis del Rover.
 *
 * Carga el Lexer y Parser del rover mediante reflexión dinámica,
 * apuntando a las clases pre-compiladas en src/rover/*.class.
 * Esto evita dependencias de compilación directas con clases generadas.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RoverController {

    // ─── DTOs ─────────────────────────────────────────────────────────────

    public static class AnalysisRequest {
        private String code;
        public String getCode()              { return code; }
        public void   setCode(String code)   { this.code = code; }
    }

    public static class AnalysisResult {
        private final String status;
        private final String message;

        public AnalysisResult(String status, String message) {
            this.status = status;
            this.message = message;
        }

        public String getStatus()  { return status; }
        public String getMessage() { return message; }
    }

    // ─── Classpath del rover (resuelto relativo al JAR/classpath actual) ──

    /**
     * Resuelve la ruta al directorio de clases compiladas del rover.
     * Sube desde webapp/target/classes hasta la raíz del proyecto
     * y luego busca src/rover/ y lib/java-cup-runtime-11b.jar/.
     */
    private URLClassLoader buildRoverClassLoader() throws Exception {
        // Ruta base: directorio donde corre el proceso (webapp/)
        Path webappDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path projectRoot = webappDir.getParent(); // ape10automatas/

        // IMPORTANTE: URLClassLoader requiere trailing "/" para tratar la URL
        // como directorio de clases, no como JAR.
        URL roverSrc   = toDirectoryUrl(projectRoot.resolve("src"));
        URL cupRuntime = toDirectoryUrl(projectRoot.resolve("lib/java-cup-runtime-11b.jar"));

        return new URLClassLoader(
            new URL[]{ roverSrc, cupRuntime },
            this.getClass().getClassLoader()
        );
    }

    /** Convierte una Path de directorio a URL garantizando el trailing slash. */
    private URL toDirectoryUrl(Path dir) throws Exception {
        String url = dir.toUri().toString();
        if (!url.endsWith("/")) url += "/";
        return new URL(url);
    }

    /**
     * Busca el primer constructor declarado con exactamente N parámetros.
     * Evita NoSuchMethodException cuando la firma exacta del tipo no coincide
     * entre classloaders (e.g. clases generadas por JFlex/CUP).
     */
    private Constructor<?> findConstructor(Class<?> clazz, int paramCount) {
        for (Constructor<?> c : clazz.getDeclaredConstructors()) {
            if (c.getParameterCount() == paramCount) {
                c.setAccessible(true);
                return c;
            }
        }
        throw new RuntimeException(
            "No se encontró constructor con " + paramCount + " parámetro(s) en " + clazz.getName()
                + ". Constructores disponibles: " + java.util.Arrays.toString(clazz.getDeclaredConstructors())
        );
    }

    // ─── Endpoint POST /api/analyze ───────────────────────────────────────

    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResult> analyze(@RequestBody AnalysisRequest req) {

        String code = req.getCode();

        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest()
                .body(new AnalysisResult("ERROR",
                    "⚠ El área de comandos está vacía.\nEscribe al menos un comando."));
        }

        try (URLClassLoader cl = buildRoverClassLoader()) {

            // Cargar las clases generadas por JFlex/CUP vía reflexión
            Class<?> lexerClass  = cl.loadClass("rover.Lexer");
            Class<?> parserClass = cl.loadClass("rover.Parser");

            // Instanciar Lexer con el único constructor de 1 parámetro (java.io.Reader)
            Constructor<?> lexerCtor = findConstructor(lexerClass, 1);
            Object lexer = lexerCtor.newInstance(new StringReader(code));

            // Instanciar Parser con el único constructor de 1 parámetro (Scanner/Lexer)
            Constructor<?> parserCtor = findConstructor(parserClass, 1);
            Object parser = parserCtor.newInstance(lexer);
            Method parseMeth = parserClass.getMethod("parse");
            parseMeth.invoke(parser);

            return ResponseEntity.ok(
                new AnalysisResult("OK", buildSuccessReport(code))
            );

        } catch (java.lang.reflect.InvocationTargetException ite) {
            // La excepción real viene envuelta en InvocationTargetException
            Throwable cause = ite.getCause() != null ? ite.getCause() : ite;
            return ResponseEntity.ok(
                new AnalysisResult("ERROR", buildErrorReport(cause))
            );
        } catch (Exception e) {
            return ResponseEntity.ok(
                new AnalysisResult("ERROR", buildErrorReport(e))
            );
        }
    }

    // ─── Endpoint GET /api/health ─────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Rover Analyzer Web — OK");
    }

    // ─── Helpers de formateo ──────────────────────────────────────────────

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
