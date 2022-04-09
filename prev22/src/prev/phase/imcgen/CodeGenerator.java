package prev.phase.imcgen;

import java.util.*;

import prev.common.report.Report;
import prev.data.ast.tree.*;
import prev.data.ast.tree.decl.*;
import prev.data.ast.tree.expr.*;
import prev.data.ast.tree.stmt.*;
import prev.data.ast.tree.type.*;
import prev.data.ast.visitor.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.mem.*;
import prev.data.typ.*;
import prev.phase.memory.*;
import prev.phase.seman.SemAn;

public class CodeGenerator extends AstNullVisitor<Object, Stack<MemFrame>> {

	// GENERAL PURPOSE

	// Identifier for info reports
	private final String TAG = "[CodeGenerator]: ";

	@Override
	public Object visit(AstTrees<? extends AstTree> trees, Stack<MemFrame> frames) {
		for (AstTree t : trees) {
			if (t != null) {
				t.accept(this, frames);
			}
		}

		return null;
	}

	// EXPRESSIONS

	@Override
	public ImcExpr visit(AstArrExpr arrExpr, Stack<MemFrame> frames) {
		// Get the array
		ImcExpr arr = (ImcExpr) arrExpr.arr.accept(this, frames);

		if (!(arr instanceof ImcMEM memAccess)) {
			throw new Report.Error(arrExpr, TAG + "array expression is not a memory access");
		}

		// Get the address of the first element
		ImcExpr firstElemAddr = memAccess.addr;

		// Get the element type, so we can calculate any element's address
		SemType elemType = ((SemArr) SemAn.ofType.get(arrExpr.arr).actualType()).elemType;

		// Get the index
		ImcExpr index = (ImcExpr) arrExpr.idx.accept(this, frames);

		// indexAccess == firstElemAddr + index * sizeOf(elementType)
		ImcExpr indexAccess = new ImcBINOP(ImcBINOP.Oper.MUL, index, new ImcCONST(elemType.size()));
		indexAccess = new ImcBINOP(ImcBINOP.Oper.ADD, firstElemAddr, indexAccess);

		ImcExpr expr = new ImcMEM(indexAccess);
		ImcGen.exprImc.put(arrExpr, expr);
		return expr;
	}

