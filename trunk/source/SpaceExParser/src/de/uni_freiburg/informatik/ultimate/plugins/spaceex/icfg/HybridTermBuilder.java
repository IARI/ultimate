package de.uni_freiburg.informatik.ultimate.plugins.spaceex.icfg;

import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.util.HybridPreprocessor;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.util.SpaceExMathHelper;

/**
 * Class to build Terms from Hybrid automata expressions like Initial values, Invariants and Jumps
 *
 * @author Julian Loeffler (loefflju@informatik.uni-freiburg.de)
 *
 */
public class HybridTermBuilder {
	private final HybridVariableManager mVariableManager;
	private final Script mScript;
	private final ManagedScript mManagedScript;
	private final Map<String, Term> mStringTerm;
	private final Map<HybridProgramVar, TermVariable> mInVars;
	private final Map<HybridProgramVar, TermVariable> mOutVars;
	private TermVariable mAuxVar;
	ILogger mLogger;
	
	public enum BuildScenario {
		INITIALLY, INVARIANT, UPDATE, GUARD, FLOW;
	}
	
	public HybridTermBuilder(final HybridVariableManager variableManger, final ManagedScript script,
			final ILogger logger) {
		mVariableManager = variableManger;
		mScript = script.getScript();
		mManagedScript = script;
		mStringTerm = new HashMap<>();
		mInVars = new HashMap<>();
		mOutVars = new HashMap<>();
		mAuxVar = null;
		mLogger = logger;
	}
	
	public Term infixToTerm(final String infix, final BuildScenario scenario) {
		List<String> infixArray = SpaceExMathHelper.expressionToArray(infix);
		if (scenario == BuildScenario.UPDATE) {
			infixArray = HybridPreprocessor.preprocessForUpdate(infixArray);
		}
		final List<String> postfix = SpaceExMathHelper.postfix(infixArray);
		final List<String> postfixSMTConform = HybridPreprocessor.preprocessForTermBuilding(postfix);
		return postfixToTerm(postfixSMTConform, scenario);
	}
	
	/**
	 * Function to convert a given formula postfix notation as array, into a term., sali ge
	 *
	 * @param postfix
	 * @param script
	 * @param variableManager
	 * @return
	 */
	public Term postfixToTerm(final List<String> postfix, final BuildScenario scenario) {
		/*
		 * 1. Create an empty stack that can hold string values. 2. Scan the postfix expression from left to right 2a.
		 * If operand then push into stack 2b. If operator then 1. Pop first two elements 2. Now make a term of the form
		 * (operand2,operator,operand1)" 3. Push the new term onto stack
		 */
		Term term = null;
		final Deque<String> stack = new LinkedList<>();
		for (final String el : postfix) {
			final String element = el;
			if (SpaceExMathHelper.isOperator(element)) {
				final String operand1 = stack.pop();
				final String operand2 = stack.pop();
				final String operator = element;
				final Term tmpTerm = checkAndbuildTerm(operand1, operand2, operator, scenario);
				if (term == null) {
					term = tmpTerm;
					stack.push(term.toString());
					mStringTerm.put(term.toString(), term);
				} else if (tmpTerm != null) {
					term = tmpTerm;
					stack.push(tmpTerm.toString());
					mStringTerm.put(tmpTerm.toString(), term);
				}
			} else {
				stack.push(element);
			}
		}
		return term;
	}
	
	// helper function to build terms from postfix notation
	private Term checkAndbuildTerm(final String operand1, final String operand2, final String operator,
			final BuildScenario scenario) {
		// check if the operand already got a term.
		Term tmpTerm;
		if (mStringTerm.containsKey(operand1) && mStringTerm.containsKey(operand2)) {
			final Term t1 = mStringTerm.get(operand1);
			final Term t2 = mStringTerm.get(operand2);
			tmpTerm = mScript.term(operator, t2, t1);
		} else if (mStringTerm.containsKey(operand1) && !mStringTerm.containsKey(operand2)) {
			final Term t1 = mStringTerm.get(operand1);
			tmpTerm = buildTerm(t1, operand2, operator, scenario);
		} else if (!mStringTerm.containsKey(operand1) && mStringTerm.containsKey(operand2)) {
			final Term t2 = mStringTerm.get(operand2);
			tmpTerm = buildTerm(operand1, t2, operator, scenario);
		} else {
			tmpTerm = buildTerm(operand1, operand2, operator, scenario);
		}
		return tmpTerm;
	}
	
