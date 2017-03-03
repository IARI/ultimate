/*
 * Copyright (C) 2017 Christian Schilling (schillic@informatik.uni-freiburg.de)
 * Copyright (C) 2017 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2017 University of Freiburg
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

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryException;
import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.AutomataOperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.AutomataOperationStatistics;
import de.uni_freiburg.informatik.ultimate.automata.AutomatonDefinitionPrinter;
import de.uni_freiburg.informatik.ultimate.automata.StatisticsType;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.DoubleDeckerAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.IDoubleDeckerAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomatonSimple;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.UnaryNwaOperation;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.oldapi.ReachableStatesCopy;
import de.uni_freiburg.informatik.ultimate.automata.statefactory.IStateFactory;

/**
 * This is a common superclass for operations that remove states and transitions.
 * 
 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * @param <LETTER>
 *            letter type
 * @param <STATE>
 *            state type
 */
public abstract class StateRemoval<LETTER, STATE> extends UnaryNwaOperation<LETTER, STATE, IStateFactory<STATE>> {
	protected final INestedWordAutomatonSimple<LETTER, STATE> mOperand;

	/**
	 * @param services
	 *            Ultimate services.
	 * @param operand
	 *            operand
	 * @throws AutomataOperationCanceledException
	 *             if timeout exceeds
	 */
	public StateRemoval(final AutomataLibraryServices services, final INestedWordAutomatonSimple<LETTER, STATE> operand)
			throws AutomataOperationCanceledException {
		super(services);
		mOperand = operand;

		printStartMessage();
	}

	@Override
	protected INestedWordAutomatonSimple<LETTER, STATE> getOperand() {
		return mOperand;
	}

	@Override
	public abstract IDoubleDeckerAutomaton<LETTER, STATE> getResult();

	@Override
	public String exitMessage() {
		return "Finished " + operationName() + ". Reduced from " + mOperand.size() + " states to "
				+ getResult().sizeInformation();
	}

	@Override
	public AutomataOperationStatistics getAutomataOperationStatistics() {
		final AutomataOperationStatistics result = new AutomataOperationStatistics();

		final int inputSize = getOperand().size();
		final int outputSize = getResult().size();

		result.addKeyValuePair(StatisticsType.STATES_INPUT, inputSize);
		result.addKeyValuePair(StatisticsType.STATES_OUTPUT, outputSize);
		result.addDifferenceData(StatisticsType.STATES_INPUT, StatisticsType.STATES_OUTPUT,
				StatisticsType.STATES_REDUCTION_ABSOLUTE);
		result.addPercentageDataInverted(StatisticsType.STATES_INPUT, StatisticsType.STATES_OUTPUT,
				StatisticsType.STATES_REDUCTION_RELATIVE);
		return result;
	}

	@Override
	@SuppressWarnings("squid:S2583") // false-positives
	public boolean checkResult(final IStateFactory<STATE> stateFactory) throws AutomataLibraryException {
		printStartCheckMessage();
		boolean correct;

		// create a ReachableStatesCopy
		final ReachableStatesCopy<LETTER, STATE> rsc =
				new ReachableStatesCopy<>(mServices, mOperand, false, false, false, false);
		checkResultModifyReachableStatesCopy(rsc);

		// check that all ReachableStatesCopy states are also present in the result
		final IDoubleDeckerAutomaton<LETTER, STATE> result = getResult();
		final IDoubleDeckerAutomaton<LETTER, STATE> reachableStatesCopy = rsc.getResult();
		correct = reachableStatesCopy.getStates().containsAll(result.getStates());
		assert correct : operationName() + " incorrect: too few states";

		correct = correct && checkEachState((DoubleDeckerAutomaton<LETTER, STATE>) reachableStatesCopy);
		assert correct : operationName() + " incorrect: checkEachState failed";

		correct = correct && checkResultFurther(reachableStatesCopy);
		assert correct : operationName() + " incorrect: further tests failed";

		if (!correct) {
			AutomatonDefinitionPrinter.writeToFileIfPreferred(mServices, operationName() + "Failed",
					"language is different", mOperand);
		}

		printExitCheckMessage();
		return correct;
	}

	/**
	 * @param rsc
	 *            {@link ReachableStatesCopy} to be modified.
	 * @throws AutomataOperationCanceledException
	 *             if operation was canceled
	 */
	protected abstract void checkResultModifyReachableStatesCopy(ReachableStatesCopy<LETTER, STATE> rsc)
			throws AutomataOperationCanceledException;

	protected abstract boolean checkResultFurther(IDoubleDeckerAutomaton<LETTER, STATE> reachableStatesCopy)
			throws AutomataLibraryException;

	// TODO Christian 2017-03-01 outsource common code from subclasses
	protected abstract boolean checkEachState(DoubleDeckerAutomaton<LETTER, STATE> reachableStatesCopy);
}
