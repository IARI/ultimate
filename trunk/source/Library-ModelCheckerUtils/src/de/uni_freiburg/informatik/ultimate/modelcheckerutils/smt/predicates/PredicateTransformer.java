/*
 * Copyright (C) 2014-2015 Betim Musa (musab@informatik.uni-freiburg.de)
 * Copyright (C) 2014-2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2014-2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
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
package de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.logic.Util;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.ModelCheckerUtils;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.TransFormula;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramNonOldVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.SimplificationTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.XnfConversionTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SubstitutionWithLocalSimplification;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.linearTerms.QuantifierPusher;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.linearTerms.QuantifierPusher.PqeTechniques;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.CallReturnPyramideInstanceProvider.Instance;
import de.uni_freiburg.informatik.ultimate.util.ConstructionCache;
import de.uni_freiburg.informatik.ultimate.util.ConstructionCache.IValueConstruction;

/**
 * @author musab@informatik.uni-freiburg.de, heizmann@informatik.uni-freiburg.de
 *
 */
public class PredicateTransformer {
	private final Script mScript;
	private final ManagedScript mMgdScript;
	private final IUltimateServiceProvider mServices;
	private final ILogger mLogger;
	private final SimplificationTechnique mSimplificationTechnique;
	private final XnfConversionTechnique mXnfConversionTechnique;

	public PredicateTransformer(final IUltimateServiceProvider services, final ManagedScript mgdScript,
			final SimplificationTechnique simplificationTechnique,
			final XnfConversionTechnique xnfConversionTechnique) {
		mServices = services;
		mLogger = mServices.getLoggingService().getLogger(ModelCheckerUtils.PLUGIN_ID);
		mSimplificationTechnique = simplificationTechnique;
		mXnfConversionTechnique = xnfConversionTechnique;
		mScript = mgdScript.getScript();
		mMgdScript = mgdScript;
	}

	private static TermVariable constructFreshTermVariable(final ManagedScript freshVarConstructor,
			final IProgramVar pv) {
		return freshVarConstructor.constructFreshTermVariable(pv.getGloballyUniqueId(), pv.getTermVariable().getSort());
	}

	/**
	 * Computes the strongest postcondition of the given predicate p and the TransFormula tf. - invars of the given
	 * transformula, which don't occur in the outvars or are mapped to different values are renamed to fresh variables.
	 * The corresponding term variables in the given predicate p, are renamed to the same fresh variables. - outvars are
	 * renamed to corresponding term variables. If an outvar doesn't occur in the invars, its occurrence in the given
	 * predicate is substituted by a fresh variable. All fresh variables are existentially quantified.
	 */
	public Term strongestPostcondition(final IPredicate p, final UnmodifiableTransFormula tf) {
		if (SmtUtils.isFalse(p.getFormula())) {
			return p.getFormula();
		}
		final Set<TermVariable> varsToQuantify = new HashSet<>();
		final IValueConstruction<IProgramVar, TermVariable> substituentConstruction =
				new IValueConstruction<IProgramVar, TermVariable>() {

					@Override
					public TermVariable constructValue(final IProgramVar pv) {
						final TermVariable result = constructFreshTermVariable(mMgdScript, pv);
						varsToQuantify.add(result);
						return result;
					}

				};
		final ConstructionCache<IProgramVar, TermVariable> termVariablesForPredecessor =
				new ConstructionCache<>(substituentConstruction);

		final Map<Term, Term> substitutionForTransFormula = new HashMap<>();
		final Map<Term, Term> substitutionForPredecessor = new HashMap<>();
		for (final Entry<IProgramVar, TermVariable> entry : tf.getInVars().entrySet()) {
			final IProgramVar pv = entry.getKey();
			if (entry.getValue() == tf.getOutVars().get(pv)) {
				// special case, variable unchanged will be renamed when
				// considering outVars
			} else {
				final TermVariable substituent = termVariablesForPredecessor.getOrConstruct(pv);
				substitutionForTransFormula.put(entry.getValue(), substituent);
				if (p.getVars().contains(pv)) {
					substitutionForPredecessor.put(pv.getTermVariable(), substituent);
				}
			}
		}

		for (final Entry<IProgramVar, TermVariable> entry : tf.getOutVars().entrySet()) {
			substitutionForTransFormula.put(entry.getValue(), entry.getKey().getTermVariable());
			if (!tf.getInVars().containsKey(entry.getKey()) && p.getVars().contains(entry.getKey())) {
				final TermVariable substituent = termVariablesForPredecessor.getOrConstruct(entry.getKey());
				substitutionForPredecessor.put(entry.getKey().getTermVariable(), substituent);
			}
		}

		final Term renamedTransFormula =
				new SubstitutionWithLocalSimplification(mMgdScript, substitutionForTransFormula)
						.transform(tf.getFormula());
		final Term renamedPredecessor = new SubstitutionWithLocalSimplification(mMgdScript, substitutionForPredecessor)
				.transform(p.getFormula());

		final Term resultBody = Util.and(mScript, renamedTransFormula, renamedPredecessor);

		// Add aux vars to varsToQuantify
		varsToQuantify.addAll(tf.getAuxVars());
		return constructQuantifiedFormula(Script.EXISTS, varsToQuantify, resultBody);
	}

