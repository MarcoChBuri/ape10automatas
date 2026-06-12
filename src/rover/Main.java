package rover;

import java.io.FileReader;
import java.io.Reader;

public class Main {
    public static void main(String[] args) {
        try {
            // Usamos FileReader que es un tipo de Reader
            Reader reader = new FileReader("prueba.txt");
            Lexer lexer = new Lexer(reader); // Ahora sí coinciden los tipos
            Parser parser = new Parser(lexer);
            
            parser.parse();
            System.out.println("¡Análisis completado con éxito!");
        } catch (Exception e) {
            System.out.println("Error al analizar: " + e.getMessage());
            e.printStackTrace();
        }
    }
}