package prev.phase.seman;

import java.util.*;

import prev.common.report.*;
import prev.data.ast.tree.*;
import prev.data.ast.tree.decl.*;
import prev.data.ast.tree.expr.*;
import prev.data.ast.tree.stmt.*;
import prev.data.ast.tree.type.*;
import prev.data.ast.visitor.*;
import prev.data.typ.*;

/**
 * Type resolver.
 *
 * Type resolver computes the values of {@link SemAn#declaresType},
 * {@link SemAn#isType}, and {@link SemAn#ofType}.
 */
public class TypeResolver extends AstFullVisitor<SemType, TypeResolver.Mode> {

	public enum Mode {
		HEAD, BODY, FEET
	}

	private final HashMap<SemRec, SymbTable> symbTableMap = new HashMap<>();

	/*
	 * EXTRA
	 */

	@Override
	public SemType visit(AstTrees<? extends AstTree> trees, Mode mode) {
		if (mode == null) {
			for (AstTree t : trees) {
				if (t != null) t.accept(this, Mode.HEAD);
			}

			for (AstTree t : trees) {
				if (t != null) t.accept(this, Mode.BODY);
			}

			for (AstTree t : trees) {
				if (t != null) t.accept(this, Mode.FEET);
			}
		} else {
			for (AstTree t : trees) {
				if (t != null) t.accept(this, mode);
			}
		}

		return null;
	}

	@Override
	public SemType visit(AstNameType nameType, Mode mode) {
		if (mode != Mode.BODY) return null;

		AstDecl nameDecl = SemAn.declaredAt.get(nameType);

		if (!(nameDecl instanceof AstTypeDecl))
			throw new Report.Error(nameType, "Type error: undeclared type");

		SemName semName = SemAn.declaresType.get((AstTypeDecl) nameDecl);
		SemAn.isType.put(nameType, semName);
		return semName;
	}

	@Override
	public SemType visit(AstParDecl parDecl, Mode mode) {
		if (mode != Mode.BODY) return null;

		parDecl.type.accept(this, mode);

		SemType parType = SemAn.isType.get(parDecl.type);
		SemType actualParType = parType.actualType();

		if (!(actualParType instanceof SemBool || actualParType instanceof SemChar || actualParType instanceof SemInt
		|| actualParType instanceof SemPtr))
			throw new Report.Error(parDecl, "Type error: parameter must be of type 'bool', 'char' or 'int'");

		SemAn.isType.put(parDecl.type, parType);
		return parType;
	}

	@Override
	public SemType visit(AstNameExpr nameExpr, Mode mode) {
		AstDecl decl = SemAn.declaredAt.get(nameExpr);
		SemType type;

		if (decl instanceof AstFunDecl) {
			if (((AstFunDecl) decl).pars != null) throw new Report.Error(nameExpr, "Type error: missing arguments for function call");
			type = SemAn.isType.get(((AstFunDecl) decl).type);
		} else if (decl instanceof AstVarDecl) {
			type = SemAn.isType.get(((AstVarDecl) decl).type);
		} else if (decl instanceof AstParDecl) {
			type = SemAn.isType.get(((AstParDecl) decl).type);
		} else if (decl instanceof AstCompDecl) {
			type = SemAn.isType.get(((AstCompDecl) decl).type);
		} else {
			throw new Report.Error(nameExpr, "Type error: you can only declare variables, parameters or records components");
		}

		SemAn.ofType.put(nameExpr, type);
		return type;
	}

	@Override
	public SemType visit(AstCompDecl compDecl, Mode mode) {
		return compDecl.type.accept(this, mode);
	}

	@Override
	public SemType visit(AstExprStmt exprStmt, Mode mode) {
		SemType type = exprStmt.expr.accept(this, mode);
		SemAn.ofType.put(exprStmt, type);
		return type;
	}

	/*
	 * TYPE EXPRESSIONS
	 */

	// Type 1
	@Override
	public SemType visit(AstAtomType atomType, Mode mode) {
		if (mode != Mode.BODY) return null;

		SemType type = switch (atomType.type) {
			case VOID -> new SemVoid();
			case CHAR -> new SemChar();
			case INT -> new SemInt();
			case BOOL -> new SemBool();
		};

		SemAn.isType.put(atomType, type);
		return type;
	}

