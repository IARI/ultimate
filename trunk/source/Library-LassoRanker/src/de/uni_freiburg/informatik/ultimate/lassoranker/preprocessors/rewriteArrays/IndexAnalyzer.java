/*
 * Copyright (C) 2012-2014 University of Freiburg
 *
 * This file is part of the ULTIMATE LassoRanker Library.
 *
 * The ULTIMATE LassoRanker Library is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * The ULTIMATE LassoRanker Library is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE LassoRanker Library. If not,
 * see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE LassoRanker Library, or any covered work, by
 * linking or combining it with Eclipse RCP (or a modified version of
 * Eclipse RCP), containing parts covered by the terms of the Eclipse Public
 * License, the licensors of the ULTIMATE LassoRanker Library grant you
 * additional permission to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.lassoranker.preprocessors.rewriteArrays;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.lassoranker.preprocessors.RewriteArrays;
import de.uni_freiburg.informatik.ultimate.lassoranker.variables.TransFormulaLR;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.logic.Util;
import de.uni_freiburg.informatik.ultimate.model.boogie.BoogieVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.Boogie2SMT;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.TransFormula;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SafeSubstitution;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.BasicPredicate;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.PredicateUtils;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.TermVarsProc;
import de.uni_freiburg.informatik.ultimate.util.HashRelation;

public class IndexAnalyzer {
	private final SetOfDoubletons<Term> allDoubletons = new SetOfDoubletons<>();
	private final SetOfDoubletons<Term> inVarDoubletons = new SetOfDoubletons<>();
	private final Term m_Term;
	private final Script m_Script;
	private final Boogie2SMT m_boogie2smt;
	private final TransFormulaLR m_TransFormula;
	private final Collection<Term> m_SupportingInvariants;
	private final ArrayList<Object> m_AdditionalEqualities;
	private final ArrayList<Object> m_AdditionalNotequals;
	
	private final SetOfDoubletons<Term> distinctDoubletons = new SetOfDoubletons<>();
	private final SetOfDoubletons<Term> equalDoubletons = new SetOfDoubletons<>();
	private final SetOfDoubletons<Term> unknownDoubletons = new SetOfDoubletons<>();
	
	private final boolean m_SearchAdditionalSupportingInvariants;
	private final TransFormula m_OriginalStem;
	private final TransFormula m_OriginalLoop;
	
	public IndexAnalyzer(Term term, HashRelation<TermVariable, 
			List<Term>> array2Indices, 
			boolean searchAdditionalSupportingInvariants, 
			Boogie2SMT boogie2smt, TransFormulaLR tf, 
			TransFormula originalStem, TransFormula originalLoop) {
		super();
//		if (!searchAdditionalSupportingInvariants && (originalStem != null || originalLoop != null)) {
//			throw new AssertionError("specified originalStem or originalLoop but I will not search for additionalSupportingInvariants");
//		}
		m_Term = term;
		m_SearchAdditionalSupportingInvariants = searchAdditionalSupportingInvariants;
		m_boogie2smt = boogie2smt;
		m_Script = boogie2smt.getScript();
		m_TransFormula = tf;
		m_SupportingInvariants = new ArrayList<>();
		m_AdditionalEqualities = new ArrayList<>();
		m_AdditionalNotequals = new ArrayList<>();
		m_OriginalStem = originalStem;
		m_OriginalLoop = originalLoop;
		analyze(array2Indices);
	}
	
	
	private void addDistinctDoubleton(Doubleton<Term> doubleton) {
		distinctDoubletons.addDoubleton(doubleton);
		m_AdditionalNotequals.add(notEqualTerm(doubleton));
	}
	
	private void addEqualDoubleton(Doubleton<Term> doubleton) {
		equalDoubletons.addDoubleton(doubleton);
		m_AdditionalEqualities.add(equalTerm(doubleton));
	}
	
	private void addUnknownDoubleton(Doubleton<Term> doubleton) {
		unknownDoubletons.addDoubleton(doubleton);
	}
	
	void analyze(HashRelation<TermVariable, List<Term>> array2Indices) { 
		for (TermVariable tv : array2Indices.getDomain()) {
			Set<List<Term>> test = array2Indices.getImage(tv);
			List<Term>[] testArr = test.toArray(new List[test.size()]);
			for (int i=0; i<testArr.length; i++) {
				for (int j=i+1; j<testArr.length; j++) {
					List<Term> fstIndex = testArr[i];
					List<Term> sndIndex = testArr[j];
					assert fstIndex.size() == sndIndex.size();
					for (int k=0; k<fstIndex.size(); k++) {
						markForComparison(fstIndex.get(k), sndIndex.get(k));
					}
				}
			}
		}
		
		Term termWithAdditionalInvariants;
		
		if (m_SearchAdditionalSupportingInvariants) { 
			for (Doubleton<Term> inVarDoubleton : inVarDoubletons.elements()) {
				if (containsTermVariable(inVarDoubleton)) {
					Doubleton<Term> definingDoubleton = constructDefiningDoubleton(inVarDoubleton);
					boolean equalityIsInvariant = isInVariant(definingDoubleton, true);
					if (equalityIsInvariant) {
						m_SupportingInvariants.add(equalTerm(definingDoubleton));
						addEqualDoubleton(inVarDoubleton);
					} else {
						boolean notEqualIsInvariant = isInVariant(definingDoubleton, false);
						if (notEqualIsInvariant) {
							m_SupportingInvariants.add(notEqualTerm(definingDoubleton));
							addDistinctDoubleton(inVarDoubleton);
						} else {
							addUnknownDoubleton(inVarDoubleton);
						}
					} 
				}

			}
			termWithAdditionalInvariants = Util.and(m_Script, m_Term, getAdditionalConjunctsEqualities(), getAdditionalConjunctsNotEquals());
		} else {
			termWithAdditionalInvariants = m_Term;
		}

		m_Script.push(1);
		Map<Term, Term> substitutionMapping = SmtUtils.termVariables2Constants(m_Script, m_boogie2smt.getVariableManager(), termWithAdditionalInvariants.getFreeVars());
		SafeSubstitution subst = new SafeSubstitution(m_Script, substitutionMapping);
		m_Script.assertTerm(subst.transform(termWithAdditionalInvariants));
		for (Doubleton<Term> Doubleton : allDoubletons.elements()) {
			//todo ignore toweltons that are already there
			Term equal = subst.transform(
					SmtUtils.binaryEquality(m_Script, 
							Doubleton.getOneElement(), Doubleton.getOtherElement()));
			m_Script.push(1);
			m_Script.assertTerm(equal);
			LBool lbool = m_Script.checkSat();
			m_Script.pop(1);
			if (lbool == LBool.UNSAT) {
				addDistinctDoubleton(Doubleton);
			} else {
				Term notEqual = Util.not(m_Script, equal);
				m_Script.push(1);
				m_Script.assertTerm(notEqual);
				lbool = m_Script.checkSat();
				m_Script.pop(1);
				if (lbool == LBool.UNSAT) {
					addEqualDoubleton(Doubleton);
				} else {
					addUnknownDoubleton(Doubleton);
				}
			}
		}
		m_Script.pop(1);
	}
	
	private Term equalTerm(Doubleton<Term> Doubleton) {
		return SmtUtils.binaryEquality(m_Script, Doubleton.getOneElement(), Doubleton.getOtherElement());
	}

	private Term notEqualTerm(Doubleton<Term> Doubleton) {
		return Util.not(m_Script, equalTerm(Doubleton));
	}
	
	private boolean isInVariant(Doubleton<Term> definingDoubleton, boolean checkEquals) {
		Term invariantCandidateTerm;
		if (checkEquals) {
			invariantCandidateTerm = equalTerm(definingDoubleton);
		} else {
			invariantCandidateTerm = notEqualTerm(definingDoubleton);
		}
		TermVarsProc tvp = TermVarsProc.computeTermVarsProc(invariantCandidateTerm, m_boogie2smt);
		IPredicate invariantCandidate = new BasicPredicate(0, tvp.getProcedures(), tvp.getFormula(), tvp.getVars(), tvp.getClosedFormula());
		Set<BoogieVar> emptyVarSet = Collections.emptySet();
		IPredicate truePredicate = new BasicPredicate(0, new String[0], m_Script.term("true"), emptyVarSet, m_Script.term("true"));
		LBool impliedByStem = PredicateUtils.isInductiveHelper(m_boogie2smt, truePredicate, invariantCandidate, m_OriginalStem);
		if (impliedByStem == LBool.UNSAT) {
			LBool invariantOfLoop = PredicateUtils.isInductiveHelper(m_boogie2smt, invariantCandidate, invariantCandidate, m_OriginalLoop);
			if (invariantOfLoop == LBool.UNSAT) {
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}
	
	private Doubleton<Term> constructDefiningDoubleton(Doubleton<Term> inVarDoubleton) {
		Term oneElement = inVarDoubleton.getOneElement();
		Term otherElement = inVarDoubleton.getOtherElement();
		Term[] translatedOne = RewriteArrays.translateTermVariablesToDefinitions(
				m_Script, m_TransFormula, oneElement);
		assert translatedOne.length == 1;
		Term[] translatedOther = RewriteArrays.translateTermVariablesToDefinitions(
				m_Script, m_TransFormula, otherElement);
		assert translatedOther.length == 1;
		return new Doubleton<Term>(translatedOne[0], translatedOther[0]);
		
	}
	
	private boolean containsTermVariable(Doubleton<Term> Doubleton) {
		if (Doubleton.getOneElement().getFreeVars().length > 0) {
			return true;
		} else if (Doubleton.getOtherElement().getFreeVars().length > 0) {
			return true;
		} else {
			return false;
		}
	}
	
	private void markForComparison(Term term1, Term term2) {
		Doubleton<Term> Doubleton = new Doubleton<Term>(term1, term2);
		allDoubletons.addDoubleton(Doubleton);
		if (RewriteArrays.allVariablesAreInVars(term1, m_TransFormula)
				&& RewriteArrays.allVariablesAreInVars(term2, m_TransFormula)) {
			inVarDoubletons.addDoubleton(Doubleton);
		}
	}
	
	public enum Equality { EQUAL, NOT_EQUAL, UNKNOWN };
	
	public Equality isEqual(List<Term> index1, List<Term> index2) {
		assert index1.size() == index2.size();
		boolean oneEntryWasUnknown = false;
		for (int i=0; i<index1.size(); i++) {
			if (index1.get(i) == index2.get(i)) {
				continue;
			}
			if (isDistinctDoubleton(index1.get(i), index2.get(i))) {
				return Equality.NOT_EQUAL;
			}
			if (isUnknownDoubleton(index1.get(i), index2.get(i))) {
				oneEntryWasUnknown = true;
			} else {
				assert (isEqualDoubleton(index1.get(i), index2.get(i)));
			}
		}
		if (oneEntryWasUnknown) {
			return Equality.UNKNOWN;
		} else {
			return Equality.EQUAL;
		}
	}
	
	public boolean isEqualDoubleton(Term t1, Term t2) {
		return equalDoubletons.containsDoubleton(t1, t2);
	}
	
	public boolean isDistinctDoubleton(Term t1, Term t2) {
		return distinctDoubletons.containsDoubleton(t1, t2);
	}
	
	public boolean isUnknownDoubleton(Term t1, Term t2) {
		return unknownDoubletons.containsDoubleton(t1, t2);
	}
	
	public Collection<Term> getSupportingInvariants() {
		return m_SupportingInvariants;
	}

	public Term getAdditionalConjunctsEqualities() {
		return Util.and(m_Script, m_AdditionalEqualities.toArray(new Term[m_AdditionalEqualities.size()]));
	}
	
	public Term getAdditionalConjunctsNotEquals() {
		return Util.and(m_Script, m_AdditionalNotequals.toArray(new Term[m_AdditionalNotequals.size()]));
	}
}
