package prev.phase.memory;

import prev.common.report.Report;
import prev.data.ast.tree.*;
import prev.data.ast.tree.decl.*;
import prev.data.ast.tree.expr.*;
import prev.data.ast.tree.type.*;
import prev.data.ast.visitor.*;
import prev.data.typ.*;
import prev.data.mem.*;
import prev.phase.seman.*;

/**
 * Computing memory layout: frames and accesses.
 */
public class MemEvaluator extends AstFullVisitor<Object, MemEvaluator.Context> {

	/**
	 * The context {@link MemEvaluator} uses while computing function frames and
	 * variable accesses.
	 */
	protected abstract class Context {
	}

	/*@Override
	public Object visit(AstTrees<? extends AstTree> trees, Context context) {
		return null;
	}*/

	/*@Override
	public Object visit(AstCompDecl compDecl, Context context) {
		return null;
	}*/

	/*@Override
	public Object visit(AstFunDecl funDecl, Context context) {
		return null;
	}*/

	/*@Override
	public Object visit(AstParDecl parDecl, Context context) {
		return null;
	}*/

	@Override
	public Object visit(AstTypeDecl typeDecl, Context context) {
		return null;
	}

	/*@Override
	public Object visit(AstVarDecl varDecl, Context context) {
		return null;
	}*/

	@Override
	public Object visit(AstAtomExpr atomExpr, Context context) {
		if (atomExpr.type != AstAtomExpr.Type.STRING) return null;

		// Map a label to the string and add it to the list of strings
		String string = atomExpr.value
			.substring(1, atomExpr.value.length() - 1) // Remove surrounding quotes from string
			.replace("\\\"", "\""); // Replace escaped quotes with normal quotes

		MemAbsAccess absAccess = new MemAbsAccess((string.length() + 1) * 8L, new MemLabel(), string);
		Memory.strings.put(atomExpr, absAccess);
		return null;
	}

	/*@Override
	public Object visit(AstCallExpr callExpr, Context context) {
		return null;
	}*/

	/*@Override
	public Object visit(AstNameExpr nameExpr, Context context) {
		return null;
	}*/

	/*@Override
	public Object visit(AstRecType recType, Context context) {
		return null;
	}*/

}
