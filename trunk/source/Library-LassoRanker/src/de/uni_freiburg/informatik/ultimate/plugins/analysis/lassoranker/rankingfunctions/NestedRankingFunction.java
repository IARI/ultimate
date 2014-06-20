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
package de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.rankingfunctions;

import java.math.BigInteger;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.logic.Rational;
import de.uni_freiburg.informatik.ultimate.logic.SMTLIBException;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.AffineFunction;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.RankVar;


/**
 * A nested ranking function as generated by the NestedTemplate
 * 
 * @author Matthias Heizmann
 */
public class NestedRankingFunction extends RankingFunction {
	private static final long serialVersionUID = 380153194719949843L;
	
	private final AffineFunction[] m_Ranking;
	public final int m_Functions;
	
	public NestedRankingFunction(AffineFunction[] ranking) {
		m_Ranking = ranking;
		m_Functions = ranking.length;
		assert(m_Functions > 0);
	}
	
	@Override
	public String getName() {
		return m_Ranking.length + "-nested";
	}

	
	@Override
	public Set<RankVar> getVariables() {
		Set<RankVar> vars = new LinkedHashSet<RankVar>();
		for (AffineFunction af : m_Ranking) {
			vars.addAll(af.getVariables());
		}
		return vars;
	}
	
	public AffineFunction[] getComponents() {
		return m_Ranking;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(m_Ranking.length);
		sb.append("-nested ranking function:\n");
		for (int i = 0; i < m_Functions; ++i) {
			sb.append("  f" + i);
			sb.append(" = ");
			sb.append(m_Ranking[i]);
			if (i < m_Functions - 1) {
				sb.append("\n");
			}
		}
		return sb.toString();
	}
	
	@Override
	public Term[] asLexTerm(Script script) throws SMTLIBException {
		// similar to the multiphase ranking function this can be seen as a
		// ranking function that proceed through phases.
		BigInteger n = BigInteger.ZERO;
		Term phase = script.numeral(n);
		Term value = m_Ranking[m_Ranking.length - 1].asTerm(script);
		for (int i = m_Ranking.length - 2; i >= 0; --i) {
			n = n.add(BigInteger.ONE);
			Term f_term = m_Ranking[i].asTerm(script);
			Term cond = script.term(">", f_term,
					script.numeral(BigInteger.ZERO));
			phase = script.term("ite", cond, script.numeral(n), phase);
			value = script.term("ite", cond, f_term, value);
		}
		return new Term[] { phase, value };
	}
	
	@Override
	public Ordinal evaluate(Map<RankVar, Rational> assignment) {
		throw new UnsupportedOperationException("not yet implemented");
	}
}
