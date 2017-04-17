/*
 * Copyright (C) 2017 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2017 Mostafa M.A. (mostafa.amin93@gmail.com)
 * Copyright (C) 2017 University of Freiburg
 *
 * This file is part of the ULTIMATE TreeAutomizer Plugin.
 *
 * The ULTIMATE TreeAutomizer Plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE TreeAutomizer Plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE TreeAutomizer Plugin. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE TreeAutomizer Plugin, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE TreeAutomizer Plugin grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.generator.treeautomizer.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.uni_freiburg.informatik.ultimate.automata.tree.ITreeRun;
import de.uni_freiburg.informatik.ultimate.automata.tree.TreeAutomatonRule;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.hornutil.HornClause;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;

/**
 * Provides rules that might be added to an interpolant automaton during the generalization phase. 
 * 
 * 
 * @author Alexander Nutz (nutz@informatik.uni-freiburg.de)
 *
 */
public class CandidateRuleProvider {
	
	
	
	private Iterable<TreeAutomatonRule<HornClause, IPredicate>> mCandidateRules;

	/**
	 * Triggers computation of candidate rules.
	 * Result can be obtained via getter method.
	 * 
	 * @param originalTreeRun
	 * @param hcSymbolsToInterpolants 
	 * @param alphabet 
	 */
	public CandidateRuleProvider(ITreeRun<HornClause, IPredicate> originalTreeRun, 
			Map<IPredicate, IPredicate> hcSymbolsToInterpolants, List<HornClause> alphabet) {
		mCandidateRules = new ArrayList<>();
		
		for (HornClause letter : alphabet) {
			
			
		}

	}

	public Iterable<TreeAutomatonRule<HornClause, IPredicate>> getCandidateRules() {
		return mCandidateRules;
	}
}
