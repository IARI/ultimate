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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.AutomataOperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomatonSimple;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedWordAutomatonCache;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.OutgoingCallTransition;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.OutgoingInternalTransition;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.OutgoingReturnTransition;
import de.uni_freiburg.informatik.ultimate.automata.statefactory.IBuchiComplementNcsbStateFactory;
import de.uni_freiburg.informatik.ultimate.automata.statefactory.IStateFactory;

/**
 * Buchi Complementation based on the algorithm proposed by Frantisek Blahoudek and Jan Stejcek. This complementation is
 * only sound for a special class of automata whose working title is <i>TABA</i> (termination analysis Büchi automata).
 * 
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * @param <LETTER>
 *            letter type
 * @param <STATE>
 *            state type
 */
public final class BuchiComplementNCSBNwa<LETTER, STATE> implements INestedWordAutomatonSimple<LETTER, STATE> {
	private static final int MAGIC_RANK = 7777;
	private static final int BARELY_COVERED_MAX_RANK = 3;
	private static final Integer RANK_FINAL = Integer.valueOf(2);
	private static final Integer RANK_NONFINAL = Integer.valueOf(3);

	/**
	 * Heuristic where we move to accepting sink already from states with nonempty difference C\F. Warning: yet this is
	 * only implemented for internal transitions.
	 */
	private static final boolean EARLY_SINK_HEURISTIC = false;

	private final AutomataLibraryServices mServices;

	private final INestedWordAutomatonSimple<LETTER, STATE> mOperand;

	private final NestedWordAutomatonCache<LETTER, STATE> mCache;

	private final IBuchiComplementNcsbStateFactory<STATE> mStateFactory;

	private final StateWithRankInfo<STATE> mEmptyStackStateWri;

	/**
	 * Maps BlaStState to its representative in the resulting automaton.
	 */
	private final Map<LevelRankingState<LETTER, STATE>, STATE> mDet2res = new HashMap<>();

	/**
	 * Maps a state in resulting automaton to the BlaStState for which it was created.
	 */
	private final Map<STATE, LevelRankingState<LETTER, STATE>> mRes2det = new HashMap<>();

	private final BarelyCoveredLevelRankingsGenerator<LETTER, STATE> mBclrg;

	/**
	 * Constructor.
	 * 
	 * @param services
	 *            Ultimate services
	 * @param stateFactory
	 *            state factory
	 * @param operand
	 *            operand
	 * @throws AutomataOperationCanceledException
	 *             if operation was canceled
	 */
	public BuchiComplementNCSBNwa(final AutomataLibraryServices services,
			final IBuchiComplementNcsbStateFactory<STATE> stateFactory,
			final INestedWordAutomatonSimple<LETTER, STATE> operand) throws AutomataOperationCanceledException {
		mServices = services;
		mOperand = operand;
		mStateFactory = stateFactory;
		mCache = new NestedWordAutomatonCache<>(mServices, operand.getInternalAlphabet(), operand.getCallAlphabet(),
				operand.getReturnAlphabet(), mStateFactory);
		mEmptyStackStateWri = new StateWithRankInfo<>(getEmptyStackState());
		mBclrg = new BarelyCoveredLevelRankingsGenerator<>(mServices, mOperand, BARELY_COVERED_MAX_RANK, false, true,
				false, false, false);
		constructInitialState();
	}

	private void constructInitialState() {
		final LevelRankingState<LETTER, STATE> lvlrk = new LevelRankingState<>(mOperand);
		for (final STATE state : mOperand.getInitialStates()) {
			if (mOperand.isFinal(state)) {
				lvlrk.addRank(mEmptyStackStateWri, state, RANK_FINAL, true);
			} else {
				lvlrk.addRank(mEmptyStackStateWri, state, RANK_NONFINAL, false);
			}
		}
		getOrAdd(true, lvlrk);
	}

