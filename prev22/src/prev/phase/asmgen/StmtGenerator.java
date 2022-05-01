package prev.phase.asmgen;

import java.util.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.imc.visitor.*;
import prev.data.mem.*;
import prev.data.asm.*;

/**
 * Machine code generator for statements.
 */
public class StmtGenerator implements ImcVisitor<Vector<AsmInstr>, Object> {

	public Vector<AsmInstr> visit(ImcCJUMP cjump, Object obj) {
		Vector<AsmInstr> instructions = new Vector<>();
		Vector<MemLabel> jumps = new Vector<>(List.of(cjump.negLabel, cjump.posLabel));

		MemTemp cond = cjump.cond.accept(new ExprGenerator(), instructions);
		Vector<MemTemp> uses = new Vector<>(List.of(cond));

		// Docs: http://mmix.cs.hm.edu/doc/instructions-en.html#Branches
		// If condition is not met (cond = 0) jump to negLabel, else no need to do anything since
		// posLabel is right behind the conditional check
		instructions.add(new AsmOPER("BZ `s0, " + cjump.negLabel.name, uses, null, jumps));

		return instructions;
	}

	public Vector<AsmInstr> visit(ImcESTMT eStmt, Object obj) {
		Vector<AsmInstr> instructions = new Vector<>();
		eStmt.expr.accept(new ExprGenerator(), instructions);
		return instructions;
	}

	public Vector<AsmInstr> visit(ImcJUMP jump, Object obj) {
		Vector<AsmInstr> instructions = new Vector<>();
		Vector<MemLabel> jumps = new Vector<>(List.of(jump.label));

		// Docs: http://mmix.cs.hm.edu/doc/instructions-en.html#Jumps
		instructions.add(new AsmOPER("JMP " + jump.label.name, null, null, jumps));

		return instructions;
	}

	public Vector<AsmInstr> visit(ImcLABEL label, Object obj) {
		return new Vector<>(List.of(new AsmLABEL(label.label)));
	}

	public Vector<AsmInstr> visit(ImcMOVE move, Object obj) {
		Vector<AsmInstr> instructions = new Vector<>();
		Vector<MemTemp> uses;
		Vector<MemTemp> defs;

		MemTemp src;
		MemTemp dst;

		if (move.dst instanceof ImcMEM) { // X -> MEM
			if (move.src instanceof ImcMEM) {
				// With MEM -> MEM load src into a register, then store it
				src = move.src.accept(new ExprGenerator(), instructions);
				dst = move.dst.accept(new ExprGenerator(), instructions);
				MemTemp temp = new MemTemp(); // Store value while moving

				uses = new Vector<>(List.of(src));
				defs = new Vector<>(List.of(temp));

				// Docs: http://mmix.cs.hm.edu/doc/instructions-en.html#Signed_Load
				instructions.add(new AsmMOVE("LDO `d0, `s0, 0", uses, defs));

				// s0 == value to store (temp), s1 == where to store it (dst)
				uses = new Vector<>(List.of(temp, dst));

				// Docs: http://mmix.cs.hm.edu/doc/instructions-en.html#Signed_Store
				instructions.add(new AsmOPER("STO `s0, `s1, 0", uses, null, null));
			} else {
				// With REG -> MEM just store register value into memory
				src = move.src.accept(new ExprGenerator(), instructions);
				dst = ((ImcMEM) move.dst).addr.accept(new ExprGenerator(), instructions);

				uses = new Vector<>(List.of(src, dst));

				// Docs: http://mmix.cs.hm.edu/doc/instructions-en.html#Signed_Store
				instructions.add(new AsmOPER("STO `s0, `s1, 0", uses, null, null));
			}
		} else { // X -> REG
			if (move.src instanceof ImcMEM) {
				// With MEM -> REG just load value from memory into register
				src = ((ImcMEM) move.src).addr.accept(new ExprGenerator(), instructions);
				dst = move.dst.accept(new ExprGenerator(), instructions);

				uses = new Vector<>(List.of(src));
				defs = new Vector<>(List.of(dst));

				// Docs: http://mmix.cs.hm.edu/doc/instructions-en.html#Signed_Load
				instructions.add(new AsmMOVE("LDO `d0, `s0, 0", uses, defs));
			} else {
				// With REG -> REG just move the value from one register into another
				src = move.src.accept(new ExprGenerator(), instructions);
				dst = move.dst.accept(new ExprGenerator(), instructions);

				uses = new Vector<>(List.of(src));
				defs = new Vector<>(List.of(dst));

				// Docs: http://mmix.cs.hm.edu/doc/instructions-en.html#SETH
				// 'SET $X,$Y' translates into 'OR $X,$Y,0', so no need for 4 separate instructions
				instructions.add(new AsmMOVE("SET `d0, `s0", uses, defs));
			}
		}

		return instructions;
	}

	public Vector<AsmInstr> visit(ImcSTMTS stmts, Object obj) {
		Vector<AsmInstr> instructions = new Vector<>();

		for (ImcStmt stmt : stmts.stmts) {
			instructions.addAll(stmt.accept(this, obj));
		}

		return instructions;
	}
}
