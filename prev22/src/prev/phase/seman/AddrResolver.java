package prev.phase.seman;

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
	public Object visit(AstNameExpr nameExpr, Object mode) {
		AstDecl decl = SemAn.declaredAt.get(nameExpr);

		if (decl instanceof AstVarDecl || decl instanceof AstParDecl)
			SemAn.isAddr.put(nameExpr, true);

		return null;
	}

	@Override
	public Object visit(AstSfxExpr sfxExpr, Object mode) {
		SemType type = SemAn.ofType.get(sfxExpr);

		if (type instanceof SemPtr)
			SemAn.isAddr.put(sfxExpr, true);

		return null;
	}

	@Override
	public Object visit(AstArrExpr arrExpr, Object mode) {
		if (SemAn.isAddr.get(arrExpr.arr))
			SemAn.isAddr.put(arrExpr, true);

		return null;
	}

	@Override
	public Object visit(AstRecExpr recExpr, Object mode) {
		if (SemAn.isAddr.get(recExpr.rec))
			SemAn.isAddr.put(recExpr, true);

		return null;
	}
}