	// Type 2
	@Override
	public SemType visit(AstArrType arrType, Mode mode) {
		if (mode != Mode.BODY) return null;

		arrType.elemType.accept(this, mode);
		arrType.numElems.accept(this, mode);

		SemType elemType = SemAn.isType.get(arrType.elemType).actualType();

		if (elemType instanceof SemVoid)
			throw new Report.Error(arrType, "Type error: array element cannot be of type 'void'");

		if (!(arrType.numElems instanceof AstAtomExpr))
			throw new Report.Error(arrType, "Type error: array index must be of type 'int'");

		if (((AstAtomExpr) arrType.numElems).type != AstAtomExpr.Type.INT)
			throw new Report.Error(arrType, "Type error: array index must be of type 'int'");

		long numElems;
		try {
			numElems = Long.parseLong(((AstAtomExpr) arrType.numElems).value);
		} catch (NumberFormatException nfe) {
			throw new Report.Error(arrType, "Type error: invalid array size");
		}

		if (numElems < 0)
			throw new Report.Error(arrType, "Type error: array index cannot be negative");

		SemType type = SemAn.isType.get(arrType.elemType);
		SemAn.isType.put(arrType, new SemArr(type, numElems));
		return type;
	}

	// Type 3
	@Override
	public SemType visit(AstRecType recType, Mode mode) {
		if (mode != Mode.BODY) return null;

		Vector<SemType> compTypes = new Vector<>();
		SymbTable symbTable = new SymbTable();

		for (AstCompDecl comp : recType.comps) {
			comp.accept(this, mode);
			SemType compType = SemAn.isType.get(comp.type);

			if (compType.actualType() instanceof SemVoid)
				throw new Report.Error(comp, "Type error: record element cannot be of type 'void'");

			try {
				symbTable.ins(comp.name, comp);
			} catch (SymbTable.CannotInsNameException e) {
				throw new Report.Error(comp, "Type error: component name already declared");
			}

			compTypes.add(compType);
		}

		SemRec rec = new SemRec(compTypes);
		SemAn.isType.put(recType, rec);
		symbTableMap.put(rec, symbTable);
		return rec;
	}

	// Type 4
	@Override
	public SemType visit(AstPtrType ptrType, Mode mode) {
		if (mode != Mode.BODY) return null;

		ptrType.baseType.accept(this, mode);

		SemType baseType = SemAn.isType.get(ptrType.baseType);

		if (baseType == null)
			throw new Report.Error(ptrType, "Type error: undeclared pointer type");

		SemType type = new SemPtr(baseType);
		SemAn.isType.put(ptrType, type);
		return type;
	}

	// Type 5 is not needed

	/*
	 * VALUE EXPRESSIONS
	 */

	// Atom expressions
	@Override
	public SemType visit(AstAtomExpr atomExpr, Mode mode) {
		SemType type = switch(atomExpr.type) {
			// Value 1
			case VOID -> new SemVoid();
			case POINTER -> new SemPtr(new SemVoid());
			case STRING -> new SemPtr(new SemChar());

			// Value 2
			case BOOL -> new SemBool();
			case CHAR -> new SemChar();
			case INT -> new SemInt();
		};

		SemAn.ofType.put(atomExpr, type);
		return type;
	}

	// Prefix expressions
	@Override
	public SemType visit(AstPfxExpr pfxExpr, Mode mode) {
		pfxExpr.expr.accept(this, mode);
		SemType exprType = SemAn.ofType.get(pfxExpr.expr).actualType();

		SemType type = null;
		switch (pfxExpr.oper) {
			// Value 3
			case NOT -> {
				if (!(exprType instanceof SemBool))
					throw new Report.Error(pfxExpr, "Type error: cannot use negation with non-boolean expression");
				type = new SemBool();
			}
			case ADD, SUB -> {
				if (!(exprType instanceof SemInt))
					throw new Report.Error(pfxExpr, "Type error: cannot add or subtract from non-integer expression");
				type = new SemInt();
			}

			// Value 8 (left)
			case PTR -> type = new SemPtr(exprType);

			// Value 9
			case NEW -> {
				if (!(exprType instanceof SemInt))
					throw new Report.Error(pfxExpr, "Type error: cannot use 'new' with non-integer expression");
				type = new SemPtr(new SemVoid());
			}
			case DEL -> {
				if (!(exprType instanceof SemPtr))
					throw new Report.Error(pfxExpr, "Type error: cannot use 'del' with non-pointer expression");
				type = new SemVoid();
			}
		}

		SemAn.ofType.put(pfxExpr, type);
		return type;
	}

