/*
 * Copyright (C) 2014-2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2010-2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2012-2015 University of Freiburg
 *
 * This file is part of the ULTIMATE ModelCheckerUtils Library.
 *
 * The ULTIMATE ModelCheckerUtils Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE ModelCheckerUtils Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE ModelCheckerUtils Library. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE ModelCheckerUtils Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE ModelCheckerUtils Library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.core.lib.exceptions.RunningTaskInfo;
import de.uni_freiburg.informatik.ultimate.core.lib.exceptions.ToolchainCanceledException;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.FormulaUnLet;
import de.uni_freiburg.informatik.ultimate.logic.QuantifiedFormula;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;
import de.uni_freiburg.informatik.ultimate.logic.Sort;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.logic.Util;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.IIcfgSymbolTable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.ModifiableGlobalsTable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula.Infeasibility;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.ILocalProgramVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramConst;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramNonOldVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramOldVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.hoaretriple.IHoareTripleChecker.Validity;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.ConstantFinder;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.DagSizePrinter;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.MonolithicImplicationChecker;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.PartialQuantifierElimination;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.SimplificationTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.XnfConversionTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SubstitutionWithLocalSimplification;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.linearTerms.QuantifierPusher;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.linearTerms.QuantifierPusher.PqeTechniques;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.partialQuantifierElimination.XnfDer;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.BasicPredicateFactory;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.PredicateTransformer;
import de.uni_freiburg.informatik.ultimate.util.DebugMessage;

/**
 * Static auxiliary methods for {@link TransFormula}s
 *
 * @author heizmann@informatik.uni-freiburg.de
 */
public class TransFormulaUtils {

	/**
	 * compute the assigned/updated variables. A variable is updated by this transition if it occurs as outVar and - it
	 * does not occur as inVar - or the inVar is represented by a different TermVariable
	 */
	public static HashSet<IProgramVar> computeAssignedVars(final Map<IProgramVar, TermVariable> inVars,
			final Map<IProgramVar, TermVariable> outVars) {
		final HashSet<IProgramVar> assignedVars = new HashSet<>();
		for (final IProgramVar var : outVars.keySet()) {
			assert outVars.get(var) != null;
			if (outVars.get(var) != inVars.get(var)) {
				assignedVars.add(var);
			}
		}
		return assignedVars;
	}