	/**
	 * Return state of result automaton that represents detState. If no such state was constructed yet, construct it.
	 */
	private STATE getOrAdd(final boolean isInitial, final LevelRankingState<LETTER, STATE> lvlrk) {
		STATE resState = mDet2res.get(lvlrk);
		if (resState == null) {
			resState = mStateFactory.buchiComplementNcsb(lvlrk);
			mDet2res.put(lvlrk, resState);
			mRes2det.put(resState, lvlrk);
			final boolean isFinal = !lvlrk.isNonAcceptingSink() && lvlrk.isOempty();
			mCache.addState(isInitial, isFinal, resState);
		} else {
			assert !isInitial;
		}
		return resState;
	}

	@Override
	public Iterable<STATE> getInitialStates() {
		return mCache.getInitialStates();
	}

	@Override
	public Set<LETTER> getInternalAlphabet() {
		return mOperand.getInternalAlphabet();
	}

	@Override
	public Set<LETTER> getCallAlphabet() {
		return mOperand.getCallAlphabet();
	}

	@Override
	public Set<LETTER> getReturnAlphabet() {
		return mOperand.getReturnAlphabet();
	}

	@Override
	public IStateFactory<STATE> getStateFactory() {
		return mStateFactory;
	}

	@Override
	public boolean isInitial(final STATE state) {
		return mCache.isInitial(state);
	}

	@Override
	public boolean isFinal(final STATE state) {
		return mCache.isFinal(state);
	}

	@Override
	public STATE getEmptyStackState() {
		return mCache.getEmptyStackState();
	}

	@Override
	public Set<LETTER> lettersInternal(final STATE state) {
		return mOperand.getInternalAlphabet();
	}

	@Override
	public Set<LETTER> lettersCall(final STATE state) {
		return mOperand.getCallAlphabet();
	}
	
	@Override
	public Set<LETTER> lettersReturn(final STATE state, final STATE hier) {
		return mOperand.getReturnAlphabet();
	}

	@Override
	public Set<LETTER> lettersReturn(final STATE state) {
		return mOperand.getReturnAlphabet();
	}

	private LevelRankingConstraintDrdCheck<LETTER, STATE> computeSuccLevelRankingConstraint_Internal(final STATE state,
			final LETTER letter) {
		final LevelRankingState<LETTER, STATE> lvlrkState = mRes2det.get(state);
		if (lvlrkState.isNonAcceptingSink()) {
			return new LevelRankingConstraintDrdCheck<>();
		}
		final LevelRankingConstraintDrdCheck<LETTER, STATE> constraint =
				new LevelRankingConstraintDrdCheck<>(mOperand, lvlrkState.isOempty(), MAGIC_RANK, true);
		boolean transitionWouldAnnihilateEvenRank = false;
		boolean somePredecessorHasRank1 = false;
		for (final StateWithRankInfo<STATE> downState : lvlrkState.getDownStates()) {
			for (final StateWithRankInfo<STATE> upState : lvlrkState.getUpStates(downState)) {
				if (upState.getRank() == 1) {
					somePredecessorHasRank1 = true;
				}
				boolean hasSuccessor = false;
				for (final OutgoingInternalTransition<LETTER, STATE> trans : mOperand
						.internalSuccessors(upState.getState(), letter)) {
					hasSuccessor = true;
					constraint.addConstraint(downState, trans.getSucc(), upState.getRank(), upState.isInO(),
							mOperand.isFinal(upState.getState()));
				}
				if (transitionWouldAnnihilateEvenRank(downState, upState, hasSuccessor)) {
					transitionWouldAnnihilateEvenRank = true;
				}
			}
		}
		if (returnEmptyLrConstraint(constraint, transitionWouldAnnihilateEvenRank, somePredecessorHasRank1)) {
			return new LevelRankingConstraintDrdCheck<>();
		}
		return constraint;
	}

	private boolean returnEmptyLrConstraint(final LevelRankingConstraintDrdCheck<LETTER, STATE> constraint,
			final boolean transitionWouldAnnihilateEvenRank, final boolean somePredecessorHasRank1) {
		if (EARLY_SINK_HEURISTIC) {
			if (transitionWouldAnnihilateEvenRank && !constraint.isEmpty()) {
				return true;
			}
			if (somePredecessorHasRank1 && constraint.isEmpty()) {
				return true;
			}
		} else {
			if (transitionWouldAnnihilateEvenRank) {
				return true;
			}
		}
		return false;
	}

