package rover;
import java_cup.runtime.*;

%%
%class Lexer
%unicode
%cup
%line
%column

%{
  private Symbol symbol(int type) { return new Symbol(type, yyline+1, yycolumn+1); }
  private Symbol symbol(int type, Object value) { return new Symbol(type, yyline+1, yycolumn+1, value); }
%}

NUM = [0-9]+
%%

"MOVE"        { return symbol(sym.MOVE); }
"TURN"        { return symbol(sym.TURN); }
"TAKE SAMPLE FROM" { return symbol(sym.TAKE_SAMPLE); }
"FORWARD"     { return symbol(sym.FORWARD); }
"LEFT"        { return symbol(sym.LEFT); }
"RIGHT"       { return symbol(sym.RIGHT); }
"METERS"      { return symbol(sym.METERS); }
"DEGREES"     { return symbol(sym.DEGREES); }
"SOIL"        { return symbol(sym.TERRAIN, yytext()); }
"ROCK"        { return symbol(sym.TERRAIN, yytext()); }
"ICE"         { return symbol(sym.TERRAIN, yytext()); }
";"           { return symbol(sym.SEMICOLON); }
{NUM}         { return symbol(sym.NUM, Integer.parseInt(yytext())); }
[ \t\r\n]+    { /* Ignorar */ }
.             { System.err.println("Error léxico: " + yytext()); }