	/**
	 * @param services
	 * @return the relational composition (concatenation) of transformula1 und transformula2
	 */
	public static UnmodifiableTransFormula sequentialComposition(final ILogger logger,
			final IUltimateServiceProvider services, final ManagedScript mgdScript, final boolean simplify,
			final boolean extPqe, final boolean tranformToCNF, final XnfConversionTechnique xnfConversionTechnique,
			final SimplificationTechnique simplificationTechnique, final List<UnmodifiableTransFormula> transFormula) {
		if (logger.isDebugEnabled()) {
			logger.debug("sequential composition with" + (simplify ? "" : "out") + " formula simplification");
		}
		final Script script = mgdScript.getScript();
		final Set<TermVariable> auxVars = new HashSet<>();
		Term formula = mgdScript.getScript().term("true");

		final TransFormulaBuilder tfb = new TransFormulaBuilder(null, null, false, null, false, null, false);
		final Set<IProgramConst> nonTheoryConsts = new HashSet<>();

		final Map<Term, Term> substitutionMapping = new HashMap<>();
		for (int i = transFormula.size() - 1; i >= 0; i--) {
			for (final IProgramVar var : transFormula.get(i).getOutVars().keySet()) {

				final TermVariable outVar = transFormula.get(i).getOutVars().get(var);
				TermVariable newOutVar;
				if (tfb.containsInVar(var)) {
					newOutVar = tfb.getInVar(var);
				} else {
					newOutVar = mgdScript.constructFreshTermVariable(var.getGloballyUniqueId(),
							var.getTermVariable().getSort());
				}
				substitutionMapping.put(outVar, newOutVar);
				// add to outvars if var is not outvar
				if (!tfb.containsOutVar(var)) {
					tfb.addOutVar(var, newOutVar);
				}
				final TermVariable inVar = transFormula.get(i).getInVars().get(var);
				if (inVar == null) {
					// case: var is assigned without reading or havoced
					if (tfb.getOutVar(var) != newOutVar) {
						// add to auxVars if not already outVar
						auxVars.add(newOutVar);
					}
					tfb.removeInVar(var);
				} else if (inVar == outVar) {
					// case: var is not modified
					tfb.addInVar(var, newOutVar);
				} else {
					// case: var is read and written
					final TermVariable newInVar = mgdScript.constructFreshTermVariable(var.getGloballyUniqueId(),
							var.getTermVariable().getSort());
					substitutionMapping.put(inVar, newInVar);
					tfb.addInVar(var, newInVar);
					if (tfb.getOutVar(var) != newOutVar) {
						// add to auxVars if not already outVar
						auxVars.add(newOutVar);
					}
				}
			}
			for (final TermVariable oldAuxVar : transFormula.get(i).getAuxVars()) {
				final TermVariable newAuxVar = mgdScript.constructFreshCopy(oldAuxVar);
				substitutionMapping.put(oldAuxVar, newAuxVar);
				auxVars.add(newAuxVar);
			}
			tfb.addBranchEncoders(transFormula.get(i).getBranchEncoders());

			for (final IProgramVar var : transFormula.get(i).getInVars().keySet()) {
				if (transFormula.get(i).getOutVars().containsKey(var)) {
					// nothing do to, this var was already considered above
				} else {
					// case var occurs only as inVar: var is not modfied.
					final TermVariable inVar = transFormula.get(i).getInVars().get(var);
					TermVariable newInVar;
					if (tfb.containsInVar(var)) {
						newInVar = tfb.getInVar(var);
					} else {
						newInVar = mgdScript.constructFreshTermVariable(var.getGloballyUniqueId(),
								var.getTermVariable().getSort());
						tfb.addInVar(var, newInVar);
					}
					substitutionMapping.put(inVar, newInVar);
				}
			}
			final Term originalFormula = transFormula.get(i).getFormula();
			final Term updatedFormula =
					new SubstitutionWithLocalSimplification(mgdScript, substitutionMapping).transform(originalFormula);
			nonTheoryConsts.addAll(transFormula.get(i).getNonTheoryConsts());
			formula = Util.and(script, formula, updatedFormula);
		}

		formula = new FormulaUnLet().unlet(formula);
		if (simplify) {
			try {
				final Term simplified = SmtUtils.simplify(mgdScript, formula, services, simplificationTechnique);
				formula = simplified;
			} catch (final ToolchainCanceledException tce) {
				final String taskDescription =
						"doing sequential composition of " + transFormula.size() + " TransFormulas";
				tce.addRunningTaskInfo(new RunningTaskInfo(PartialQuantifierElimination.class, taskDescription));
				throw tce;
			}
		}

		if (extPqe) {
			final Term eliminated = PartialQuantifierElimination.elim(mgdScript, QuantifiedFormula.EXISTS, auxVars,
					formula, services, logger, simplificationTechnique, xnfConversionTechnique);
			logger.debug(new DebugMessage("DAG size before PQE {0}, DAG size after PQE {1}",
					new DagSizePrinter(formula), new DagSizePrinter(eliminated)));
			formula = eliminated;
		} else {
			final XnfDer xnfDer = new XnfDer(mgdScript, services);
			formula = Util.and(script,
					xnfDer.tryToEliminate(QuantifiedFormula.EXISTS, SmtUtils.getConjuncts(formula), auxVars));
		}
		if (simplify) {
			formula = SmtUtils.simplify(mgdScript, formula, services, simplificationTechnique);
		} else {
			final LBool isSat = Util.checkSat(script, formula);
			if (isSat == LBool.UNSAT) {
				if (logger.isDebugEnabled()) {
					logger.debug("CodeBlock already infeasible");
				}
				formula = script.term("false");
			}
		}

		Infeasibility infeasibility;
		if (formula == script.term("false")) {
			infeasibility = Infeasibility.INFEASIBLE;
		} else {
			infeasibility = Infeasibility.UNPROVEABLE;
		}

		if (tranformToCNF) {
			final Term cnf = SmtUtils.toCnf(services, mgdScript, formula, xnfConversionTechnique);
			formula = cnf;
		}

		TransFormulaUtils.addConstantsIfInFormula(tfb, formula, nonTheoryConsts);
		tfb.setFormula(formula);
		tfb.setInfeasibility(infeasibility);
		for (final TermVariable auxVar : auxVars) {
			tfb.addAuxVar(auxVar);
		}
		return tfb.finishConstruction(mgdScript);
	}

