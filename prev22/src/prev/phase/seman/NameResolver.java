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
 * Name resolver connects each node of an abstract syntax tree where a name is
 * used with the node where it is declared. The only exceptions are a record
 * field names which are connected with its declarations by type resolver. The
 * results of the name resolver are stored in
 * {@link prev.phase.seman.SemAn#declaredAt}.
 */
public class NameResolver extends AstFullVisitor<Object, NameResolver.Mode> {

	public enum Mode {
		HEAD, BODY
	}

	private final SymbTable symbTable = new SymbTable();

	@Override
	public Object visit(AstTrees<? extends AstTree> trees, Mode mode) {

		if (mode == null) {
			for (AstTree t : trees) {
				if (t != null) t.accept(this, Mode.HEAD);
			}

			for (AstTree t : trees) {
				if (t != null) t.accept(this, Mode.BODY);
			}
		} else {
			for (AstTree t : trees) {
				if (t != null) t.accept(this, mode);
			}
		}

		return null;
	}

	@Override
	public Object visit(AstFunDecl funDecl, NameResolver.Mode mode) {
		if (mode == Mode.HEAD) {
			try {
				symbTable.ins(funDecl.name, funDecl);
			} catch (SymbTable.CannotInsNameException e) {
				throw new Report.Error(funDecl, "Semantic error: function '" + funDecl.name + "' has already been declared");
			}
		} else if (mode == Mode.BODY) {
			funDecl.pars.accept(this, Mode.BODY);
			funDecl.type.accept(this, Mode.BODY);

			symbTable.newScope();

			funDecl.pars.accept(this, Mode.HEAD);
			funDecl.expr.accept(this, Mode.BODY);

			symbTable.oldScope();
		}

		return null;
	}

	@Override
	public Object visit(AstParDecl parDecl, NameResolver.Mode mode) {
		if (mode == Mode.HEAD) {
			try {
				symbTable.ins(parDecl.name, parDecl);
			} catch (SymbTable.CannotInsNameException e) {
				throw new Report.Error(parDecl, "Semantic error: function argument '" + parDecl.name + "' has already been declared");
			}
		} else if (mode == Mode.BODY) {
			parDecl.type.accept(this, Mode.BODY);
		}

		return null;
	}

	@Override
	public Object visit(AstTypeDecl typeDecl, NameResolver.Mode mode) {
		if (mode == Mode.HEAD) {
			try {
				symbTable.ins(typeDecl.name, typeDecl);
			} catch (SymbTable.CannotInsNameException e) {
				throw new Report.Error(typeDecl, "Semantic error: type '" + typeDecl.name + "' has already been declared");
			}
		} else if (mode == Mode.BODY) {
			typeDecl.type.accept(this, Mode.BODY);
		}

		return null;
	}

	@Override
	public Object visit(AstVarDecl varDecl, NameResolver.Mode mode) {
		if (mode == Mode.HEAD) {
			try {
				symbTable.ins(varDecl.name, varDecl);
			} catch (SymbTable.CannotInsNameException e) {
				throw new Report.Error(varDecl, "Semantic error: variable '" + varDecl.name + "' has already been declared");
			}
		} else if (mode == Mode.BODY) {
			varDecl.type.accept(this, Mode.BODY);
		}

		return null;
	}

	@Override
	public Object visit(AstCallExpr callExpr, NameResolver.Mode mode) {
		if (mode == Mode.BODY) {
			try {
				SemAn.declaredAt.put(callExpr, symbTable.fnd(callExpr.name));
			} catch (SymbTable.CannotFndNameException e) {
				throw new Report.Error(callExpr, "Semantic error: could not find call expression '" + callExpr.name + "'");
			}

			callExpr.args.accept(this, Mode.BODY);
		}

		return null;
	}

	@Override
	public Object visit(AstNameExpr nameExpr, NameResolver.Mode mode) {
		if (mode == Mode.BODY) {
			try {
				SemAn.declaredAt.put(nameExpr, symbTable.fnd(nameExpr.name));
			} catch (SymbTable.CannotFndNameException e) {
				throw new Report.Error(nameExpr, "Semantic error: could not find name expression '" + nameExpr.name + "'");
			}
		}

		return null;
	}

	@Override
	public Object visit(AstRecExpr recExpr, NameResolver.Mode mode) {
		if (mode == Mode.BODY) {
			recExpr.rec.accept(this, Mode.BODY);
		}

		return null;
	}

	@Override
	public Object visit(AstWhereExpr whereExpr, NameResolver.Mode mode) {
		if (mode == Mode.BODY) {
			symbTable.newScope();

			whereExpr.decls.accept(this, Mode.HEAD);
			whereExpr.decls.accept(this, Mode.BODY);
			whereExpr.expr.accept(this, Mode.BODY);

			symbTable.oldScope();
		}

		return null;
	}

	@Override
	public Object visit(AstNameType nameType, NameResolver.Mode mode) {
		if (mode == Mode.BODY) {
			try {
				SemAn.declaredAt.put(nameType, symbTable.fnd(nameType.name));
			} catch (SymbTable.CannotFndNameException e) {
				throw new Report.Error(nameType, "Semantic error: could not find name type '" + nameType.name + "'");
			}
		}

		return null;
	}
}