	// TODO Christian 2016-09-07: 'downState' is not used, a bug?
	private boolean transitionWouldAnnihilateEvenRank(final StateWithRankInfo<STATE> downState,
			final StateWithRankInfo<STATE> upState, final boolean hasSuccessor) {
		return !hasSuccessor && !mOperand.isFinal(upState.getState()) && LevelRankingState.isEven(upState.getRank());
	}

	private LevelRankingConstraintDrdCheck<LETTER, STATE> computeSuccLevelRankingConstraint_Call(final STATE state,
			final LETTER letter) {
		final LevelRankingState<LETTER, STATE> lvlrkState = mRes2det.get(state);
		if (lvlrkState.isNonAcceptingSink()) {
			return new LevelRankingConstraintDrdCheck<>();
		}
		final LevelRankingConstraintDrdCheck<LETTER, STATE> constraint =
				new LevelRankingConstraintDrdCheck<>(mOperand, lvlrkState.isOempty(), MAGIC_RANK, true);
		for (final StateWithRankInfo<STATE> downState : lvlrkState.getDownStates()) {
			for (final StateWithRankInfo<STATE> upState : lvlrkState.getUpStates(downState)) {
				boolean hasSuccessor = false;
				for (final OutgoingCallTransition<LETTER, STATE> trans : mOperand.callSuccessors(upState.getState(),
						letter)) {
					hasSuccessor = true;
					constraint.addConstraint(upState, trans.getSucc(), upState.getRank(), upState.isInO(),
							mOperand.isFinal(upState.getState()));
				}
				if (transitionWouldAnnihilateEvenRank(downState, upState, hasSuccessor)) {
					return new LevelRankingConstraintDrdCheck<>();
				}
			}
		}
		return constraint;
	}

	private LevelRankingConstraintDrdCheck<LETTER, STATE> computeSuccLevelRankingConstraint_Return(final STATE state,
			final STATE hier, final LETTER letter) {
		final LevelRankingState<LETTER, STATE> lvlrkState = mRes2det.get(state);
		if (lvlrkState.isNonAcceptingSink()) {
			return new LevelRankingConstraintDrdCheck<>();
		}
		final LevelRankingState<LETTER, STATE> lvlrkHier = mRes2det.get(hier);
		final LevelRankingConstraintDrdCheck<LETTER, STATE> constraint =
				new LevelRankingConstraintDrdCheck<>(mOperand, lvlrkState.isOempty(), MAGIC_RANK, true);
		for (final StateWithRankInfo<STATE> downHier : lvlrkHier.getDownStates()) {
			for (final StateWithRankInfo<STATE> upHier : lvlrkHier.getUpStates(downHier)) {
				if (!lvlrkState.getDownStates().contains(upHier)) {
					continue;
				}
				final boolean transitionWouldAnnihilateEvenRank =
						computeSuccLevelRankingConstraintReturnHelper(letter, lvlrkState, constraint, downHier, upHier);
				if (transitionWouldAnnihilateEvenRank) {
					return new LevelRankingConstraintDrdCheck<>();
				}
			}
		}
		return constraint;
	}

	private boolean computeSuccLevelRankingConstraintReturnHelper(final LETTER letter,
			final LevelRankingState<LETTER, STATE> lvlrkState,
			final LevelRankingConstraintDrdCheck<LETTER, STATE> constraint, final StateWithRankInfo<STATE> downHier,
			final StateWithRankInfo<STATE> upHier) {
		for (final StateWithRankInfo<STATE> upState : lvlrkState.getUpStates(upHier)) {
			boolean hasSuccessor = false;
			for (final OutgoingReturnTransition<LETTER, STATE> trans : mOperand.returnSuccessors(upState.getState(),
					upHier.getState(), letter)) {
				hasSuccessor = true;
				constraint.addConstraint(downHier, trans.getSucc(), upState.getRank(), upState.isInO(),
						mOperand.isFinal(upState.getState()));
			}
			if (transitionWouldAnnihilateEvenRank(downHier, upState, hasSuccessor)) {
				return true;
			}
		}
		return false;
	}

