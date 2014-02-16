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
package de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.templates;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import de.uni_freiburg.informatik.ultimate.logic.Rational;
import de.uni_freiburg.informatik.ultimate.logic.SMTLIBException;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.model.boogie.BoogieVar;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.AuxiliaryMethods;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.LinearInequality;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.rankingfunctions.RankingFunction;


/**
 * This is the superclass for templates for linear ranking. All templates will
 * derive from this class.
 * 
 * @author Jan Leike
 *
 */
public abstract class RankingFunctionTemplate {
	protected Script m_script;
	protected Collection<BoogieVar> m_variables;
	
	private boolean m_initialized = false;
	
	RankingFunctionTemplate() {
		m_script = null;
		m_variables = null;
	}
	
	/**
	 * Initialize the template; call this before constaints()
	 * @param script The SMTLib script
	 * @param vars A collection of all variables that are relevant for
	 *                   ranking
	 */
	public void init(Script script, Collection<BoogieVar> vars) {
		m_script = script;
		m_variables = vars;
		m_initialized = true;
	}
	
	/**
	 * Check if the template was properly initialized using init()
	 */
	protected void checkInitialized() {
		assert(m_initialized);
		assert(m_script != null);
		assert(m_variables != null);
	}
	
	
	/**
	 * Returns the name of the template (e.g., affine, 2-phase, or 3-nested)
	 * 
	 */
	public abstract String getName();
	
	/**
	 * Generate the Farkas' Lemma applications for this template
	 * 
	 * Must be initialized before calling this!
	 * 
	 * @param inVars  Input variables for the loop transition
	 * @param outVars Output variables for the loop transition
	 * @return FarkasApplications in CNF; one clause for every conjunct in this
	 *          template's formula. These Applictions will be augmented by
	 *          the loop transition in form of affine terms and the supporting
	 *          invariants.
	 */
	public abstract List<List<LinearInequality>> constraints(
			Map<BoogieVar, TermVariable> inVars,
			Map<BoogieVar, TermVariable> outVars);
	
	/**
	 * Returns a string for every constraint conjunct for annotating
	 * MotzkinTransformation instances.
	 * 
	 * The returned list should have exactly as many elements as the list
	 * returned by constraints()
	 * 
	 * @return a list of annotations
	 */
	public abstract List<String> getAnnotations();
	
	/**
	 * Return all SMT variables used by this template
	 */
	public abstract Collection<Term> getVariables();
	
	/**
	 * Returns the degree of the template, i.e, the number of Motzkin
	 * coefficients occurring in non-linear operation in the generated
	 * constraints
	 * @return degree of the template
	 */
	public abstract int getDegree();
	
	/**
	 * Extract the ranking function from a model
	 * @param script The SMTLib interface script
	 * @return ranking function
	 * @throws SMTLIBException
	 */
	public abstract RankingFunction extractRankingFunction(Map<Term,
			Rational> val) throws SMTLIBException;
	
	/**
	 * Create a new positive variable (as a nullary function symbol)
	 * @param script current SMT script
	 * @param name the new variable's name
	 * @return the new variable as a term
	 */
	public static Term newDelta(Script script, String name) {
		Term delta = AuxiliaryMethods.newConstant(script, name, "Real");
		Term t = script.term(">", delta, script.decimal("0"));
		script.assertTerm(t);
		return delta;
	}
}