	// Binary expressions
	@Override
	public SemType visit(AstBinExpr binExpr, Mode mode) {
		if (mode != Mode.FEET) return null;

		binExpr.fstExpr.accept(this, mode);
		binExpr.sndExpr.accept(this, mode);

		SemType fstExprType = SemAn.ofType.get(binExpr.fstExpr).actualType();
		SemType sndExprType = SemAn.ofType.get(binExpr.sndExpr).actualType();

		SemType type = null;
		switch (binExpr.oper) {
			// Value 4
			case AND, OR:
				if (!(fstExprType instanceof SemBool && sndExprType instanceof SemBool))
					throw new Report.Error(binExpr, "Type error: cannot use operators '&' and '|' in non-boolean expressions");
				type = new SemBool();
				break;

			// Value 5
			case ADD, SUB, MUL, DIV, MOD:
				if (!(fstExprType instanceof SemInt && sndExprType instanceof SemInt))
					throw new Report.Error(binExpr, "Type error: cannot use operators '+', '-', '*', '/', '%' in non-integer expressions");
				type = new SemInt();
				break;

			// Value 6
			case EQU, NEQ:
				if (fstExprType instanceof SemBool && sndExprType instanceof SemBool) { // Both booleans
					type = new SemBool();
				} else if (fstExprType instanceof SemChar && sndExprType instanceof SemChar) { // Both chars
					type = new SemChar();
				} else if (fstExprType instanceof SemInt && sndExprType instanceof SemInt) { // Both ints
					type = new SemInt();
				} else if (fstExprType instanceof SemPtr && sndExprType instanceof SemPtr && // Both pointers
						((SemPtr) fstExprType).baseType == ((SemPtr) sndExprType).baseType && // Same type
						(((SemPtr) fstExprType).baseType instanceof SemBool || ((SemPtr) fstExprType).baseType instanceof SemChar
							|| ((SemPtr) fstExprType).baseType instanceof SemInt)) { // Pointer of type bool/char/int
					type = new SemPtr(((SemPtr) fstExprType).baseType);
				} else {
					throw new Report.Error(binExpr, "Type error: cannot use operators '==' and '!=' with a combination of" +
						" expressions of type " + fstExprType.toString() + " and " + sndExprType.toString());
				}
				break;

			// Value 7
			case LEQ, GEQ, LTH, GTH:
				if (fstExprType instanceof SemChar && sndExprType instanceof SemChar) {
					type = new SemChar();
				} else if (fstExprType instanceof SemInt && sndExprType instanceof SemInt) {
					type = new SemInt();
				} else if (fstExprType instanceof SemPtr && sndExprType instanceof SemPtr && // Both pointers
						((SemPtr) fstExprType).baseType == ((SemPtr) sndExprType).baseType && // Same type
						(((SemPtr) fstExprType).baseType instanceof SemChar || ((SemPtr) fstExprType).baseType instanceof SemInt)) { // Pointer of type char/int
					type = new SemPtr(((SemPtr) fstExprType).baseType);
				} else {
					throw new Report.Error(binExpr, "Type error: cannot use operators '<=', '>=', '<' and '>' with " +
						"a combination of expressions of type " + fstExprType.toString() + " and " + sndExprType.toString());
				}
				break;
		}

		SemAn.ofType.put(binExpr, type);
		return type;
	}

	// Value 8 (right)
	@Override
	public SemType visit(AstSfxExpr sfxExpr, Mode mode) {
		if (mode != Mode.FEET) return null;

		sfxExpr.expr.accept(this, mode);
		SemType exprType = SemAn.ofType.get(sfxExpr.expr);

		if (!(exprType.actualType() instanceof SemPtr))
			throw new Report.Error(sfxExpr, "Type error: " + exprType + " is not a pointer");

		SemType type = ((SemPtr) exprType).baseType;
		SemAn.ofType.put(sfxExpr, type);
		return type;
	}

	// Value 10
	@Override
	public SemType visit(AstArrExpr arrExpr, Mode mode) {
		if (mode != Mode.FEET) return null;

		arrExpr.arr.accept(this, mode);
		arrExpr.idx.accept(this, mode);

		SemType arrType = SemAn.ofType.get(arrExpr.arr);
		SemType elemType = SemAn.ofType.get(arrExpr.idx);

		if (!(arrType.actualType() instanceof SemArr && elemType.actualType() instanceof SemInt))
			throw new Report.Error(arrExpr, "Type error: wrong array addressing (either not array or non-integer index)");

		SemType type = ((SemArr) arrType).elemType;
		SemAn.ofType.put(arrExpr, type);
		return type;
	}

