package prev.phase.regall;

import prev.data.asm.AsmInstr;
import prev.data.asm.AsmOPER;
import prev.data.asm.Code;
import prev.data.mem.MemTemp;
import prev.data.typ.SemPtr;
import prev.data.typ.SemVoid;
import prev.phase.Phase;
import prev.phase.asmgen.AsmGen;
import prev.phase.livean.LiveAn;

import java.util.*;

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

	private Vector<AsmInstr> loadOffset(Vector<MemTemp> uses, long offset) {
		Vector<AsmInstr> instrs = new Vector<>();
		long val = Math.abs(offset);

		instrs.add(new AsmOPER("SETL `d0," + (short) (val & 0xFFFF), null, uses, null));
		val >>= 16;

		if (val > 0) {
			instrs.add(new AsmOPER("INCML `d0," + (short) (val & 0xFFFF), null, uses, null));
			val >>= 16;
		}

		if (val > 0) {
			instrs.add(new AsmOPER("INCMH `d0," + (short) (val & 0xFFFF), null, uses, null));
			val >>= 16;
		}

		if (val > 0) {
			instrs.add(new AsmOPER("INCH `d0," + (short) (val & 0xFFFF), null, uses, null));
		}

		// Negate the value if needed
		if (offset < 0) {
			instrs.add(new AsmOPER("NEG `d0,`s0", uses, uses, null));
		}

		return instrs;
	}

	public void allocate() {
		for (Code code : AsmGen.codes) {

			// Re-do liveness analysis
			LiveAn liveAn = new LiveAn();
			liveAn.compLifetimes();

			// Try building and coloring a graph until it succeeds
			Graph finalGraph = new Graph();
			do {
				Graph graph = new Graph();

				// Add all variables to graph
				for (AsmInstr instr : code.instrs) {
					HashSet<MemTemp> combined = new HashSet<>(instr.uses());
					combined.addAll(instr.defs());

					// Convert every variable to a Vertex
					for (MemTemp temp : combined) {
						graph.addVertex(temp);
					}
				}

				// Add all neighbors to variables
				for (AsmInstr instr : code.instrs) {
					if (instr.defs() == null || instr.defs().size() == 0) continue;

					// Only outs are relevant
					for (MemTemp out : instr.out()) {
						graph.addNeighbors(instr.defs().get(0), out);
					}
				}

				// FP is needed for adding neighbors, but not afterwards, since it has a
				// predetermined register
				graph.removeVertex(code.frame.FP);

				// Store a list of all neighbors before simplifying graph
				HashMap<Vertex, HashSet<Vertex>> neighbors = graph.getNeighbors();

				// Simplify graph by removing vertices and handling spills
				Stack<Vertex> stack = new Stack<>();
				do {
					// Remove vertices with num of neighbors < num of regs
					boolean changed;
					do {
						changed = false;

						for (Vertex vertex : graph.vertices()) {
							if (vertex.neighbors.size() < nregs) {
								graph.removeVertex(vertex.variable);
								stack.push(vertex);
								changed = true;
							}
						}
					} while (changed);

					// Check for spills
					if (graph.vertices.size() > 0) {
						Vertex maxVertex = null;
						int maxNeighbors = nregs;

						// Find vertex with the greatest number of neighbors
						for (Vertex vertex : graph.vertices()) {
							if (vertex.neighbors.size() >= maxNeighbors) {
								maxNeighbors = vertex.neighbors.size();
								maxVertex = vertex;
							}
						}

						if (maxVertex != null) {
							// Remove the potential spill
							graph.removeVertex(maxVertex.variable);
							stack.push(maxVertex);
						}
					}
				} while (!graph.vertices.isEmpty());

				// Try coloring all the vertices
				ArrayList<Vertex> spills = new ArrayList<>();
				while (!stack.empty()) {
					Vertex vertex = stack.pop();
					boolean[] colors = new boolean[nregs];

					// Re-add vertex to new graph
					finalGraph.addVertex(vertex);

					// Re-add vertex's neighbors
					HashSet<Vertex> newNeighbors = neighbors.get(vertex);

					// Check neighbors' colors
					for (Vertex neighbor : newNeighbors) {
						finalGraph.addNeighbors(vertex, neighbor);

						if (neighbor.color >= 0) {
							colors[neighbor.color] = true;
						}
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
					// Don't restructure temps that were made from spilled temps
					//if (spilled.contains(spill.variable)) continue;

					Vector<AsmInstr> instrs = new Vector<>();
					long ptrSize = new SemPtr(new SemVoid()).size();
					code.tempSize += ptrSize;
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

							instrs.addAll(loadOffset(uses, offset));

							// Load value from offset
							instrs.add(new AsmOPER("LDO `d0,$253,`s0", uses, defs, null));

							// Replace old temp variable with new one if needed
							Vector<MemTemp> newUses = new Vector<>();
							for (MemTemp oldTemp : instr.uses()) {
								newUses.add(oldTemp == spill.variable ? newTemp : oldTemp);
							}

							// Add the new modified instruction to the list of instructions
							instrs.add(new AsmOPER(((AsmOPER) instr).instr(), newUses, instr.defs(), instr.jumps()));

							// Store new temp and offset temp to prevent them from spilling
							//spilled.addAll(List.of(newTemp, offsetTemp));
						}

						if (defsSpill) {
							// Get value and offset from memory
							MemTemp newTemp = new MemTemp();
							MemTemp offsetTemp = new MemTemp();
							Vector<MemTemp> uses = new Vector<>(List.of(offsetTemp));
							Vector<MemTemp> defs = new Vector<>(List.of(newTemp));

							// Add the new modified instruction to the list of instructions
							instrs.add(new AsmOPER(((AsmOPER) instr).instr(), instr.uses(), defs, instr.jumps()));

							instrs.addAll(loadOffset(uses, offset));

							// Store value to offset
							uses = new Vector<>(List.of(newTemp, offsetTemp));
							instrs.add(new AsmOPER("STO `s0,$253,`s1", uses, null, null));

							// Store new temp and offset temp to prevent them from spilling
							//spilled.addAll(List.of(newTemp, offsetTemp));
						}
					}

					// Replace instructions
					code.instrs.clear();
					code.instrs.addAll(instrs);
				}
			} while (true);

			// Replace temporary variables with registers
			tempToReg.put(code.frame.FP, 253);
			for (Vertex vertex : finalGraph.vertices()) {
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
