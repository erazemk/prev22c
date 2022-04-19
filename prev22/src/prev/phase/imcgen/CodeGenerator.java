package prev.phase.imcgen;

import java.util.*;

import prev.common.report.Report;
import prev.data.ast.tree.*;
import prev.data.ast.tree.decl.*;
import prev.data.ast.tree.expr.*;
import prev.data.ast.tree.stmt.*;
import prev.data.ast.visitor.*;
import prev.data.imc.code.ImcInstr;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.mem.*;
import prev.data.typ.*;
import prev.phase.memory.*;
import prev.phase.seman.SemAn;

public class CodeGenerator extends AstNullVisitor<ImcInstr, Stack<MemFrame>> {

	// GENERAL PURPOSE

	// Identifier for info reports
	private final String TAG = "[CodeGenerator]: ";

	// Helper function for NEW and DEL keywords
	public ImcExpr mallocOrFree(AstPfxExpr pfxExpr, boolean malloc) {
		MemLabel label;

		if (malloc) {
			label = new MemLabel("new");
		} else { // Free
			label = new MemLabel("del");
		}

		// Parameters needed for ImcCALL
		Vector<Long> offs = new Vector<>();
		Vector<ImcExpr> args = new Vector<>();

		// Add SL
		args.add(new ImcCONST(0));
		offs.add(0L);

		// Add the offset and argument
		ImcExpr subExpr = ImcGen.exprImc.get(pfxExpr.expr);
		args.add(subExpr);
		offs.add(new SemPtr(new SemVoid()).size());

		// Offload the work to a runtime function
		return new ImcCALL(label, offs, args);
	}

	@Override
	public ImcInstr visit(AstTrees<? extends AstTree> trees, Stack<MemFrame> frames) {
		if (frames == null) {
			frames = new Stack<>();
		}

		for (AstTree t : trees) {
			if (t instanceof AstFunDecl) {
				t.accept(this, frames);
			}
		}

		return null;
	}

	// DECLARATIONS

	@Override
	public ImcInstr visit(AstFunDecl funDecl, Stack<MemFrame> frames) {
		frames.push(Memory.frames.get(funDecl));

		if (funDecl.expr != null) {
			funDecl.expr.accept(this, frames);
		}

		frames.pop();
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

		Report.info(TAG + "(ArrExpr): " + expr + "=" + indexAccess);

		ImcGen.exprImc.put(arrExpr, expr);
		return expr;
	}

