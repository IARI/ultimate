/*
 * Copyright (C) 2013-2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
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
package de.uni_freiburg.informatik.ultimate.automata.nestedword.operations;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryException;
import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.AutomataOperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.ResultChecker;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.DoubleDeckerAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.IDoubleDeckerAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomatonSimple;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedWordAutomatonFilteredStates;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.buchi.BuchiAccepts;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.buchi.BuchiIsEmpty;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.buchi.NestedLassoWord;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.oldapi.ReachableStatesCopy;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.reachablestates.NestedWordAutomatonReachableStates;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.OutgoingCallTransition;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.OutgoingInternalTransition;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.OutgoingReturnTransition;

/**
 * Creates a nested word automaton where non-live states have been removed.
 * 
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * @param <LETTER>
 *            letter type
 * @param <STATE>
 *            state type
 */
public final class RemoveNonLiveStates<LETTER, STATE> extends StateRemoval<LETTER, STATE> {
	private final NestedWordAutomatonReachableStates<LETTER, STATE> mReach;
	private final IDoubleDeckerAutomaton<LETTER, STATE> mResult;

	/**
	 * Given an INestedWordAutomaton nwa return a nested word automaton that has
	 * the same states, but all states that are not reachable or dead ends are
	 * omitted. (A dead end is a state from which no accepting state can be
	 * reached).
	 * Each state of the result also occurred in the input. Only the auxiliary
	 * empty stack state of the result is different.
	 * 
	 * @param services
	 *            Ultimate services
	 * @param operand
	 *            operand
	 * @throws AutomataOperationCanceledException
	 *             if timeout exceeds
	 */
	public RemoveNonLiveStates(final AutomataLibraryServices services,
			final INestedWordAutomatonSimple<LETTER, STATE> operand) throws AutomataOperationCanceledException {
		super(services, operand);

		mReach = new NestedWordAutomatonReachableStates<>(mServices, mOperand);
		mReach.computeNonLiveStates();
		final NestedWordAutomatonFilteredStates<LETTER, STATE> filtered =
				new NestedWordAutomatonFilteredStates<>(mServices, mReach, mReach.getOnlyLiveStates());
		mResult = new NestedWordAutomatonReachableStates<>(mServices, filtered);

		printExitMessage();
		// assert (new TransitionConsitenceCheck<LETTER, STATE>(mResult)).consistentForAll();
	}

	@Override
	public String operationName() {
		return "RemoveNonLiveStates";
	}

	@Override
	public IDoubleDeckerAutomaton<LETTER, STATE> getResult() {
		return mResult;
	}

	/**
	 * @return Size of the input automaton. If input was an automaton for on-demand construction, this is the size after
	 *         the on-demand construction.
	 */
	public int getInputSize() {
		return mReach.size();
	}

	@Override
	protected void modifyReachableStatesCopyForCheckResult(final ReachableStatesCopy<LETTER, STATE> rsc)
			throws AutomataOperationCanceledException {
		rsc.removeNonLiveStates();
	}

	@Override
	protected boolean checkResultFurther(final IDoubleDeckerAutomaton<LETTER, STATE> reachableStatesCopy)
			throws AutomataLibraryException {
		boolean correct = true;

		final List<NestedLassoWord<LETTER>> lassoWords = new ArrayList<>();
		final BuchiIsEmpty<LETTER, STATE> operandEmptiness = new BuchiIsEmpty<>(mServices, mOperand);
		final boolean operandEmpty = operandEmptiness.getResult();
		if (!operandEmpty) {
			lassoWords.add(operandEmptiness.getAcceptingNestedLassoRun().getNestedLassoWord());
		}
		final BuchiIsEmpty<LETTER, STATE> resultEmptiness = new BuchiIsEmpty<>(mServices, mResult);
		final boolean resultEmpty = resultEmptiness.getResult();
		if (!resultEmpty) {
			lassoWords.add(resultEmptiness.getAcceptingNestedLassoRun().getNestedLassoWord());
		}
		correct = correct && (operandEmpty == resultEmpty);
		assert correct;
		final int numberLassoWords = 10;
		for (int i = 0; i < numberLassoWords; i++) {
			lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, mResult.size()));
			lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, mOperand.size()));
		}

		for (final NestedLassoWord<LETTER> nlw : lassoWords) {
			correct = correct && checkAcceptance(nlw, mOperand);
			assert correct;
		}

		return correct;
	}

	@Override
	protected boolean checkEachState(final DoubleDeckerAutomaton<LETTER, STATE> reachableStatesCopy) {
		boolean correct = true;
		final NestedWordAutomatonReachableStates<LETTER, STATE> reach = mReach;
		final IDoubleDeckerAutomaton<LETTER, STATE> result = mResult;
		for (final STATE state : result.getStates()) {
			for (final OutgoingInternalTransition<LETTER, STATE> outTrans : reachableStatesCopy
					.internalSuccessors(state)) {
				correct = correct && reach.containsInternalTransition(state, outTrans.getLetter(), outTrans.getSucc());
				assert correct;
			}
			for (final OutgoingCallTransition<LETTER, STATE> outTrans : reachableStatesCopy.callSuccessors(state)) {
				correct = correct && reach.containsCallTransition(state, outTrans.getLetter(), outTrans.getSucc());
				assert correct;
			}
			for (final OutgoingReturnTransition<LETTER, STATE> outTrans : reachableStatesCopy.returnSuccessors(state)) {
				correct = correct && reach.containsReturnTransition(state, outTrans.getHierPred(), outTrans.getLetter(),
						outTrans.getSucc());
				assert correct;
			}
			for (final OutgoingInternalTransition<LETTER, STATE> outTrans : result.internalSuccessors(state)) {
				correct = correct && reachableStatesCopy.containsInternalTransition(state, outTrans.getLetter(),
						outTrans.getSucc());
				assert correct;
			}
			for (final OutgoingCallTransition<LETTER, STATE> outTrans : result.callSuccessors(state)) {
				correct = correct
						&& reachableStatesCopy.containsCallTransition(state, outTrans.getLetter(), outTrans.getSucc());
				assert correct;
			}
			for (final OutgoingReturnTransition<LETTER, STATE> outTrans : result.returnSuccessors(state)) {
				correct = correct && reachableStatesCopy.containsReturnTransition(state, outTrans.getHierPred(),
						outTrans.getLetter(), outTrans.getSucc());
				assert correct;
			}
			final Set<STATE> rCSdownStates = reachableStatesCopy.getDownStates(state);
			final Set<STATE> rCAdownStates = reach.getOnlyLiveStates().getDownStates(state);
			correct = correct && rCSdownStates.containsAll(rCAdownStates);
			assert correct;
			// After enhanced non-live/dead end removal the following does not hold.
			// correct = correct && ResultChecker.isSubset(rCSdownStates, rCAdownStates);
			assert correct;
		}
		return correct;
	}

	private boolean checkAcceptance(final NestedLassoWord<LETTER> nlw,
			final INestedWordAutomatonSimple<LETTER, STATE> operand) throws AutomataLibraryException {
		final boolean op = (new BuchiAccepts<>(mServices, operand, nlw)).getResult();
		final boolean res = (new BuchiAccepts<>(mServices, mResult, nlw)).getResult();
		final boolean correct = op == res;
		assert correct : operationName() + " wrong result!";
		return correct;
	}
}
