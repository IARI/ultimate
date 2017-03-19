/*
 * Copyright (C) 2017 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2017 University of Freiburg
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.logic.QuantifiedFormula;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SubstitutionWithLocalSimplification;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.partialQuantifierElimination.EqualityInformation;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.HashRelation;

/**
 *
 * @author heizmann@informatik.uni-freiburg.de
 */
public class SimultaneousUpdate {

	Map<IProgramVar, Term> mUpdatedVars = new HashMap<>();
	Set<IProgramVar> mHavocedVars = new HashSet<>();

	public SimultaneousUpdate(final TransFormula tf, final ManagedScript mgdScript) {
		final Map<TermVariable, IProgramVar> inVarsReverseMapping = TransFormulaUtils
				.constructReverseMapping(tf.getInVars());
		final Map<TermVariable, IProgramVar> outVarsReverseMapping = TransFormulaUtils
				.constructReverseMapping(tf.getOutVars());
		final Term[] conjuncts = SmtUtils.getConjuncts(tf.getFormula());
		final HashRelation<IProgramVar, Term> pv2conjuncts = new HashRelation<>();
		for (final Term conjunct : conjuncts) {
			for (final TermVariable tv : conjunct.getFreeVars()) {
				final IProgramVar pv = outVarsReverseMapping.get(tv);
				if (pv != null) {
					pv2conjuncts.addPair(pv, conjunct);
				}
			}
		}
		final Set<IProgramVar> allProgVars = new HashSet<>();
		allProgVars.addAll(tf.getInVars().keySet());
		allProgVars.addAll(tf.getOutVars().keySet());
		for (final IProgramVar pv : allProgVars) {
			if (tf.getInVars().get(pv) == tf.getOutVars().get(pv)) {
				// var unchanged
				continue;
			}
			if (tf.isHavocedOut(pv)) {
				mHavocedVars.add(pv);
			} else {
				final Set<Term> pvContainingConjuncts = pv2conjuncts.getImage(pv);
				if (pvContainingConjuncts.isEmpty()) {
					if (tf.getInVars().get(pv) != tf.getOutVars().get(pv)) {
						throw new AssertionError("in and out have to be similar");
					}
				} else {
					// extract
					final Term pvContainingConjunct = pvContainingConjuncts.iterator().next();
					final Term forbiddenTerm = null;
					final TermVariable outVar = tf.getOutVars().get(pv);
					final Term renamed = extractUpdateRhs(outVar, conjuncts, forbiddenTerm, inVarsReverseMapping,
							outVarsReverseMapping, mgdScript);
					if (renamed == null) {
						throw new IllegalArgumentException("cannot bring into simultaneous update form " + pv
								+ "'s outvar occurs in several conjuncts.");
					}
					mUpdatedVars.put(pv, renamed);
				}

				// else {
				// throw new IllegalArgumentException("cannot bring into
				// simultaneous update form " + pv
				// + "'s outvar occurs in several conjuncts.");
				// }
			}
		}

		mgdScript.toString();
	}

	private Term extractUpdateRhs(final TermVariable outVar, final Term[] conjuncts, final Term forbiddenTerm,
			final Map<TermVariable, IProgramVar> inVarsReverseMapping,
			final Map<TermVariable, IProgramVar> outVarsReverseMapping, final ManagedScript mgdScript) {
		final EqualityInformation ei = EqualityInformation.getEqinfo(mgdScript.getScript(), outVar, conjuncts,
				forbiddenTerm, QuantifiedFormula.EXISTS);
		if (ei == null) {
			return null;
		}
		final Term rhs = ei.getTerm();
		final Map<Term, Term> substitutionMapping = computeSubstitutionMapping(rhs, conjuncts, outVar,
				inVarsReverseMapping, outVarsReverseMapping, mgdScript);
		final Term renamed = new SubstitutionWithLocalSimplification(mgdScript, substitutionMapping).transform(rhs);
		return renamed;
	}

	private Map<Term, Term> computeSubstitutionMapping(final Term rhs, final Term[] conjuncts,
			final TermVariable forbiddenTerm, final Map<TermVariable, IProgramVar> inVarsReverseMapping,
			final Map<TermVariable, IProgramVar> outVarsReverseMapping, final ManagedScript mgdScript) {
		final Map<Term, Term> result = new HashMap<>();
		for (final TermVariable tv : rhs.getFreeVars()) {
			IProgramVar pv = inVarsReverseMapping.get(tv);
			if (pv != null) {
				result.put(tv, pv.getTermVariable());
			} else {
				pv = outVarsReverseMapping.get(tv);
				if (pv != null) {
					final Term renamed = extractUpdateRhs(tv, conjuncts, forbiddenTerm, inVarsReverseMapping,
							outVarsReverseMapping, mgdScript);
					if (renamed == null) {
						throw new IllegalArgumentException(
								"cannot bring into simultaneous update form, two outvars in equality ");

					}
					result.put(tv, renamed);
				} else {
					throw new IllegalArgumentException(
							"cannot bring into simultaneous update form, neither invar nor outvar " + tv);
				}
			}
		}
		return result;
	}

	public Map<IProgramVar, Term> getUpdatedVars() {
		return mUpdatedVars;
	}

	public Set<IProgramVar> getHavocedVars() {
		return mHavocedVars;
	}

}
