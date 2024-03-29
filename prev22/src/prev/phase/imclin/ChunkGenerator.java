package prev.phase.imclin;

import java.util.*;

import prev.data.ast.tree.decl.*;
import prev.data.ast.tree.expr.*;
import prev.data.ast.visitor.*;
import prev.data.mem.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.lin.*;
import prev.phase.imcgen.*;
import prev.phase.memory.*;

public class ChunkGenerator extends AstFullVisitor<Object, Object> {

	@Override
	public Object visit(AstAtomExpr atomExpr, Object arg) {
		if (atomExpr.type == AstAtomExpr.Type.STRING) {
			MemAbsAccess absAccess = Memory.strings.get(atomExpr);
			ImcLin.addDataChunk(new LinDataChunk(absAccess));
		}

		return null;
	}

	@Override
	public Object visit(AstFunDecl funDecl, Object arg) {
		if (funDecl.expr == null) return null;

		funDecl.expr.accept(this, arg);

		MemFrame frame = Memory.frames.get(funDecl);
		MemLabel entryLabel = new MemLabel();
		MemLabel exitLabel = new MemLabel();

		Vector<ImcStmt> canonStmts = new Vector<>();
		canonStmts.add(new ImcLABEL(entryLabel));
		ImcExpr bodyExpr = ImcGen.exprImc.get(funDecl.expr);
		ImcStmt bodyStmt = new ImcMOVE(new ImcTEMP(frame.RV), bodyExpr);
		canonStmts.addAll(bodyStmt.accept(new StmtCanonizer(), null));
		canonStmts.add(new ImcJUMP(exitLabel));

		Vector<ImcStmt> linearStmts = linearize (canonStmts);
		ImcLin.addCodeChunk(new LinCodeChunk(frame, linearStmts, entryLabel, exitLabel));

		return null;
	}

	@Override
	public Object visit(AstVarDecl varDecl, Object arg) {
		MemAccess access = Memory.accesses.get(varDecl);
		if (access instanceof MemAbsAccess absAccess) {
			ImcLin.addDataChunk(new LinDataChunk(absAccess));
		}
		return null;
	}

	private Vector<ImcStmt> linearize(Vector<ImcStmt> stmts) {
		Vector<ImcStmt> linearStmts = new Vector<>();
		for (ImcStmt stmt : stmts) {
			if (stmt instanceof ImcCJUMP imcCJump) {
				MemLabel negLabel = new MemLabel();
				linearStmts.add(new ImcCJUMP(imcCJump.cond, imcCJump.posLabel, negLabel));
				linearStmts.add(new ImcLABEL(negLabel));
				linearStmts.add(new ImcJUMP(imcCJump.negLabel));
			} else
				linearStmts.add(stmt);
		}
		return linearStmts;
	}

}
