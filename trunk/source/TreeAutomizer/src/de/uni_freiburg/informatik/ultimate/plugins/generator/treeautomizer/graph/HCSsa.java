/*
 * Copyright (C) 2016 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2016 Mostafa M.A. (mostafa.amin93@gmail.com)
 * Copyright (C) 2016 University of Freiburg
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.uni_freiburg.informatik.ultimate.automata.tree.TreeRun;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Pair;

/**
 * HCSsa HornClause-SSA
 * 
 * @author Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * @author Mostafa M.A. (mostafa.amin93@gmail.com)
 * 
 */
public class HCSsa {

	private final TreeRun<Term, IPredicate> mNestedFormulas;
	private final Term mPostCondition;
	private final Term mPreCondition;
	private final Map<Term, Integer> mCounters;
	private final Map<Term, Term> mTermToAssertion;

	private boolean mCountersAreFinalized;
	private Term[] mFlattenedTerms;
	private int[] mStartsOfSubtrees;

	/**
	 * Constructor for HC-SSA
	 * 
	 * @param nestedFormulas
	 *            A given treeRun
	 * @param pre
	 *            The precondition (the condition of the initial state)
	 * @param post
	 *            The postcondition (the condition of the final state)
	 * @param counters
	 *            A map of the counts of each Term.
	 */
	public HCSsa(final TreeRun<Term, IPredicate> nestedFormulas, final Term pre, final Term post) {
		mNestedFormulas = nestedFormulas;
		mPostCondition = post;
		mPreCondition = pre;

		mCounters = new HashMap<>();
		mCountersAreFinalized = false;
		mTermToAssertion = new HashMap<>();

		final Pair<List<Term>, List<Integer>> flattenRes = flatten(mNestedFormulas, 0);
		final List<Term> flattenedTermslist = flattenRes.getFirst();
		final List<Integer> startsOfSubtrees = flattenRes.getSecond();
		mFlattenedTerms = flattenedTermslist.toArray(new Term[flattenedTermslist.size()]);
		mStartsOfSubtrees = new int[startsOfSubtrees.size()];
		for (int i = 0; i < startsOfSubtrees.size(); i++) {
			mStartsOfSubtrees[i] = startsOfSubtrees.get(i);
		}
		mCountersAreFinalized = true;
	}

	int getCounter(final Term t) {
		if (!mCounters.containsKey(t)) {
			assert !mCountersAreFinalized;
			int r = mCounters.size() + 1;
			mCounters.put(t, r);
		}
		return mCounters.get(t);
	}

	/**
	 * Returns a name for the given term. The term must be one of those that are in the List returned by flatten().
	 * The name will be used by Tree checker for making annotated terms out of the flattened terms, and for posing
	 * the get-interpolants query.
	 * 
	 * @param t
	 * @return
	 */
	public String getName(final Term t) {
		return "HCsSATerm_" + getCounter(t);
	}

	/**
	 * Computes a flat (i.e. array instead of tree) version of the SSA.
	 * This flat version is used by the TreeChecker to construct named formulas from it and assert each one in the 
	 *  solver.
	 *  
	 * The order of the flattened list corresponds to a post-order over the tree, this 
	 * 
	 * @return 
	 */
	public Term[] getFlattenedTermList() {
		return mFlattenedTerms;
	}

	private Pair<List<Term>, List<Integer>> flatten(final TreeRun<Term, IPredicate> tree, int depth) {
		ArrayList<Term> res = new ArrayList<>();
		ArrayList<Integer> resStartsOfSubtrees = new ArrayList<>();
		for (int i = 0; i < tree.getChildren().size(); i++) {
			TreeRun<Term, IPredicate> child = tree.getChildren().get(i);
			Pair<List<Term>, List<Integer>> childRes = flatten(child, depth + i);
			res.addAll(childRes.getFirst());
			resStartsOfSubtrees.addAll(childRes.getSecond());
		}
		if (tree.getRootSymbol() != null) {
			res.add(tree.getRootSymbol());
			resStartsOfSubtrees.add(depth);
			this.getCounter(tree.getRootSymbol());
		}
		return new Pair<>(res, resStartsOfSubtrees);
	}

	public TreeRun<Term, IPredicate> getFormulasTree() {
		return mNestedFormulas;
	}

	public Term[] getNamedTermList(final ManagedScript script, final Object lockOwner) {
		final Term[] result = new Term[mFlattenedTerms.length];
		for (int i = 0; i < mFlattenedTerms.length; i++) {
			result[i] = script.term(lockOwner, getName(mFlattenedTerms[i]));
		}
		return result;
	}

	public int[] getStartsOfSubTrees() {
		return mStartsOfSubtrees;
	}
}
