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
		return null;
	}

	public Vector<ImcStmt> visit(ImcESTMT eStmt, Object obj) {
		return null;
	}

	public Vector<ImcStmt> visit(ImcJUMP jump, Object obj) {
		return null;
	}

	public Vector<ImcStmt> visit(ImcLABEL label, Object obj) {
		return null;
	}

	public Vector<ImcStmt> visit(ImcMOVE move, Object obj) {
		return null;
	}

	public Vector<ImcStmt> visit(ImcSTMTS stmts, Object obj) {
		return null;
	}
}