	// helper function to build terms from postfix notation
	private Term buildTerm(final String operand1, final Term term2, final String operator,
			final BuildScenario scenario) {
		Term tmpTerm;
		final TermVariable tv1 = checkAndGetTermVariable(operand1, scenario, false);
		/*
		 * There are 2 cases what can happen, either a Var Inequality or not
		 */
		final TermVariable[] free = term2.getFreeVars();
		final boolean isVarInequality = free.length > 0 && isInequality(operator) ? true : false;
		// build term
		if (isVarInequality) {
			if (tv1 == null) {
				final Term t1 = mScript.term(operator, free[free.length - 1], mScript.decimal(operand1));
				tmpTerm = mScript.term("and", term2, t1);
			} else {
				final Term t1 = mScript.term(operator, free[free.length - 1], tv1);
				tmpTerm = mScript.term("and", term2, t1);
			}
		} else {
			if (tv1 == null) {
				tmpTerm = mScript.term(operator, term2, mScript.decimal(operand1));
			} else {
				tmpTerm = mScript.term(operator, term2, tv1);
			}
		}
		return tmpTerm;
	}
	
	private boolean isInequality(final String operator) {
		return ">=".equals(operator) || ">".equals(operator) || "<=".equals(operator) || "<".equals(operator);
	}
	
	// helper function to build terms from postfix notation
	private Term buildTerm(final String operand1, final String operand2, final String operator,
			final BuildScenario scenario) {
		Term tmpTerm;
		final TermVariable tv1 = checkAndGetTermVariable(operand1, scenario, false);
		final TermVariable tv2 = checkAndGetTermVariable(operand2, scenario, !SpaceExMathHelper.isEvaluation(operator));
		// build term
		if (tv1 == null && tv2 == null) {
			tmpTerm = mScript.term(operator, mScript.decimal(operand2), mScript.decimal(operand1));
		} else if (tv1 != null && tv2 == null) {
			tmpTerm = mScript.term(operator, mScript.decimal(operand2), tv1);
		} else if (tv1 == null) {
			tmpTerm = mScript.term(operator, tv2, mScript.decimal(operand1));
		} else {
			tmpTerm = mScript.term(operator, tv2, tv1);
		}
		return tmpTerm;
	}
	
	// helper function to build terms from postfix notation
	private Term buildTerm(final Term term1, final String operand2, final String operator,
			final BuildScenario scenario) {
		Term tmpTerm;
		final TermVariable tv2 = checkAndGetTermVariable(operand2, scenario, !SpaceExMathHelper.isEvaluation(operator));
		// build term
		if (tv2 == null) {
			tmpTerm = mScript.term(operator, mScript.decimal(operand2), term1);
		} else {
			tmpTerm = mScript.term(operator, tv2, term1);
		}
		return tmpTerm;
	}
	
	// helper function to get the correct termvariable for each scenario
	private TermVariable checkAndGetTermVariable(final String operand1, final BuildScenario scenario,
			final boolean isAssignedValue) {
		if (mVariableManager.getConstants().contains(operand1) && scenario != BuildScenario.INITIALLY) {
			return null;
		} else if (scenario == BuildScenario.INITIALLY) {
			return getInitiallyTV(operand1);
		} else if (scenario == BuildScenario.GUARD || scenario == BuildScenario.INVARIANT) {
			return getInvariantTV(operand1);
		} else if (scenario == BuildScenario.UPDATE) {
			return getUpdateTV(operand1, isAssignedValue);
		} else if (scenario == BuildScenario.FLOW) {
			return getFlowTV(operand1, isAssignedValue);
		} else {
			throw new UnsupportedOperationException("Unknown scenario " + scenario.toString());
		}
	}
	
