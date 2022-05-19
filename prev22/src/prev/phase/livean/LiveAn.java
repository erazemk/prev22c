package prev.phase.livean;

import prev.data.asm.AsmInstr;
import prev.data.asm.AsmLABEL;
import prev.data.asm.Code;
import prev.data.mem.MemLabel;
import prev.data.mem.MemTemp;
import prev.phase.Phase;
import prev.phase.asmgen.AsmGen;

import java.util.HashMap;
import java.util.HashSet;

/**
 * Liveness analysis.
 */
public class LiveAn extends Phase {

	public LiveAn() {
		super("livean");
	}

	public void compLifetimes() {
		boolean changed;
		HashMap<String, AsmLABEL> labelMap = new HashMap<>();

		for (Code code : AsmGen.codes) {
			do {
				changed = false;
				for (int i = 0; i < code.instrs.size(); i++) {
					AsmInstr instr = code.instrs.get(i);
					int inSize = instr.in().size();
					int outSize = instr.out().size();

					if (instr instanceof AsmLABEL asmLabel) {
						labelMap.put(asmLabel.toString(), asmLabel);
					}

					// If instruction is a jump, then next instruction will be
					// the location it jumps to (or neg/pos)
					// If instruction is a function call, then the label will be
					// outside this code block, so continue normally
					if (instr.jumps().size() > 0 && !instr.toString().contains("PUSHJ")) {
						for (MemLabel label : instr.jumps()) {
							if (labelMap.get(label.name) != null) {
								instr.addOutTemp(labelMap.get(label.name).in());
							}
						}
					} else if (i < code.instrs.size() - 1) {
						instr.addOutTemp(code.instrs.get(i + 1).in());
					}

					instr.addInTemps(new HashSet<>(instr.uses()));
					HashSet<MemTemp> filtered = new HashSet<>(instr.out());
					instr.defs().forEach(filtered::remove);
					instr.addInTemps(filtered);

					// Check if there were any changes
					if (inSize != instr.in().size() || outSize != instr.out().size()) {
						changed = true;
					}
				}
			} while (changed);
		}
	}

	public void log() {
		if (logger == null)
			return;
		for (Code code : AsmGen.codes) {
			logger.begElement("code");
			logger.addAttribute("entrylabel", code.entryLabel.name);
			logger.addAttribute("exitlabel", code.exitLabel.name);
			logger.addAttribute("tempsize", Long.toString(code.tempSize));
			code.frame.log(logger);
			logger.begElement("instructions");
			for (AsmInstr instr : code.instrs) {
				logger.begElement("instruction");
				logger.addAttribute("code", instr.toString());
				logger.begElement("temps");
				logger.addAttribute("name", "use");
				for (MemTemp temp : instr.uses()) {
					logger.begElement("temp");
					logger.addAttribute("name", temp.toString());
					logger.endElement();
				}
				logger.endElement();
				logger.begElement("temps");
				logger.addAttribute("name", "def");
				for (MemTemp temp : instr.defs()) {
					logger.begElement("temp");
					logger.addAttribute("name", temp.toString());
					logger.endElement();
				}
				logger.endElement();
				logger.begElement("temps");
				logger.addAttribute("name", "in");
				for (MemTemp temp : instr.in()) {
					logger.begElement("temp");
					logger.addAttribute("name", temp.toString());
					logger.endElement();
				}
				logger.endElement();
				logger.begElement("temps");
				logger.addAttribute("name", "out");
				for (MemTemp temp : instr.out()) {
					logger.begElement("temp");
					logger.addAttribute("name", temp.toString());
					logger.endElement();
				}
				logger.endElement();
				logger.endElement();
			}
			logger.endElement();
			logger.endElement();
		}
	}

}
