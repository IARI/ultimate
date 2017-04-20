/*
 * Copyright (C) 2017 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2017 University of Freiburg
 * 
 * This file is part of the ULTIMATE ASTBuilder plug-in.
 * 
 * The ULTIMATE ASTBuilder plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * The ULTIMATE ASTBuilder plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE ASTBuilder plug-in. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE ASTBuilder plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE ASTBuilder plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.astbuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Emitter that automatically generates visitor and transformer code for ASTs.
 * 
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 */
@SuppressWarnings("squid:S1192")
public abstract class EmitAstWithVisitors extends Emit {
	private static final String COMMA = ", ";
	private static final String BLANK = " ";
	private static final String CLOSE_PARENTHESIS_SEMICOLON = ");";
	private static final String NEW = "new";
	private static final String OR = " || ";
	private static final int LENGTH_OF_OR = 4;
	private static final int MIN_SIZE_EMIT_CLASS_DECLARATION = 33;

	protected boolean isNonClassicNode(final Node node) {
		return getNonClassicNode().contains(node.getName());
	}

	protected abstract Set<String> getNonClassicNode();

	protected abstract String getVisitorName();

	protected abstract String getTransformerName();

	protected abstract String getRootClassName();

	/**
	 * @return {@code true} iff root is serializable.
	 */
	@SuppressWarnings("static-method")
	protected boolean isRootSerializable() {
		return false;
	}

	@Override
	public void emitPreamble(final Node node) {
		super.emitPreamble(node);
		mWriter.println("import java.util.List;");

		if (getAllParameters(node).stream().anyMatch(EmitAstWithVisitors::isArrayType)) {
			mWriter.println("import java.util.Arrays;");
			mWriter.println("import java.util.ArrayList;");
		}
	}

	@Override
	public void emitClassDeclaration(final Node node) {
		final StringBuilder classDecl = new StringBuilder(MIN_SIZE_EMIT_CLASS_DECLARATION);
		classDecl.append("public ");
		if (node.isAbstract()) {
			classDecl.append("abstract ");
		}
		classDecl.append("class ").append(node.getName());

		if (node.getParent() != null) {
			classDecl.append(" extends ").append(node.getParent().getName());
		} else if (!isNonClassicNode(node)) {
			classDecl.append(" extends ").append(getRootClassName());
		}

		if (node.getInterfaces() != null) {
			classDecl.append(" implements ").append(node.getInterfaces());
		}
		classDecl.append(" {");
		mWriter.println(classDecl.toString());
		if (isNonClassicNode(node)) {
			return;
		}

		if (isRootSerializable()) {
			mWriter.println("    private static final long serialVersionUID = 1L;");
		}

		mWriter.println(
				"    private static final java.util.function.Predicate<" + getRootClassName() + "> VALIDATOR = ");
		mWriter.println("			" + getRootClassName() + ".VALIDATORS.get(" + node.getName() + ".class);");
	}

	@Override
	public void emitNodeHook(final Node node) {
		if (node.name.equals(getVisitorName())) {
			emitVisitorHook();
		} else if (node.name.equals(getTransformerName())) {
			emitTransformerHook();
		} else {
			emitClassicNodeHook(node);
		}
	}

	private void emitClassicNodeHook(final Node node) {
		mWriter.println();
		mWriter.println("    public List<" + getRootClassName() + "> getOutgoingNodes() {");
		mWriter.println("        List<" + getRootClassName() + "> children = super.getOutgoingNodes();");
		final Parameter[] parameters = node.getParameters();
		System.out.println(node.getName() + " has " + parameters.length + " parameters");
		for (int i = 0; i < parameters.length; i++) {

			if (isNoRegularChild(parameters[i].getType())) {
				continue;
			}
			System.out.println(parameters[i].getName() + " is an array? " + isArray(parameters[i].getType()));

			if (isArray(parameters[i].getType())) {
				mWriter.println(String.format("        if(%s!=null){", parameters[i].getName()));
				mWriter.println(
						String.format("            children.addAll(Arrays.asList(%s));", parameters[i].getName()));
				mWriter.println("        }");
			} else {
				mWriter.println("        children.add(" + parameters[i].getName() + CLOSE_PARENTHESIS_SEMICOLON);
			}
		}
		mWriter.println("        return children;");
		mWriter.println("    }");

		if (!node.isAbstract()) {
			final List<Parameter> allParameters = getAllParameters(node);
			writeVisitorAcceptMethod(node, allParameters);
			writeTransformerAcceptMethod(node, allParameters);

		} else {
			mWriter.println();
			mWriter.println("    public abstract void accept(" + getVisitorName() + " visitor);");

			mWriter.println();
			mWriter.println("    public abstract " + node.name + " accept(" + getTransformerName() + " visitor);");
		}
	}

