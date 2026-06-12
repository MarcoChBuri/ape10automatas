package rover;

import java.util.ArrayList;
import java.util.List;

/**
 * Nodo raíz del AST. Contiene la lista completa de comandos del programa.
 * Parte del patrón Composite para el AST.
 */
public class NodoPrograma {

    private final List<NodoComando> comandos;

    public NodoPrograma() {
        this.comandos = new ArrayList<>();
    }

    /** Agrega un comando al final de la lista. */
    public void agregar(NodoComando comando) {
        comandos.add(comando);
    }

    /**
     * Agrega un comando al inicio de la lista.
     * Útil para la regla: comando programa → el programa ya contiene el resto.
     */
    public void agregarAlFrente(NodoComando comando) {
        comandos.add(0, comando);
    }

    public List<NodoComando> getComandos() { return comandos; }

    /** Serializa el árbol completo a JSON sin dependencias externas. */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"tipo\":\"Programa\",\"comandos\":[");
        for (int i = 0; i < comandos.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(comandos.get(i).toJson());
        }
        sb.append("]}");
        return sb.toString();
    }
}
