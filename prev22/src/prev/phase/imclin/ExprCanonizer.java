package prev.phase.imclin;

import java.util.*;

import prev.common.report.Report;
import prev.data.ast.tree.expr.*;
import prev.data.mem.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.imc.visitor.*;

/**
 * Expression canonizer.
 */
public class ExprCanonizer implements ImcVisitor<ImcExpr, Vector<ImcStmt>> {

	// Identifier for info reports
	private final String TAG = "[ExprCanonizer]: ";

	public ImcExpr visit(ImcBINOP binOp, Vector<ImcStmt> stmts) {
		// Store subexpression results
		MemTemp memTemp1 = new MemTemp();
		MemTemp memTemp2 = new MemTemp();

		ImcTEMP temp1 = new ImcTEMP(memTemp1);
		ImcTEMP temp2 = new ImcTEMP(memTemp2);

		stmts.add(new ImcMOVE(temp1, binOp.fstExpr.accept(this, stmts)));
		stmts.add(new ImcMOVE(temp2, binOp.sndExpr.accept(this, stmts)));

		Report.info(TAG + "(binOp): " + binOp.fstExpr + "=" + memTemp1 + ", " + binOp.sndExpr + "=" + memTemp2);

		return new ImcBINOP(binOp.oper, temp1, temp2);
	}

	public ImcExpr visit(ImcCALL call, Vector<ImcStmt> stmts) {
		Vector<ImcExpr> args = new Vector<>();

		for (ImcExpr arg : call.args) {
			MemTemp memTemp = new MemTemp();
			ImcTEMP temp = new ImcTEMP(memTemp);
			stmts.add(new ImcMOVE(temp, arg.accept(this, stmts)));
			args.add(temp);
		}

		return new ImcCALL(call.label, call.offs, args);
	}

	public ImcExpr visit(ImcCONST constant, Vector<ImcStmt> stmts) {
		return constant;
	}

	public ImcExpr visit(ImcMEM mem, Vector<ImcStmt> stmts) {
		MemTemp memTemp = new MemTemp();
		ImcTEMP temp = new ImcTEMP(memTemp);
		stmts.add(new ImcMOVE(temp, new ImcMEM(mem.addr.accept(this, stmts))));

		Report.info(TAG + "(mem): " + mem.addr + "=" + memTemp);

		return temp;
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