	private TermVariable getFlowTV(final String operand1, final boolean isAssignedValue) {
		if (operand1.equals(HybridIcfgGenerator.TIME_VAR)) {
			// Create new term variable and add to auxvars
			if (mAuxVar == null) {
				mAuxVar = mManagedScript.constructFreshTermVariable(operand1, mScript.sort("Real"));
			}
			return mAuxVar;
		} else {
			return getUpdateTV(operand1, isAssignedValue);
		}
	}
	
	// helper function to get TermVariable for initially Terms
	private TermVariable getInitiallyTV(final String operand1) {
		if (mVariableManager.getVar2OutVarTermVariable().containsKey(operand1)) {
			final HybridProgramVar progvar = mVariableManager.getVar2ProgramVar().get(operand1);
			final TermVariable outvar = mVariableManager.getVar2OutVarTermVariable().get(operand1);
			mOutVars.put(progvar, outvar);
			return outvar;
		} else {
			return null;
		}
	}
	
	// helper function to get TermVariable for Invariant or Guard Terms
	private TermVariable getInvariantTV(final String operand1) {
		if (mVariableManager.getVar2InVarTermVariable().containsKey(operand1)) {
			final HybridProgramVar progvar = mVariableManager.getVar2ProgramVar().get(operand1);
			final TermVariable invar = mVariableManager.getVar2InVarTermVariable().get(operand1);
			mInVars.put(progvar, invar);
			mOutVars.put(progvar, invar);
			return invar;
		} else {
			return null;
		}
	}
	
	// helper function to get TermVariable for Invariant or Update Terms
	private TermVariable getUpdateTV(final String operand1, final boolean isLeftHandSide) {
		if (isLeftHandSide) {
			if (mVariableManager.getVar2OutVarTermVariable().containsKey(operand1)) {
				final HybridProgramVar progvar = mVariableManager.getVar2ProgramVar().get(operand1);
				final TermVariable outvar = mVariableManager.getVar2OutVarTermVariable().get(operand1);
				mOutVars.put(progvar, outvar);
				return outvar;
			} else {
				return null;
			}
		} else {
			if (mVariableManager.getVar2InVarTermVariable().containsKey(operand1)) {
				final HybridProgramVar progvar = mVariableManager.getVar2ProgramVar().get(operand1);
				final TermVariable invar = mVariableManager.getVar2InVarTermVariable().get(operand1);
				mInVars.put(progvar, invar);
				return invar;
			} else {
				return null;
			}
		}
	}
	
	private void testTermBuilding() {
		final Map<String, BuildScenario> tests = new HashMap<>();
		tests.put("0 <= x <= y <= 5", BuildScenario.INVARIANT);
		tests.put("x==y", BuildScenario.UPDATE);
		tests.put("x==5", BuildScenario.UPDATE);
		tests.put("x==5 & y==5.01", BuildScenario.UPDATE);
		tests.put("0 <= x <=5", BuildScenario.INITIALLY);
		tests.put("0 <= x <=5", BuildScenario.INVARIANT);
		tests.forEach((t, s) -> {
			mLogger.info("###########START###########");
			mLogger.info("INFIX: " + t);
			final Term term = infixToTerm(t, s);
			mLogger.info("TERM: " + term.toStringDirect());
			mLogger.info("###########END###########");
		});
		
	}
	
	public Map<HybridProgramVar, TermVariable> getmInVars() {
		return mInVars;
	}
	
	public Map<HybridProgramVar, TermVariable> getmOutVars() {
		return mOutVars;
	}
	
	public TermVariable getAuxVar() {
		return mAuxVar;
	}
	
}
