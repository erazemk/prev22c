package prev.phase.regall;

import java.util.*;

import prev.data.mem.*;
import prev.data.asm.*;
import prev.data.typ.SemPtr;
import prev.data.typ.SemVoid;
import prev.phase.*;
import prev.phase.asmgen.*;

/**
 * Register allocation.
 */
public class RegAll extends Phase {

	/** Mapping of temporary variables to registers. */
	public final HashMap<MemTemp, Integer> tempToReg = new HashMap<>();

	/** Number of available registers */
	private final int nregs;

	public RegAll(int nregs) {
		super("regall");
		this.nregs = nregs;
	}

	private void loadOffset(Vector<AsmInstr> instrs, Vector<MemTemp> uses, long offset) {
		// Load offset (always reuse offsetTemp - uses)
		// Code copied from asmgen/ExprGenerator/ImcCONST visitor
		long val = Math.abs(offset);
		instrs.add(new AsmOPER("SETL `d0, " + (short) (val & 0xFFFF), null, uses, null));
		val >>= 16;

		if (val > 0) {
			instrs.add(new AsmOPER("INCML `d0, " + (short) (val & 0xFFFF), null, uses, null));
			val >>= 16;
		}

		if (val > 0) {
			instrs.add(new AsmOPER("INCMH `d0, " + (short) (val & 0xFFFF), null, uses, null));
			val >>= 16;
		}

		if (val > 0) {
			instrs.add(new AsmOPER("INCH `d0, " + (short) (val & 0xFFFF), null, uses, null));
		}

		// Negate the value if needed
		if (offset < 0) {
			instrs.add(new AsmOPER("NEG `d0, `s0", uses, uses, null));
		}
	}

