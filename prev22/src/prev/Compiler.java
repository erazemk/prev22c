package prev;

import org.antlr.v4.runtime.Token;
import prev.common.report.Report;
import prev.data.mem.MemTemp;
import prev.phase.abstr.AbsLogger;
import prev.phase.abstr.Abstr;
import prev.phase.all.MMIXTranslator;
import prev.phase.asmgen.AsmGen;
import prev.phase.imcgen.CodeGenerator;
import prev.phase.imcgen.ImcGen;
import prev.phase.imcgen.ImcLogger;
import prev.phase.imclin.ChunkGenerator;
import prev.phase.imclin.ImcLin;
import prev.phase.imclin.Interpreter;
import prev.phase.lexan.LexAn;
import prev.phase.livean.LiveAn;
import prev.phase.memory.MemEvaluator;
import prev.phase.memory.MemLogger;
import prev.phase.memory.Memory;
import prev.phase.regall.RegAll;
import prev.phase.seman.*;
import prev.phase.synan.SynAn;

import java.util.HashMap;

/**
 * The compiler.
 */
public class Compiler {

	// COMMAND LINE ARGUMENTS

	/** All valid phases of the compiler. */
	private static final String phases = "none|lexan|synan|abstr|seman|memory|imcgen|imclin|asmgen|livean|regall|all";

	/** A flag for enabling the printing of Report.info messages */
	public static boolean debug = false;

	/** Values of command line arguments. */
	private static final HashMap<String, String> cmdLine = new HashMap<>();

	/**
	 * Returns the value of a command line argument.
	 *
	 * @param cmdLineArgName The name of the command line argument.
	 * @return The value of the specified command line argument or {@code null} if
	 *         the specified command line argument has not been used.
	 */
	public static String cmdLineArgValue(String cmdLineArgName) {
		return cmdLine.get(cmdLineArgName);
	}

	// THE COMPILER'S STARTUP METHOD

