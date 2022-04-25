package prev.phase.asmgen;

import java.util.*;
import prev.data.mem.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.visitor.*;
import prev.data.asm.*;

/**
 * Machine code generator for expressions.
 */
public class ExprGenerator implements ImcVisitor<MemTemp, Vector<AsmInstr>> {

	// DOCS: http://mmix.cs.hm.edu/doc/instructions-en.html

	public MemTemp visit(ImcBINOP binOp, Vector<AsmInstr> instructions) {
		MemTemp src1 = binOp.fstExpr.accept(this, instructions);
		MemTemp src2 = binOp.sndExpr.accept(this, instructions);
		MemTemp dst = new MemTemp();

		Vector<MemTemp> uses = new Vector<>(List.of(src1, src2));
		Vector<MemTemp> defs = new Vector<>(List.of(dst));

		switch (binOp.oper) {
			// Docs: http://mmix.cs.hm.edu/doc/instructions-en.html#Signed_Arithmetic
			//       http://mmix.cs.hm.edu/doc/instructions-en.html#Branches
			case OR -> instructions.add(new AsmOPER("OR `d0, `s0, `s1", uses, defs, null));
			case AND -> instructions.add(new AsmOPER("AND `d0, `s0, `s1", uses, defs, null));
			case EQU -> {
				instructions.add(new AsmOPER("CMP `d0, `s0, `s1", uses, defs, null));

				// Zero or Set: If zero (0 -> EQU)
				instructions.add(new AsmOPER("ZSZ `d0, `s0, 1", defs, defs, null));
			}
			case NEQ -> {
				instructions.add(new AsmOPER("CMP `d0, `s0, `s1", uses, defs, null));

				// Zero or Set: If nonzero (+1/-1 -> NEQ)
				instructions.add(new AsmOPER("ZSNZ `d0, `s0, 1", defs, defs, null));
			}
			case LTH -> {
				instructions.add(new AsmOPER("CMP `d0, `s0, `s1", uses, defs, null));

				// Zero or Set: If negative (-1 -> LTH)
				instructions.add(new AsmOPER("ZSN `d0, `s0, 1", defs, defs, null));
			}
			case GTH -> {
				instructions.add(new AsmOPER("CMP `d0, `s0, `s1", uses, defs, null));

				// Zero or Set: If positive (+1 -> GTH)
				instructions.add(new AsmOPER("ZSP `d0, `s0, 1", defs, defs, null));
			}
			case LEQ -> {
				instructions.add(new AsmOPER("CMP `d0, `s0, `s1", uses, defs, null));

				// Zero or Set: If non-positive (-1/0 -> LEQ)
				instructions.add(new AsmOPER("ZSNP `d0, `s0, 1", defs, defs, null));
			}
			case GEQ -> {
				instructions.add(new AsmOPER("CMP `d0, `s0, `s1", uses, defs, null));

				// Zero or Set: If non-negative (0/+1 -> GEQ)
				instructions.add(new AsmOPER("ZSNN `d0, `s0, 1", defs, defs, null));
			}
			case ADD -> instructions.add(new AsmOPER("ADD `d0, `s0, `s1", uses, defs, null));
			case SUB -> instructions.add(new AsmOPER("SUB `d0, `s0, `s1", uses, defs, null));
			case MUL -> instructions.add(new AsmOPER("MUL `d0, `s0, `s1", uses, defs, null));
			case DIV -> instructions.add(new AsmOPER("DIV `d0, `s0, `s1", uses, defs, null));
			case MOD -> {
				instructions.add(new AsmOPER("DIV `d0, `s0, `s1", uses, defs, null));

				// Docs: http://mmix.cs.hm.edu/doc/instructions-en.html#System
				// DIV leaves the remainder in rR
				instructions.add(new AsmOPER("GET `d0, rR", null, defs, null));
			}
		}

		return dst;
	}