	@Override
	public ImcExpr visit(AstAtomExpr atomExpr, Stack<MemFrame> frames) {
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
	public ImcExpr visit(AstBinExpr binExpr, Stack<MemFrame> frames) {
		// Get subexpressions
		ImcExpr subExpr1 = (ImcExpr) binExpr.fstExpr.accept(this, frames);
		ImcExpr subExpr2 = (ImcExpr) binExpr.sndExpr.accept(this, frames);

		ImcBINOP.Oper oper = switch (binExpr.oper) {
			case OR -> ImcBINOP.Oper.OR;
			case AND -> ImcBINOP.Oper.AND;
			case EQU -> ImcBINOP.Oper.EQU;
			case NEQ -> ImcBINOP.Oper.NEQ;
			case LTH -> ImcBINOP.Oper.LTH;
			case GTH -> ImcBINOP.Oper.GTH;
			case LEQ -> ImcBINOP.Oper.LEQ;
			case GEQ -> ImcBINOP.Oper.GEQ;
			case ADD -> ImcBINOP.Oper.ADD;
			case SUB -> ImcBINOP.Oper.SUB;
			case MUL -> ImcBINOP.Oper.MUL;
			case DIV -> ImcBINOP.Oper.DIV;
			case MOD -> ImcBINOP.Oper.MOD;
		};

		ImcExpr expr = new ImcBINOP(oper, subExpr1, subExpr2);
		ImcGen.exprImc.put(binExpr, expr);
		return expr;
	}

	@Override
	public ImcExpr visit(AstCallExpr callExpr, Stack<MemFrame> frames) {
		// TODO (EX12)
		return null;
	}

	@Override
	public ImcExpr visit(AstCastExpr castExpr, Stack<MemFrame> frames) {
		// Get the subexpression
		ImcExpr expr = (ImcExpr) castExpr.expr.accept(this, frames);

		// Resolve the type
		SemType type = SemAn.isType.get(castExpr.type);

		// If the type is a character use mod(256)
		if (type.actualType() instanceof SemChar) {
			expr = new ImcBINOP(ImcBINOP.Oper.MOD, expr, new ImcCONST(256));
		}

		ImcGen.exprImc.put(castExpr, expr);
		return expr;
	}

	@Override
	public ImcExpr visit(AstNameExpr nameExpr, Stack<MemFrame> frames) {
		// Get the original declaration
		AstDecl decl = SemAn.declaredAt.get(nameExpr);

		if (!(decl instanceof AstMemDecl memDecl)) {
			throw new Report.Error(nameExpr, TAG + "not a memory declaration");
		}

		// Get the variable from memory
		MemAccess memAccess = Memory.accesses.get(memDecl);

		ImcExpr addr;
		// Memory access can be relative or absolute
		if (memAccess instanceof MemAbsAccess absAccess) {
			// Absolute is easy, just get the label
			addr = new ImcNAME(absAccess.label);
		} else {
			// If relative we have to calculate offsets
			MemRelAccess relAccess = (MemRelAccess) memAccess;

			/* FP holds the location of the previous one, so we need to go n-levels up (delta of depths times)
				and then get the variable at the offset of the last level */
			int deltaDepth = (frames.size() - 1) - relAccess.depth;
			ImcExpr tempFP = new ImcTEMP(frames.peek().FP);

			for (int i = 0; i < deltaDepth; i++) {
				tempFP = new ImcMEM(tempFP);
			}

			// addr == location of the last FP + offset of the variable in that frame
			addr = new ImcBINOP(ImcBINOP.Oper.ADD, tempFP, new ImcCONST(relAccess.offset));
		}

		ImcExpr expr = new ImcMEM(addr);
		ImcGen.exprImc.put(nameExpr, expr);
		return expr;
	}

	@Override
	public ImcExpr visit(AstPfxExpr pfxExpr, Stack<MemFrame> frames) {
		// Get subexpression
		ImcExpr subExpr = (ImcExpr) pfxExpr.expr.accept(this, frames);

		ImcExpr expr = switch (pfxExpr.oper) {
			case NOT -> new ImcUNOP(ImcUNOP.Oper.NOT, subExpr);
			case ADD -> subExpr;
			case SUB -> new ImcUNOP(ImcUNOP.Oper.NEG, subExpr);
			case PTR -> ((ImcMEM) subExpr).addr; // Return the address of the subexpression
			case NEW -> null; // TODO
			case DEL -> new ImcCONST(42); // Random value (undefined)
		};

		ImcGen.exprImc.put(pfxExpr, expr);
		return expr;
	}

	@Override
	public ImcExpr visit(AstRecExpr recExpr, Stack<MemFrame> frames) {
		// Get the record
		ImcExpr rec = (ImcExpr) recExpr.rec.accept(this, frames);

		if (!(rec instanceof ImcMEM memAccess)) {
			throw new Report.Error(recExpr, TAG + "record expression is not a memory access");
		}

		// Get the address of the first component
		ImcExpr firstCompAddr = memAccess.addr;

		// Resolve the component
		recExpr.comp.accept(this, frames);

		// Get the component declaration
		AstDecl decl = SemAn.declaredAt.get(recExpr.comp);

		if (!(decl instanceof AstMemDecl memDecl)) {
			throw new Report.Error(recExpr, TAG + "component is not a memory declaration");
		}

		// Access the component (record components are all relative, so we can cast without type checking)
		MemRelAccess comp = (MemRelAccess) Memory.accesses.get(memDecl);

		// Calculate the component address (recAddr + offset)
		ImcExpr compAddr = new ImcBINOP(ImcBINOP.Oper.ADD, firstCompAddr, new ImcCONST(comp.offset));

		ImcExpr expr = new ImcMEM(compAddr);
		ImcGen.exprImc.put(recExpr, expr);
		return expr;
	}

	@Override
	public ImcExpr visit(AstSfxExpr sfxExpr, Stack<MemFrame> frames) {
		// Get subexpression
		ImcExpr subExpr = (ImcExpr) sfxExpr.expr.accept(this, frames);

		ImcMEM memAccess = new ImcMEM(subExpr);
		ImcGen.exprImc.put(sfxExpr, memAccess);
		return memAccess;
	}

	@Override
	public ImcExpr visit(AstStmtExpr stmtExpr, Stack<MemFrame> frames) {
		// TODO (EX13)
		return null;
	}

	@Override
	public ImcExpr visit(AstWhereExpr whereExpr, Stack<MemFrame> frames) {
		// Get the expression
		ImcExpr expr = (ImcExpr) whereExpr.expr.accept(this, frames);

		// Resolve the declarations
		whereExpr.decls.accept(this, frames);

		ImcGen.exprImc.put(whereExpr, expr);
		return expr;
	}

	// STATEMENTS

	@Override
	public ImcStmt visit(AstAssignStmt assignStmt, Stack<MemFrame> frames) {
		return null;
	}

	@Override
	public ImcStmt visit(AstExprStmt exprStmt, Stack<MemFrame> frames) {
		return null;
	}

	@Override
	public ImcStmt visit(AstIfStmt ifStmt, Stack<MemFrame> frames) {
		return null;
	}

	@Override
	public ImcStmt visit(AstWhileStmt whileStmt, Stack<MemFrame> frames) {
		return null;
	}
}
