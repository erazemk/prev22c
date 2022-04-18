package prev.phase.imclin;

import java.util.*;

import prev.data.mem.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.imc.visitor.*;

/**
 * Statement canonizer.
 */
public class StmtCanonizer implements ImcVisitor<Vector<ImcStmt>, Object> {

	public Vector<ImcStmt> visit(ImcCJUMP cjump, Object obj) {
		Vector<ImcStmt> stmts = new Vector<>();
		ImcExpr cond = cjump.cond.accept(new ExprCanonizer(), stmts);
		return new Vector<>(List.of(new ImcCJUMP(cond, cjump.posLabel, cjump.negLabel)));
	}

	public Vector<ImcStmt> visit(ImcESTMT eStmt, Object obj) {
		Vector<ImcStmt> stmts = new Vector<>();
		ImcExpr expr = eStmt.expr.accept(new ExprCanonizer(), stmts);
		return new Vector<>(List.of(new ImcESTMT(expr)));
	}

	public Vector<ImcStmt> visit(ImcJUMP jump, Object obj) {
		return new Vector<>(List.of(jump));
	}

	public Vector<ImcStmt> visit(ImcLABEL label, Object obj) {
		return new Vector<>(List.of(label));
	}

	public Vector<ImcStmt> visit(ImcMOVE move, Object obj) {
		return null;
	}

	public Vector<ImcStmt> visit(ImcSTMTS stmts, Object obj) {
		Vector<ImcStmt> stmtVector = new Vector<>();

		for (ImcStmt stmt : stmts.stmts) {
			stmtVector.addAll(stmt.accept(this, null));
		}

		return stmtVector;
	}
}
