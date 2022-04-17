package prev.phase.imclin;

import java.util.*;

import prev.data.ast.tree.expr.*;
import prev.data.mem.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.imc.visitor.*;

/**
 * Expression canonizer.
 */
public class ExprCanonizer implements ImcVisitor<ImcExpr, Vector<ImcStmt>> {

	public ImcExpr visit(ImcBINOP binOp, Vector<ImcStmt> stmts) {
		return null;
	}

	public ImcExpr visit(ImcCALL call, Vector<ImcStmt> stmts) {
		return null;
	}

	public ImcExpr visit(ImcCONST constant, Vector<ImcStmt> stmts) {
		return null;
	}

	public ImcExpr visit(ImcMEM mem, Vector<ImcStmt> stmts) {
		return null;
	}

	public ImcExpr visit(ImcNAME name, Vector<ImcStmt> stmts) {
		return null;
	}

	public ImcExpr visit(ImcSEXPR sExpr, Vector<ImcStmt> stmts) {
		return null;
	}

	public ImcExpr visit(ImcTEMP temp, Vector<ImcStmt> stmts) {
		return null;
	}

	public ImcExpr visit(ImcUNOP unOp, Vector<ImcStmt> stmts) {
		return null;
	}
}
