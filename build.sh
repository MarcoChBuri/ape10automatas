#!/bin/bash
# ============================================================
#  build.sh — Script de construcción del Proyecto Rover (Linux)
# ============================================================
set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$PROJECT_DIR/src/rover"
LIB_DIR="$PROJECT_DIR/lib"

JFLEX_CP="$LIB_DIR/jflex-1.9.1/target/classes"
CUP_JAR="$LIB_DIR/java-cup-11b.jar"
CUP_RT_JAR="$LIB_DIR/java-cup-runtime-11b.jar"

echo "==========================================="
echo "  Iniciando construcción del Proyecto Rover"
echo "==========================================="

# 1. Limpieza
echo "[1/4] Limpiando archivos generados anteriormente..."
rm -f "$SRC_DIR"/*.class \
      "$SRC_DIR/Lexer.java" \
      "$SRC_DIR/Parser.java" \
      "$SRC_DIR/sym.java"

# 2. Generar Lexer con JFlex
echo "[2/4] Generando Lexer (JFlex)..."
java -cp "$JFLEX_CP:$CUP_RT_JAR" jflex.Main -d "$SRC_DIR" "$SRC_DIR/Lexer.flex"

# 3. Generar Parser con CUP
echo "[3/4] Generando Parser (CUP)..."
java -cp "$CUP_JAR" java_cup.Main \
     -parser Parser \
     -destdir "$SRC_DIR" \
     "$SRC_DIR/Parser.cup"

# 4. Compilar todo el proyecto
echo "[4/4] Compilando fuentes Java..."
javac -source 8 -target 8 \
      -cp "$CUP_RT_JAR:$SRC_DIR" \
      "$SRC_DIR"/*.java

echo ""
echo "==========================================="
echo "  ✓ Construcción finalizada con éxito."
echo "==========================================="
echo ""
echo "Para ejecutar la interfaz web:"
echo "  cd webapp && mvn spring-boot:run"
echo ""
echo "Para ejecutar el parser en consola:"
echo "  java -cp \"lib/java-cup-runtime-11b.jar:src\" rover.Main"