	// Value 11
	@Override
	public SemType visit(AstRecExpr recExpr, Mode mode) {
		if (mode != Mode.FEET) return null;

		recExpr.rec.accept(this, mode);

		SemType recType = SemAn.ofType.get(recExpr.rec);

		if (!(recType.actualType() instanceof SemRec))
			throw new Report.Error(recExpr, "Type error: not a record");

		SymbTable table = symbTableMap.get(recType);

		AstCompDecl decl;
		try {
			decl = (AstCompDecl) table.fnd(recExpr.comp.name);
		} catch (SymbTable.CannotFndNameException e) {
			throw new Report.Error(recExpr, "Type error: " + recExpr.comp.name + " has not been declared");
		}

		SemType declType = SemAn.isType.get(decl.type);
		SemAn.ofType.put(recExpr, declType);
		SemAn.declaredAt.put(recExpr.comp, decl);
		return declType;
	}

	// Value 12
	@Override
	public SemType visit(AstCallExpr callExpr, Mode mode) {
		if (mode != Mode.FEET) return null;

		AstDecl decl = SemAn.declaredAt.get(callExpr);

		if (!(decl instanceof AstFunDecl funDecl))
			throw new Report.Error(callExpr, "Type error: not a function declaration");

		if (callExpr.args.size() != funDecl.pars.size())
			throw new Report.Error(callExpr, "Type error: mismatch between number of function parameters");

		for (int i = 0; i < callExpr.args.size(); i++) {
			AstExpr arg = callExpr.args.get(i);
			arg.accept(this, mode);

			SemType argType = SemAn.ofType.get(arg);
			SemType parType = SemAn.isType.get(funDecl.pars.get(i).type);

			if (!argType.getClass().equals(parType.getClass()))
				throw new Report.Error(arg, "Type error: mismatch between declared and called argument type");
		}

		SemType type = SemAn.isType.get(funDecl.type);
		SemAn.ofType.put(callExpr, type);
		return type;
	}

	// Value 13
	@Override
	public SemType visit(AstStmtExpr stmtExpr, Mode mode) {
		if (mode != Mode.FEET) return null;

		// Use last statement's index to find its type
		int lastIndex = stmtExpr.stmts.size() - 1;
		SemType semType = SemAn.ofType.get(stmtExpr.stmts.get(lastIndex));
		SemAn.ofType.put(stmtExpr, semType);
		return semType;
	}

	// Value 14
	@Override
	public SemType visit(AstCastExpr castExpr, Mode mode) {
		if (mode != Mode.FEET) return null;

		castExpr.expr.accept(this, mode);
		castExpr.type.accept(this, mode);

		SemType exprType = SemAn.ofType.get(castExpr.expr).actualType();
		SemType castType = SemAn.isType.get(castExpr.type);
		SemType actualCastType = castType.actualType();

		if (!((exprType instanceof SemChar || exprType instanceof SemInt || (exprType instanceof SemPtr &&
			(((SemPtr) exprType).baseType instanceof SemChar || ((SemPtr) exprType).baseType instanceof SemInt))) &&
			(actualCastType instanceof SemChar || actualCastType instanceof SemInt || (actualCastType instanceof SemPtr &&
				(((SemPtr) actualCastType).baseType instanceof SemChar || ((SemPtr) actualCastType).baseType instanceof SemInt)))))
			throw new Report.Error(castExpr, "Type error: invalid typecast expression");

		SemAn.ofType.put(castExpr, castType);
		return castType;
	}

	// Value 15 (left) is not needed

	// Value 15 (right)
	@Override
	public SemType visit(AstWhereExpr whereExpr, Mode mode) {
		whereExpr.decls.accept(this, mode);
		whereExpr.expr.accept(this, mode);

		SemType semType = SemAn.ofType.get(whereExpr.expr);
		SemAn.ofType.put(whereExpr, semType);
		return semType;
	}

	/*
	 * STATEMENTS
	 */

