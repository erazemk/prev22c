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
	protected static class Context {
		int depth = 0;
		long offset = 0;
		long argsSize = 0;
		long locsSize = 0;
	}

	// DECLARATIONS

	@Override
	public Object visit(AstCompDecl compDecl, Context context) {
		compDecl.type.accept(this, context);

		// Get the component's type to calculate size
		SemType compType = SemAn.isType.get(compDecl.type);

		// Create a new relative variable
		MemRelAccess relAccess = new MemRelAccess(compType.size(), context.offset, 0);
		context.offset += compType.size();
		Memory.accesses.put(compDecl, relAccess);

		return null;
	}

	@Override
	public Object visit(AstFunDecl funDecl, Context context) {
		Context newContext = new Context();

		// If top level function, the context will be null (so depth of children is 1)
		if (context == null) {
			newContext.depth = 1;
		} else {
			newContext.depth = context.depth + 1;
		}

		newContext.offset = (new SemPtr(new SemVoid())).size();

		// Evaluate parameters' size
		if (funDecl.pars != null) {
			funDecl.pars.accept(this, newContext);
		}

		newContext.offset = 0;

		// Evaluate return type's size (shouldn't be null, but AstFullVisitor does it this way)
		if (funDecl.type != null) {
			funDecl.type.accept(this, newContext);
		}

		// Evaluate expression's size
		if (funDecl.expr != null) {
			funDecl.expr.accept(this, newContext);
		}

		// Create a named label if top level, otherwise create an anonymous label
		MemLabel label;
		if (newContext.depth == 1) {
			label = new MemLabel(funDecl.name);
		} else {
			label = new MemLabel();
		}

		// Create a stack frame
		MemFrame frame = new MemFrame(label, newContext.depth - 1, newContext.locsSize, newContext.argsSize);
		Memory.frames.put(funDecl, frame);

		return null;
	}

	@Override
	public Object visit(AstParDecl parDecl, Context context) {

		// Evaluate type's size
		parDecl.type.accept(this, context);

		// Get the parameter's type to calculate size
		SemType type = SemAn.isType.get(parDecl.type);

		// Create a new relative variable
		MemRelAccess relAccess = new MemRelAccess(type.size(), context.offset, context.depth);
		context.offset += type.size();
		Memory.accesses.put(parDecl, relAccess);

		return null;
	}

	@Override
	public Object visit(AstTypeDecl typeDecl, Context context) {
		typeDecl.type.accept(this, context);
		return null;
	}

	@Override
	public Object visit(AstVarDecl varDecl, Context context) {
		// Evaluate type's size
		varDecl.type.accept(this, context);

		// Get variable's type to calculate size
		SemType type = SemAn.isType.get(varDecl.type);

		// If top level variable, the context will be null
		if (context == null) {
			// Top level variables use absolute addresses and proper names
			MemLabel label = new MemLabel(varDecl.name);
			MemAbsAccess absAccess = new MemAbsAccess(type.size(), label);
			Memory.accesses.put(varDecl, absAccess);
		} else {
			// Non-top level variables use relative addresses
			context.locsSize += type.size();
			context.offset -= type.size();
			MemRelAccess relAccess = new MemRelAccess(type.size(), context.offset, context.depth);
			Memory.accesses.put(varDecl, relAccess);
		}

		varDecl.type.accept(this, new Context());

		return null;
	}

	// EXPRESSIONS

	@Override
	public Object visit(AstAtomExpr atomExpr, Context context) {
		// We only need to evaluate strings
		if (atomExpr.type != AstAtomExpr.Type.STRING) return null;

		String string = atomExpr.value
			.substring(1, atomExpr.value.length() - 1) // Remove surrounding quotes from string
			.replace("\\\"", "\""); // Replace escaped quotes with normal quotes

		// Create an anonymous label, each char is 8 bits, +1 char for \0
		MemAbsAccess absAccess = new MemAbsAccess((string.length() + 1) * (new SemChar()).size(),
			new MemLabel(), string);
		Memory.strings.put(atomExpr, absAccess);

		return null;
	}

	@Override
	public Object visit(AstCallExpr callExpr, Context context) {
		if (callExpr.args == null) {
			context.argsSize = (new SemPtr(new SemInt())).size();
			return null;
		}

		// Evaluate arguments' sizes
		callExpr.args.accept(this, context);

		// Size of arguments is the actual size + the size of a pointer that points to SL
		long size = (new SemPtr(new SemInt())).size();

		for (AstExpr expr: callExpr.args) {
			SemType type = SemAn.ofType.get(expr);
			size += type.size();
		}

		// Args size is either the calculated args size or the size of call parameters
		context.argsSize = Math.max(size, context.argsSize);

		return null;
	}

	// TYPES

	@Override
	public Object visit(AstRecType recType, Context context) {
		// Evaluate components' sizes
		if (recType.comps != null) {
			recType.comps.accept(this, new Context());
		}

		return null;
	}

}
