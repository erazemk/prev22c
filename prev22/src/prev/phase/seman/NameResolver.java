package prev.phase.seman;

import prev.common.report.*;
import prev.data.ast.tree.*;
import prev.data.ast.tree.decl.*;
import prev.data.ast.tree.expr.*;
import prev.data.ast.tree.stmt.*;
import prev.data.ast.tree.type.*;
import prev.data.ast.visitor.*;

/**
 * Name resolver.
 *
 * Name resolver connects each node of a abstract syntax tree where a name is
 * used with the node where it is declared. The only exceptions are a record
 * field names which are connected with its declarations by type resolver. The
 * results of the name resolver are stored in
 * {@link prev.phase.seman.SemAn#declaredAt}.
 */
public class NameResolver extends AstFullVisitor<Object, NameResolver.Mode> {

	public enum Mode {
		HEAD, BODY
	}

	private SymbTable symbTable = new SymbTable();

	@Override
	public Object visit(AstFunDecl funDecl, NameResolver.Mode mode) {
		return super.visit(funDecl, mode);
	}

	@Override
	public Object visit(AstParDecl parDecl, NameResolver.Mode mode) {
		return super.visit(parDecl, mode);
	}

	@Override
	public Object visit(AstTypeDecl typeDecl, NameResolver.Mode mode) {
		return super.visit(typeDecl, mode);
	}

	@Override
	public Object visit(AstVarDecl varDecl, NameResolver.Mode mode) {
		return super.visit(varDecl, mode);
	}

	@Override
	public Object visit(AstCallExpr callExpr, NameResolver.Mode mode) {
		return super.visit(callExpr, mode);
	}

	@Override
	public Object visit(AstNameExpr nameExpr, NameResolver.Mode mode) {
		return super.visit(nameExpr, mode);
	}

	@Override
	public Object visit(AstRecExpr recExpr, NameResolver.Mode mode) {
		return super.visit(recExpr, mode);
	}

	@Override
	public Object visit(AstWhereExpr whereExpr, NameResolver.Mode mode) {
		return super.visit(whereExpr, mode);
	}

	@Override
	public Object visit(AstNameType nameType, NameResolver.Mode mode) {
		return super.visit(nameType, mode);
	}
}
