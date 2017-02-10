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
import java.util.List;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryException;
import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.AutomataOperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.AutomatonDefinitionPrinter;
import de.uni_freiburg.informatik.ultimate.automata.ResultChecker;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomatonSimple;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.UnaryNwaOperation;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.reachablestates.NestedWordAutomatonReachableStates;
import de.uni_freiburg.informatik.ultimate.automata.statefactory.IBuchiComplementNcsbStateFactory;
import de.uni_freiburg.informatik.ultimate.automata.statefactory.IStateFactory;

/**
 * Buchi Complementation based on the algorithm proposed by Frantisek Blahoudek
 * and Jan Stejcek. This complementation is only sound for a special class of
 * automata whose working title is TABA (termination analysis Büchi automata).
 * 
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * @param <LETTER>
 *            letter type
 * @param <STATE>
 *            state type
 */
public final class BuchiComplementNCSB<LETTER, STATE> extends UnaryNwaOperation<LETTER, STATE> {
	private final INestedWordAutomatonSimple<LETTER, STATE> mOperand;
	private final NestedWordAutomatonReachableStates<LETTER, STATE> mResult;
	
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
	public <FACTORY extends IStateFactory<STATE> & IBuchiComplementNcsbStateFactory<STATE>> BuchiComplementNCSB(
			final AutomataLibraryServices services, final FACTORY stateFactory,
			final INestedWordAutomatonSimple<LETTER, STATE> operand) throws AutomataOperationCanceledException {
		super(services);
		mOperand = operand;
		
		if (mLogger.isInfoEnabled()) {
			mLogger.info(startMessage());
		}
		final BuchiComplementNCSBNwa<LETTER, STATE> complemented =
				new BuchiComplementNCSBNwa<>(mServices, stateFactory, operand);
		mResult = new NestedWordAutomatonReachableStates<>(mServices, complemented);
		if (mLogger.isInfoEnabled()) {
			mLogger.info(exitMessage());
		}
	}
	
	@Override
	public String operationName() {
		return "BuchiComplementNCBS";
	}
	
	@Override
	public String exitMessage() {
		return "Finished " + operationName() + ". Operand " + mOperand.sizeInformation() + " Result "
				+ mResult.sizeInformation();
	}
	
	@Override
	public boolean checkResult(final IStateFactory<STATE> stateFactory) throws AutomataLibraryException {
		if (mLogger.isInfoEnabled()) {
			mLogger.info("Start testing correctness of " + operationName());
		}
		
		final boolean underApproximationOfComplement = false;
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
		boolean correct = true;
		correct &= !(operandEmpty && resultEmpty);
		assert correct;
		/*
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, mResult.size()));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, mResult.size()));
		*/
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, 1));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, 1));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, 1));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, 1));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, 1));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, 1));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, 1));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, 1));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, 1));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, 1));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, 1));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, 2));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, 2));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, 2));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, 2));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, 2));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, 2));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, 2));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, 2));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, 2));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, 2));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, 2));
		lassoWords.addAll((new LassoExtractor<>(mServices, mOperand)).getResult());
		lassoWords.addAll((new LassoExtractor<>(mServices, mResult)).getResult());
		
		for (final NestedLassoWord<LETTER> nlw : lassoWords) {
			boolean thistime = checkAcceptance(nlw, mOperand, underApproximationOfComplement);
			if (!thistime) {
				thistime = checkAcceptance(nlw, mOperand, underApproximationOfComplement);
			}
			correct &= thistime;
			assert correct;
		}
		
		if (!correct) {
			AutomatonDefinitionPrinter.writeToFileIfPreferred(mServices, operationName() + "Failed",
					"language is different", mOperand, mResult);
		}
		if (mLogger.isInfoEnabled()) {
			mLogger.info("Finished testing correctness of " + operationName());
		}
		return correct;
	}
	
	private boolean checkAcceptance(final NestedLassoWord<LETTER> nlw,
			final INestedWordAutomatonSimple<LETTER, STATE> operand, final boolean underApproximationOfComplement)
			throws AutomataLibraryException {
		final boolean op = (new BuchiAccepts<>(mServices, operand, nlw)).getResult();
		final boolean res = (new BuchiAccepts<>(mServices, mResult, nlw)).getResult();
		boolean correct;
		if (underApproximationOfComplement) {
			correct = !res || op;
		} else {
			correct = op ^ res;
		}
		return correct;
	}
	
	@Override
	protected INestedWordAutomatonSimple<LETTER, STATE> getOperand() {
		return mOperand;
	}
	
	@Override
	public NestedWordAutomatonReachableStates<LETTER, STATE> getResult() {
		return mResult;
	}
}
