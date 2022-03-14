lexer grammar PrevLexer;

@header {
	package prev.phase.lexan;
	import prev.common.report.*;
	import prev.data.sym.*;
}

@members {
	@Override
	public Token nextToken() { return (Token) super.nextToken(); }
}

// Constants
CONST_NONE: 'none';
CONST_BOOL: ('true'|'false');
CONST_INT: ([1-9][0-9]*|'0');
CONST_CHAR: '\''([ -&]|[(-~]|'\\\'')'\'';
CONST_STR: '"'([ -!]|[#-~]|'\\"')*'"';
CONST_PTR: 'nil';

// Symbols
LPAREN: '(';
RPAREN: ')';
LBRACK: '{';
RBRACK: '}';
LBRACE: '[';
RBRACE: ']';
DOT: '.';
COMMA: ',';
COLON: ':';
SEMICOLON: ';';
AMPERSAND: '&';
VERT_LINE: '|';
BANG: '!';
DEQU: '==';
NEQU: '!=';
LT: '<';
GT: '>';
LTE: '<=';
GTE: '>=';
STAR: '*';
SLASH: '/';
PERCENT: '%';
PLUS: '+';
MINUS: '-';
EXP: '^';
EQU: '=';

// Keywords
KEY_BOOL: 'bool';
KEY_CHAR: 'char';
KEY_DEL: 'del';
KEY_DO: 'do';
KEY_ELSE: 'else';
KEY_FUN: 'fun';
KEY_IF: 'if';
KEY_INT: 'int';
KEY_NEW: 'new';
KEY_THEN: 'then';
KEY_TYP: 'typ';
KEY_VAR: 'var';
KEY_VOID: 'void';
KEY_WHERE: 'where';
KEY_WHILE: 'while';

// Comments
COMMENT: '#'~[\n]* -> skip;

// White space
WHITESP: (' '|'\r'? '\n'|'\r') -> skip;
TAB: '\t' {
	if (true) setCharPositionInLine((getCharPositionInLine() / 8) * 8 + 8);
} -> skip;

// Identifiers
IDENT: [a-zA-Z_][a-zA-Z0-9_]*;

// Error handling
ERR_PADDED_INT: [0][0-9]+ {
	if (true) {
		new Report.Error(new Location( _tokenStartLine,
			_tokenStartCharPositionInLine, getLine(), getCharPositionInLine()),
			"Lexical error: 0-padded integer"
		);
	}
};
ERR_LONG_CHAR: '\''([ -&]|[(-~])([ -&]|[(-~])+'\'' {
	if (true) {
		new Report.Error(new Location(_tokenStartLine,
			_tokenStartCharPositionInLine, getLine(), getCharPositionInLine()),
			"Lexical error: multiple characters inside single quote"
		);
	}
};
ERR_UNESCAPED_QUOTE: '\'''\'''\'' {
	if (true) {
		new Report.Error(new Location(_tokenStartLine,
			_tokenStartCharPositionInLine, getLine(), getCharPositionInLine()),
			"Lexical error: unescaped single quote"
		);
	}
};
ERR_UNTERMINATED_CHAR: '\''([ -&]|[(-~]|'\\\'')*('\n'|EOF) {
	if (true) {
		new Report.Error(new Location(_tokenStartLine,
			_tokenStartCharPositionInLine, getLine(), getCharPositionInLine()),
			"Lexical error: unterminated char"
		);
	}
};
ERR_UNTERMINATED_STR: '"'([ -!]|[#-~]|'\\"')*('\n'|EOF) {
	if (true) {
		new Report.Error(new Location(_tokenStartLine,
			_tokenStartCharPositionInLine, getLine(), getCharPositionInLine()),
			"Lexical error: unterminated string"
		);
	}
};
OTHER_ERR: . {
	if (true) {
		new Report.Error(new Location(_tokenStartLine,
			_tokenStartCharPositionInLine, getLine(), getCharPositionInLine()),
			"Unrecognised symbol " + getText()
		);
	}
};