	public MemTemp visit(ImcCALL call, Vector<AsmInstr> instructions) {
		// Store all the arguments to the stack
		for (int i = 0; i < call.args.size(); i++) {
			ImcExpr arg = call.args.get(i);
			long offset = call.offs.get(i);

			Vector<MemTemp> uses = new Vector<>(List.of(arg.accept(this, instructions)));

			// Docs: http://mmix.cs.hm.edu/doc/instructions-en.html#Signed_Store
			// $254 = SP, store each arg at SP + its offset (s0 <- $254 + offset)
			instructions.add(new AsmOPER("STO `s0, $254, " + offset, uses, null, null));
		}

		// Docs: http://mmix.cs.hm.edu/doc/instructions-en.html#Subroutines
		// Save first 8 registers before calling function
		instructions.add(new AsmOPER("PUSHJ $8, " + call.label.name, null, null, null));

		MemTemp dst = new MemTemp();
		Vector<MemTemp> defs = new Vector<>(List.of(dst));

		// Docs: http://mmix.cs.hm.edu/doc/instructions-en.html#Signed_Load
		// Get the return value
		instructions.add(new AsmMOVE("LDO `d0, $254, 0", null, defs));

		return dst;
	}
	public MemTemp visit(ImcCONST constant, Vector<AsmInstr> instructions) {
		MemTemp dst = new MemTemp();
		Vector<MemTemp> defs = new Vector<>(List.of(dst));

		// Use the constant's absolute value, since negation happens at the end if needed
		long val = Math.abs(constant.value);

		// Docs: http://mmix.cs.hm.edu/doc/instructions-en.html#Address
		// Set all bytes one after another using SETL, INCML, INCMH, INCH (short because they set 16-bit constants)
		// Since creating vectors is wasteful, just use constants directly
		instructions.add(new AsmOPER("SETL `d0, " + (short) (val & 0xFFFF), null, defs, null));
		val >>= 16;

		if (val > 0) {
			instructions.add(new AsmOPER("INCML `d0, " + (short) (val & 0xFFFF), null, defs, null));
			val >>= 16;
		}

		if (val > 0) {
			instructions.add(new AsmOPER("INCMH `d0, " + (short) (val & 0xFFFF), null, defs, null));
			val >>= 16;
		}

		if (val > 0) {
			instructions.add(new AsmOPER("INCH `d0, " + (short) (val & 0xFFFF), null, defs, null));
		}

		// Negate the value if needed
		if (constant.value < 0) {
			// Docs: http://mmix.cs.hm.edu/doc/instructions-en.html#Arithmetic
			instructions.add(new AsmOPER("NEG `d0, `s0", defs, defs, null));
		}

		return dst;
	}

	public MemTemp visit(ImcMEM mem, Vector<AsmInstr> instructions) {
		MemTemp src = mem.addr.accept(this, instructions);
		MemTemp dst = new MemTemp();

		Vector<MemTemp> uses = new Vector<>(List.of(src));
		Vector<MemTemp> defs = new Vector<>(List.of(dst));

		// Docs: http://mmix.cs.hm.edu/doc/instructions-en.html#Signed_Load
		// Use an assembly move since the offset is 0
		instructions.add(new AsmMOVE("LDO `d0, `s0, 0", uses, defs));

		return dst;
	}

	public MemTemp visit(ImcNAME name, Vector<AsmInstr> instructions) {
		MemTemp dst = new MemTemp();
		Vector<MemTemp> defs = new Vector<>(List.of(dst));

		// Docs: http://mmix.cs.hm.edu/doc/instructions-en.html#LDA
		// LDA stores the label's address in d0
		instructions.add(new AsmOPER("LDA `d0, " + name.label.name, null, defs, null));

		return dst;
	}

	public MemTemp visit(ImcSEXPR sExpr, Vector<AsmInstr> instructions) {
		instructions.addAll(sExpr.stmt.accept(new StmtGenerator(), null));
		return sExpr.expr.accept(this, instructions);
	}

	public MemTemp visit(ImcTEMP temp, Vector<AsmInstr> instructions) {
		return temp.temp;
	}

	public MemTemp visit(ImcUNOP unOp, Vector<AsmInstr> instructions) {
		MemTemp src = unOp.subExpr.accept(this, instructions);
		MemTemp dst = new MemTemp();

		Vector<MemTemp> uses = new Vector<>(List.of(src));
		Vector<MemTemp> defs = new Vector<>(List.of(dst));

		instructions.add(switch (unOp.oper) {
			// Docs: http://mmix.cs.hm.edu/doc/instructions-en.html#Arithmetic
			case NEG -> new AsmOPER("NEG `d0, `s0", uses, defs, null);

			// Docs: http://mmix.cs.hm.edu/doc/instructions-en.html#Bit_Operations
			case NOT -> new AsmOPER("NAND `d0, `s0, 1", uses, defs, null);
		});

		return dst;
	}
}
