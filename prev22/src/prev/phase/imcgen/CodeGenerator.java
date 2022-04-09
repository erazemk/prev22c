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
		// Get subexpressions
		ImcExpr subExpr1 = (ImcExpr) binExpr.fstExpr.accept(this, frame);
		ImcExpr subExpr2 = (ImcExpr) binExpr.sndExpr.accept(this, frame);

		ImcExpr expr = switch (binExpr.oper) {
			case OR -> new ImcBINOP(ImcBINOP.Oper.OR, subExpr1, subExpr2);
			case AND -> new ImcBINOP(ImcBINOP.Oper.AND, subExpr1, subExpr2);
			case EQU -> new ImcBINOP(ImcBINOP.Oper.EQU, subExpr1, subExpr2);
			case NEQ -> new ImcBINOP(ImcBINOP.Oper.NEQ, subExpr1, subExpr2);
			case LTH -> new ImcBINOP(ImcBINOP.Oper.LTH, subExpr1, subExpr2);
			case GTH -> new ImcBINOP(ImcBINOP.Oper.GTH, subExpr1, subExpr2);
			case LEQ -> new ImcBINOP(ImcBINOP.Oper.LEQ, subExpr1, subExpr2);
			case GEQ -> new ImcBINOP(ImcBINOP.Oper.GEQ, subExpr1, subExpr2);
			case ADD -> new ImcBINOP(ImcBINOP.Oper.ADD, subExpr1, subExpr2);
			case SUB -> new ImcBINOP(ImcBINOP.Oper.SUB, subExpr1, subExpr2);
			case MUL -> new ImcBINOP(ImcBINOP.Oper.MUL, subExpr1, subExpr2);
			case DIV -> new ImcBINOP(ImcBINOP.Oper.DIV, subExpr1, subExpr2);
			case MOD -> new ImcBINOP(ImcBINOP.Oper.MOD, subExpr1, subExpr2);
		};

		ImcGen.exprImc.put(binExpr, expr);
		return expr;
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
		// Get subexpression
		ImcExpr subExpr = (ImcExpr) pfxExpr.expr.accept(this, frame);

		ImcExpr expr = switch (pfxExpr.oper) {
			case NOT -> new ImcUNOP(ImcUNOP.Oper.NOT, subExpr);
			case ADD -> subExpr;
			case SUB -> new ImcUNOP(ImcUNOP.Oper.NEG, subExpr);
			default -> null;
		};

		ImcGen.exprImc.put(pfxExpr, expr);
		return expr;
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