	private void emitTransformerHook() {
		for (final Node n : mGrammar.getNodeTable().values()) {
			if (getNonClassicNode().contains(n.getName())) {
				continue;
			}
			mWriter.println();
			mWriter.println("    public " + n.name + " transform(" + n.name + " node) {");
			mWriter.println("        return node;");
			mWriter.println("    }");
		}
	}

	private void emitVisitorHook() {
		for (final Node n : mGrammar.getNodeTable().values()) {
			if (getNonClassicNode().contains(n.getName())) {
				continue;
			}
			mWriter.println();
			mWriter.println("    public boolean visit(" + n.name + " node) {");
			mWriter.println("        return true;");
			mWriter.println("    }");
		}
	}

	@Override
	public void emitConstructors(final Node node) {
		int numNotOptionalParams = 1;
		int numTotalParams = 1;

		/* Default constructor is only emitted if all fields are writeable */
		/* Optional constructor is only emitted if there are optional fields */
		Node ancestor = node;
		while (ancestor != null) {
			for (final Parameter p : ancestor.parameters) {
				numTotalParams++;
				if (!p.isOptional()) {
					numNotOptionalParams++;
				}
			}
			ancestor = ancestor.getParent();
		}

		if (numNotOptionalParams < numTotalParams) {
			emitConstructor(node, false);
		}
		emitConstructor(node, true);
	}

	@Override
	public void emitConstructorAfterParamAssign(final Node node, final boolean optional) {
		super.emitConstructorAfterParamAssign(node, optional);
		if (isNonClassicNode(node)) {
			return;
		}
		mWriter.println("        assert VALIDATOR == null || VALIDATOR.test(this) : \"Invalid " + node.getName()
				+ ": \" + this;");
	}

	protected static boolean isArray(final String type) {
		return type.contains("[");

	}

	protected boolean isNoRegularChild(final String type) {
		String acc = type;
		while (acc.endsWith("[]")) {
			acc = acc.substring(0, acc.length() - 2);
		}
		return !mGrammar.getNodeTable().containsKey(acc);
	}

	private static boolean isArrayType(final Parameter param) {
		return param.getType().contains("[]");
	}

	private static String getBaseType(final Parameter param) {
		return param.getType().replaceAll("\\[\\]", "");
	}

	private void writeTransformerAcceptMethod(final Node node, final List<Parameter> allAcslParameters) {
		// accept method for transformer
		mWriter.println();
		mWriter.println("    public " + node.name + " accept(" + getTransformerName() + " visitor) {");
		mWriter.println("        " + node.name + " node = visitor.transform(this);");
		mWriter.println("        if(node != this){");
		mWriter.println("            return node;");
		mWriter.println("        }");
		mWriter.println();

		boolean isChangedPrinted = false;

		for (final Parameter p : allAcslParameters) {
			final String newName = NEW + p.getName();
			final String listName = "tmpList" + newName;
			// declarations
			if (isArrayType(p)) {
				if (!isChangedPrinted) {
					mWriter.println("        boolean isChanged=false;");
					isChangedPrinted = true;
				}
				mWriter.println("            ArrayList<" + getBaseType(p) + "> " + listName + " = new ArrayList<>();");
			} else {
				mWriter.println("            " + p.type + BLANK + newName + " = null;");
			}

			mWriter.println("        if(" + p.getName() + " != null){");
			if (isArrayType(p)) {
				mWriter.println("            for(" + getBaseType(p) + " elem : " + p.getName() + "){");
				mWriter.println("                " + getBaseType(p) + BLANK + newName + " = elem.accept(visitor);");
				mWriter.println("                isChanged = isChanged || " + newName + " != elem;");
				mWriter.println("                " + listName + ".add(elem.accept(visitor));");
				mWriter.println("            }");
			} else {
				mWriter.println("            " + newName + " = " + p.getName() + ".accept(visitor);");
			}
			mWriter.println("        }");
		}

		if (!allAcslParameters.isEmpty()) {

			final StringBuilder builder = new StringBuilder();
			builder.append("        if(");

			if (isChangedPrinted) {
				builder.append("isChanged || ");
			}

			for (final Parameter p : allAcslParameters) {
				if (isArrayType(p)) {
					continue;
				}
				final String newName = NEW + p.getName();
				builder.append(p.name).append(" != ").append(newName).append(OR);
			}
			if (OR.equals(builder.substring(builder.length() - LENGTH_OF_OR, builder.length()))) {
				builder.delete(builder.length() - LENGTH_OF_OR, builder.length());
			}
			builder.append("){");
			mWriter.println(builder.toString());
			mWriter.println(
					"            return new " + node.name + "(" + getNewCallParams(node) + CLOSE_PARENTHESIS_SEMICOLON);
			mWriter.println("        }");
		}
		mWriter.println("        return this;");
		mWriter.println("    }");
	}