	public void allocate() {
		for (Code code : AsmGen.codes) {

			// Try building and coloring a graph until it succeeds
			Graph finalGraph;
			do {
				Graph graph = new Graph();

				// Add all variables to graph
				for (AsmInstr instr : code.instrs) {
					HashSet<MemTemp> combined = new HashSet<>(instr.uses());
					combined.addAll(instr.defs());

					// Convert every variable to a Vertex
					for (MemTemp temp : combined) {

						// Skip adding FP, since it has a separate register
						if (temp == code.frame.FP) continue;

						graph.addVertex(temp);
					}

					// TODO: Check if adding neighbors can be moved here
				}

				// Add all neighbors to variables
				for (AsmInstr instr : code.instrs) {
					if (instr.defs() != null) continue;
					if (instr.defs().get(0) == code.frame.FP) continue;

					// Only outs are relevant
					for (MemTemp out : instr.out()) {
						graph.addNeighbors(instr.defs().get(0), out);
					}
				}

				// Simplify graph by removing vertices and handling spills
				finalGraph = graph.copy();
				Stack<Vertex> stack = new Stack<>();
				do {

					// Remove vertices with num of neighbors < num of regs
					boolean changed;
					do {
						changed = false;

						// Normal foreach doesn't work here, because modifying a HashMap while
						// iterating over it isn't allowed
						Iterator<Vertex> iterator = finalGraph.vertices.iterator();
						while(iterator.hasNext()) {
							Vertex vertex = iterator.next();

							if (vertex.neighbors.size() < nregs) {
								finalGraph.removeVertex(vertex);
								stack.push(vertex);
								iterator.remove();
								changed = true;
							}
						}
					} while (changed);

					// TODO: Check MOVE operations

					// Check for spills
					if (finalGraph.vertices.size() > 0) {
						Vertex maxVertex = null;
						int maxNeighbors = 0;

						// Find vertex with the greatest number of neighbors
						for (Vertex vertex : finalGraph.vertexMap.values()) {
							if (vertex.neighbors.size() > maxNeighbors) {
								maxNeighbors = vertex.neighbors.size();
								maxVertex = vertex;
							}
						}

						// maxVertex should never be null, this is to stop IDE from complaining
						if (maxVertex == null) return;

						// Mark vertex for spill and retry removing nodes
						maxVertex.potentialSpill = true;
						finalGraph.removeVertex(maxVertex);
						stack.push(maxVertex);
					}
				} while (finalGraph.vertices.size() > 0);

				// Color first vertex in the graph
				Vertex first = stack.pop();
				first.color = 0;

				// Try coloring all the other vertices
				ArrayList<Vertex> spills = new ArrayList<>();
				while (!stack.empty()) {
					Vertex vertex = stack.pop();
					boolean[] colors = new boolean[nregs];

					// Check neighbors' colors
					for (Vertex neighbor : vertex.neighbors) {
						colors[neighbor.color] = true;
					}

					// Pick first available color
					for (int j = 0; j < colors.length; j++) {
						if (!colors[j]) {
							vertex.color = j;
							break;
						}
					}

					// No available color
					if (vertex.color == -1) {
						vertex.spill = true;
						spills.add(vertex);
					}
				}

				// If there were no spills, the coloring was successful
				if (spills.size() == 0) break;

				// Modify code for each spill
				for (Vertex spill : spills) {
					Vector<AsmInstr> instrs = new Vector<>();
					long ptrSize = new SemPtr(new SemVoid()).size();
					code.tempSize += ptrSize;

					// Offset = - size of existing local variables - current size of temps - 2 * pointer size
					long offset = - code.frame.locsSize - code.tempSize - 2 * ptrSize;

					for (AsmInstr instr : code.instrs) {
						// Check if instruction uses or defines a spilled temporary variable
						boolean usesSpill = instr.uses().contains(spill.variable);
						boolean defsSpill = instr.defs().contains(spill.variable);

						if (!usesSpill && !defsSpill) instrs.add(instr);

						if (usesSpill) {
							// Get value and offset from memory
							MemTemp newTemp = new MemTemp();
							MemTemp offsetTemp = new MemTemp();
							Vector<MemTemp> uses = new Vector<>(List.of(offsetTemp));
							Vector<MemTemp> defs = new Vector<>(List.of(newTemp));

							loadOffset(instrs, uses, offset);

							// Load value from offset
							instrs.add(new AsmOPER("LDO `d0, $254, `s0", uses, defs, null));

							// Replace old temp variable with new one if needed
							Vector<MemTemp> newUses = new Vector<>();
							for (MemTemp oldTemp : instr.uses()) {
								newUses.add(oldTemp == spill.variable ? newTemp : oldTemp);
							}

							// Add the new modified instruction to the list of instructions
							instrs.add(new AsmOPER(((AsmOPER) instr).instr(), newUses, instr.defs(), instr.jumps()));
						}

						if (defsSpill) {
							// Get value and offset from memory
							MemTemp newTemp = new MemTemp();
							MemTemp offsetTemp = new MemTemp();
							Vector<MemTemp> uses = new Vector<>(List.of(offsetTemp));
							Vector<MemTemp> defs = new Vector<>(List.of(newTemp));

							// Add the new modified instruction to the list of instructions
							instrs.add(new AsmOPER(((AsmOPER) instr).instr(), instr.uses(), defs, instr.jumps()));

							loadOffset(instrs, uses, offset);

							// Store value to offset
							uses = new Vector<>(List.of(newTemp, offsetTemp));
							instrs.add(new AsmOPER("STO `s0, $254, `s0", uses, null, null));
						}
					}

					// Replace instructions
					code.instrs.clear();
					code.instrs.addAll(instrs);
				}
			} while (true);

			// Replace temporary variables with registers
			tempToReg.put(code.frame.FP, 253);
			for (Vertex vertex : finalGraph.vertices) {
				// Skip vertices that could not be moved to registers
				if (vertex.color == -1) continue;

				tempToReg.put(vertex.variable, vertex.color);
			}
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
				logger.addAttribute("code", instr.toString(tempToReg));
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