	@Override
	public ImcExpr visit(AstAtomExpr atomExpr, Stack<MemFrame> frames) {
		// Convert to constant or address based on type
		ImcExpr expr = switch (atomExpr.type) {
			case VOID -> new ImcCONST(0); // Default to 0, so that exit code for void functions is 0
			case POINTER -> new ImcCONST(0); // Nil returns 0
			case BOOL -> new ImcCONST((atomExpr.value.equals("true")) ? 1 : 0); // Bool returns 0 or 1
			case CHAR -> new ImcCONST(atomExpr.value.length() == 3 ? atomExpr.value.charAt(1) : atomExpr.value.charAt(2)); // ASCII value (position changes if escaped single quote)
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
		// Get the original declaration
		if (!(SemAn.declaredAt.get(callExpr) instanceof AstFunDecl funDecl)) {
			throw new Report.Error(callExpr, TAG + "not a function declaration");
		}

		// Get the function's stack frame
		MemFrame frame = Memory.frames.get(funDecl);

		// Get the caller (parent) function's stack frame
		MemFrame callerFrame = frames.peek();

		ImcExpr tempSL = new ImcCONST(0);
		if (frame.depth > 0) {
			tempSL = new ImcTEMP(callerFrame.FP);
			Report.info(TAG + "(CallExpr): " + tempSL);
			for (int i = 0; i <= callerFrame.depth - frame.depth; i++) {
				tempSL = new ImcMEM(tempSL);
			}
		}

		// Function calls have multiple arguments, so we need a vector to store them and another one for their offsets
		Vector<ImcExpr> args = new Vector<>();
		Vector<Long> argOffsets = new Vector<>();

		// Add static link to the list of arguments (and its offset - 0)
		args.add(tempSL);
		argOffsets.add(0L);

		// Add all the other arguments to the list along with their offsets
		long offset = new SemPtr(new SemVoid()).size(); // Argument offsets start at 8, because of SL
		for (AstExpr arg : callExpr.args) {
			args.add((ImcExpr) arg.accept(this, frames));
			argOffsets.add(offset);

			// Increment the offset
			SemType argType = SemAn.ofType.get(arg);
			offset += argType.actualType().size();
		}

		ImcExpr expr = new ImcCALL(frame.label, argOffsets, args);
		ImcGen.exprImc.put(callExpr, expr);
		return expr;
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
			Report.info(TAG + "(NameExpr): " + tempFP);

			for (int i = 0; i <= deltaDepth; i++) {
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
			case NEW -> mallocOrFree(pfxExpr, true);
			case DEL -> mallocOrFree(pfxExpr, false);
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
		// Statement expressions need multiple statements so we need a vector
		Vector<ImcStmt> stmts = new Vector<>();

		// Add all but the last statement to the stmts vector
		for (int i = 0; i < stmtExpr.stmts.size() - 1; i++) {
			AstStmt astStmt = stmtExpr.stmts.get(i);
			stmts.add((ImcStmt) astStmt.accept(this, frames));
		}

		// Resolve the last statement
		AstStmt astStmt = stmtExpr.stmts.get(stmtExpr.stmts.size() - 1);
		ImcStmt lastStmt = (ImcStmt) astStmt.accept(this, frames);

		ImcExpr lastStmtExpr;
		if (lastStmt instanceof ImcESTMT) {
			lastStmtExpr = ((ImcESTMT) lastStmt).expr;
		} else {
			stmts.add(lastStmt);
			lastStmtExpr = new ImcCONST(0); // Return an undefined value
		}

		ImcStmt stmt = new ImcSTMTS(stmts);
		ImcExpr expr = new ImcSEXPR(stmt, lastStmtExpr);
		ImcGen.exprImc.put(stmtExpr, expr);
		return expr;
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
		// Get the source and destination expressions
		ImcExpr dstExpr = (ImcExpr) assignStmt.dst.accept(this, frames);
		ImcExpr srcExpr = (ImcExpr) assignStmt.src.accept(this, frames);

		// Move source to destination
		ImcStmt stmt = new ImcMOVE(dstExpr, srcExpr);
		ImcGen.stmtImc.put(assignStmt, stmt);
		return stmt;
	}

	@Override
	public ImcStmt visit(AstExprStmt exprStmt, Stack<MemFrame> frames) {
		// Get the expression
		ImcExpr expr = (ImcExpr) exprStmt.expr.accept(this, frames);

		// Create a new expression statement
		ImcStmt stmt = new ImcESTMT(expr);
		ImcGen.stmtImc.put(exprStmt, stmt);
		return stmt;
	}

	@Override
	public ImcStmt visit(AstIfStmt ifStmt, Stack<MemFrame> frames) {
		// Get the condition and both expressions
		ImcExpr condExpr = (ImcExpr) ifStmt.cond.accept(this, frames);
		ImcStmt thenStmt = (ImcStmt) ifStmt.thenStmt.accept(this, frames);
		ImcStmt elseStmt = (ImcStmt) ifStmt.elseStmt.accept(this, frames);

		// Create labels for then and else statements and a break for the then statement
		ImcLABEL thenLabel = new ImcLABEL(new MemLabel());
		ImcLABEL elseLabel = new ImcLABEL(new MemLabel());
		ImcLABEL breakLabel = new ImcLABEL(new MemLabel());

		// If statements need multiple statements, so we need a vector
		Vector<ImcStmt> stmts = new Vector<>();

		// Statement order: cond. jump -> then label -> then -> break jump -> else label -> else -> break label
		stmts.add(new ImcCJUMP(condExpr, thenLabel.label, elseLabel.label));
		stmts.add(thenLabel);
		stmts.add(thenStmt);
		stmts.add(new ImcJUMP(breakLabel.label));
		stmts.add(elseLabel);
		stmts.add(elseStmt);
		stmts.add(breakLabel);

		ImcStmt stmt = new ImcSTMTS(stmts);
		ImcGen.stmtImc.put(ifStmt, stmt);
		return stmt;
	}

	@Override
	public ImcStmt visit(AstWhileStmt whileStmt, Stack<MemFrame> frames) {
		// Get the condition and body
		ImcExpr condExpr = (ImcExpr) whileStmt.cond.accept(this, frames);
		ImcStmt bodyStmt = (ImcStmt) whileStmt.bodyStmt.accept(this, frames);

		// Create labels for the condition, loop and break
		ImcLABEL condLabel = new ImcLABEL(new MemLabel());
		ImcLABEL loopLabel = new ImcLABEL(new MemLabel());
		ImcLABEL breakLabel = new ImcLABEL(new MemLabel());

		// While statements also need multiple statements, so we need another vector
		Vector<ImcStmt> stmts = new Vector<>();
		ImcCJUMP cJump = new ImcCJUMP(condExpr, loopLabel.label, breakLabel.label);

		// Statement order: cond. label -> cond. jump -> loop label -> loop (body) -> jump to cond. -> break label
		stmts.add(condLabel);
		stmts.add(cJump);
		stmts.add(loopLabel);
		stmts.add(bodyStmt);
		stmts.add(new ImcJUMP(condLabel.label));
		stmts.add(breakLabel);

		ImcStmt stmt = new ImcSTMTS(stmts);
		ImcGen.stmtImc.put(whileStmt, stmt);
		return stmt;
	}
}