	/**
	 * The compiler's startup method.
	 *
	 * @param args Command line arguments (see {@link prev.Compiler}).
	 */
	public static void main(String[] args) {
		try {
			System.out.println(":-) This is PREV'22 compiler:");

			// Scan the command line.
			for (String arg : args) {
				if (arg.startsWith("--")) {
					// Command-line switch.
					if (arg.matches("--debug")) {
						debug = true;
						continue;
					}
					if (arg.matches("--src-file-name=.*")) {
						if (cmdLine.get("--src-file-name") == null) {
							cmdLine.put("--src-file-name", arg);
							continue;
						}
					}
					if (arg.matches("--dst-file-name=.*")) {
						if (cmdLine.get("--dst-file-name") == null) {
							cmdLine.put("--dst-file-name", arg);
							continue;
						}
					}
					if (arg.matches("--target-phase=(" + phases + "|all)")) {
						if (cmdLine.get("--target-phase") == null) {
							cmdLine.put("--target-phase", arg.replaceFirst("^[^=]*=", ""));
							continue;
						}
					}
					if (arg.matches("--logged-phase=(" + phases + "|all)")) {
						if (cmdLine.get("--logged-phase") == null) {
							cmdLine.put("--logged-phase", arg.replaceFirst("^[^=]*=", ""));
							continue;
						}
					}
					if (arg.matches("--xml=.*")) {
						if (cmdLine.get("--xml") == null) {
							cmdLine.put("--xml", arg.replaceFirst("^[^=]*=", ""));
							continue;
						}
					}
					if (arg.matches("--xsl=.*")) {
						if (cmdLine.get("--xsl") == null) {
							cmdLine.put("--xsl", arg.replaceFirst("^[^=]*=", ""));
							continue;
						}
					}
					if (arg.matches("--nregs=[0-9]+")) {
						if (cmdLine.get("--nregs") == null) {
							cmdLine.put("--nregs", arg.replaceFirst("^[^=]*=", ""));
							continue;
						}
					}
					Report.warning("Command line argument '" + arg + "' ignored.");
				} else {
					// Source file name.
					if (cmdLine.get("--src-file-name") == null) {
						cmdLine.put("--src-file-name", arg);
					} else {
						Report.warning("Source file '" + arg + "' ignored.");
					}
				}
			}
			if (cmdLine.get("--src-file-name") == null) {
				throw new Report.Error("Source file not specified.");
			}
			if (cmdLine.get("--dst-file-name") == null) {
				cmdLine.put("--dst-file-name", cmdLine.get("--src-file-name").replaceFirst("\\.[^./]*$", "") + ".mms");
			}
			if ((cmdLine.get("--target-phase") == null) || (cmdLine.get("--target-phase").equals("all"))) {
				cmdLine.put("--target-phase", phases.replaceFirst("^.*\\|", ""));
			}

			// Compilation process carried out phase by phase.
			while (true) {

				// Lexical analysis.
				if (Compiler.cmdLineArgValue("--target-phase").equals("lexan"))
					try (LexAn lexan = new LexAn()) {
						while (lexan.lexer.nextToken().getType() != Token.EOF) {}
						break;
					}

				// Syntax analysis.
				try (LexAn lexan = new LexAn(); SynAn synan = new SynAn(lexan)) {
					SynAn.tree = synan.parser.source();
					synan.log(SynAn.tree);
				}
				if (Compiler.cmdLineArgValue("--target-phase").equals("synan"))
					break;

				// Abstract syntax tree construction.
				try (Abstr abstr = new Abstr()) {
					Abstr.tree = SynAn.tree.ast;
					AbsLogger logger = new AbsLogger(abstr.logger);
					Abstr.tree.accept(logger, "Decls");
				}
				if (Compiler.cmdLineArgValue("--target-phase").equals("abstr"))
					break;

				// Semantic analysis.
				try (SemAn seman = new SemAn()) {
					Abstr.tree.accept(new NameResolver(), null);
					Abstr.tree.accept(new TypeResolver(), null);
					Abstr.tree.accept(new AddrResolver(), null);
					AbsLogger logger = new AbsLogger(seman.logger);
					logger.addSubvisitor(new SemLogger(seman.logger));
					Abstr.tree.accept(logger, "Decls");
				}
				if (Compiler.cmdLineArgValue("--target-phase").equals("seman"))
					break;

				// Memory layout.
				try (Memory memory = new Memory()) {
					Abstr.tree.accept(new MemEvaluator(), null);
					AbsLogger logger = new AbsLogger(memory.logger);
					logger.addSubvisitor(new SemLogger(memory.logger));
					logger.addSubvisitor(new MemLogger(memory.logger));
					Abstr.tree.accept(logger, "Decls");
				}
				if (Compiler.cmdLineArgValue("--target-phase").equals("memory"))
					break;

				// Intermediate code generation.
				try (ImcGen imcgen = new ImcGen()) {
					Abstr.tree.accept(new CodeGenerator(), null);
					AbsLogger logger = new AbsLogger(imcgen.logger);
					logger.addSubvisitor(new SemLogger(imcgen.logger));
					logger.addSubvisitor(new MemLogger(imcgen.logger));
					logger.addSubvisitor(new ImcLogger(imcgen.logger));
					Abstr.tree.accept(logger, "Decls");
				}
				if (Compiler.cmdLineArgValue("--target-phase").equals("imcgen"))
					break;

				// Linearization of intermediate code.
				try (ImcLin imclin = new ImcLin()) {
					Abstr.tree.accept(new ChunkGenerator(), null);
					imclin.log();

					// Only run interpreter if this is the last phase
					if (Compiler.cmdLineArgValue("--target-phase").equals("imclin")) {
						Interpreter interpreter = new Interpreter(ImcLin.dataChunks(), ImcLin.codeChunks());
						System.out.println("EXIT CODE: " + interpreter.run("_main"));
					}
				}
				if (Compiler.cmdLineArgValue("--target-phase").equals("imclin"))
					break;

				// Machine code generation.
				try (AsmGen asmgen = new AsmGen()) {
					asmgen.genAsmCodes();
					asmgen.log();
				}
				if (Compiler.cmdLineArgValue("--target-phase").equals("asmgen"))
					break;

				// Liveness analysis.
				try (LiveAn livean = new LiveAn()) {
					livean.compLifetimes();
					livean.log();
				}
				if (Compiler.cmdLineArgValue("--target-phase").equals("livean"))
					break;

				// Register allocation.
				HashMap<MemTemp, Integer> tempToReg;
				try (RegAll regall = new RegAll(Integer.decode(cmdLine.get("--nregs")))) {
					regall.allocate();
					regall.log();
					tempToReg = regall.tempToReg;
				}
				if (Compiler.cmdLineArgValue("--target-phase").equals("regall"))
					break;

				// Working compiler.
				MMIXTranslator translator = new MMIXTranslator(cmdLine.get("--dst-file-name"), tempToReg);
				translator.translate();
				if (Compiler.cmdLineArgValue("--target-phase").equals("all"))
					break;
			}

			System.out.println(":-) Done.");
		} catch (Report.Error __) {
			System.exit(1);
		}
	}

}