	private String getNewCallParams(final Node node) {
		if (node == null) {
			return "";
		}

		final StringBuilder builder = new StringBuilder();

		builder.append(getNewCallParams(node.getParent()));

		String comma = "";
		if (builder.length() > 0) {
			comma = COMMA;
		}

		for (final Parameter param : node.getParameters()) {
			String pname;
			if (!mGrammar.nodeTable.containsKey(getBaseType(param))) {
				pname = param.getName();
			} else if (isArrayType(param)) {
				pname = "tmpListnew" + param.getName() + ".toArray(new " + getBaseType(param) + "[0])";
			} else {
				pname = NEW + param.getName();
			}
			builder.append(comma).append(pname);
			comma = COMMA;
		}
		return builder.toString();

	}

	private void writeVisitorAcceptMethod(final Node node, final List<Parameter> allAcslParameters) {
		// accept method for visitor
		mWriter.println();
		mWriter.println("    public void accept(" + getVisitorName() + " visitor) {");

		final String lineSep = System.getProperty("line.separator");

		Node parent = node.getParent();
		final String indent = "        ";
		int index1 = 0;
		final StringBuilder builder = new StringBuilder();
		if (parent != null) {
			while (parent != null) {
				int index2 = index1 + 1;
				while (index2 > 0) {
					builder.append(indent);
					index2--;
				}
				// @formatter:off
				builder.append("if(visitor.visit((")
						.append(parent.name)
						.append(")this)){")
						.append(lineSep);
				// @formatter:on
				parent = parent.getParent();
				index1++;
			}

			while (index1 > 0) {
				int index2 = index1 + 1;
				final StringBuilder localIndentBuilder = new StringBuilder();
				while (index2 > 0) {
					localIndentBuilder.append(indent);
					index2--;
				}
				final String localIndent = localIndentBuilder.toString();
				// @formatter:off
				builder.append(localIndent)
						.append("} else {")
						.append(lineSep)
						.append(indent)
						.append(localIndent)
						.append("return;")
						.append(lineSep)
						.append(localIndent);
				// @formatter:on
				index1--;
			}
			builder.append(indent).append('}');
			mWriter.println(builder.toString());
		}

		mWriter.println("        if(visitor.visit(this)){");
		for (final Parameter p : allAcslParameters) {
			mWriter.println("            if(" + p.getName() + "!=null){");
			if (isArrayType(p)) {
				mWriter.println("                for(" + getBaseType(p) + " elem : " + p.getName() + "){");
				mWriter.println("                    elem.accept(visitor);");
				mWriter.println("                }");

			} else {
				mWriter.println("                " + p.getName() + ".accept(visitor);");
			}
			mWriter.println("            }");
		}
		mWriter.println("        }");
		mWriter.println("    }");
	}

	protected List<Parameter> getAllParameters(final Node node) {
		final List<Parameter> allParameters = new ArrayList<>();
		Node current = node;
		while (current != null) {
			for (final Parameter p : current.getParameters()) {
				if (mGrammar.nodeTable.containsKey(getBaseType(p))) {
					allParameters.add(p);
				}
			}
			current = current.getParent();
		}
		return allParameters;
	}

	@Override
	public void setGrammar(final Grammar grammar) {
		final HashSet<String> types = new HashSet<>();
		for (final Node n : grammar.getNodeTable().values()) {
			types.add(n.name);
		}
		grammar.getNodeTable().put(getVisitorName(),
				new Node(getVisitorName(), null, null, "", types, false, new Parameter[0]));
		grammar.getNodeTable().put(getTransformerName(),
				new Node(getTransformerName(), null, null, "", types, false, new Parameter[0]));
		super.setGrammar(grammar);
	}
}
