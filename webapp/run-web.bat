@echo off
echo =====================================================
echo   Rover Analyzer Web — Inicio del Servidor
echo =====================================================
echo.

:: Paso 1: Generar clases del analizador (Lexer + Parser)
echo [1/2] Compilando analizador lexico/sintatico...
call "%~dp0..\build.bat" >nul 2>&1
if %errorlevel% neq 0 (
    echo      build.bat encontro errores, pero continuando...
)
echo      Listo.
echo.

:: Paso 2: Iniciar el servidor Spring Boot con Maven
echo [2/2] Iniciando servidor Spring Boot en http://localhost:8080
echo       Presiona Ctrl+C para detener el servidor.
echo.
cd /d "%~dp0"
mvn spring-boot:run

pause
