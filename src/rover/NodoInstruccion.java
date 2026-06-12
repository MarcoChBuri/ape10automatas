package rover;

/**
 * Clase base abstracta para todos los tipos de instrucciones del Rover.
 * Parte del patrón Composite para el AST.
 */
public abstract class NodoInstruccion {

    /** Devuelve la representación JSON de este nodo instrucción. */
    public abstract String toJson();
}