	/**
	 * The parallel composition of transFormulas is the disjunction of the underlying relations. If we check
	 * satisfiability of a path which contains this transFormula we want know one disjuncts that is satisfiable. We use
	 * additional boolean variables called branchIndicators to encode this disjunction. Example: Assume we have two
	 * TransFormulas tf1 and tf2. Instead of the Formula tf1 || tf2 we use the following formula. (BI1 -> tf1) && (BI2
	 * -> tf2) && (BI1 || BI2) The following holds
	 * <ul>
	 * <li>tf1 || tf2 is satisfiable iff (BI1 -> tf1) && (BI2 -> tf2) && (BI1 || BI2) is satisfiable.
	 * <li>in a satisfying assignment BIi can only be true if tfi is true for i \in {1,2}
	 *
	 * @param logger
	 * @param services
	 * @param xnfConversionTechnique
	 */
	public static UnmodifiableTransFormula parallelComposition(final ILogger logger,
			final IUltimateServiceProvider services, final int serialNumber, final ManagedScript mgdScript,
			final TermVariable[] branchIndicators, final boolean tranformToCNF,
			final XnfConversionTechnique xnfConversionTechnique, final UnmodifiableTransFormula... transFormulas) {
		logger.debug("parallel composition");
		boolean useBranchEncoders;
		if (branchIndicators == null) {
			useBranchEncoders = false;
		} else {
			useBranchEncoders = true;
			if (branchIndicators.length != transFormulas.length) {
				throw new IllegalArgumentException();
			}

		}

		final Term[] renamedFormulas = new Term[transFormulas.length];
		final TransFormulaBuilder tfb;
		if (useBranchEncoders) {
			tfb = new TransFormulaBuilder(null, null, false, null, false, Arrays.asList(branchIndicators), false);
		} else {
			tfb = new TransFormulaBuilder(null, null, false, null, true, null, false);
		}
		final Set<IProgramConst> nonTheoryConsts = new HashSet<>();

		final Map<IProgramVar, Sort> assignedInSomeBranch = new HashMap<>();
		for (final UnmodifiableTransFormula tf : transFormulas) {
			for (final IProgramVar bv : tf.getInVars().keySet()) {
				if (!tfb.containsInVar(bv)) {
					final Sort sort = tf.getInVars().get(bv).getSort();
					final String inVarName = bv.getGloballyUniqueId() + "_In" + serialNumber;
					tfb.addInVar(bv, mgdScript.variable(inVarName, sort));
				}
			}
			for (final IProgramVar bv : tf.getOutVars().keySet()) {

				// vars which are assigned in some but not all branches must
				// also occur as inVar
				// We can omit this step in the special case where the
				// variable is assigned in all branches.
				if (!tfb.containsInVar(bv) && !assignedInAll(bv, transFormulas)) {
					final Sort sort = tf.getOutVars().get(bv).getSort();
					final String inVarName = bv.getGloballyUniqueId() + "_In" + serialNumber;
					tfb.addInVar(bv, mgdScript.variable(inVarName, sort));
				}

				final TermVariable outVar = tf.getOutVars().get(bv);
				final TermVariable inVar = tf.getInVars().get(bv);
				final boolean isAssignedVar = outVar != inVar;
				if (isAssignedVar) {
					final Sort sort = tf.getOutVars().get(bv).getSort();
					assignedInSomeBranch.put(bv, sort);
				}
				// auxilliary step, add all invars. Some will be overwritten by
				// outvars
				tfb.addOutVar(bv, tfb.getInVar(bv));
			}
			nonTheoryConsts.addAll(tf.getNonTheoryConsts());
		}

		// overwrite (see comment above) the outvars if the outvar does not
		// coincide with the invar in some of the transFormulas
		for (final IProgramVar bv : assignedInSomeBranch.keySet()) {
			final Sort sort = assignedInSomeBranch.get(bv);
			final String outVarName = bv.getGloballyUniqueId() + "_Out" + serialNumber;
			tfb.addOutVar(bv, mgdScript.variable(outVarName, sort));
		}

		final Set<TermVariable> auxVars = new HashSet<>();
		for (int i = 0; i < transFormulas.length; i++) {
			tfb.addBranchEncoders(transFormulas[i].getBranchEncoders());
			final Map<Term, Term> substitutionMapping = new HashMap<>();
			for (final IProgramVar bv : transFormulas[i].getInVars().keySet()) {
				final TermVariable inVar = transFormulas[i].getInVars().get(bv);
				substitutionMapping.put(inVar, tfb.getInVar(bv));
			}
			for (final IProgramVar bv : transFormulas[i].getOutVars().keySet()) {
				final TermVariable outVar = transFormulas[i].getOutVars().get(bv);
				final TermVariable inVar = transFormulas[i].getInVars().get(bv);

				final boolean isAssignedVar = inVar != outVar;
				if (isAssignedVar) {
					substitutionMapping.put(outVar, tfb.getOutVar(bv));
				} else {
					assert substitutionMapping.containsKey(outVar);
					assert substitutionMapping.containsValue(tfb.getInVar(bv));
				}
			}
			for (final TermVariable oldAuxVar : transFormulas[i].getAuxVars()) {
				final TermVariable newAuxVar = mgdScript.constructFreshCopy(oldAuxVar);
				substitutionMapping.put(oldAuxVar, newAuxVar);
				auxVars.add(newAuxVar);
			}
			final Term originalFormula = transFormulas[i].getFormula();
			renamedFormulas[i] =
					new SubstitutionWithLocalSimplification(mgdScript, substitutionMapping).transform(originalFormula);

			for (final IProgramVar bv : assignedInSomeBranch.keySet()) {
				final TermVariable inVar = transFormulas[i].getInVars().get(bv);
				final TermVariable outVar = transFormulas[i].getOutVars().get(bv);
				if (inVar == null && outVar == null) {
					// bv does not occur in transFormula
					assert tfb.getInVar(bv) != null;
					assert tfb.getOutVar(bv) != null;
					final Term equality = mgdScript.getScript().term("=", tfb.getInVar(bv), tfb.getOutVar(bv));
					renamedFormulas[i] = Util.and(mgdScript.getScript(), renamedFormulas[i], equality);
				} else if (inVar == outVar) {
					// bv is not modified in transFormula
					assert tfb.getInVar(bv) != null;
					assert tfb.getOutVar(bv) != null;
					final Term equality = mgdScript.getScript().term("=", tfb.getInVar(bv), tfb.getOutVar(bv));
					renamedFormulas[i] = Util.and(mgdScript.getScript(), renamedFormulas[i], equality);
				}
			}

			if (useBranchEncoders) {
				renamedFormulas[i] = Util.implies(mgdScript.getScript(), branchIndicators[i], renamedFormulas[i]);
			}
		}

		Term resultFormula;
		if (useBranchEncoders) {
			resultFormula = Util.and(mgdScript.getScript(), renamedFormulas);
			final Term atLeastOneBranchTaken = Util.or(mgdScript.getScript(), branchIndicators);
			resultFormula = Util.and(mgdScript.getScript(), resultFormula, atLeastOneBranchTaken);
		} else {
			resultFormula = Util.or(mgdScript.getScript(), renamedFormulas);
		}
		final LBool termSat = Util.checkSat(mgdScript.getScript(), resultFormula);
		Infeasibility inFeasibility;
		if (termSat == LBool.UNSAT) {
			inFeasibility = Infeasibility.INFEASIBLE;
		} else {
			inFeasibility = Infeasibility.UNPROVEABLE;
		}
		if (tranformToCNF) {
			resultFormula = SmtUtils.toCnf(services, mgdScript, resultFormula, xnfConversionTechnique);
		}

		TransFormulaUtils.addConstantsIfInFormula(tfb, resultFormula, nonTheoryConsts);
		tfb.setFormula(resultFormula);
		tfb.setInfeasibility(inFeasibility);
		for (final TermVariable auxVar : auxVars) {
			tfb.addAuxVar(auxVar);
		}
		return tfb.finishConstruction(mgdScript);
	}

