/*
 * Copyright (C) 2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2009-2015 University of Freiburg
 * 
 * This file is part of the ULTIMATE Automata Library.
 * 
 * The ULTIMATE Automata Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * The ULTIMATE Automata Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Automata Library. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Automata Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE Automata Library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.automata.nestedword.buchi;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.automata.nestedword.DoubleDecker;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomatonSimple;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.OutgoingCallTransition;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.OutgoingInternalTransition;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.OutgoingReturnTransition;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Pair;

/**
 * Constraints that define a set of LevelRankingStates.
 * <ul>
 * <li>mLevelRanking represents an upper bound for ranks of LevelRankingStates defined by this LevelRankingConstraints.
 * <li>A DoubleDecker is in LevelRankingState.mO iff (it is in LevelRankingConstraints.mO and it has an even level rank)
 * </ul>
 * 
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * @param <LETTER>
 *            letter type
 * @param <STATE>
 *            state type
 */
public class LevelRankingConstraint<LETTER, STATE> extends LevelRankingState<LETTER, STATE> {
	protected final boolean mPredecessorOwasEmpty;

	private final int mUserDefinedMaxRank;
	/**
	 * if !mUseDoubleDeckers we always use getEmptyStackState() as down state to obtain sets of states instead of sets
	 * of DoubleDeckers.
	 */
	private final boolean mUseDoubleDeckers;

	/**
	 * Information if the direct predecessor of a DoubleDecker was accepting. If this information is used by the
	 * LevelRankingGenerator.
	 */
	private final Set<DoubleDecker<StateWithRankInfo<STATE>>> mPredecessorWasAccepting = new HashSet<>();

	/**
	 * Extended constructor.
	 * 
	 * @param operand
	 *            operand
	 * @param predecessorOwasEmpty
	 *            {code true} iff predecessor O was empty
	 * @param userDefinedMaxRank
	 *            user-defined maximal rank
	 * @param useDoubleDeckers
	 *            {code true} iff double deckers should be used
	 */
	public LevelRankingConstraint(final INestedWordAutomatonSimple<LETTER, STATE> operand,
			final boolean predecessorOwasEmpty, final int userDefinedMaxRank, final boolean useDoubleDeckers) {
		super(operand);
		mPredecessorOwasEmpty = predecessorOwasEmpty;
		mUserDefinedMaxRank = userDefinedMaxRank;
		mUseDoubleDeckers = useDoubleDeckers;
	}

	/**
	 * Constructor for the constraint that is only satisfied by the non accepting sink state.
	 */
	public LevelRankingConstraint() {
		super();
		mPredecessorOwasEmpty = false;
		mUserDefinedMaxRank = -1;
		mUseDoubleDeckers = true;
	}

	void internalSuccessorConstraints(final IFkvState<LETTER, STATE> state, final LETTER symbol) {
		for (final StateWithRankInfo<STATE> downState : state.getDownStates()) {
			for (final StateWithRankInfo<STATE> upState : state.getUpStates(downState)) {
				final Pair<Boolean, Integer> inOAndUpRank = getInOAndUpRankInternalCall(state, upState);
				for (final OutgoingInternalTransition<LETTER, STATE> trans : mOperand
						.internalSuccessors(upState.getState(), symbol)) {
					addConstraint(downState, trans.getSucc(), inOAndUpRank.getSecond(), inOAndUpRank.getFirst(),
							mOperand.isFinal(upState.getState()));
				}
			}
		}
	}

	void callSuccessorConstraints(final IFkvState<LETTER, STATE> state, final LETTER symbol) {
		for (final StateWithRankInfo<STATE> downState : state.getDownStates()) {
			for (final StateWithRankInfo<STATE> upState : state.getUpStates(downState)) {
				final Pair<Boolean, Integer> inOAndUpRank = getInOAndUpRankInternalCall(state, upState);
				for (final OutgoingCallTransition<LETTER, STATE> trans : mOperand.callSuccessors(upState.getState(),
						symbol)) {
					// if !mUseDoubleDeckers we always use getEmptyStackState()
					// as down state to obtain sets of states instead of
					// sets of DoubleDeckers.
					final StateWithRankInfo<STATE> succDownState =
							mUseDoubleDeckers ? upState : new StateWithRankInfo<>(mOperand.getEmptyStackState());
					addConstraint(succDownState, trans.getSucc(), inOAndUpRank.getSecond(), inOAndUpRank.getFirst(),
							mOperand.isFinal(upState.getState()));
				}
			}
		}
	}

	private Pair<Boolean, Integer> getInOAndUpRankInternalCall(final IFkvState<LETTER, STATE> state,
			final StateWithRankInfo<STATE> upState) {
		final Pair<Boolean, Integer> inOAndUpRank;
		if (state instanceof LevelRankingState) {
			assert mPredecessorOwasEmpty == ((LevelRankingState<LETTER, STATE>) state).isOempty();
			inOAndUpRank = new Pair<>(upState.isInO(), upState.getRank());
		} else {
			assert state instanceof FkvSubsetComponentState;
			inOAndUpRank = new Pair<>(Boolean.FALSE, mUserDefinedMaxRank);
		}
		return inOAndUpRank;
	}

