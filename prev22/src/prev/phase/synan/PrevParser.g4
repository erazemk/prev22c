parser grammar PrevParser;

@header {
	package prev.phase.synan;
	import java.util.*;
	import prev.common.report.*;
	import prev.phase.lexan.*;
	import prev.data.ast.tree.*;
	import prev.data.ast.tree.decl.*;
	import prev.data.ast.tree.expr.*;
	import prev.data.ast.tree.stmt.*;
	import prev.data.ast.tree.type.*;
}

@members {
	private Location loc(Token tok) { return new Location((prev.data.sym.Token) tok); }
	private Location loc(Locatable loc) { return new Location(loc); }
	private Location loc(Token tok1, Token tok2) { return new Location((prev.data.sym.Token) tok1, (prev.data.sym.Token) tok2); }
	private Location loc(Token tok1, Locatable loc2) { return new Location((prev.data.sym.Token) tok1, loc2); }
	private Location loc(Locatable loc1, Token tok2) { return new Location(loc1, (prev.data.sym.Token) tok2); }
	private Location loc(Locatable loc1, Locatable loc2) { return new Location(loc1, loc2); }
}

options {
	tokenVocab = PrevLexer;
}

source returns [AstTrees<AstDecl> ast]
	: { Vector<AstDecl> declarations = new Vector<>(); }
		(declaration { declarations.add($declaration.astDecl); })+
		{ $ast = new AstTrees<AstDecl>(declarations); } EOF
	;

declaration returns [AstDecl astDecl]
	: KEY_TYP IDENT EQU type { $astDecl = new AstTypeDecl(loc($KEY_TYP, $type.astType), $IDENT.getText(), $type.astType); } /* Type declaration */
	| KEY_VAR IDENT COLON type { $astDecl = new AstVarDecl(loc($KEY_VAR, $type.astType), $IDENT.getText(), $type.astType); } /* Variable declaration */
	| KEY_FUN funcName=IDENT LPAREN { Vector<AstParDecl> parameters = new Vector<>(); } /* Function declaration */
		(IDENT COLON type { parameters.add(new AstParDecl(loc($IDENT, $type.astType), $IDENT.getText(), $type.astType)); } /* First parameter */
		(COMMA IDENT COLON type { parameters.add(new AstParDecl(loc($IDENT, $type.astType), $IDENT.getText(), $type.astType)); } /* Other parameters */ )*)?
		RPAREN COLON funcType=type { AstExpr astExpr = null; Location location = loc($KEY_FUN, $funcType.astType); }
		(EQU expression { astExpr = $expression.astExpr; location = loc($KEY_FUN, $expression.astExpr); })? /* Extra expressions */
		{ $astDecl = new AstFunDecl(location, $funcName.getText(), new AstTrees<AstParDecl>(parameters), $funcType.astType, astExpr); }
	;

type returns [AstType astType]
	: (KEY_VOID { $astType = new AstAtomType(loc($KEY_VOID), AstAtomType.Type.VOID); } /* Atomic type */
		| KEY_CHAR { $astType = new AstAtomType(loc($KEY_CHAR), AstAtomType.Type.CHAR); }
		| KEY_INT { $astType = new AstAtomType(loc($KEY_INT), AstAtomType.Type.INT); }
		| KEY_BOOL { $astType = new AstAtomType(loc($KEY_BOOL), AstAtomType.Type.BOOL); })
	| IDENT { $astType = new AstNameType(loc($IDENT), $IDENT.getText()); } /* Named type */
	| LBRACE expression RBRACE type { $astType = new AstArrType(loc($LBRACE, $type.astType), $type.astType, $expression.astExpr); } /* Array type */
	| EXP type { $astType = new AstPtrType(loc($EXP, $type.astType), $type.astType); } /* Pointer type */
	| LBRACK IDENT COLON type { Vector<AstCompDecl> components = new Vector<>(); } /* Record type */
		{ components.add(new AstCompDecl(loc($IDENT, $type.astType), $IDENT.getText(), $type.astType)); } /* First component */
		(COMMA IDENT COLON type { components.add(new AstCompDecl(loc($IDENT, $type.astType), $IDENT.getText(), $type.astType)); } /* Other components */)*
			RBRACK { $astType = new AstRecType(loc($LBRACK, $RBRACK), new AstTrees<AstCompDecl>(components)); }
	| LPAREN type RPAREN { $astType = $type.astType; $astType.relocate(loc($LPAREN, $RPAREN)); } /* Enclosed type */
	;

