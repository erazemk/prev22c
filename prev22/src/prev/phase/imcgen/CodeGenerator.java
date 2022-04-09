package prev.phase.imcgen;

import java.util.*;

import prev.data.ast.tree.*;
import prev.data.ast.tree.decl.*;
import prev.data.ast.tree.expr.*;
import prev.data.ast.tree.stmt.*;
import prev.data.ast.tree.type.*;
import prev.data.ast.visitor.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.mem.*;
import prev.phase.memory.*;

public class CodeGenerator extends AstNullVisitor<Object, Stack<MemFrame>> {

	// GENERAL PURPOSE

	@Override
	public Object visit(AstTrees<? extends AstTree> trees, Stack<MemFrame> frame) {
		for (AstTree t : trees) {
			if (t != null) {
				t.accept(this, frame);
			}
		}

		return null;
	}

	// EXPRESSIONS

	@Override
	public ImcExpr visit(AstArrExpr arrExpr, Stack<MemFrame> frame) {
		return null;
	}

	@Override
	public ImcExpr visit(AstAtomExpr atomExpr, Stack<MemFrame> frame) {
		// Convert to constant or address based on type
		ImcExpr expr = switch (atomExpr.type) {
			case VOID -> new ImcCONST(42); // Null returns random value (undefined)
			case POINTER -> new ImcCONST(0); // Nil returns 0
			case BOOL -> new ImcCONST((atomExpr.value.equals("true")) ? 1 : 0); // Bool returns 0 or 1
			case CHAR -> new ImcCONST(atomExpr.value.charAt(0)); // ASCII value
			case INT -> new ImcCONST(Long.parseLong(atomExpr.value)); // Parsed value
			case STRING -> new ImcNAME(Memory.strings.get(atomExpr).label); // Returns the address of the label
		};

		ImcGen.exprImc.put(atomExpr, expr);
		return expr;
	}

	@Override
	public ImcExpr visit(AstBinExpr binExpr, Stack<MemFrame> frame) {
		return null;
	}

	@Override
	public ImcExpr visit(AstCallExpr callExpr, Stack<MemFrame> frame) {
		return null;
	}

	@Override
	public ImcExpr visit(AstCastExpr castExpr, Stack<MemFrame> frame) {
		return null;
	}

	@Override
	public ImcExpr visit(AstNameExpr nameExpr, Stack<MemFrame> frame) {
		return null;
	}

	@Override
	public ImcExpr visit(AstPfxExpr pfxExpr, Stack<MemFrame> frame) {
		return null;
	}

	@Override
	public ImcExpr visit(AstRecExpr recExpr, Stack<MemFrame> frame) {
		return null;
	}

	@Override
	public ImcExpr visit(AstSfxExpr sfxExpr, Stack<MemFrame> frame) {
		return null;
	}

	@Override
	public ImcExpr visit(AstStmtExpr stmtExpr, Stack<MemFrame> frame) {
		return null;
	}

	@Override
	public ImcExpr visit(AstWhereExpr whereExpr, Stack<MemFrame> frame) {
		return null;
	}

	// STATEMENTS

	@Override
	public ImcStmt visit(AstAssignStmt assignStmt, Stack<MemFrame> frame) {
		return null;
	}

	@Override
	public ImcStmt visit(AstExprStmt exprStmt, Stack<MemFrame> frame) {
		return null;
	}

	@Override
	public ImcStmt visit(AstIfStmt ifStmt, Stack<MemFrame> frame) {
		return null;
	}

	@Override
	public ImcStmt visit(AstWhileStmt whileStmt, Stack<MemFrame> frame) {
		return null;
	}
}
