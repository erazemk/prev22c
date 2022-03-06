lexer grammar PrevLexer;

@header {
	package prev.phase.lexan;
	import prev.common.report.*;
	import prev.data.sym.*;
}

@members {
    @Override
	public Token nextToken() {
		return (Token) super.nextToken();
	}
}

// Constants
CONSTANT_NONE: 'none';
CONSTANT_BOOLEAN: ('true'|'false');
CONSTANT_INTEGER: ([1-9][0-9]*|'0');
CONSTANT_CHARACTER: '\''([ -&]|[(-~]|'\\\'')'\'';
CONSTANT_STRING: '"'([ -!]|[#-~]|'\\"')*'"';
CONSTANT_POINTER: 'nil';

// Symbols
LEFT_PARENTHESIS: '(';
RIGHT_PARENTHESIS: ')';
LEFT_CURLY_PARENTHESIS: '{';
RIGHT_CURLY_PARENTHESIS: '}';
LEFT_SQUARE_PARENTHESIS: '[';
RIGHT_SQUARE_PARENTHESIS: ']';
DOT: '.';
COMMA: ',';
COLON: ':';
SEMICOLON: ';';
AMPERSAND: '&';
VERTICAL_LINE: '|';
EXCLAMATION_POINT: '!';
DOUBLE_EQUALS: '==';
NOT_EQUALS: '!=';
SMALLER_THAN: '<';
GREATER_THAN: '>';
LESS_THAN_OR_EQUALS: '<=';
GREATER_THAN_OR_EQUALS: '>=';
STAR: '*';
SLASH: '/';
PERCENT: '%';
PLUS: '+';
MINUS: '-';
EXPONENT: '^';
EQUALS: '=';

// Keywords
KEYWORD_BOOL: 'bool';
KEYWORD_CHAR: 'char';
KEYWORD_DEL: 'del';
KEYWORD_DO: 'do';
KEYWORD_ELSE: 'else';
KEYWORD_FUN: 'fun';
KEYWORD_IF: 'if';
KEYWORD_INT: 'int';
KEYWORD_NEW: 'new';
KEYWORD_THEN: 'then';
KEYWORD_TYP: 'typ';
KEYWORD_VAR: 'var';
KEYWORD_VOID: 'void';
KEYWORD_WHERE: 'where';
KEYWORD_WHILE: 'while';

// Comments
COMMENT: '#'~[\n]*;

// White space
WHITESPACE: (' '|'\r'? '\n'|'\r') -> skip;
TAB: '\t' {
    if (true) {
        setCharPositionInLine(getCharPositionInLine() + 7);
    }
} -> skip;

// Identifiers
IDENTIFIER: [a-zA-Z_][a-zA-Z0-9_]*;

// Error handling
ERROR_PADDED_INTEGER: [0][0-9]+ {
	if (true) {
		new Report.Error(new Location(
				_tokenStartLine, _tokenStartCharPositionInLine,
				getLine(), getCharPositionInLine()),
			"Lexical error: 0-padded integer"
		);
	}
};
ERROR_LONG_CHARACTER: '\''([ -&]|[(-~])([ -&]|[(-~])+'\'' {
	if (true) {
		new Report.Error(new Location(
			_tokenStartLine, _tokenStartCharPositionInLine,
			getLine(), getCharPositionInLine()),
			"Lexical error: multiple characters inside single quote"
		);
	}
};
ERROR_UNESCAPED_QUOTE: '\'''\'''\'' {
	if (true) {
		new Report.Error(new Location(
			_tokenStartLine, _tokenStartCharPositionInLine,
			getLine(), getCharPositionInLine()),
			"Lexical error: unescaped single quote"
		);
	}
};
ERROR_UNTERMINATED_CHAR: '\''([ -&]|[(-~]|'\\\'')*('\n'|EOF) {
	if (true) {
		new Report.Error(new Location(
			_tokenStartLine, _tokenStartCharPositionInLine,
			getLine(), getCharPositionInLine()),
			"Lexical error: unterminated char"
		);
	}
};
ERROR_UNTERMINATED_STRING: '"'([ -!]|[#-~]|'\\"')*('\n'|EOF) {
    if (true) {
        new Report.Error(new Location(
            _tokenStartLine, _tokenStartCharPositionInLine,
            getLine(), getCharPositionInLine()),
            "Lexical error: unterminated string"
        );
	}
};
ERROR: . {
	if (true) {
		new Report.Error(new Location(
			_tokenStartLine, _tokenStartCharPositionInLine,
			getLine(), getCharPositionInLine()),
			"Unrecognised symbol " + getText()
		);
	}
};