	void returnSuccessorConstraints(final IFkvState<LETTER, STATE> state, final IFkvState<LETTER, STATE> hier,
			final LETTER symbol) {
		for (final StateWithRankInfo<STATE> hierDown : hier.getDownStates()) {
			for (final StateWithRankInfo<STATE> hierUp : hier.getUpStates(hierDown)) {
				returnSuccessorConstraintsHelper(state, symbol, hierDown, hierUp);
			}
		}
	}

	@SuppressWarnings("squid:S1698")
	private void returnSuccessorConstraintsHelper(final IFkvState<LETTER, STATE> state, final LETTER symbol,
			final StateWithRankInfo<STATE> hierDown, final StateWithRankInfo<STATE> hierUp) {
		if (state.getDownStates().isEmpty()) {
			return;
			//throw new AssertionError();
		}
		StateWithRankInfo<STATE> downState;
		if (mUseDoubleDeckers) {
			if (!state.getDownStates().contains(hierUp)) {
				return;
			}
			downState = hierUp;
		} else {
			assert state.getDownStates().size() == 1;
			// equality intended here
			assert state.getDownStates().iterator().next() == mOperand.getEmptyStackState();
			// if !mUseDoubleDeckers we always use getEmptyStackState()
			// as down state to obtain sets of states instead of
			// sets of DoubleDeckers.
			downState = new StateWithRankInfo<>(mOperand.getEmptyStackState());
		}
		final Iterable<StateWithRankInfo<STATE>> upStates = state.getUpStates(downState);
		addReturnSuccessorConstraintsGivenDownState(state, downState, upStates, hierDown, hierUp, symbol);
	}

	@SuppressWarnings("squid:S1698")
	private void addReturnSuccessorConstraintsGivenDownState(final IFkvState<LETTER, STATE> state,
			final StateWithRankInfo<STATE> downState, final Iterable<StateWithRankInfo<STATE>> upStates,
			final StateWithRankInfo<STATE> hierDown, final StateWithRankInfo<STATE> hierUp, final LETTER symbol) {
		for (final StateWithRankInfo<STATE> stateUp : upStates) {
			final boolean inO;
			final Integer upRank;
			if (state instanceof LevelRankingState) {
				assert mPredecessorOwasEmpty == ((LevelRankingState<LETTER, STATE>) state).isOempty();
				//TODO: obtain rank and inO directly from StateWithRankInfo
				final LevelRankingState<LETTER, STATE> lvlRkState = (LevelRankingState<LETTER, STATE>) state;
				inO = lvlRkState.inO(downState, stateUp.getState());
				upRank = lvlRkState.getRank(downState, stateUp.getState());
			} else {
				assert state instanceof FkvSubsetComponentState;
				inO = false;
				upRank = mUserDefinedMaxRank;
			}
			for (final OutgoingReturnTransition<LETTER, STATE> trans : mOperand.returnSuccessors(stateUp.getState(),
					hierUp.getState(), symbol)) {
				// equality intended here
				assert mUseDoubleDeckers || hierDown == mOperand.getEmptyStackState();
				addConstraint(hierDown, trans.getSucc(), upRank, inO, mOperand.isFinal(stateUp.getState()));
			}
		}
	}

	/**
	 * Add constraint to the double decker (down,up). This constraints are only obtained from incoming transitions.
	 * Further constraints (odd rank only allowed for non-finals or state in o if not odd) are added later.
	 */
	protected void addConstraint(final StateWithRankInfo<STATE> downState, final STATE upState,
			final Integer predecessorRank, final boolean predecessorIsInO, final boolean predecessorIsAccepting) {
		// This method is very similar to addRank(), but it does not
		// override a rank that was already set (instead takes the minimum)
		// and one assert is missing.
		assert predecessorRank != null;
		HashMap<STATE, Integer> up2rank = mLevelRanking.get(downState);
		if (up2rank == null) {
			up2rank = new HashMap<>();
			mLevelRanking.put(downState, up2rank);
		}
		final Integer oldRank = up2rank.get(upState);
		if (oldRank == null || oldRank > predecessorRank) {
			up2rank.put(upState, predecessorRank);
		}
		final boolean oCandidate = predecessorIsInO || mPredecessorOwasEmpty;
		if (oCandidate) {
			addToO(downState, upState);
		}
		if (mHighestRank < predecessorRank) {
			mHighestRank = predecessorRank;
		}
		if (predecessorIsAccepting) {
			mPredecessorWasAccepting
					.add(new DoubleDecker<>(downState, new StateWithRankInfo<>(upState, predecessorRank, oCandidate)));
		}
	}

	public Set<DoubleDecker<StateWithRankInfo<STATE>>> getPredecessorWasAccepting() {
		return mPredecessorWasAccepting;
	}
}