	private Term constructQuantifiedFormula(final int quantifier, final Set<TermVariable> varsToQuantify,
			final Term resultBody) {
		final Term quantified = SmtUtils.quantifier(mScript, quantifier, varsToQuantify, resultBody);
		final Term pushed =
				new QuantifierPusher(mMgdScript, mServices, false, PqeTechniques.ONLY_DER).transform(quantified);
		return pushed;
	}

	public Term strongestPostconditionCall(final IPredicate callPred,
			final UnmodifiableTransFormula localVarAssignments, final UnmodifiableTransFormula globalVarAssignments,
			final UnmodifiableTransFormula oldVarAssignments,
			final Set<IProgramNonOldVar> modifiableGlobalsOfCalledProcedure) {

		final CallReturnPyramideInstanceProvider crpip =
				new CallReturnPyramideInstanceProvider(mMgdScript, Collections.emptySet(),
						localVarAssignments.getAssignedVars(), modifiableGlobalsOfCalledProcedure, Instance.AFTER_CALL);
		final Term callPredTerm = renamePredicateToInstance(callPred, Instance.BEFORE_CALL, crpip);
		final Term localVarAssignmentsTerm =
				renameTransFormulaToInstances(localVarAssignments, Instance.BEFORE_CALL, Instance.AFTER_CALL, crpip);
		final Term oldVarsAssignmentTerm =
				renameTransFormulaToInstances(oldVarAssignments, Instance.BEFORE_CALL, Instance.AFTER_CALL, crpip);
		final Term globalVarsAssignmentTerm =
				renameTransFormulaToInstances(globalVarAssignments, Instance.AFTER_CALL, Instance.AFTER_CALL, crpip);

		final Term result = Util.and(mScript, localVarAssignmentsTerm, oldVarsAssignmentTerm, globalVarsAssignmentTerm,
				callPredTerm);
		return constructQuantifiedFormula(Script.EXISTS, crpip.getFreshTermVariables(), result);
	}

	/**
	 * Special post operator that we use to obtain a modular (interprocedural) sequence of inductive interpolants.
	 */
	public Term modularPostconditionCall(final IPredicate callPred, final UnmodifiableTransFormula globalVarAssignments,
			final Set<IProgramNonOldVar> modifiableGlobalsOfCalledProcedure) {

		final CallReturnPyramideInstanceProvider crpip =
				new CallReturnPyramideInstanceProvider(mMgdScript, Collections.emptySet(), Collections.emptySet(),
						modifiableGlobalsOfCalledProcedure, Instance.AFTER_CALL);
		final Term callPredTerm = renamePredicateToInstance(callPred, Instance.BEFORE_CALL, crpip);
		final Term globalVarsAssignmentTerm =
				renameTransFormulaToInstances(globalVarAssignments, Instance.AFTER_CALL, Instance.AFTER_CALL, crpip);

		final Term result = Util.and(mScript, globalVarsAssignmentTerm, callPredTerm);
		return constructQuantifiedFormula(Script.EXISTS, crpip.getFreshTermVariables(), result);
	}