	/**
	 * Return true iff bv is assigned in all transFormulas.
	 */
	private static boolean assignedInAll(final IProgramVar bv, final UnmodifiableTransFormula... transFormulas) {
		for (final UnmodifiableTransFormula tf : transFormulas) {
			if (!tf.getAssignedVars().contains(bv)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Returns TransFormula that describes a sequence of code blocks that contains a pending call. Note the the scope of
	 * inVars and outVars is different. Do not compose the result with the default/intraprocedural composition.
	 *
	 * @param beforeCall
	 *            TransFormula that describes transition relation before the call.
	 * @param callTf
	 *            TransFormula that describes parameter assignment of call.
	 * @param oldVarsAssignment
	 *            TransFormula that assigns to oldVars of modifiable globals the value of the global var.
	 * @param globalVarsAssignment
	 *            TODO
	 * @param afterCall
	 *            TransFormula that describes the transition relation after the call.
	 * @param logger
	 * @param services
	 * @param modifiableGlobalsOfEndProcedure
	 *            Set of variables that are modifiable globals in the procedure in which the afterCall TransFormula
	 *            ends.
	 * @param symbolTable
	 */
	public static UnmodifiableTransFormula sequentialCompositionWithPendingCall(final ManagedScript mgdScript,
			final boolean simplify, final boolean extPqe, final boolean transformToCNF,
			final List<UnmodifiableTransFormula> beforeCall, final UnmodifiableTransFormula callTf,
			final UnmodifiableTransFormula oldVarsAssignment, final UnmodifiableTransFormula globalVarsAssignment,
			final UnmodifiableTransFormula afterCall, final ILogger logger, final IUltimateServiceProvider services,
			final Set<IProgramNonOldVar> modifiableGlobalsOfEndProcedure,
			final XnfConversionTechnique xnfConversionTechnique, final SimplificationTechnique simplificationTechnique,
			final IIcfgSymbolTable symbolTable, final String procAtStart, final String procBeforeCall,
			final String procAfterCall, final String procAtEnd, final ModifiableGlobalsTable modifiableGlobalsTable) {
		assert procAtStart != null : "proc at start must not be null";
		if (!procAtStart.equals(procBeforeCall)) {
			throw new UnsupportedOperationException("proc change before call");
		}

		logger.debug(
				"sequential composition (pending call) with" + (simplify ? "" : "out") + " formula simplification");
		final UnmodifiableTransFormula callAndBeforeTF;
		{
			final List<UnmodifiableTransFormula> callAndBefore = new ArrayList<>(beforeCall);
			callAndBefore.add(callTf);
			callAndBefore.add(oldVarsAssignment);
			final UnmodifiableTransFormula composition = sequentialComposition(logger, services, mgdScript, simplify,
					extPqe, transformToCNF, xnfConversionTechnique, simplificationTechnique, callAndBefore);

			// remove outVars that are not "interface variables"
			// see isInterfaceVariable()
			final List<IProgramVar> outVarsToRemove = new ArrayList<>();
			for (final IProgramVar bv : composition.getOutVars().keySet()) {
				final boolean isInterfaceVariable =
						isInterfaceVariable(bv, callTf, oldVarsAssignment, procBeforeCall, procAfterCall, true, false);
				if (isInterfaceVariable) {
					// keep variable
				} else {
					outVarsToRemove.add(bv);
				}
			}

			final Map<IProgramVar, TermVariable> varsToHavoc = new HashMap<>();
			// we havoc all oldvars that are modifiable by the caller
			// but not modifiable y the callee
			final Set<IProgramNonOldVar> modifiableByCaller =
					modifiableGlobalsTable.getModifiedBoogieVars(procBeforeCall);
			for (final IProgramNonOldVar modifiable : modifiableByCaller) {
				final IProgramOldVar oldVar = modifiable.getOldVar();
				final boolean modifiableByCallee = oldVarsAssignment.getAssignedVars().contains(oldVar);
				if (!modifiableByCallee) {
					varsToHavoc.put(oldVar, mgdScript.constructFreshCopy(oldVar.getTermVariable()));
				}
			}

			// we havoc all local variables of the caller (unless they are inparams of callee)
			final Set<ILocalProgramVar> locals = symbolTable.getLocals(procBeforeCall);
			for (final ILocalProgramVar localVar : locals) {
				final boolean isInParamOfCallee = callTf.getAssignedVars().contains(localVar);
				if (!isInParamOfCallee) {
					varsToHavoc.put(localVar, mgdScript.constructFreshCopy(localVar.getTermVariable()));
				}
			}

			callAndBeforeTF = TransFormulaBuilder.constructCopy(mgdScript, composition, Collections.emptySet(),
					outVarsToRemove, varsToHavoc);

		}

		final UnmodifiableTransFormula globalVarAssignAndAfterTF;
		{
			final List<UnmodifiableTransFormula> oldAssignAndAfterList = new ArrayList<>(Arrays.asList(afterCall));
			oldAssignAndAfterList.add(0, globalVarsAssignment);
			final UnmodifiableTransFormula composition = sequentialComposition(logger, services, mgdScript, simplify,
					extPqe, transformToCNF, xnfConversionTechnique, simplificationTechnique, oldAssignAndAfterList);

			// remove inVars that are not "interface variables"
			// see isInterfaceVariable()
			final List<IProgramVar> inVarsToRemove = new ArrayList<>();
			for (final IProgramVar bv : composition.getInVars().keySet()) {
				final boolean isInterfaceVariable =
						isInterfaceVariable(bv, callTf, oldVarsAssignment, procBeforeCall, procAfterCall, false, true);
				if (isInterfaceVariable) {
					// keep variable
				} else {
					inVarsToRemove.add(bv);
				}
			}

			globalVarAssignAndAfterTF = TransFormulaBuilder.constructCopy(mgdScript, composition, inVarsToRemove,
					Collections.emptySet(), Collections.emptyMap());
		}

		final UnmodifiableTransFormula preliminaryResult = sequentialComposition(logger, services, mgdScript, simplify,
				extPqe, transformToCNF, xnfConversionTechnique, simplificationTechnique,
				Arrays.asList(new UnmodifiableTransFormula[] { callAndBeforeTF, globalVarAssignAndAfterTF }));

		// If the procedure does not change after the call, we already have
		// the result. Otherwise we have to remove the inparams since they
		// do not have the scope of the procedure at the end
		// Note that in case of recursive procedure calls we do not have to
		// remove anything. If the after-call-formula was build correctly
		// it ensures that the inparam instances are not outvars after the
		// composition above.
		final UnmodifiableTransFormula result;
		if (procAfterCall.equals(procAtEnd)) {
			result = preliminaryResult;
		} else {
			final List<IProgramVar> outVarsToRemove = new ArrayList<>();
			// remove inparams of callee that are still in the outvars
			for (final IProgramVar pv : preliminaryResult.getOutVars().keySet()) {
				if (callTf.getAssignedVars().contains(pv)) {
					// pv is inparam, we have to remove it
					outVarsToRemove.add(pv);
				}
			}
			if (outVarsToRemove.isEmpty()) {
				// nothing to remove
				result = preliminaryResult;
			} else {
				result = TransFormulaBuilder.constructCopy(mgdScript, preliminaryResult, Collections.emptySet(),
						outVarsToRemove, Collections.emptyMap());
			}
		}

		assert !result.getBranchEncoders().isEmpty()
				|| predicateBasedResultCheck(services, mgdScript, xnfConversionTechnique, simplificationTechnique,
						beforeCall, callTf, oldVarsAssignment, globalVarsAssignment, afterCall, result, symbolTable,
						modifiableGlobalsOfEndProcedure) : "sequentialCompositionWithPendingCall - incorrect result";
		return result;
	}

	/**
	 * Check if {@link IProgramVar} is variable at the interface between caller and callee. This is used for
	 * interprocedural sequential compositions with pending calls. We say that a variable is an interface variable if it
	 * is either - an inparam of the callee (local variable) - an oldvar that is in the callee's set of modifiable
	 * variables - an non-old global variable that is not in the callee's set of modifiable variables.
	 */
	private static boolean isInterfaceVariable(final IProgramVar bv, final UnmodifiableTransFormula callTf,
			final UnmodifiableTransFormula oldVarsAssignment, final String procBeforeCall, final String procAfterCall,
			final boolean tolerateLocalVarsOfCaller, final boolean tolerateLocalVarsOfCallee) {
		final boolean isInterfaceVariable;
		if (bv.isGlobal()) {
			if (bv.isOldvar()) {
				if (oldVarsAssignment.getOutVars().containsKey(bv)) {
					// is a modifiable oldvar
					isInterfaceVariable = true;
				} else {
					// has to be renamed to non-old var
					throw new AssertionError("oldvars not yet implemented");
				}
			} else {
				if (oldVarsAssignment.getInVars().containsKey(bv)) {
					isInterfaceVariable = false;
				} else {
					// global and not modified by procedure
					isInterfaceVariable = true;
				}
			}
		} else {
			if (bv.getProcedure().equals(procAfterCall)) {
				if (callTf.getAssignedVars().contains(bv)) {
					// is an inparam
					isInterfaceVariable = true;
				} else {
					if (tolerateLocalVarsOfCallee) {
						// no AssertionError
					} else {
						if (procBeforeCall.equals(procAfterCall) && tolerateLocalVarsOfCaller) {
							// no AssertionError
						} else {
							throw new AssertionError("local var of callee is no inparam " + bv);
						}
					}
					isInterfaceVariable = false;
				}
			} else if (bv.getProcedure().equals(procBeforeCall)) {
				if (!tolerateLocalVarsOfCaller) {
					throw new AssertionError("local var of caller " + bv);
				}
				isInterfaceVariable = false;
			} else {
				throw new AssertionError("local var neither from caller nor callee " + bv);
			}
		}
		return isInterfaceVariable;
	}

	private static boolean predicateBasedResultCheck(final IUltimateServiceProvider services,
			final ManagedScript mgdScript, final XnfConversionTechnique xnfConversionTechnique,
			final SimplificationTechnique simplificationTechnique, final List<UnmodifiableTransFormula> beforeCall,
			final UnmodifiableTransFormula callTf, final UnmodifiableTransFormula oldVarsAssignment,
			final UnmodifiableTransFormula globalVarsAssignment, final UnmodifiableTransFormula afterCallTf,
			final UnmodifiableTransFormula result, final IIcfgSymbolTable symbolTable,
			final Set<IProgramNonOldVar> modifiableGlobalsOfEndProcedure) {
		assert result.getBranchEncoders().isEmpty() : "result check not applicable with branch encoders";
		final PredicateTransformer pt =
				new PredicateTransformer(services, mgdScript, simplificationTechnique, xnfConversionTechnique);
		final BasicPredicateFactory bpf = new BasicPredicateFactory(services, mgdScript, symbolTable,
				simplificationTechnique, xnfConversionTechnique);
		final IPredicate truePredicate = bpf.newPredicate(mgdScript.getScript().term("true"));
		Term resultComposition = pt.strongestPostcondition(truePredicate, result);
		resultComposition =
				new QuantifierPusher(mgdScript, services, true, PqeTechniques.ALL_LOCAL).transform(resultComposition);
		final IPredicate resultCompositionPredicate = bpf.newPredicate(resultComposition);
		IPredicate beforeCallPredicate = truePredicate;
		for (final UnmodifiableTransFormula tf : beforeCall) {
			final Term tmp = pt.strongestPostcondition(beforeCallPredicate, tf);
			beforeCallPredicate = bpf.newPredicate(tmp);
		}
		final Term afterCallTerm = pt.strongestPostconditionCall(beforeCallPredicate, callTf, globalVarsAssignment,
				oldVarsAssignment, modifiableGlobalsOfEndProcedure);
		final IPredicate afterCallPredicate = bpf.newPredicate(afterCallTerm);
		Term endTerm = pt.strongestPostcondition(afterCallPredicate, afterCallTf);
		endTerm = new QuantifierPusher(mgdScript, services, true, PqeTechniques.ALL_LOCAL).transform(endTerm);
		final IPredicate endPredicate = bpf.newPredicate(endTerm);
		final MonolithicImplicationChecker mic = new MonolithicImplicationChecker(services, mgdScript);
		final Validity check1 = mic.checkImplication(endPredicate, false, resultCompositionPredicate, false);
		final Validity check2 = mic.checkImplication(resultCompositionPredicate, false, endPredicate, false);
		assert check1 != Validity.INVALID
				&& check2 != Validity.INVALID : "sequentialCompositionWithPendingCall - incorrect result";
		return check1 != Validity.INVALID && check2 != Validity.INVALID;
	}

	/**
	 * Returns a TransFormula that can be seen as procedure summary.
	 *
	 * @param callTf
	 *            TransFormula that describes parameter assignment of call.
	 * @param oldVarsAssignment
	 *            TransFormula that assigns to oldVars of modifiable globals the value of the global var.
	 * @param procedureTf
	 *            TransFormula that describes the procedure.
	 * @param returnTf
	 *            TransFormula that assigns the result of the procedure call.
	 * @param logger
	 * @param services
	 * @param symbolTable
	 * @param modifiableGlobalsOfCallee
	 */
	public static UnmodifiableTransFormula sequentialCompositionWithCallAndReturn(final ManagedScript mgdScript,
			final boolean simplify, final boolean extPqe, final boolean transformToCNF,
			final UnmodifiableTransFormula callTf, final UnmodifiableTransFormula oldVarsAssignment,
			final UnmodifiableTransFormula globalVarsAssignment, final UnmodifiableTransFormula procedureTf,
			final UnmodifiableTransFormula returnTf, final ILogger logger, final IUltimateServiceProvider services,
			final XnfConversionTechnique xnfConversionTechnique, final SimplificationTechnique simplificationTechnique,
			final IIcfgSymbolTable symbolTable, final Set<IProgramNonOldVar> modifiableGlobalsOfCallee) {
		logger.debug("sequential composition (call/return) with" + (simplify ? "" : "out") + " formula simplification");
		final UnmodifiableTransFormula composition =
				sequentialComposition(logger, services, mgdScript, simplify, extPqe, transformToCNF,
						xnfConversionTechnique, simplificationTechnique, Arrays.asList(new UnmodifiableTransFormula[] {
								callTf, oldVarsAssignment, globalVarsAssignment, procedureTf, returnTf }));

		// remove invars except for
		// local vars that occur in arguments of the call
		// oldvars that are modifiable by the callee unless they occur in
		// arguments of the call
		final List<IProgramVar> inVarsToRemove = new ArrayList<>();
		for (final IProgramVar bv : composition.getInVars().keySet()) {
			if (bv.isGlobal()) {
				if (bv.isOldvar()) {
					final boolean isModifiableByCallee = oldVarsAssignment.getAssignedVars().contains(bv);
					if (isModifiableByCallee) {
						final boolean occursInArguments = callTf.getInVars().containsKey(bv);
						if (occursInArguments) {
							// keep, invar instance refers to start of caller
						} else {
							// remove, invar instance refers to start of callee
							inVarsToRemove.add(bv);
						}
					} else {
						// keep, invar instance refers to start of caller
					}
				} else {
					// keep, invar instance's scope is caller or before
					// (because for the modifiables the oldvarsassignment
					// introduced a new instance
				}
			} else {
				final boolean occursInArguments = callTf.getInVars().containsKey(bv);
				if (occursInArguments) {
					// keep, invar instance's scope is caller
				} else {
					// remove, this is a local variables of callee
					inVarsToRemove.add(bv);
				}
			}
		}

		// remove outvars except for
		// local vars that are outvars of return
		// oldvars that are modifiable by the callee
		// note that it is not possible that return assigns an oldvar
		final List<IProgramVar> outVarsToRemove = new ArrayList<>();
		for (final IProgramVar bv : composition.getOutVars().keySet()) {
			if (bv.isGlobal()) {
				if (bv.isOldvar()) {
					final boolean isModifiableByCallee = oldVarsAssignment.getAssignedVars().contains(bv);
					if (isModifiableByCallee) {
						// remove, outvar instance refers to instance at beginning of calleee
						outVarsToRemove.add(bv);
					} else {
						// keep, outvar instance refers to start of caller
					}
				} else {
					// keep
				}
			} else {
				if (!returnTf.getOutVars().containsKey(bv)) {
					// bv is local var of callee
					outVarsToRemove.add(bv);
				}
			}
		}
		// our composition might have introduced arguments of the caller as
		// inVars, they should not count as havoced, we have to add them to
		// outvars
		final Map<IProgramVar, TermVariable> additionalOutVars = new HashMap<>();
		for (final Entry<IProgramVar, TermVariable> entry : callTf.getInVars().entrySet()) {
			// we add the invar as outvar if there is not yet an outvar,
			// or we remove the outvar (e.g., in recursive programs in can
			// happen that the outvar instance does not coincide with
			// the invar but the outvar instance belongs to the caller
			if (!composition.getOutVars().containsKey(entry.getKey()) || outVarsToRemove.contains(entry.getKey())) {
				final TermVariable inVar = composition.getInVars().get(entry.getKey());
				if (inVar == null) {
					// do nothing, not in formula any more
				} else {
					additionalOutVars.put(entry.getKey(), inVar);
				}
			}
		}
		final UnmodifiableTransFormula result = TransFormulaBuilder.constructCopy(mgdScript, composition,
				inVarsToRemove, outVarsToRemove, additionalOutVars);

		assert SmtUtils.neitherKeyNorValueIsNull(
				result.getOutVars()) : "sequentialCompositionWithCallAndReturn introduced null entries";
		assert isIntraprocedural(result);
		assert !result.getBranchEncoders().isEmpty() || predicateBasedResultCheck(services, logger, mgdScript,
				xnfConversionTechnique, simplificationTechnique, callTf, oldVarsAssignment, globalVarsAssignment,
				procedureTf, returnTf, result, symbolTable,
				modifiableGlobalsOfCallee) : "sequentialCompositionWithCallAndReturn - incorrect result";
		return result;
	}

	private static boolean predicateBasedResultCheck(final IUltimateServiceProvider services, final ILogger logger,
			final ManagedScript mgdScript, final XnfConversionTechnique xnfConversionTechnique,
			final SimplificationTechnique simplificationTechnique, final UnmodifiableTransFormula callTf,
			final UnmodifiableTransFormula oldVarsAssignment, final UnmodifiableTransFormula globalVarsAssignment,
			final UnmodifiableTransFormula procedureTf, final UnmodifiableTransFormula returnTf,
			final UnmodifiableTransFormula result, final IIcfgSymbolTable symbolTable,
			final Set<IProgramNonOldVar> modifiableGlobals) {
		assert result.getBranchEncoders().isEmpty() : "result check not applicable with branch encoders";
		final PredicateTransformer pt =
				new PredicateTransformer(services, mgdScript, simplificationTechnique, xnfConversionTechnique);
		final BasicPredicateFactory bpf = new BasicPredicateFactory(services, mgdScript, symbolTable,
				simplificationTechnique, xnfConversionTechnique);
		final IPredicate truePredicate = bpf.newPredicate(mgdScript.getScript().term("true"));
		Term resultComposition = pt.strongestPostcondition(truePredicate, result);
		resultComposition =
				new QuantifierPusher(mgdScript, services, true, PqeTechniques.ALL_LOCAL).transform(resultComposition);
		final IPredicate resultCompositionPredicate = bpf.newPredicate(resultComposition);
		final Term afterCallTerm = pt.strongestPostconditionCall(truePredicate, callTf, globalVarsAssignment,
				oldVarsAssignment, modifiableGlobals);
		final IPredicate afterCallPredicate = bpf.newPredicate(afterCallTerm);
		final Term beforeReturnTerm = pt.strongestPostcondition(afterCallPredicate, procedureTf);
		final IPredicate beforeReturnPredicate = bpf.newPredicate(beforeReturnTerm);
		Term afterReturnTerm = pt.strongestPostconditionReturn(beforeReturnPredicate, truePredicate, returnTf, callTf,
				oldVarsAssignment, modifiableGlobals);
		afterReturnTerm =
				new QuantifierPusher(mgdScript, services, true, PqeTechniques.ALL_LOCAL).transform(afterReturnTerm);
		final IPredicate afterReturnPredicate = bpf.newPredicate(afterReturnTerm);
		final MonolithicImplicationChecker mic = new MonolithicImplicationChecker(services, mgdScript);
		final Validity check1 = mic.checkImplication(afterReturnPredicate, false, resultCompositionPredicate, false);
		final Validity check2 = mic.checkImplication(resultCompositionPredicate, false, afterReturnPredicate, false);
		assert check1 != Validity.INVALID
				&& check2 != Validity.INVALID : "sequentialCompositionWithCallAndReturn - incorrect result";
		if (check1 == Validity.UNKNOWN || check2 == Validity.UNKNOWN) {
			logger.warn("predicate-based correctness check returned UNKNOWN, "
					+ "hence correctness of interprocedural sequential composition was not checked.");
		}
		return check1 != Validity.INVALID && check2 != Validity.INVALID;
	}

	/**
	 * Returns true iff all local variables in tf belong to a single procedure.
	 */
	static boolean isIntraprocedural(final UnmodifiableTransFormula tf) {
		final Set<String> procedures = new HashSet<>();
		for (final IProgramVar bv : tf.getInVars().keySet()) {
			if (!bv.isGlobal()) {
				procedures.add(bv.getProcedure());
			}
		}
		for (final IProgramVar bv : tf.getOutVars().keySet()) {
			if (!bv.isGlobal()) {
				procedures.add(bv.getProcedure());
			}
		}
		return procedures.size() <= 1;
	}

	private static UnmodifiableTransFormula computeGuard(final UnmodifiableTransFormula tf,
			final ManagedScript script) {
		final Set<TermVariable> auxVars = new HashSet<>(tf.getAuxVars());
		for (final IProgramVar bv : tf.getAssignedVars()) {
			final TermVariable outVar = tf.getOutVars().get(bv);
			if (Arrays.asList(tf.getFormula().getFreeVars()).contains(outVar)) {
				auxVars.add(outVar);
			}
		}
		if (!tf.getBranchEncoders().isEmpty()) {
			throw new AssertionError("I think this does not make sense with branch enconders");
		}
		// yes! outVars of result are indeed the inVars of input

		final TransFormulaBuilder tfb =
				new TransFormulaBuilder(tf.getInVars(), tf.getInVars(), tf.getNonTheoryConsts().isEmpty(),
						tf.getNonTheoryConsts().isEmpty() ? null : tf.getNonTheoryConsts(), true, null, false);
		tfb.setFormula(tf.getFormula());
		tfb.setInfeasibility(tf.isInfeasible());
		tfb.addAuxVarsButRenameToFreshCopies(auxVars, script);
		return tfb.finishConstruction(script);
	}

	private static UnmodifiableTransFormula negate(final UnmodifiableTransFormula tf, final ManagedScript maScript,
			final IUltimateServiceProvider services, final ILogger logger,
			final XnfConversionTechnique xnfConversionTechnique,
			final SimplificationTechnique simplificationTechnique) {
		if (!tf.getBranchEncoders().isEmpty()) {
			throw new AssertionError("I think this does not make sense with branch enconders");
		}
		Term formula = tf.getFormula();
		formula = PartialQuantifierElimination.quantifier(services, logger, maScript, simplificationTechnique,
				xnfConversionTechnique, QuantifiedFormula.EXISTS, tf.getAuxVars(), formula, new Term[0]);
		final Set<TermVariable> freeVars = new HashSet<>(Arrays.asList(formula.getFreeVars()));
		freeVars.retainAll(tf.getAuxVars());
		if (!freeVars.isEmpty()) {
			throw new UnsupportedOperationException("cannot negate if there are auxVars");
		}
		formula = SmtUtils.not(maScript.getScript(), formula);

		final TransFormulaBuilder tfb = new TransFormulaBuilder(tf.getInVars(), tf.getOutVars(),
				tf.getNonTheoryConsts().isEmpty(), tf.getNonTheoryConsts().isEmpty() ? null : tf.getNonTheoryConsts(),
				false, tf.getBranchEncoders(), true);
		tfb.setFormula(formula);
		tfb.setInfeasibility(Infeasibility.NOT_DETERMINED);
		return tfb.finishConstruction(maScript);
	}

	public static UnmodifiableTransFormula computeMarkhorTransFormula(final UnmodifiableTransFormula tf,
			final ManagedScript maScript, final IUltimateServiceProvider services, final ILogger logger,
			final XnfConversionTechnique xnfConversionTechnique,
			final SimplificationTechnique simplificationTechnique) {
		final UnmodifiableTransFormula guard = computeGuard(tf, maScript);
		final UnmodifiableTransFormula negGuard =
				negate(guard, maScript, services, logger, xnfConversionTechnique, simplificationTechnique);
		final UnmodifiableTransFormula markhor = parallelComposition(logger, services, tf.hashCode(), maScript, null,
				false, xnfConversionTechnique, tf, negGuard);
		return markhor;
	}

	/**
	 * Add all elements of progConsts to tfb that occur in formula, ignore the those that do not occur in the formula.
	 */
	public static <T extends IProgramConst> void addConstantsIfInFormula(final TransFormulaBuilder tfb,
			final Term formula, final Set<T> progConsts) {
		final Set<ApplicationTerm> constsInFormula = new ConstantFinder().findConstants(formula, false);
		for (final IProgramConst progConst : progConsts) {
			if (constsInFormula.contains(progConst.getDefaultConstant())) {
				tfb.addProgramConst(progConst);
			}
		}
	}

}