expression returns [AstExpr astExpr]
	: ( /* Constant expression */
		CONST_NONE { $astExpr = new AstAtomExpr(loc($CONST_NONE), AstAtomExpr.Type.VOID, $CONST_NONE.getText()); }
		| CONST_BOOL { $astExpr = new AstAtomExpr(loc($CONST_BOOL), AstAtomExpr.Type.BOOL, $CONST_BOOL.getText()); }
		| CONST_INT { $astExpr = new AstAtomExpr(loc($CONST_INT), AstAtomExpr.Type.INT, $CONST_INT.getText()); }
		| CONST_CHAR { $astExpr = new AstAtomExpr(loc($CONST_CHAR), AstAtomExpr.Type.CHAR, $CONST_CHAR.getText()); }
		| CONST_STR { $astExpr = new AstAtomExpr(loc($CONST_STR), AstAtomExpr.Type.STRING, $CONST_STR.getText()); }
		| CONST_PTR { $astExpr = new AstAtomExpr(loc($CONST_PTR), AstAtomExpr.Type.POINTER, $CONST_PTR.getText()); }
		)
	| IDENT { $astExpr = new AstNameExpr(loc($IDENT), $IDENT.getText()); } /* Variable access */
	| IDENT LPAREN { Vector<AstExpr> arguments = new Vector<>(); } /* Function call */
		(expression { arguments.add($expression.astExpr); } /* First argument */
		(COMMA expression { arguments.add($expression.astExpr); } /* Other arguments */)*)?
		RPAREN { $astExpr = new AstCallExpr(loc($IDENT, $RPAREN), $IDENT.getText(), new AstTrees<AstExpr>(arguments)); }
	| LBRACK statement { Vector<AstStmt> statements = new Vector<>(); statements.add($statement.astStmt); } /* Compound expression */
		SEMICOLON (statement SEMICOLON { statements.add($statement.astStmt); })*
		RBRACK { $astExpr = new AstStmtExpr(loc($LBRACK, $RBRACK), new AstTrees<AstStmt>(statements)); }
	| LPAREN expression COLON type RPAREN { $astExpr = new AstCastExpr(loc($LPAREN, $RPAREN), $expression.astExpr, $type.astType); } /* Typecast expression */
	| LPAREN expression RPAREN { $astExpr = $expression.astExpr; $astExpr.relocate(loc($LPAREN, $RPAREN)); } /* Enclosed expression */
	| e1=expression /* Postfix operators */
		(LBRACE e2=expression RBRACE { $astExpr = new AstArrExpr(loc($e1.astExpr, $RBRACE), $e1.astExpr, $e2.astExpr); } /* Array */
		| EXP { $astExpr = new AstSfxExpr(loc($e1.astExpr, $EXP), AstSfxExpr.Oper.PTR, $e1.astExpr); } /* Pointer */
		| DOT IDENT { $astExpr = new AstRecExpr(loc($e1.astExpr, $IDENT), $e1.astExpr, new AstNameExpr(loc($IDENT), $IDENT.getText())); }) /* Attribute */
	| { AstPfxExpr.Oper operator; Location location; } /* Prefix operators */
		(BANG { operator = AstPfxExpr.Oper.NOT; location = loc($BANG); }
		| PLUS { operator = AstPfxExpr.Oper.ADD; location = loc($PLUS); }
		| MINUS { operator = AstPfxExpr.Oper.SUB; location = loc($MINUS); }
		| EXP { operator = AstPfxExpr.Oper.PTR; location = loc($EXP); }
		| KEY_NEW { operator = AstPfxExpr.Oper.NEW; location = loc($KEY_NEW); }
		| KEY_DEL { operator = AstPfxExpr.Oper.DEL; location = loc($KEY_DEL); }
		) expression { $astExpr = new AstPfxExpr(loc(location, $expression.astExpr), operator, $expression.astExpr); }
	| e1=expression { AstBinExpr.Oper operator; } /* Multiplicative operators */
		(STAR { operator = AstBinExpr.Oper.MUL; }
		| SLASH { operator = AstBinExpr.Oper.DIV; }
		| PERCENT { operator = AstBinExpr.Oper.MOD; }
		) e2=expression { $astExpr = new AstBinExpr(loc($e1.astExpr, $e2.astExpr), operator, $e1.astExpr, $e2.astExpr); }
	| e1=expression { AstBinExpr.Oper operator; } /* Additive operators */
		(PLUS { operator = AstBinExpr.Oper.ADD; }
		| MINUS { operator = AstBinExpr.Oper.SUB; }
		) e2=expression { $astExpr = new AstBinExpr(loc($e1.astExpr, $e2.astExpr), operator, $e1.astExpr, $e2.astExpr); }
	| e1=expression { AstBinExpr.Oper operator; } /* Relational operators */
		(DEQU { operator = AstBinExpr.Oper.EQU; }
		| NEQU { operator = AstBinExpr.Oper.NEQ; }
		| LT { operator = AstBinExpr.Oper.LTH; }
		| GT { operator = AstBinExpr.Oper.GTH; }
		| LTE { operator = AstBinExpr.Oper.LEQ; }
		| GTE { operator = AstBinExpr.Oper.GEQ; }
		) e2=expression { $astExpr = new AstBinExpr(loc($e1.astExpr, $e2.astExpr), operator, $e1.astExpr, $e2.astExpr); }
	| e1=expression AMPERSAND e2=expression /* Conjunctive operator */
		{ $astExpr = new AstBinExpr(loc($e1.astExpr, $e2.astExpr), AstBinExpr.Oper.AND, $e1.astExpr, $e2.astExpr); }
	| e1=expression VERT_LINE e2=expression /* Disjunctive operator */
		{ $astExpr = new AstBinExpr(loc($e1.astExpr, $e2.astExpr), AstBinExpr.Oper.OR, $e1.astExpr, $e2.astExpr); }
	| e1=expression KEY_WHERE LBRACK { Vector<AstDecl> declarations = new Vector<>(); } /* Where expression */
		declaration { declarations.add($declaration.astDecl); }
		(declaration { declarations.add($declaration.astDecl); })*
		RBRACK { $astExpr = new AstWhereExpr(loc($e1.astExpr, $RBRACK), $e1.astExpr, new AstTrees<AstDecl>(declarations)); }
	;

statement returns [AstStmt astStmt]
	: expression { $astStmt = new AstExprStmt(loc($expression.astExpr), $expression.astExpr); } /* Expression statement */
	| e1=expression EQU e2=expression { $astStmt = new AstAssignStmt(loc($e1.astExpr, $e2.astExpr), $e1.astExpr, $e2.astExpr); } /* Assignment statement */
	| KEY_IF expression KEY_THEN s1=statement KEY_ELSE s2=statement /* Conditional statement */
		{ $astStmt = new AstIfStmt(loc($KEY_IF, $s2.astStmt), $expression.astExpr, $s1.astStmt, $s2.astStmt); }
	| KEY_WHILE expression KEY_DO statement /* Loop statement */
		{ $astStmt = new AstWhileStmt(loc($KEY_WHILE, $statement.astStmt), $expression.astExpr, $statement.astStmt); }
	;
