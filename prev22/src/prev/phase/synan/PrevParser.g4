parser grammar PrevParser;

@header {
	package prev.phase.synan;
	import java.util.*;
	import prev.common.report.*;
	import prev.phase.lexan.*;
}

@members {
	private Location loc(Token tok) { return new Location((prev.data.sym.Token) tok); }
	private Location loc(Locatable loc) { return new Location(loc); }
	private Location loc(Token tok1, Token tok2) { return new Location((prev.data.sym.Token) tok1,
			(prev.data.sym.Token) tok2); }
	private Location loc(Token tok1, Locatable loc2) { return new Location((prev.data.sym.Token)
			tok1, loc2); }
	private Location loc(Locatable loc1, Token tok2) { return new Location(loc1,
			(prev.data.sym.Token) tok2); }
	private Location loc(Locatable loc1, Locatable loc2) { return new Location(loc1, loc2); }
}

options {
	tokenVocab = PrevLexer;
}

source      : program EOF
            ;

program     : declaration*;

declaration : KEY_TYP IDENT EQU type // Type declaration
            | KEY_VAR IDENT COLON type // Variable declaration
            | KEY_FUN IDENT LPAREN (IDENT COLON type (COMMA IDENT COLON type)*)? RPAREN COLON type
                    (EQU expression)?; // Function declaration

type        : KEY_VOID | KEY_CHAR | KEY_INT | KEY_BOOL // Atomic type
            | IDENT // Named type
            | LBRACE expression RBRACE type // Array type
            | EXP type // Pointer type
            | LBRACK IDENT COLON type (COMMA IDENT COLON type)* RBRACK // Record type
            | LPAREN type RPAREN; // Enclosed type

expression  : CONST_NONE | CONST_BOOL | CONST_INT | CONST_CHAR | CONST_STR
                    | CONST_PTR // Constant expression
            | IDENT // Variable access
            | IDENT LPAREN (expression (COMMA expression)*)? RPAREN // Function call
            | LBRACK statement SEMICOLON (statement SEMICOLON)* RBRACK // Compound expression
            | LPAREN expression COLON type RPAREN // Typecast expression
            | LPAREN expression RPAREN // Enclosed expression
            | expression (LBRACE expression RBRACE | EXP | DOT IDENT) // Postfix operators
            | (BANG | PLUS | MINUS | EXP | KEY_NEW | KEY_DEL) expression // Prefix operators
            | expression (STAR | SLASH | PERCENT) expression // Multiplicative operators
            | expression (PLUS | MINUS) expression // Additive operators
            | expression (DEQU | NEQU | LT | GT | LTE | GTE) expression // Relational operators
            | expression AMPERSAND expression // Conjunctive operator
            | expression VERT_LINE expression // Disjunctive operator
            | expression KEY_WHERE LBRACK declaration (declaration)* RBRACK; // Where expression

statement   : expression // Expression statement
            | expression EQU expression // Assignment statement
            | KEY_IF expression KEY_THEN statement KEY_ELSE statement // Conditional statement
            | KEY_WHILE expression KEY_DO statement; // Loop statement