	// Statement 1
	@Override
	public SemType visit(AstAssignStmt assignStmt, Mode mode) {
		if (mode != Mode.FEET) return null;

		assignStmt.src.accept(this, mode);
		assignStmt.dst.accept(this, mode);

		SemType dstType = SemAn.ofType.get(assignStmt.dst).actualType();
		SemType srcType = SemAn.ofType.get(assignStmt.src).actualType();
		SemType type;

		if (!((dstType instanceof SemBool && srcType instanceof SemBool) ||
			(dstType instanceof SemChar && srcType instanceof SemChar) ||
			(dstType instanceof SemInt && srcType instanceof SemInt) ||
			(dstType instanceof SemPtr && srcType instanceof SemPtr &&
				((SemPtr) dstType).baseType == ((SemPtr) srcType).baseType) &&
				(((SemPtr) dstType).baseType instanceof SemBool || ((SemPtr) dstType).baseType instanceof SemChar ||
					((SemPtr) dstType).baseType instanceof SemInt)))
			throw new Report.Error(assignStmt, "Type error: invalid types in assignment");

			type = new SemVoid();
			SemAn.ofType.put(assignStmt, type);
			return type;
	}

	// Statement 2
	@Override
	public SemType visit(AstIfStmt ifStmt, Mode mode) {
		if (mode != Mode.FEET) return null;

		ifStmt.cond.accept(this, mode);
		ifStmt.thenStmt.accept(this, mode);
		ifStmt.elseStmt.accept(this, mode);

		if (!(SemAn.ofType.get(ifStmt.cond).actualType() instanceof SemBool))
			throw new Report.Error(ifStmt, "Type error: if statement condition must be a boolean expression");

		SemType semType = new SemVoid();
		SemAn.ofType.put(ifStmt, semType);
		return semType;
	}

	// Statement 3
	@Override
	public SemType visit(AstWhileStmt whileStmt, Mode mode) {
		if (mode != Mode.FEET) return null;

		whileStmt.cond.accept(this, mode);
		whileStmt.bodyStmt.accept(this, mode);

		if (!(SemAn.ofType.get(whileStmt.cond).actualType() instanceof SemBool))
			throw new Report.Error(whileStmt, "Type error: while statement condition must be a boolean expression");

		SemType semType = new SemVoid();
		SemAn.ofType.put(whileStmt, semType);
		return semType;
	}

	/*
	 * DECLARATIONS
	 */

	// Declaration 1
	@Override
	public SemType visit(AstTypeDecl typeDecl, Mode mode) {
		if (mode == Mode.HEAD)
			SemAn.declaresType.put(typeDecl, new SemName(typeDecl.name));

		if (mode != Mode.BODY) return null;

		typeDecl.type.accept(this, mode);

		SemName semName = SemAn.declaresType.get(typeDecl);
		semName.define(typeDecl.type.accept(this, mode));
		return SemAn.declaresType.get(typeDecl);
	}

	// Declaration 2
	@Override
	public SemType visit(AstVarDecl varDecl, Mode mode) {
		if (mode != Mode.BODY) return null;

		varDecl.type.accept(this, mode);

		SemType semType = SemAn.isType.get(varDecl.type);

		if (semType.actualType() instanceof SemVoid)
			throw new Report.Error(varDecl, "Type error: variable cannot be of type 'void'");

		SemName semName = new SemName(varDecl.name);
		semName.define(semType);
		SemAn.isType.put(varDecl.type, semType);
		return semType;
	}

	// Function declarations (3 & 4)
	@Override
	public SemType visit(AstFunDecl funDecl, Mode mode) {
		if (mode == Mode.HEAD && funDecl.pars != null)
			funDecl.pars.accept(this, mode);

		if (mode != Mode.BODY) return null;

		SemType funTyp = funDecl.type.accept(this, mode);
		SemType funType = funTyp.actualType();

		// Check function's return type
		if (!(funType instanceof SemVoid || funType instanceof SemBool || funType instanceof SemChar
			|| funType instanceof SemInt || (funType instanceof SemPtr && (((SemPtr) funType).baseType instanceof SemVoid
			|| ((SemPtr) funType).baseType instanceof SemBool || ((SemPtr) funType).baseType instanceof SemChar
			|| ((SemPtr) funType).baseType instanceof SemInt))))
			throw new Report.Error(funDecl, "Type error: invalid function type " + funTyp);

		if (funDecl.expr != null) {
			SemType exprType = funDecl.expr.accept(this, mode);

			// Check if function expression matches return type
			if (exprType != null && !funType.getClass().equals(exprType.getClass()))
				throw new Report.Error(funDecl, "Type error: mismatch between function expression and return types");
		}

		SemAn.isType.put(funDecl.type, funTyp);
		return funTyp;
	}
}
