package prev.phase.all;

import prev.Compiler;
import prev.data.asm.AsmInstr;
import prev.data.asm.AsmLABEL;
import prev.data.asm.Code;
import prev.data.lin.LinDataChunk;
import prev.data.mem.MemTemp;
import prev.phase.asmgen.AsmGen;
import prev.phase.imclin.ImcLin;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Working compiler.
 */
public class MMIXTranslator {

	private final String outputFile;
	private final ArrayList<String> instructions;
	private final HashMap<MemTemp, Integer> tempToReg;
	private final String nregs = Compiler.cmdLineArgValue("--nregs");

	public MMIXTranslator(String outputFile, HashMap<MemTemp, Integer> tempToReg) {
		this.outputFile = outputFile;
		this.tempToReg = tempToReg;
		instructions = new ArrayList<>();
	}

	public void translate() {
		// Add bootstrap code
		addBootstrap();

		// Add all the necessary parts, then write function to file
		for (Code code : AsmGen.codes) {
			addPrologue(code);
			addBody(code);
			addEpilogue(code);
		}

		optimization();

		// Write all instructions to output file
		writeToFile();
	}

	// Create an instruction with a label
	private void addInstruction(String label, String mnemonic, String params) {
		// Align text based on attribute lengths
		StringBuilder sb = new StringBuilder();

		if (label != null) {
			sb.append(label).append("\t");
			if (label.length() < 4) sb.append("\t");
		} else {
			sb.append("\t\t");
		}

		sb.append(mnemonic).append("\t");
		if (mnemonic.length() < 4) sb.append("\t");
		sb.append(params).append("\n");
		instructions.add(sb.toString());
	}

	// Create an instruction without a label
	private void addInstruction(String mnemonic, String params) {
		addInstruction(null, mnemonic, params);
	}

	// Create an empty line
	private void addNewline() {
		instructions.add("\n");
	}

	// Create a comment
	private void addComment(String comment) {
		instructions.add("\t\t% " + comment + "\n");
	}

	private void loadValue(long offset, String label) {
		long absOffset = Math.abs(offset);

		// Add label to first instruction if needed
		if (label != null) {
			addInstruction(label, "SETL", "$0," + (absOffset & 0xFFFF));
		} else {
			addInstruction("SETL", "$0," + (absOffset & 0xFFFF));
		}

		if ((absOffset >>= 16) > 0) {
			addInstruction("INCML", "$0," + (absOffset & 0xFFFF));
		}

		if ((absOffset >>= 16) > 0) {
			addInstruction("INCMH", "$0," + (absOffset & 0xFFFF));
		}

		if ((absOffset >>= 16) > 0) {
			addInstruction("INCH", "$0," + (absOffset & 0xFFFF));
		}

		if (offset < 0) {
			addInstruction("NEG", "$0,0,$0");
		}
	}

	private void loadValue(long offset) {
		loadValue(offset, null);
	}

	private void addBootstrap() {
		/*
		 *  ADD GLOBAL VARIABLES TO DATA SEGMENT
		 */
		addInstruction("LOC", "Data_Segment");
		addInstruction("GREG", "@");

		for (LinDataChunk chunk : ImcLin.dataChunks()) {
			// If initial value is null, this is a normal variable, otherwise it's a string
			if (chunk.init == null) {
				String sizeStr;

				// Reserve proper amount of space for variables
				if (chunk.size < 1) {
					sizeStr = "BYTE";
				} else if (chunk.size < 2) {
					sizeStr = "WYDE";
				} else if (chunk.size < 4) {
					sizeStr = "TETRA";
				} else {
					sizeStr = "OCTA";
				}

				addInstruction(chunk.label.name, sizeStr, "0");
			} else {
				// Append newline and null terminator to string
				addInstruction(chunk.label.name, "BYTE", "\"" + chunk.init +
					"\",10,0");
			}
		}

		// Start of code segment (prepend newline to have an empty line between data and program)
		addNewline();
		addInstruction("LOC", "#100");
		addNewline();

		// Set stack pointer
		addInstruction("SETH", "$254,7000");

		// Call main
		addInstruction("Main", "PUSHJ", "$" + nregs + ",_main");

		// Copy return value into $255 (used for sending data to system calls)
		addInstruction("LDA", "$255,$254");

		// Exit (exit code is in $255)
		addInstruction("TRAP", "0,Halt,0");
	}

