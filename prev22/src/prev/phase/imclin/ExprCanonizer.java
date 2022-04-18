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
		// Store subexpression results
		MemTemp temp1 = new MemTemp();
		MemTemp temp2 = new MemTemp();

		stmts.add(new ImcMOVE(new ImcTEMP(temp1), binOp.fstExpr.accept(this, stmts)));
		stmts.add(new ImcMOVE(new ImcTEMP(temp2), binOp.sndExpr.accept(this, stmts)));

		return new ImcBINOP(binOp.oper, new ImcTEMP(temp1), new ImcTEMP(temp2));
	}

	public ImcExpr visit(ImcCALL call, Vector<ImcStmt> stmts) {
		Vector<ImcExpr> args = new Vector<>();

		for (ImcExpr arg : call.args) {
			MemTemp temp = new MemTemp();
			stmts.add(new ImcMOVE(new ImcTEMP(temp), arg.accept(this, stmts)));
			args.add(new ImcTEMP(temp));
		}

		return new ImcCALL(call.label, call.offs, args);
	}

	public ImcExpr visit(ImcCONST constant, Vector<ImcStmt> stmts) {
		return constant;
	}

	public ImcExpr visit(ImcMEM mem, Vector<ImcStmt> stmts) {
		return new ImcMEM(mem.addr.accept(this, stmts));
	}

	public ImcExpr visit(ImcNAME name, Vector<ImcStmt> stmts) {
		return name;
	}

	public ImcExpr visit(ImcSEXPR sExpr, Vector<ImcStmt> stmts) {
		stmts.addAll(sExpr.stmt.accept(new StmtCanonizer(), null));
		return sExpr.expr.accept(this, stmts);
	}

	public ImcExpr visit(ImcTEMP temp, Vector<ImcStmt> stmts) {
		return temp;
	}

	public ImcExpr visit(ImcUNOP unOp, Vector<ImcStmt> stmts) {
		return new ImcUNOP(unOp.oper, unOp.subExpr.accept(this, stmts));
	}
}
