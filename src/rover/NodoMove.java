package rover;

/**
 * Nodo AST que representa una instrucción MOVE.
 * Ejemplo: MOVE FORWARD 50 METERS
 */
public class NodoMove extends NodoInstruccion {

    private final String direccion;
    private final int metros;

    public NodoMove(String direccion, int metros) {
        this.direccion = direccion;
        this.metros    = metros;
    }

    public String getDireccion() { return direccion; }
    public int    getMetros()    { return metros; }

    @Override
    public String toJson() {
        return "{\"tipo\":\"Move\","
             + "\"direccion\":\"" + direccion + "\","
             + "\"metros\":" + metros
             + "}";
    }
}
