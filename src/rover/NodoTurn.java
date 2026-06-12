package rover;

/**
 * Nodo AST que representa una instrucción TURN.
 * Ejemplo: TURN LEFT 90 DEGREES
 */
public class NodoTurn extends NodoInstruccion {

    private final String direccion;
    private final int grados;

    public NodoTurn(String direccion, int grados) {
        this.direccion = direccion;
        this.grados    = grados;
    }

    public String getDireccion() { return direccion; }
    public int    getGrados()    { return grados; }

    @Override
    public String toJson() {
        return "{\"tipo\":\"Turn\","
             + "\"direccion\":\"" + direccion + "\","
             + "\"grados\":" + grados
             + "}";
    }
}