	private void addPrologue(Code code) {
		addNewline();

		// Save old FP
		loadValue(- code.frame.locsSize - 8, code.frame.label.name); // FP
		addInstruction("SUB", "$0,$254,$0"); // $0 <- SP - $0
		addInstruction("STO", "$253,$0,0"); // M[$0] <- FP

		// Save return address
		addInstruction("SUB", "$0,$0,8"); // $0 <- ret addr (oldFP - 8)
		addInstruction("GET", "$1,rJ"); // $1 <- rJ
		addInstruction("STO", "$1,$0,0"); // M[$0] <- $1

		// Set new FP
		addInstruction("SET", "$253,$254"); // FP <- SP

		// Set new SP
		loadValue(code.frame.size + code.tempSize); // Actual frame size
		addInstruction("SUB", "$254,$254,$0");

		 // Jump to function body
		addInstruction("JMP", code.entryLabel.name);
	}

	private void addBody(Code code) {
		String label = null;
		for (AsmInstr instr : code.instrs) {

			// Apply label to next instruction
			if (instr instanceof AsmLABEL) {
				// Check if there are two consecutive labels
				if (label != null) {
					// Create a "NOP" instruction for the first label
					addInstruction(label, "SET", "$0,$0");
				}

				label = instr.toString();
				continue;
			}

			// For nicer output, split instruction into mnemonic and parameters
			String[] split = instr.toString(tempToReg).split(" ", 2);
			if (label != null) {
				addInstruction(label, split[0], split[1]);
				label = null;
			} else {
				addInstruction(split[0], split[1]);
			}
		}
	}

	private void addEpilogue(Code code) {
		// Save return value
		//System.out.println("RV: " + code.frame.RV);
		addInstruction(code.exitLabel.name, "STO",
			"$" + tempToReg.get(code.frame.RV) + ",$253,0");

		// Set SP
		addInstruction("SET", "$254,$253"); // SP <- FP

		// Restore FP
		loadValue(- code.frame.locsSize - 8); // FP
		addInstruction("SUB", "$0,$254,$0"); // $0 <- SP - FP
		addInstruction("LDO", "$253,$0,0"); // FP <- $0

		// Restore return address
		addInstruction("SUB", "$0,$0,8"); // FP <- FP - 8
		addInstruction("LDO", "$0,$0,0"); // FP <- M[$0]
		addInstruction("PUT", "rJ,$0"); // RJ <- FP

		// Return from function
		addInstruction("POP", nregs + ",0");
	}

	private void addStdlib(Code code) {}

	private void optimization() {
		// Remove unnecessary jumps
		for (int i = 0; i < instructions.size(); i++) {
			String instruction = instructions.get(i);
			if (!instruction.startsWith("\t")) { // Label
				String label = instruction.split("\t", 2)[0].strip();

				// If previous instruction jumps to this one, remove it
				String prevInstruction = instructions.get(i-1);
				if (prevInstruction.contains("JMP")) {
					String[] text = prevInstruction.split("\t");
					String jmpLabel = text[text.length - 1].strip();
					if (label.equals(jmpLabel)) {
						instructions.remove(prevInstruction);
						i--; // Fix positioning, since an element was removed
					}
				}
			}
		}

		// Remove unnecessary sets
		for (int i = 0; i < instructions.size(); i++) {
			String instruction = instructions.get(i);
			if (instruction.contains("SET")) {
				String[] params = instruction.split("\t");
				params = params[params.length - 1].split(",");

				// If writing to same register, remove the instruction
				if (params[0].strip().equals(params[1].strip())) {
					instructions.remove(instruction);
					i--; // Fix positioning, since an element was removed
				}
			}
		}
	}

	private void writeToFile() {
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));

			for (String instruction : instructions) {
				writer.write(instruction);
			}

			writer.close();
		} catch (IOException ioe) {
			ioe.getMessage();
		}
	}
}
