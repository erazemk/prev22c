package prev.phase.seman;

import prev.common.report.Report;
import prev.data.ast.tree.AstTree;
import prev.data.ast.tree.AstTrees;
import prev.data.ast.tree.decl.AstDecl;
import prev.data.ast.tree.decl.AstParDecl;
import prev.data.ast.tree.decl.AstVarDecl;
import prev.data.ast.tree.expr.*;
import prev.data.ast.tree.stmt.AstAssignStmt;
import prev.data.ast.visitor.*;
import prev.data.typ.SemPtr;
import prev.data.typ.SemType;

/**
 * Address resolver.
 *
 * The address resolver finds out which expressions denote lvalues and leaves
 * the information in {@link SemAn#isAddr}.
 */
public class AddrResolver extends AstFullVisitor<Object, Object> {

	@Override
	public Object visit(AstTrees<? extends AstTree> trees, Object mode) {
		for (AstTree t : trees) {
			if (t != null) t.accept(this, mode);
		}

		return null;
	}

	@Override
	public Object visit(AstNameExpr nameExpr, Object mode) {
		AstDecl decl = SemAn.declaredAt.get(nameExpr);

		if (decl instanceof AstVarDecl || decl instanceof AstParDecl) {
			SemAn.isAddr.put(nameExpr, true);
		}

		return null;
	}

	@Override
	public Object visit(AstSfxExpr sfxExpr, Object mode) {
		SemType type = (SemType) sfxExpr.expr.accept(this, mode);

		if (type instanceof SemPtr) {
			SemAn.isAddr.put(sfxExpr, true);
		}

		return null;
	}

	@Override
	public Object visit(AstArrExpr arrExpr, Object mode) {
		arrExpr.arr.accept(this, mode);
		arrExpr.idx.accept(this, mode);
		Boolean isAddr = SemAn.isAddr.get(arrExpr.arr);

		if (isAddr != null && isAddr) {
			SemAn.isAddr.put(arrExpr, true);
		}

		return null;
	}

	@Override
	public Object visit(AstRecExpr recExpr, Object mode) {
		recExpr.rec.accept(this, mode);
		recExpr.comp.accept(this, mode);
		Boolean isAddr = SemAn.isAddr.get(recExpr.rec);

		if (isAddr != null && isAddr) {
			SemAn.isAddr.put(recExpr, true);
		}

		return null;
	}

	@Override
	public Object visit(AstAssignStmt assignStmt, Object mode) {
		assignStmt.src.accept(this, mode);
		assignStmt.dst.accept(this, mode);

		Boolean isAddr = SemAn.isAddr.get(assignStmt.dst);

		if (isAddr == null || !isAddr) {
			throw new Report.Error(assignStmt, "Type error: cannot assign to non-identifier");
		}

		return null;
	}
}