	private Collection<STATE> computeStates(final LevelRankingConstraintDrdCheck<LETTER, STATE> constraint) {
		if (constraint.isTargetOfDelayedRankDecrease()) {
			// in this case we do not want to have successor states
			return Collections.emptyList();
		}
		final Collection<LevelRankingState<LETTER, STATE>> succLvls = mBclrg.generateLevelRankings(constraint, false);
		final List<STATE> computedSuccs = new ArrayList<>();
		for (final LevelRankingState<LETTER, STATE> succLvl : succLvls) {
			final STATE resSucc = getOrAdd(false, succLvl);
			computedSuccs.add(resSucc);
		}
		return computedSuccs;
	}

	@Override
	public Iterable<OutgoingInternalTransition<LETTER, STATE>> internalSuccessors(final STATE state,
			final LETTER letter) {
		final Collection<STATE> succs = mCache.succInternal(state, letter);
		if (succs == null) {
			final LevelRankingConstraintDrdCheck<LETTER, STATE> constraint =
					computeSuccLevelRankingConstraint_Internal(state, letter);
			final Collection<STATE> computedSuccs = computeStates(constraint);
			mCache.addInternalTransitions(state, letter, computedSuccs);
		}
		return mCache.internalSuccessors(state, letter);
	}

	@Override
	public Iterable<OutgoingInternalTransition<LETTER, STATE>> internalSuccessors(final STATE state) {
		for (final LETTER letter : getInternalAlphabet()) {
			internalSuccessors(state, letter);
		}
		return mCache.internalSuccessors(state);
	}

	@Override
	public Iterable<OutgoingCallTransition<LETTER, STATE>> callSuccessors(final STATE state, final LETTER letter) {
		final Collection<STATE> succs = mCache.succCall(state, letter);
		if (succs == null) {
			final LevelRankingConstraintDrdCheck<LETTER, STATE> constraint =
					computeSuccLevelRankingConstraint_Call(state, letter);
			final Collection<STATE> computedSuccs = computeStates(constraint);
			mCache.addCallTransitions(state, letter, computedSuccs);
		}
		return mCache.callSuccessors(state, letter);
	}

	@Override
	public Iterable<OutgoingCallTransition<LETTER, STATE>> callSuccessors(final STATE state) {
		for (final LETTER letter : getCallAlphabet()) {
			callSuccessors(state, letter);
		}
		return mCache.callSuccessors(state);
	}

	@Override
	public Iterable<OutgoingReturnTransition<LETTER, STATE>> returnSuccessors(final STATE state, final STATE hier,
			final LETTER letter) {
		final Collection<STATE> succs = mCache.succReturn(state, hier, letter);
		if (succs == null) {
			final LevelRankingConstraintDrdCheck<LETTER, STATE> constraint =
					computeSuccLevelRankingConstraint_Return(state, hier, letter);
			final Collection<STATE> computedSuccs = computeStates(constraint);
			mCache.addReturnTransitions(state, hier, letter, computedSuccs);
		}
		return mCache.returnSuccessors(state, hier, letter);
	}

	@Override
	public Iterable<OutgoingReturnTransition<LETTER, STATE>> returnSuccessorsGivenHier(final STATE state,
			final STATE hier) {
		for (final LETTER letter : getReturnAlphabet()) {
			returnSuccessors(state, hier, letter);
		}
		return mCache.returnSuccessorsGivenHier(state, hier);
	}

	@Override
	public int size() {
		return mCache.size();
	}

	@Override
	public Set<LETTER> getAlphabet() {
		return mOperand.getAlphabet();
	}

	@Override
	public String sizeInformation() {
		return "size Information not available";
	}
}