	public Term strongestPostconditionReturn(final IPredicate returnPred, final IPredicate callPred,
			final UnmodifiableTransFormula returnTF, final UnmodifiableTransFormula callTF,
			final UnmodifiableTransFormula oldVarAssignments, final Set<IProgramNonOldVar> modifiableGlobals) {

		final CallReturnPyramideInstanceProvider crpip = new CallReturnPyramideInstanceProvider(mMgdScript,
				returnTF.getAssignedVars(), callTF.getAssignedVars(), modifiableGlobals, Instance.AFTER_RETURN);
		final Term callPredTerm = renamePredicateToInstance(callPred, Instance.BEFORE_CALL, crpip);
		final Term returnPredTerm = renamePredicateToInstance(returnPred, Instance.BEFORE_RETURN, crpip);
		final Term callTfTerm = renameTransFormulaToInstances(callTF, Instance.BEFORE_CALL, Instance.AFTER_CALL, crpip);
		final Term oldVarsAssignmentTerm =
				renameTransFormulaToInstances(oldVarAssignments, Instance.BEFORE_CALL, Instance.AFTER_CALL, crpip);
		final Term returnTfTerm =
				renameTransFormulaToInstances(returnTF, Instance.BEFORE_RETURN, Instance.AFTER_RETURN, crpip);

		final Term result =
				Util.and(mScript, callTfTerm, oldVarsAssignmentTerm, returnTfTerm, callPredTerm, returnPredTerm);
		return constructQuantifiedFormula(Script.EXISTS, crpip.getFreshTermVariables(), result);
	}

	public Term weakestPrecondition(final IPredicate p, final UnmodifiableTransFormula tf) {
		if (SmtUtils.isTrue(p.getFormula())) {
			return p.getFormula();
		}
		final Set<TermVariable> varsToQuantify = new HashSet<>();
		final IValueConstruction<IProgramVar, TermVariable> substituentConstruction =
				new IValueConstruction<IProgramVar, TermVariable>() {

					@Override
					public TermVariable constructValue(final IProgramVar pv) {
						final TermVariable result = constructFreshTermVariable(mMgdScript, pv);
						varsToQuantify.add(result);
						return result;
					}

				};
		final ConstructionCache<IProgramVar, TermVariable> termVariablesForSuccessor =
				new ConstructionCache<>(substituentConstruction);

		final Map<Term, Term> substitutionForTransFormula = new HashMap<>();
		final Map<Term, Term> substitutionForSuccessor = new HashMap<>();

		for (final Entry<IProgramVar, TermVariable> entry : tf.getOutVars().entrySet()) {
			final IProgramVar pv = entry.getKey();
			if (entry.getValue() == tf.getInVars().get(pv)) {
				// special case, variable unchanged will be renamed when
				// considering outVars
			} else {
				final TermVariable substituent = termVariablesForSuccessor.getOrConstruct(pv);
				substitutionForTransFormula.put(entry.getValue(), substituent);
				if (p.getVars().contains(pv)) {
					substitutionForSuccessor.put(pv.getTermVariable(), substituent);
				}
			}
		}

		for (final Entry<IProgramVar, TermVariable> entry : tf.getInVars().entrySet()) {
			substitutionForTransFormula.put(entry.getValue(), entry.getKey().getTermVariable());
			if (!tf.getOutVars().containsKey(entry.getKey()) && p.getVars().contains(entry.getKey())) {
				final TermVariable substituent = termVariablesForSuccessor.getOrConstruct(entry.getKey());
				substitutionForSuccessor.put(entry.getKey().getTermVariable(), substituent);
			}
		}

		final Term renamedTransFormula =
				new SubstitutionWithLocalSimplification(mMgdScript, substitutionForTransFormula)
						.transform(tf.getFormula());
		final Term renamedSuccessor =
				new SubstitutionWithLocalSimplification(mMgdScript, substitutionForSuccessor).transform(p.getFormula());

		final Term result = Util.or(mScript, SmtUtils.not(mScript, renamedTransFormula), renamedSuccessor);
		// Add aux vars to varsToQuantify
		varsToQuantify.addAll(tf.getAuxVars());
		return constructQuantifiedFormula(Script.FORALL, varsToQuantify, result);
	}

