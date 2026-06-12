@echo off
echo ===========================================
echo   Iniciando construccion del Proyecto Rover
echo ===========================================

:: 1. Limpieza de archivos generados previamente
echo Limpiando archivos antiguos...
del src\rover\*.class
del src\rover\Lexer.java
del src\rover\Parser.java
del src\rover\sym.java

:: 2. Generación del Lexer (usando tu ruta de JFlex)
echo Generando Lexer...
::java -jar "E:\jflex\jflex-1.9.1\lib\jflex-full-1.9.1.jar" -d src/rover/ src/rover/Lexer.flex
java -jar "lib\jflex-1.9.1\lib\jflex-full-1.9.1.jar" -d src/rover/ src/rover/Lexer.flex
:: 3. Generación del Parser (usando CUP)
echo Generando Parser...
java -cp "lib/java-cup-11b.jar" java_cup.Main -parser Parser -destdir src/rover/ src/rover/Parser.cup

:: 4. Compilación de todo el proyecto (con compatibilidad Java 8)
echo Compilando codigo fuente...
javac -target 1.8 -source 1.8 -cp "lib/java-cup-runtime-11b.jar;src" src/rover/*.java

echo.
echo ===========================================
echo Proceso finalizado con exito.
echo ===========================================
pause