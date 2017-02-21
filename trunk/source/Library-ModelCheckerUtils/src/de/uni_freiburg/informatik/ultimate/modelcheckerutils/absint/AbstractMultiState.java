/*
 * Copyright (C) 2015-2016 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2015-2016 University of Freiburg
 *
 * This file is part of the ULTIMATE AbstractInterpretationV2 plug-in.
 *
 * The ULTIMATE AbstractInterpretationV2 plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE AbstractInterpretationV2 plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE AbstractInterpretationV2 plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE AbstractInterpretationV2 plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE AbstractInterpretationV2 plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils;

/**
 * An {@link AbstractMultiState} is an abstract state that consists of many abstract states of the same type.
 *
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 *
 * @param <STATE>
 * @param <ACTION>
 * @param <VARDECL>
 */
public class AbstractMultiState<STATE extends IAbstractState<STATE, VARDECL>, ACTION, VARDECL>
		implements IAbstractState<AbstractMultiState<STATE, ACTION, VARDECL>, VARDECL> {

	private static int sNextFreeId;
	private final Set<STATE> mStates;
	private final int mMaxSize;
	private final int mId;

	public AbstractMultiState(final int maxSize) {
		this(maxSize, newSet(maxSize));
	}

	public AbstractMultiState(final int maxSize, final STATE state) {
		this(maxSize, newSet(maxSize));
		mStates.add(state);
	}

	public AbstractMultiState(final Set<STATE> state) {
		this(state.size(), state);
	}

	private AbstractMultiState(final int maxSize, final Set<STATE> states) {
		mMaxSize = maxSize;
		mStates = states;
		sNextFreeId++;
		mId = sNextFreeId;
	}

	@Override
	public AbstractMultiState<STATE, ACTION, VARDECL> addVariable(final VARDECL variable) {
		return map(a -> a.addVariable(variable));
	}

	@Override
	public AbstractMultiState<STATE, ACTION, VARDECL> removeVariable(final VARDECL variable) {
		return map(a -> a.removeVariable(variable));
	}

	@Override
	public AbstractMultiState<STATE, ACTION, VARDECL> addVariables(final Collection<VARDECL> variables) {
		return map(a -> a.addVariables(variables));
	}

	@Override
	public AbstractMultiState<STATE, ACTION, VARDECL> removeVariables(final Collection<VARDECL> variables) {
		return map(a -> a.removeVariables(variables));
	}

	@Override
	public boolean containsVariable(final VARDECL var) {
		return mStates.stream().anyMatch(a -> a.containsVariable(var));
	}

	@Override
	public Set<VARDECL> getVariables() {
		if (mStates.isEmpty()) {
			return Collections.emptySet();
		}
		return Collections.unmodifiableSet(mStates.iterator().next().getVariables());
	}

	@Override
	public AbstractMultiState<STATE, ACTION, VARDECL>
			patch(final AbstractMultiState<STATE, ACTION, VARDECL> dominator) {
		assert mStates.size() != dominator.mStates
				.size() : "Cannot apply symmetrical with differently sized multi-states";
		final Set<STATE> newSet = newSet(mStates.size());
		final Iterator<STATE> iter = mStates.iterator();
		final Iterator<STATE> otherIter = dominator.mStates.iterator();
		while (iter.hasNext() && otherIter.hasNext()) {
			newSet.add(iter.next().patch(otherIter.next()));
		}
		return new AbstractMultiState<>(mMaxSize, newSet);
	}

	@Override
	public boolean isEmpty() {
		return mStates.stream().anyMatch(a -> a.isEmpty());
	}

	@Override
	public boolean isBottom() {
		return mStates.isEmpty() || mStates.stream().allMatch(a -> a.isBottom());
	}

	@Override
	public boolean isEqualTo(final AbstractMultiState<STATE, ACTION, VARDECL> other) {
		if (other == null) {
			return false;
		}
		if (!other.getVariables().equals(getVariables())) {
			return false;
		}

		for (final STATE state : mStates) {
			if (!other.mStates.stream().anyMatch(state::isEqualTo)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public SubsetResult isSubsetOf(final AbstractMultiState<STATE, ACTION, VARDECL> other) {
		if (other == null) {
			return SubsetResult.NONE;
		}
		if (!other.getVariables().equals(getVariables())) {
			return SubsetResult.NONE;
		}
		if (other.mStates.isEmpty() && !mStates.isEmpty()) {
			return SubsetResult.NONE;
		}

		SubsetResult result = SubsetResult.EQUAL;
		for (final STATE state : mStates) {
			final Optional<SubsetResult> min =
					other.mStates.stream().map(a -> state.isSubsetOf(a)).min((a, b) -> a.compareTo(b));
			if (min.isPresent()) {
				result = result.update(min.get());
			}
			if (result == SubsetResult.NONE) {
				break;
			}
		}
		return result;
	}

	@Override
	public Term getTerm(final Script script) {
		return SmtUtils.or(script, mStates.stream().map(a -> a.getTerm(script)).collect(Collectors.toSet()));
	}

	@Override
	public String toLogString() {
		final StringBuilder sb = new StringBuilder();
		sb.append('#').append(mStates.size());
		for (final STATE state : mStates) {
			sb.append('{');
			final String logStr = state.toLogString();
			if (logStr == null || logStr.isEmpty()) {
				sb.append("__");
			} else {
				sb.append(logStr);
			}
			sb.append('}');
			sb.append(", ");
		}
		if (!mStates.isEmpty()) {
			sb.delete(sb.length() - 2, sb.length());
		}
		return sb.toString();
	}

	@Override
	public int hashCode() {
		return mId;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final AbstractMultiState<?, ?, ?> other = (AbstractMultiState<?, ?, ?>) obj;
		if (mId != other.mId) {
			return false;
		}
		if (mStates == null) {
			if (other.mStates != null) {
				return false;
			}
		} else if (!mStates.equals(other.mStates)) {
			return false;
		}
		return true;
	}

	/**
	 * Apply the {@link IVariableProvider#defineVariablesAfter(Object, IAbstractState, IAbstractState)} function to all
	 * states in this multi-state. This state acts as local pre state, and all states in hierachicalPreState are used as
	 * hierachical pre states.
	 */
	public AbstractMultiState<STATE, ACTION, VARDECL> defineVariablesAfter(
			final IVariableProvider<STATE, ACTION, VARDECL> varProvider, final ACTION transition,
			final AbstractMultiState<STATE, ACTION, VARDECL> hierachicalPreState) {
		return crossProduct((a, b) -> varProvider.defineVariablesAfter(transition, a, b), hierachicalPreState,
				mMaxSize);
	}

	/**
	 * Apply the {@link IVariableProvider#createValidPostOpStateAfterLeaving(Object, IAbstractState, IAbstractState)}
	 * function to all states in this multi-state. This state acts as local pre state, and all states in
	 * hierachicalPreState are used as hierachical pre states.
	 */
	public AbstractMultiState<STATE, ACTION, VARDECL> createValidPostOpStateAfterLeaving(
			final IVariableProvider<STATE, ACTION, VARDECL> varProvider, final ACTION act,
			final AbstractMultiState<STATE, ACTION, VARDECL> hierachicalPreState) {
		if (hierachicalPreState == null) {
			return map(a -> varProvider.createValidPostOpStateAfterLeaving(act, a, null));
		}
		return crossProduct((a, b) -> varProvider.createValidPostOpStateAfterLeaving(act, a, b), hierachicalPreState,
				mStates.size() * hierachicalPreState.mStates.size());
	}

	public AbstractMultiState<STATE, ACTION, VARDECL> createValidPostOpStateBeforeLeaving(
			final IVariableProvider<STATE, ACTION, VARDECL> varProvider, final ACTION act) {
		return map(a -> varProvider.createValidPostOpStateBeforeLeaving(act, a));
	}

	public AbstractMultiState<STATE, ACTION, VARDECL> synchronizeVariables(
			final IVariableProvider<STATE, ACTION, VARDECL> varProvider,
			final AbstractMultiState<STATE, ACTION, VARDECL> toSynchronize) {
		// any state will do:
		return new AbstractMultiState<STATE, ACTION, VARDECL>(mMaxSize, mStates.iterator().next())
				.crossProduct(varProvider::synchronizeVariables, toSynchronize, toSynchronize.mStates.size());
	}

	public AbstractMultiState<STATE, ACTION, VARDECL> apply(final IAbstractTransformer<STATE, ACTION, VARDECL> op,
			final ACTION transition) {
		return mapCollection(a -> op.apply(a, transition));
	}

	public AbstractMultiState<STATE, ACTION, VARDECL> apply(final IAbstractPostOperator<STATE, ACTION, VARDECL> postOp,
			final AbstractMultiState<STATE, ACTION, VARDECL> multiStateBeforeLeaving, final ACTION transition) {
		return crossProductCollection((a, b) -> postOp.apply(b, a, transition), multiStateBeforeLeaving, mMaxSize);
	}

	public AbstractMultiState<STATE, ACTION, VARDECL> apply(final IAbstractStateBinaryOperator<STATE> op,
			final AbstractMultiState<STATE, ACTION, VARDECL> other) {
		return crossProduct(op::apply, other, mMaxSize);
	}

	@Override
	public String toString() {
		return toLogString();
	}

	public Set<STATE> getStates() {
		return Collections.unmodifiableSet(mStates);
	}

	public STATE getSingleState(final IAbstractStateBinaryOperator<STATE> mergeOp) {
		return mStates.stream().reduce(mergeOp::apply).orElse(null);
	}

	public AbstractMultiState<STATE, ACTION, VARDECL> merge(final IAbstractStateBinaryOperator<STATE> mergeOp,
			final AbstractMultiState<STATE, ACTION, VARDECL> other) {
		assert other != null && other.getVariables().equals(getVariables()) : "Cannot merge incompatible states";
		final Set<STATE> set = newSet();
		set.addAll(mStates);
		set.addAll(other.mStates);
		return new AbstractMultiState<>(mMaxSize, reduce(mergeOp, set));
	}

	/**
	 * Create a new {@link AbstractMultiState} by applying some function to each pair of states from this
	 * {@link AbstractMultiState} and some other {@link AbstractMultiState} (i.e., the first argument is a state from
	 * this instance). If the resulting set of states does not differ from this state, return this state. If it differs,
	 * create a new {@link AbstractMultiState} that retains as many as <code>maxSize</code> disjunctive states.
	 */
	private AbstractMultiState<STATE, ACTION, VARDECL> crossProduct(
			final BiFunction<STATE, STATE, STATE> funCreateState,
			final AbstractMultiState<STATE, ACTION, VARDECL> otherMultiState, final int maxSize) {
		final Set<STATE> newSet = newSet(mStates.size() * otherMultiState.mStates.size());
		for (final STATE localState : mStates) {
			for (final STATE otherState : otherMultiState.mStates) {
				newSet.add(funCreateState.apply(localState, otherState));
			}
		}
		if (newSet.equals(mStates)) {
			return this;
		}
		return new AbstractMultiState<>(maxSize, getMaximalElements(newSet));
	}

	private AbstractMultiState<STATE, ACTION, VARDECL> crossProductCollection(
			final BiFunction<STATE, STATE, Collection<STATE>> funCreateState,
			final AbstractMultiState<STATE, ACTION, VARDECL> otherMultiState, final int maxSize) {
		final Set<STATE> newSet = newSet(mStates.size() * otherMultiState.mStates.size());
		for (final STATE localState : mStates) {
			for (final STATE otherState : otherMultiState.mStates) {
				newSet.addAll(funCreateState.apply(localState, otherState));
			}
		}
		if (newSet.equals(mStates)) {
			return this;
		}
		return new AbstractMultiState<>(maxSize, getMaximalElements(newSet));
	}

	private AbstractMultiState<STATE, ACTION, VARDECL> map(final Function<STATE, STATE> func) {
		final Set<STATE> newSet = newSet(mStates.size());
		for (final STATE state : mStates) {
			newSet.add(func.apply(state));
		}
		if (mStates.equals(newSet)) {
			return this;
		}
		return new AbstractMultiState<>(mMaxSize, newSet);
	}

	private AbstractMultiState<STATE, ACTION, VARDECL> mapCollection(final Function<STATE, Collection<STATE>> func) {
		final Set<STATE> newSet = newSet();
		for (final STATE state : mStates) {
			newSet.addAll(func.apply(state));
		}
		return new AbstractMultiState<>(mMaxSize, getMaximalElements(newSet));
	}

	private Set<STATE> newSet() {
		return newSet(mMaxSize);
	}

	private static <STATE> Set<STATE> newSet(final int maxSize) {
		return new LinkedHashSet<>(maxSize, 1.0F);
	}

	private Set<STATE> reduce(final IAbstractStateBinaryOperator<STATE> mergeOp, final Set<STATE> states) {
		final Set<STATE> maximalElements = getMaximalElements(states);
		if (maximalElements.size() <= mMaxSize) {
			return maximalElements;
		}
		return reduceByOrderedMerge(mergeOp, maximalElements);
	}

	private Set<STATE> reduceByOrderedMerge(final IAbstractStateBinaryOperator<STATE> mergeOp,
			final Set<STATE> states) {
		final Set<STATE> reducibleSet = new LinkedHashSet<>(states);
		int numberOfMerges = states.size() - mMaxSize;
		while (numberOfMerges > 0) {
			final Iterator<STATE> iter = reducibleSet.iterator();
			final STATE first = iter.next();
			iter.remove();
			final STATE second = iter.next();
			iter.remove();
			if (reducibleSet.add(mergeOp.apply(first, second))) {
				--numberOfMerges;
			} else {
				numberOfMerges -= 2;
			}
		}
		assert reducibleSet.size() <= mMaxSize;
		return reducibleSet;
	}

	private Set<STATE> getMaximalElements(final Set<STATE> states) {
		final Set<STATE> maximalElements = newSet(states.size());
		for (final STATE state : states) {
			final Iterator<STATE> iter = maximalElements.iterator();
			boolean maximal = true;
			while (iter.hasNext()) {
				final STATE candidate = iter.next();
				final SubsetResult stateIsCovered = state.isSubsetOf(candidate);
				final SubsetResult stateCovers = candidate.isSubsetOf(state);
				if (stateIsCovered != SubsetResult.NONE) {
					// state is covered by someone, it cannot be maximal
					maximal = false;
					break;
				}
				if (stateCovers != SubsetResult.NONE) {
					// state covers someone
					iter.remove();
				}
			}

			if (maximal) {
				maximalElements.add(state);
			}
		}
		assert maximalElements.stream().filter(STATE::isBottom).count() <= 1 : "There can be only one bottom element";
		return maximalElements;
	}

}