	public Term weakestPreconditionCall(final IPredicate callSucc, final UnmodifiableTransFormula callTF,
			final UnmodifiableTransFormula globalVarsAssignments, final UnmodifiableTransFormula oldVarAssignments,
			final Set<IProgramNonOldVar> modifiableGlobals) {

		final CallReturnPyramideInstanceProvider crpip = new CallReturnPyramideInstanceProvider(mMgdScript,
				Collections.emptySet(), callTF.getAssignedVars(), modifiableGlobals, Instance.BEFORE_CALL);
		final Term callSuccTerm = renamePredicateToInstance(callSucc, Instance.AFTER_CALL, crpip);
		final Term callTfTerm = renameTransFormulaToInstances(callTF, Instance.BEFORE_CALL, Instance.AFTER_CALL, crpip);
		final Term oldVarsAssignmentTerm =
				renameTransFormulaToInstances(oldVarAssignments, Instance.BEFORE_CALL, Instance.AFTER_CALL, crpip);
		final Term globalVarsAssignmentTerm =
				renameTransFormulaToInstances(globalVarsAssignments, Instance.AFTER_CALL, Instance.AFTER_CALL, crpip);

		final Term result =
				Util.or(mScript, SmtUtils.not(mScript, callTfTerm), SmtUtils.not(mScript, oldVarsAssignmentTerm),
						SmtUtils.not(mScript, globalVarsAssignmentTerm), callSuccTerm);
		return constructQuantifiedFormula(Script.FORALL, crpip.getFreshTermVariables(), result);
	}

	public Term weakestPreconditionReturn(final IPredicate returnSucc, final IPredicate callPred,
			final UnmodifiableTransFormula returnTF, final UnmodifiableTransFormula callTF,
			final UnmodifiableTransFormula oldVarAssignments, final Set<IProgramNonOldVar> modifiableGlobals) {

		final CallReturnPyramideInstanceProvider crpip = new CallReturnPyramideInstanceProvider(mMgdScript,
				returnTF.getAssignedVars(), callTF.getAssignedVars(), modifiableGlobals, Instance.BEFORE_RETURN);
		final Term callPredTerm = renamePredicateToInstance(callPred, Instance.BEFORE_CALL, crpip);
		final Term returnSuccTerm = renamePredicateToInstance(returnSucc, Instance.AFTER_RETURN, crpip);
		final Term callTfTerm = renameTransFormulaToInstances(callTF, Instance.BEFORE_CALL, Instance.AFTER_CALL, crpip);
		final Term oldVarsAssignmentTerm =
				renameTransFormulaToInstances(oldVarAssignments, Instance.BEFORE_CALL, Instance.AFTER_CALL, crpip);
		final Term returnTfTerm =
				renameTransFormulaToInstances(returnTF, Instance.BEFORE_RETURN, Instance.AFTER_RETURN, crpip);

		final Term result =
				Util.or(mScript, SmtUtils.not(mScript, callTfTerm), SmtUtils.not(mScript, oldVarsAssignmentTerm),
						SmtUtils.not(mScript, returnTfTerm), SmtUtils.not(mScript, callPredTerm), returnSuccTerm);
		return constructQuantifiedFormula(Script.FORALL, crpip.getFreshTermVariables(), result);
	}

	private Term renamePredicateToInstance(final IPredicate p, final Instance instance,
			final CallReturnPyramideInstanceProvider crpip) {
		final Map<Term, Term> substitution = new HashMap<>();
		for (final IProgramVar pv : p.getVars()) {
			substitution.put(pv.getTermVariable(), crpip.getInstance(pv, instance));
		}
		final Term result = new SubstitutionWithLocalSimplification(mMgdScript, substitution).transform(p.getFormula());
		return result;
	}

	private Term renameTransFormulaToInstances(final TransFormula tf, final Instance preInstance,
			final Instance succInstance, final CallReturnPyramideInstanceProvider crpip) {

		final Map<Term, Term> substitution = new HashMap<>();
		for (final Entry<IProgramVar, TermVariable> entry : tf.getOutVars().entrySet()) {
			substitution.put(entry.getValue(), crpip.getInstance(entry.getKey(), succInstance));
		}
		for (final Entry<IProgramVar, TermVariable> entry : tf.getInVars().entrySet()) {
			substitution.put(entry.getValue(), crpip.getInstance(entry.getKey(), preInstance));
		}
		final Term result =
				new SubstitutionWithLocalSimplification(mMgdScript, substitution).transform(tf.getFormula());
		return result;
	}

}
