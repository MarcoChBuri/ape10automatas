package rover;

/**
 * Nodo AST que representa una instrucción TAKE SAMPLE FROM.
 * Ejemplo: TAKE SAMPLE FROM SOIL
 */
public class NodoTakeSample extends NodoInstruccion {

    private final String terreno;

    public NodoTakeSample(String terreno) {
        this.terreno = terreno;
    }

    public String getTerreno() { return terreno; }

    @Override
    public String toJson() {
        return "{\"tipo\":\"TakeSample\","
             + "\"terreno\":\"" + terreno + "\""
             + "}";
    }
}
