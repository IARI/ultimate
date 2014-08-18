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
package de.uni_freiburg.informatik.ultimate.lassoranker.preprocessors;

import de.uni_freiburg.informatik.ultimate.lassoranker.variables.TransFormulaLR;
import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermTransformer;
import de.uni_freiburg.informatik.ultimate.logic.Util;
import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;


/**
 * Replaces equalities (atoms of the form a = b) with (a ≤ b \/ a ≥ b).
 * 
 * @author Jan Leike
 */
public class RewriteEquality extends TransformerPreProcessor {
	
	@Override
	public String getDescription() {
		return "Replaces atoms of the form a = b with (a <= b /\\ a >= b)";
	}
	
	@Override
	protected boolean checkSoundness(Script script, TransFormulaLR oldTF,
			TransFormulaLR newTF) {
		Term old_term = oldTF.getFormula();
		Term new_term = newTF.getFormula();
		return LBool.SAT != Util.checkSat(script,
				script.term("distinct", old_term, new_term));
	}
	
	@Override
	protected TermTransformer getTransformer(Script script) {
		return new RewriteEqualityTransformer(script);
	}
	
	private class RewriteEqualityTransformer extends TermTransformer {
		
		private final Script m_Script;
		
		RewriteEqualityTransformer(Script script) {
			assert script != null;
			m_Script = script;
		}
		
		@Override
		protected void convert(Term term) {
			if (term instanceof ApplicationTerm) {
				ApplicationTerm appt = (ApplicationTerm) term;
				if (appt.getFunction().getName().equals("=") &&
						!appt.getParameters()[0].getSort().getName().equals("Bool")) {
					assert(appt.getParameters().length == 2);
					Term param1 = m_Script.term("<=", appt.getParameters());
					Term param2 = m_Script.term(">=", appt.getParameters());
					setResult(m_Script.term("and", param1, param2));
					return;
				}
			}
			super.convert(term);
		}
	}
}