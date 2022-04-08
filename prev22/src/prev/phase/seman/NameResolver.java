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

	// Two passes
	public enum Mode {
		HEAD, // For adding labels (left values) to symbTable
		BODY // For checking if all right values are defined on the left (so in symbTable)
	}

	// Identifier for info reports
	private final String TAG = "[NameResolver]: ";

	// Global symbol table to check mismatches in declarations
	private final SymbTable symbTable = new SymbTable();

	// GENERAL PURPOSE

	// Helper method for declarations
	public Object addOrAccept(AstDecl astDecl, Mode mode) {
		if (mode == Mode.HEAD) {
			// If 1st pass, add the name to the symbol table
			String name = ((AstNameDecl) astDecl).name;

			try {
				symbTable.ins(name, astDecl);
			} catch (SymbTable.CannotInsNameException e) {
				throw new Report.Error(astDecl, TAG + "'" + name + "' has already been declared");
			}
		} else if (mode == Mode.BODY) {
			// If second pass, get the type's name
			if (astDecl instanceof AstMemDecl) {
				((AstMemDecl) astDecl).type.accept(this, mode);
			} else if (astDecl instanceof AstTypeDecl) {
				((AstTypeDecl) astDecl).type.accept(this, mode);
			}
		}

		return null;
	}

	// Helper method to visit whole tree with the provided mode
	public void visitTree(AstTrees<? extends AstTree> trees, Mode mode) {
		for (AstTree t : trees) {
			if (t != null) {
				t.accept(this, mode);
			}
		}
	}

	@Override
	public Object visit(AstTrees<? extends AstTree> trees, Mode mode) {
		if (mode == null) {
			// If called from Compiler, run both passes
			visitTree(trees, Mode.HEAD);
			visitTree(trees, Mode.BODY);
		} else {
			// If called recursively, run just the needed pass
			visitTree(trees, mode);
		}

		return null;
	}

	// DECLARATIONS

	@Override
	public Object visit(AstFunDecl funDecl, Mode mode) {
		// 1st pass adds the function name to the symbol table and throws an error if the name was already declared
		if (mode == Mode.HEAD) {
			try {
				symbTable.ins(funDecl.name, funDecl);
			} catch (SymbTable.CannotInsNameException e) {
				throw new Report.Error(funDecl, TAG + "'" + funDecl.name + "' has already been declared");
			}
		// 2nd pass check the function's parameters and expression
		} else if (mode == Mode.BODY) {
			if (funDecl.pars != null) {
				funDecl.pars.accept(this, Mode.BODY);
			}

			funDecl.type.accept(this, mode);

			// Function body has a new scope
			symbTable.newScope();

			if (funDecl.pars != null) {
				funDecl.pars.accept(this, Mode.HEAD);
			}


			if (funDecl.expr != null) {
				funDecl.expr.accept(this, Mode.BODY);
			}

			symbTable.oldScope();
		}

		return null;
	}

	@Override
	public Object visit(AstParDecl parDecl, Mode mode) {
		return addOrAccept(parDecl, mode);
	}

	@Override
	public Object visit(AstTypeDecl typeDecl, Mode mode) {
		return addOrAccept(typeDecl, mode);
	}

	@Override
	public Object visit(AstVarDecl varDecl, Mode mode) {
		return addOrAccept(varDecl, mode);
	}

	// EXPRESSIONS

	@Override
	public Object visit(AstCallExpr callExpr, Mode mode) {
		if (mode != Mode.BODY) return null;

		// Wait until the 2nd pass to check if a function has been declared
		try {
			SemAn.declaredAt.put(callExpr, symbTable.fnd(callExpr.name));
		} catch (SymbTable.CannotFndNameException e) {
			throw new Report.Error(callExpr, TAG + "could not find call expression '" + callExpr.name + "'");
		}

		// Name resolve the expression's arguments
		if (callExpr.args != null) {
			callExpr.args.accept(this, mode);
		}

		return null;
	}

	@Override
	public Object visit(AstNameExpr nameExpr, Mode mode) {
		if (mode != Mode.BODY) return null;

		try {
			SemAn.declaredAt.put(nameExpr, symbTable.fnd(nameExpr.name));
		} catch (SymbTable.CannotFndNameException e) {
			throw new Report.Error(nameExpr, TAG + "could not find name expression '" + nameExpr.name + "'");
		}

		return null;
	}

	@Override
	public Object visit(AstRecExpr recExpr, Mode mode) {
		if (mode != Mode.BODY) return null;

		// Wait until the 2nd pass to check the record's components
		return recExpr.rec.accept(this, Mode.BODY);
	}

	@Override
	public Object visit(AstWhereExpr whereExpr, Mode mode) {
		if (mode != Mode.BODY) return null;

		// Where expression's body has a new scope
		symbTable.newScope();

		// Name resolve all the declarations and expression
		whereExpr.decls.accept(this, Mode.HEAD);
		whereExpr.decls.accept(this, Mode.BODY);
		whereExpr.expr.accept(this, Mode.BODY);

		symbTable.oldScope();
		return null;
	}

	// TYPES

	@Override
	public Object visit(AstNameType nameType, NameResolver.Mode mode) {
		if (mode != Mode.BODY) return null;

		// Wait until the 2nd pass to check if the name has been declared
		try {
			SemAn.declaredAt.put(nameType, symbTable.fnd(nameType.name));
		} catch (SymbTable.CannotFndNameException e) {
			throw new Report.Error(nameType, TAG + "could not find name type '" + nameType.name + "'");
		}

		return null;
	}
}
