/*
 * Copyright (C) 2015-2017 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2015-2017 Marius Greitschus (greitsch@informatik.uni-freiburg.de)
 * Copyright (C) 2015-2017 University of Freiburg
 *
 * This file is part of the ULTIMATE AbstractInterpretationV2 plug-in.
 *
 * The ULTIMATE AbstractInterpretationV2 plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE AbstractInterpretationV2 plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE AbstractInterpretationV2 plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE AbstractInterpretationV2 plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE AbstractInterpretationV2 plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.boogie.DeclarationInformation;
import de.uni_freiburg.informatik.ultimate.boogie.DeclarationInformation.StorageClass;
import de.uni_freiburg.informatik.ultimate.boogie.ast.AssignmentStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.CallStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Expression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.IdentifierExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.LeftHandSide;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Procedure;
import de.uni_freiburg.informatik.ultimate.boogie.ast.VarList;
import de.uni_freiburg.informatik.ultimate.boogie.ast.VariableLHS;
import de.uni_freiburg.informatik.ultimate.boogie.symboltable.BoogieSymbolTable;
import de.uni_freiburg.informatik.ultimate.core.model.models.IBoogieType;
import de.uni_freiburg.informatik.ultimate.core.model.models.ILocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.Boogie2SmtSymbolTable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.IBoogieVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramNonOldVar;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Pair;

/**
 * The {@link CallInfoCache} provides for each call of an Icfg a {@link CallInfo} instance that contains assignment
 * statements for inparams and oldvars.
 *
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * @author Marius Greitschus (greitsch@informatik.uni-freiburg.de)
 */
public final class CallInfoCache {
	private final Map<CallStatement, CallInfo> mCall2CallInfo;
	private final CfgSmtToolkit mCfgSmtToolkit;
	private final BoogieSymbolTable mSymbolTable;
	private final Boogie2SmtSymbolTable mBoogie2SmtSymbolTable;

	public CallInfoCache(final CfgSmtToolkit cfgSmtToolkit, final BoogieSymbolTable boogieSymbolTable,
			final Boogie2SmtSymbolTable boogieSmtSymbolTable) {
		mCall2CallInfo = new HashMap<>();
		mCfgSmtToolkit = cfgSmtToolkit;
		mSymbolTable = boogieSymbolTable;
		mBoogie2SmtSymbolTable = boogieSmtSymbolTable;
	}

	/**
	 * Get a {@link CallInfo} instance for the specified call statement.
	 * 
	 * @param callStatement
	 *            The call statement.
	 * @return A {@link CallInfo} instance.
	 */
	public CallInfo getCallInfo(final CallStatement callStatement) {
		final CallInfo callAssignment = mCall2CallInfo.get(callStatement);
		if (callAssignment != null) {
			return callAssignment;
		}
		final CallInfo newCallAssignment = createCallInfo(callStatement);
		mCall2CallInfo.put(callStatement, newCallAssignment);
		return newCallAssignment;
	}

	private CallInfo createCallInfo(final CallStatement callStatement) {
		final Expression[] args = callStatement.getArguments();
		final List<IBoogieVar> realInParams = getInParams(callStatement);
		final AssignmentStatement oldVarAssign = getOldvarAssign(callStatement);

		// If there are no arguments, we don't need to rewrite states and thus no inparam assign.
		if (args.length == 0) {
			return new CallInfo(realInParams, oldVarAssign);
		}

		// create a multi-assignment statement that assigns each procedure argument (expression) to a temporary
		// variable

		final List<String> tmpParamNames =
				getArgumentTemporaries(args.length, getForbiddenNames(callStatement.getMethodName()));
		final List<LeftHandSide> idents = new ArrayList<>();
		final Map<LeftHandSide, IBoogieVar> tmpVarUses = new HashMap<>();
		final List<IBoogieVar> tmpParamVars = new ArrayList<>();
		final ILocation loc = callStatement.getLocation();
		for (int i = 0; i < args.length; i++) {
			final String name = tmpParamNames.get(i);
			final IBoogieVar boogieVar = AbsIntUtil.createTemporaryIBoogieVar(name, args[i].getType());
			final VariableLHS lhs = new VariableLHS(loc, name);
			tmpParamVars.add(boogieVar);
			tmpVarUses.put(lhs, boogieVar);
			idents.add(lhs);
		}

		final AssignmentStatement inParamTmpAssign =
				new AssignmentStatement(loc, idents.toArray(new LeftHandSide[idents.size()]), args);
		return new CallInfo(realInParams, oldVarAssign, inParamTmpAssign, tmpParamVars, tmpVarUses);
	}

	/**
	 * Creates a map of Integers -&gt; Identifiers for a given desired number of variables. The created identifiers are
	 * unique temporary variable identifiers. It is ensured that the created identifiers do not already occur in the
	 * current state.
	 *
	 * @param argNum
	 *            The number of temporary variables to create.
	 * @param state
	 *            The current state.
	 * @return A map containing for each index of the call statement's argument
	 */
	private static List<String> getArgumentTemporaries(final int argNum, final Set<String> reservedNames) {
		final List<String> rtr = new ArrayList<>(argNum);

		final StringBuilder paramBuilder = new StringBuilder("param_");
		boolean uniqueFound = false;

		while (!uniqueFound) {
			final String currentPrefix = paramBuilder.toString();
			for (int i = 0; i < argNum; i++) {
				final String currentParamName = currentPrefix + String.valueOf(i);
				if (reservedNames.contains(currentParamName)) {
					paramBuilder.append('_');
					break;
				}
			}
			uniqueFound = true;
		}

		final String finalPrefix = paramBuilder.toString();
		for (int i = 0; i < argNum; i++) {
			rtr.add(finalPrefix + String.valueOf(i));
		}

		return rtr;
	}

