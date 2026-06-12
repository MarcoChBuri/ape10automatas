package rover.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rover.Lexer;
import rover.Parser;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * REST controller que expone el endpoint de análisis del Rover.
 * Recibe el código fuente en JSON, lo pasa al analizador léxico/sintáctico
 * (generado por JFlex + CUP) y devuelve el resultado del análisis.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RoverController {

    // ─────────────────────────────────────────────────────────────────────
    // DTOs internos (Request / Response)
    // ─────────────────────────────────────────────────────────────────────

    /** Cuerpo de la petición POST /api/analyze */
    public static class AnalysisRequest {
        private String code;
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }

    /** Cuerpo de la respuesta */
    public static class AnalysisResult {
        private final String status;   // "OK" | "ERROR"
        private final String message;  // Detalle del resultado o del error

        public AnalysisResult(String status, String message) {
            this.status = status;
            this.message = message;
        }

        public String getStatus()  { return status; }
        public String getMessage() { return message; }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Endpoint principal
    // ─────────────────────────────────────────────────────────────────────

    /**
     * POST /api/analyze
     *
     * Ejemplo de cuerpo JSON:
     * {
     *   "code": "MOVE FORWARD 50 METERS;\nTURN LEFT 90 DEGREES;"
     * }
     *
     * Respuesta exitosa:
     * { "status": "OK", "message": "✓ Análisis completado exitosamente.\n\nComandos analizados:\n  1. MOVE FORWARD 50 METERS;\n  ..." }
     *
     * Respuesta con error:
     * { "status": "ERROR", "message": "Error sintáctico en línea 2: ..." }
     */
    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResult> analyze(@RequestBody AnalysisRequest req) {

        String code = req.getCode();

        // Validación básica de entrada vacía
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new AnalysisResult("ERROR", "⚠ No se recibió ningún comando para analizar."));
        }

        try {
            // ── Instanciar el Lexer (JFlex) pasando el texto como StringReader ──
            StringReader reader = new StringReader(code);
            Lexer  lexer  = new Lexer(reader);

            // ── Instanciar el Parser (CUP) y ejecutar el análisis ──
            Parser parser = new Parser(lexer);
            parser.parse();

            // ── Si llegamos aquí, el análisis fue exitoso ──
            String report = buildSuccessReport(code);
            return ResponseEntity.ok(new AnalysisResult("OK", report));

        } catch (Exception e) {
            // Captura errores léxicos (símbolo no reconocido) y sintácticos (producción inválida)
            String errorDetail = buildErrorReport(e);
            return ResponseEntity.ok(new AnalysisResult("ERROR", errorDetail));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers de formateo de reportes
    // ─────────────────────────────────────────────────────────────────────

    /** Construye un reporte detallado de éxito listando cada comando reconocido. */
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

        sb.append("\n─".repeat(1)).append("─".repeat(49)).append("\n");
        sb.append("Total de instrucciones válidas: ").append(count - 1);
        return sb.toString();
    }

    /** Extrae el mensaje de error más útil de la excepción capturada. */
    private String buildErrorReport(Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append("✗ Error durante el análisis.\n");
        sb.append("─".repeat(50)).append("\n");

        // Mensaje principal de la excepción
        String msg = e.getMessage();
        if (msg != null && !msg.isBlank()) {
            sb.append("Detalle: ").append(msg).append("\n");
        } else {
            sb.append("Detalle: ").append(e.getClass().getSimpleName()).append("\n");
        }

        // Stack trace resumido (primeras 6 líneas) para mayor contexto
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

    // ─────────────────────────────────────────────────────────────────────
    // Endpoint de salud (útil para verificar que el servidor está activo)
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Rover Analyzer Web — OK");
    }
}
