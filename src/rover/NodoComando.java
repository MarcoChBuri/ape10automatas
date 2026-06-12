package rover;

/**
 * Nodo AST que representa un comando completo (instrucción + punto y coma).
 * Parte del patrón Composite para el AST.
 */
public class NodoComando {

    private final NodoInstruccion instruccion;

    public NodoComando(NodoInstruccion instruccion) {
        this.instruccion = instruccion;
    }

    public NodoInstruccion getInstruccion() { return instruccion; }

    public String toJson() {
        return "{\"tipo\":\"Comando\","
             + "\"instruccion\":" + instruccion.toJson()
             + "}";
    }
}
