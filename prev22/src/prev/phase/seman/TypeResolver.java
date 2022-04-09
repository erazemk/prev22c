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

	// Three passes are needed
	public enum Mode {
		HEAD,
		BODY,
		FEET // Used for properly resolving arrays and records
	}

	// Identifier for info reports
	private final String TAG = "[TypeResolver]: ";

	// Stores symbol tables for records
	private final HashMap<SemRec, SymbTable> recordMap = new HashMap<>();

	// GENERAL PURPOSE

	// Detect recursive types by adding them to a HashSet and then comparing new ones
	public void detectRecursiveType(AstType type, HashSet<Integer> set) {
		// Check if type is already in the set
		if (set.contains(type.id())) {
			throw new Report.Error(type, TAG + "recursive type");
		}

		// If not, add it to the set
		set.add(type.id());

		// Cast based on the type
		if (type instanceof AstNameType nameType) {
			AstDecl decl = SemAn.declaredAt.get(nameType);

			// We only need to check subtypes if the name is a type declaration
			if (decl instanceof AstTypeDecl typeDecl) {
				detectRecursiveType(typeDecl.type, set);
			}
		} else if (type instanceof AstRecType recType) {
			// Check all components' types
			for (AstCompDecl compDecl: recType.comps) {
				detectRecursiveType(compDecl.type, set);
			}
		} else if (type instanceof AstArrType arrType) {
			detectRecursiveType(arrType.elemType, set);
		}
	}

	@Override
	public SemType visit(AstTrees<? extends AstTree> trees, Mode mode) {
		// First pass checks for recursion and declares a new type
		for (AstTree t : trees) {
			if (t instanceof AstTypeDecl) {
				t.accept(this, Mode.HEAD);
			}
		}

		// Second pass resolves all non-array and non-record types
		for (AstTree t : trees) {
			if (t instanceof AstTypeDecl) {
				t.accept(this, Mode.BODY);
			}
		}

		// Third pass resolves arrays and records
		for (AstTree t : trees) {
			if (t instanceof AstTypeDecl) {
				t.accept(this, Mode.FEET);
			}
		}

		// Mode doesn't matter in this case
		for (AstTree t : trees) {
			if (t instanceof AstVarDecl) {
				t.accept(this, Mode.HEAD);
			}
		}

		// First pass resolves parameters and function type
		for (AstTree t : trees) {
			if (t instanceof AstFunDecl) {
				t.accept(this, Mode.HEAD);
			}
		}

		// Second pass resolves the expression (mode doesn't matter)
		for (AstTree t : trees) {
			if (t instanceof AstFunDecl) {
				t.accept(this, Mode.BODY);
			}
		}

		return null;
	}

	// DECLARATIONS

	@Override
	public SemType visit(AstCompDecl compDecl, Mode mode) {
		SemType type = compDecl.type.accept(this, mode);
		SemAn.isType.put(compDecl.type, type);
		return type;
	}
	@Override
	public SemType visit(AstFunDecl funDecl, Mode mode) {
		// First pass
		if (mode == Mode.HEAD) {
			for (AstParDecl par : funDecl.pars) {
				par.accept(this, mode);
			}

			SemType funType = funDecl.type.accept(this, mode);

			SemType actualFunType = funType.actualType();
			if (actualFunType instanceof SemArr || actualFunType instanceof SemRec) {
				throw new Report.Error(funDecl, TAG + "invalid function type " + funType.getClass().getSimpleName());
			}

			SemAn.isType.put(funDecl.type, funType);
			return null;
		}

		// Second pass
		if (funDecl.expr != null) {
			SemType exprType = funDecl.expr.accept(this, mode).actualType();
			SemType funType = SemAn.isType.get(funDecl.type).actualType();

			if (exprType != null && !funType.getClass().equals(exprType.getClass())) {
				throw new Report.Error(funDecl, TAG + "mismatch between function expression and return types");
			}
		}

		return null;
	}

	@Override
	public SemType visit(AstParDecl parDecl, Mode mode) {
		SemType parType = parDecl.type.accept(this, mode);

		SemType actualParType = parType.actualType();
		if (!(actualParType instanceof SemBool || actualParType instanceof SemChar ||
			actualParType instanceof SemInt || actualParType instanceof SemPtr)) {
			throw new Report.Error(parDecl, TAG + "parameter must be of type 'bool', " +
				"'char' or 'int'");
		}

		return parType;
	}

	@Override
	public SemType visit(AstTypeDecl typeDecl, Mode mode) {
		if (mode == Mode.HEAD) {
			// First pass check for recursion and declares the name
			detectRecursiveType(typeDecl.type, new HashSet<>());
			SemAn.declaresType.put(typeDecl, new SemName(typeDecl.name));
		} else if (mode == Mode.BODY && !(typeDecl.type instanceof AstRecType || typeDecl.type instanceof AstArrType)) {
			// Second pass resolves all non-record and non-array types
			SemType type = typeDecl.type.accept(this, mode);
			SemAn.declaresType.get(typeDecl).define(type);
		} else if (mode == Mode.FEET) {
			// Third pass resolves all types, but most importantly, arrays and records
			SemType type = typeDecl.type.accept(this, mode);

			Report.info(typeDecl, TAG + "type in feet: " + type);

			SemType declaredType = SemAn.declaresType.get(typeDecl).type();
			if (declaredType == null) {
				SemAn.declaresType.get(typeDecl).define(type);
			}
		}

		return null;
	}

	@Override
	public SemType visit(AstVarDecl varDecl, Mode mode) {
		varDecl.type.accept(this, mode);

		SemType semType = SemAn.isType.get(varDecl.type);
		if (semType.actualType() instanceof SemVoid) {
			throw new Report.Error(varDecl, TAG + "variable cannot be of type 'void'");
		}

		SemAn.isType.put(varDecl.type, semType);
		return semType;
	}

	// EXPRESSIONS

	@Override
	public SemType visit(AstArrExpr arrExpr, Mode mode) {
		arrExpr.arr.accept(this, mode);
		arrExpr.idx.accept(this, mode);

		SemType arrType = SemAn.ofType.get(arrExpr.arr);
		SemType elemType = SemAn.ofType.get(arrExpr.idx);

		if (!(arrType.actualType() instanceof SemArr && elemType.actualType() instanceof SemInt)) {
			throw new Report.Error(arrExpr, TAG + "wrong array addressing (either not array or " +
				"non-integer index)");
		}

		SemType type = ((SemArr) arrType).elemType;
		SemAn.ofType.put(arrExpr, type);
		return type;
	}

	@Override
	public SemType visit(AstAtomExpr atomExpr, Mode mode) {
		SemType type = switch(atomExpr.type) {
			case VOID -> new SemVoid();
			case POINTER -> new SemPtr(new SemVoid());
			case STRING -> new SemPtr(new SemChar());
			case BOOL -> new SemBool();
			case CHAR -> new SemChar();
			case INT -> new SemInt();
		};

		SemAn.ofType.put(atomExpr, type);
		return type;
	}
	@Override
	public SemType visit(AstBinExpr binExpr, Mode mode) {
		SemType fstExprType = binExpr.fstExpr.accept(this, mode).actualType();
		SemType sndExprType = binExpr.sndExpr.accept(this, mode).actualType();

		SemType type = null;
		switch (binExpr.oper) {
			case AND, OR:
				if (!(fstExprType instanceof SemBool && sndExprType instanceof SemBool)) {
					throw new Report.Error(binExpr, TAG + "cannot use operators '&' and '|' in " +
						"non-boolean expressions");
				}

				type = new SemBool();
				break;

			case ADD, SUB, MUL, DIV, MOD:
				if (!(fstExprType instanceof SemInt && sndExprType instanceof SemInt)) {
					throw new Report.Error(binExpr, TAG + "cannot use operators '+', '-', '*', '/', " +
						"'%' in non-integer expressions");
				}

				type = new SemInt();
				break;

			case EQU, NEQ:
				if ((fstExprType instanceof SemBool && sndExprType instanceof SemBool) ||
					(fstExprType instanceof SemChar && sndExprType instanceof SemChar) ||
					(fstExprType instanceof SemInt && sndExprType instanceof SemInt) ||
					(fstExprType instanceof SemPtr && sndExprType instanceof SemPtr)) {
					type = new SemBool();
				} else {
					throw new Report.Error(binExpr, TAG + "cannot use operators '==' and '!=' with " +
						"a combination of expressions of type " + fstExprType.getClass().getSimpleName() + " and " +
						sndExprType.getClass().getSimpleName());
				}
				break;

			case LEQ, GEQ, LTH, GTH:
				if ((fstExprType instanceof SemChar && sndExprType instanceof SemChar) ||
					(fstExprType instanceof SemInt && sndExprType instanceof SemInt) ||
					(fstExprType instanceof SemPtr && sndExprType instanceof SemPtr)) {
					type = new SemBool();
				} else {
					throw new Report.Error(binExpr, TAG + "cannot use operators '<=', '>=', '<' and " +
						"'>' with a combination of expressions of type " + fstExprType.getClass().getSimpleName() +
						" and " + sndExprType.getClass().getSimpleName());
				}
				break;
		}

		SemAn.ofType.put(binExpr, type);
		return type;
	}

	@Override
	public SemType visit(AstCallExpr callExpr, Mode mode) {
		AstDecl decl = SemAn.declaredAt.get(callExpr);

		if (!(decl instanceof AstFunDecl funDecl)) {
			throw new Report.Error(callExpr, TAG + "not a function declaration");
		}

		if (callExpr.args.size() != funDecl.pars.size()) {
			throw new Report.Error(callExpr, TAG + "mismatch between number of function parameters");
		}

		for (int i = 0; i < callExpr.args.size(); i++) {
			AstExpr arg = callExpr.args.get(i);
			AstParDecl parDecl = funDecl.pars.get(i);

			arg.accept(this, mode);
			parDecl.accept(this, mode);

			SemType argType = SemAn.ofType.get(arg).actualType();
			SemType parType = SemAn.isType.get(funDecl.pars.get(i).type).actualType();

			if (!argType.getClass().equals(parType.getClass())) {
				throw new Report.Error(arg, TAG + "mismatch between declared and called argument type");
			}
		}

		SemType type = SemAn.isType.get(funDecl.type);
		SemAn.ofType.put(callExpr, type);
		return type;
	}

	@Override
	public SemType visit(AstCastExpr castExpr, Mode mode) {
		castExpr.expr.accept(this, mode);
		castExpr.type.accept(this, mode);

		SemType exprType = SemAn.ofType.get(castExpr.expr).actualType();
		SemType castType = SemAn.isType.get(castExpr.type);
		SemType actualCastType = castType.actualType();

		Report.info(castExpr.expr, TAG + "expr type: " + exprType);
		Report.info(castExpr.type, TAG + "cast type: " + actualCastType);

		if (!((exprType instanceof SemChar || exprType instanceof SemInt || exprType instanceof SemPtr) &&
			(actualCastType instanceof SemChar || actualCastType instanceof SemInt || actualCastType instanceof SemPtr))) {
			throw new Report.Error(castExpr, TAG + "invalid typecast expression");
		}

		SemAn.ofType.put(castExpr, castType);
		return castType;
	}

	@Override
	public SemType visit(AstNameExpr nameExpr, Mode mode) {
		AstDecl decl = SemAn.declaredAt.get(nameExpr);
		SemType type;

		if (decl instanceof AstFunDecl) {
			if (((AstFunDecl) decl).pars != null) {
				throw new Report.Error(nameExpr, TAG + "missing arguments for function call");
			}

			type = SemAn.isType.get(((AstFunDecl) decl).type);
		} else if (decl instanceof AstVarDecl) {
			type = SemAn.isType.get(((AstVarDecl) decl).type);
		} else if (decl instanceof AstParDecl) {
			type = SemAn.isType.get(((AstParDecl) decl).type);
		} else {
			throw new Report.Error(nameExpr, TAG + "you can only declare variables or parameters");
		}

		SemAn.ofType.put(nameExpr, type);
		return type;
	}

	@Override
	public SemType visit(AstPfxExpr pfxExpr, Mode mode) {
		SemType exprType = pfxExpr.expr.accept(this, mode);
		SemType actualExprType = exprType.actualType();

		SemType type = null;
		switch (pfxExpr.oper) {
			case NOT -> {
				if (!(actualExprType instanceof SemBool)) {
					throw new Report.Error(pfxExpr, TAG + "cannot use negation with non-boolean expression");
				}

				type = new SemBool();
			}
			case ADD, SUB -> {
				if (!(actualExprType instanceof SemInt)) {
					throw new Report.Error(pfxExpr, TAG + "cannot add or subtract from non-integer expression");
				}

				type = new SemInt();
			}
			case PTR -> type = new SemPtr(exprType);
			case NEW -> {
				if (!(actualExprType instanceof SemInt)) {
					throw new Report.Error(pfxExpr, TAG + "cannot use 'new' with non-integer expression");
				}

				type = new SemPtr(new SemVoid());
			}
			case DEL -> {
				if (!(actualExprType instanceof SemPtr)) {
					throw new Report.Error(pfxExpr, TAG + "cannot use 'del' with non-pointer expression");
				}

				type = new SemVoid();
			}
		}

		SemAn.ofType.put(pfxExpr, type);
		return type;
	}

	@Override
	public SemType visit(AstRecExpr recExpr, Mode mode) {
		recExpr.rec.accept(this, mode);

		SemType recType = SemAn.ofType.get(recExpr.rec).actualType();
		if (!(recType instanceof SemRec)) {
			throw new Report.Error(recExpr, TAG + "not a record");
		}

		Report.info(recExpr, TAG + recType + ": " + recType);
		Report.info(recExpr, TAG + "records: " + recordMap);

		SymbTable compNames = recordMap.get((SemRec) recType);

		Report.info(recExpr, TAG + recordMap.get(recType));
		Report.info(recExpr, TAG + recType);

		AstCompDecl compDecl;

		try {
			compDecl = (AstCompDecl) compNames.fnd(recExpr.comp.name);
		} catch (SymbTable.CannotFndNameException e) {
			throw new Report.Error(recExpr, TAG + "not a component of this record");
		}

		SemType compType = SemAn.isType.get(compDecl.type);
		SemAn.ofType.put(recExpr, compType);
		SemAn.declaredAt.put(recExpr.comp, compDecl);
		return compType;
	}

	@Override
	public SemType visit(AstSfxExpr sfxExpr, Mode mode) {
		sfxExpr.expr.accept(this, mode);
		SemType exprType = SemAn.ofType.get(sfxExpr.expr).actualType();

		if (!(exprType instanceof SemPtr)) {
			throw new Report.Error(sfxExpr, TAG + sfxExpr.expr + " is not a pointer");
		}

		SemType type = ((SemPtr) exprType).baseType;
		SemAn.ofType.put(sfxExpr, type);
		return type;
	}

	@Override
	public SemType visit(AstStmtExpr stmtExpr, Mode mode) {
		SemType type = null;

		for (AstStmt stmt: stmtExpr.stmts) {
			type = stmt.accept(this, mode);
		}

		SemAn.ofType.put(stmtExpr, type);
		return type;
	}

	@Override
	public SemType visit(AstWhereExpr whereExpr, Mode mode) {
		whereExpr.decls.accept(this, mode);
		whereExpr.expr.accept(this, mode);

		SemType semType = SemAn.ofType.get(whereExpr.expr);
		SemAn.ofType.put(whereExpr, semType);
		return semType;
	}

	// STATEMENTS

	@Override
	public SemType visit(AstAssignStmt assignStmt, Mode mode) {
		assignStmt.src.accept(this, mode);
		assignStmt.dst.accept(this, mode);

		SemType dstType = SemAn.ofType.get(assignStmt.dst).actualType();
		SemType srcType = SemAn.ofType.get(assignStmt.src).actualType();

		if (!((dstType instanceof SemBool && srcType instanceof SemBool) ||
			(dstType instanceof SemChar && srcType instanceof SemChar) ||
			(dstType instanceof SemInt && srcType instanceof SemInt) || (
			(dstType instanceof SemPtr && srcType instanceof SemPtr)))) {
			throw new Report.Error(assignStmt, TAG + "invalid types in assignment");
		}

		SemType type = new SemVoid();
		SemAn.ofType.put(assignStmt, type);
		return type;
	}

	@Override
	public SemType visit(AstExprStmt exprStmt, Mode mode) {
		SemType type = exprStmt.expr.accept(this, mode);
		SemAn.ofType.put(exprStmt, type);
		return type;
	}

	@Override
	public SemType visit(AstIfStmt ifStmt, Mode mode) {
		SemType condType = ifStmt.cond.accept(this, mode);

		Report.info(ifStmt, TAG + "condition type: " + condType);

		if (!(condType.actualType() instanceof SemBool)) {
			throw new Report.Error(ifStmt, TAG + "if statement condition must be a boolean expression");
		}

		ifStmt.thenStmt.accept(this, mode);
		ifStmt.elseStmt.accept(this, mode);

		SemType semType = new SemVoid();
		SemAn.ofType.put(ifStmt, semType);
		return semType;
	}

	@Override
	public SemType visit(AstWhileStmt whileStmt, Mode mode) {
		whileStmt.cond.accept(this, mode);
		whileStmt.bodyStmt.accept(this, mode);

		if (!(SemAn.ofType.get(whileStmt.cond).actualType() instanceof SemBool)) {
			throw new Report.Error(whileStmt, TAG + "while statement condition must be a boolean expression");
		}

		SemType semType = new SemVoid();
		SemAn.ofType.put(whileStmt, semType);
		return semType;
	}

	// TYPES

	@Override
	public SemType visit(AstArrType arrType, Mode mode) {
		SemType elemType = arrType.elemType.accept(this, mode);
		arrType.numElems.accept(this, mode);

		if (elemType.actualType() instanceof SemVoid) {
			throw new Report.Error(arrType, TAG + "array element cannot be of type 'void'");
		}

		if (!(arrType.numElems instanceof AstAtomExpr)) {
			throw new Report.Error(arrType, TAG + "array index must be of type 'int'");
		}

		if (((AstAtomExpr) arrType.numElems).type != AstAtomExpr.Type.INT) {
			throw new Report.Error(arrType, TAG + "array index must be of type 'int'");
		}

		long numElems;
		try {
			numElems = Long.parseLong(((AstAtomExpr) arrType.numElems).value);
		} catch (NumberFormatException nfe) {
			throw new Report.Error(arrType, TAG + "invalid array size");
		}

		if (numElems < 0) {
			throw new Report.Error(arrType, TAG + "array index cannot be negative");
		}

		SemType type = new SemArr(elemType, numElems);
		SemAn.isType.put(arrType, type);
		return type;
	}

	@Override
	public SemType visit(AstAtomType atomType, Mode mode) {
		SemType type = switch (atomType.type) {
			case VOID -> new SemVoid();
			case CHAR -> new SemChar();
			case INT -> new SemInt();
			case BOOL -> new SemBool();
		};

		SemAn.isType.put(atomType, type);
		return type;
	}

	@Override
	public SemType visit(AstNameType nameType, Mode mode) {
		// Get declaration from name
		AstDecl nameDecl = SemAn.declaredAt.get(nameType);
		if (!(nameDecl instanceof AstTypeDecl)) {
			throw new Report.Error(nameType, TAG + "undeclared type");
		}

		// Get semantic name from type declaration
		SemType type = SemAn.declaresType.get((AstTypeDecl) nameDecl);
		SemAn.isType.put(nameType, type);
		return type;
	}

	@Override
	public SemType visit(AstPtrType ptrType, Mode mode) {
		SemType baseType = ptrType.baseType.accept(this, mode);
		if (baseType == null) {
			throw new Report.Error(ptrType, TAG + "undeclared pointer type");
		}

		SemType type = new SemPtr(baseType);
		SemAn.isType.put(ptrType, type);
		return type;
	}

	@Override
	public SemType visit(AstRecType recType, Mode mode) {
		// Create a new symbol table and component type table for the record
		SymbTable compNames = new SymbTable();
		Vector<SemType> compTypes = new Vector<>();

		for (AstCompDecl comp : recType.comps) {
			SemType compType = comp.accept(this, mode);
			if (compType.actualType() instanceof SemVoid) {
				throw new Report.Error(comp, TAG + "record component must be non-void");
			}

			try {
				compNames.ins(comp.name, comp);
			} catch (SymbTable.CannotInsNameException e) {
				throw new Report.Error(comp, TAG + "could not insert component name into symbol table");
			}

			compTypes.add(SemAn.isType.get(comp.type));
		}

		SemRec rec = new SemRec(compTypes);
		SemAn.isType.put(recType, rec);
		recordMap.put(rec, compNames);
		return rec;
	}
}