	/**
	 * Create an assignment for oldvar values at the beginning of a procedure: <code>
	 * old(x1),...,old(xn) := x1, .... , xn;
	 * </code>
	 */
	private AssignmentStatement getOldvarAssign(final CallStatement callStatement) {
		final String methodName = callStatement.getMethodName();
		final Set<IProgramNonOldVar> modifiableGlobals =
				mCfgSmtToolkit.getModifiableGlobalsTable().getModifiedBoogieVars(methodName);
		final int modglobsize = modifiableGlobals.size();
		if (modglobsize == 0) {
			return null;
		}
		final LeftHandSide[] lhs = new LeftHandSide[modglobsize];
		final Expression[] rhs = new Expression[modglobsize];
		int i = 0;
		final ILocation loc = callStatement.getLocation();
		for (final IProgramNonOldVar modGlob : modifiableGlobals) {

			final DeclarationInformation declInfo = new DeclarationInformation(StorageClass.GLOBAL, null);
			final IBoogieType bType =
					mSymbolTable.getTypeForVariableSymbol(modGlob.getGloballyUniqueId(), StorageClass.GLOBAL, null);
			lhs[i] = new VariableLHS(loc, bType, modGlob.getOldVar().getGloballyUniqueId(), declInfo);
			rhs[i] = new IdentifierExpression(loc, bType, modGlob.getGloballyUniqueId(), declInfo);
			++i;
		}
		return new AssignmentStatement(loc, lhs, rhs);
	}

	private List<IBoogieVar> getInParams(final CallStatement callStatement) {
		final Procedure procedure = getProcedure(callStatement.getMethodName());
		assert procedure != null;
		final VarList[] inParams = procedure.getInParams();
		final List<IBoogieVar> realParamVars = new ArrayList<>();

		for (final VarList varlist : inParams) {
			for (final String var : varlist.getIdentifiers()) {
				final IBoogieVar bVar = mBoogie2SmtSymbolTable.getBoogieVar(var, callStatement.getMethodName(), true);
				assert bVar != null;
				realParamVars.add(bVar);
			}
		}

		if (callStatement.getArguments().length != realParamVars.size()) {
			throw new UnsupportedOperationException(
					"The number of the expressions in the call statement arguments does not correspond to the length of the number of arguments in the symbol table.");
		}
		return realParamVars;
	}

	private Procedure getProcedure(final String procedureName) {
		return mSymbolTable.getFunctionOrProcedureDeclaration(procedureName).stream()
				.filter(decl -> decl instanceof Procedure).map(decl -> (Procedure) decl)
				.filter(proc -> proc.getBody() != null).findFirst().orElseThrow(() -> new UnsupportedOperationException(
						"Only uninterpreted functions available for " + procedureName));
	}

	private Set<String> getForbiddenNames(final String methodName) {
		final Set<String> rtr = new HashSet<>();
		mBoogie2SmtSymbolTable.getLocals(methodName).forEach(a -> rtr.add(a.getGloballyUniqueId()));
		mBoogie2SmtSymbolTable.getGlobals().forEach(a -> rtr.add(a.getGloballyUniqueId()));
		mBoogie2SmtSymbolTable.getConstants().forEach(a -> rtr.add(a.getGloballyUniqueId()));
		return rtr;
	}

	public static final class CallInfo {
		private final AssignmentStatement mInParamAssign;
		private final List<IBoogieVar> mTmpVars;
		private final Map<LeftHandSide, IBoogieVar> mTmpVarUses;
		private final List<IBoogieVar> mRealInParams;
		private final AssignmentStatement mOldVarAssign;
		private final List<Pair<IBoogieVar, IBoogieVar>> mInParam2TmpVars;

		private CallInfo(final List<IBoogieVar> realInParams, final AssignmentStatement oldVarAssign) {
			this(realInParams, oldVarAssign, null, Collections.emptyList(), Collections.emptyMap());
		}

		private CallInfo(final List<IBoogieVar> realInParams, final AssignmentStatement oldVarAssign,
				final AssignmentStatement inParamAssign, final List<IBoogieVar> tmpVars,
				final Map<LeftHandSide, IBoogieVar> tmpVarUses) {
			mOldVarAssign = oldVarAssign;
			mInParamAssign = inParamAssign;
			mTmpVars = Collections.unmodifiableList(tmpVars);
			mTmpVarUses = Collections.unmodifiableMap(tmpVarUses);
			mRealInParams = Collections.unmodifiableList(realInParams);

			assert realInParams.size() == tmpVars.size();
			final List<Pair<IBoogieVar, IBoogieVar>> inparams2tmpvars = new ArrayList<>(realInParams.size());
			for (int i = 0; i < realInParams.size(); ++i) {
				inparams2tmpvars.add(new Pair<>(realInParams.get(i), tmpVars.get(i)));
			}
			mInParam2TmpVars = Collections.unmodifiableList(inparams2tmpvars);
		}

		public List<IBoogieVar> getRealInParams() {
			return mRealInParams;
		}

		public List<IBoogieVar> getTempInParams() {
			return mTmpVars;
		}

		public Map<LeftHandSide, IBoogieVar> getLhs2TmpVar() {
			return mTmpVarUses;
		}

		public List<Pair<IBoogieVar, IBoogieVar>> getInParam2TmpVars() {
			return mInParam2TmpVars;
		}

		public AssignmentStatement getInParamAssign() {
			return mInParamAssign;
		}

		public AssignmentStatement getOldVarAssign() {
			return mOldVarAssign;
		}
	}
}