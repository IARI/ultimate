/*
 * Copyright (C) 2015-2016 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2016 Christian Schilling (schillic@informatik.uni-freiburg.de)
 * Copyright (C) 2009-2016 University of Freiburg
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
import de.uni_freiburg.informatik.ultimate.automata.AutomatonDefinitionPrinter;
import de.uni_freiburg.informatik.ultimate.automata.ResultChecker;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.BinaryNwaOperation;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomatonSimple;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.reachablestates.NestedWordAutomatonReachableStates;
import de.uni_freiburg.informatik.ultimate.automata.statefactory.IBuchiIntersectStateFactory;
import de.uni_freiburg.informatik.ultimate.automata.statefactory.IEmptyStackStateFactory;
import de.uni_freiburg.informatik.ultimate.automata.statefactory.IStateFactory;

/**
 * Abstract superclass of Buchi difference operations.
 * 
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
 * @param <LETTER>
 *            letter type
 * @param <STATE>
 *            state type
 */
public abstract class AbstractBuchiDifference<LETTER, STATE> extends BinaryNwaOperation<LETTER, STATE> {
	protected final INestedWordAutomatonSimple<LETTER, STATE> mFstOperand;
	protected final INestedWordAutomatonSimple<LETTER, STATE> mSndOperand;
	protected BuchiIntersectNwa<LETTER, STATE> mIntersect;
	protected NestedWordAutomatonReachableStates<LETTER, STATE> mResult;
	
	/**
	 * Constructor.
	 * 
	 * @param services
	 *            Ultimate services
	 * @param stateFactory
	 *            state factory
	 * @param fstOperand
	 *            first operand
	 * @param sndOperand
	 *            second operand
	 */
	public AbstractBuchiDifference(final AutomataLibraryServices services,
			final INestedWordAutomatonSimple<LETTER, STATE> fstOperand,
			final INestedWordAutomatonSimple<LETTER, STATE> sndOperand) {
		super(services);
		mFstOperand = fstOperand;
		mSndOperand = sndOperand;
	}
	
	/**
	 * @return The second operand complemented.
	 */
	public abstract INestedWordAutomatonSimple<LETTER, STATE> getSndComplemented();
	
	/**
	 * Constructs the difference using the complement of the second operand.
	 * 
	 * @throws AutomataLibraryException
	 *             if construction fails
	 */
	protected <FACTORY extends IBuchiIntersectStateFactory<STATE> & IEmptyStackStateFactory<STATE>> void
			constructDifferenceFromComplement(final FACTORY stateFactory) throws AutomataLibraryException {
		mIntersect = new BuchiIntersectNwa<>(mFstOperand, getSndComplemented(), stateFactory);
		mResult = new NestedWordAutomatonReachableStates<>(mServices, mIntersect);
	}
	
	@Override
	public String exitMessage() {
		return "Finished " + operationName() + ". First operand " + mFstOperand.sizeInformation() + ". Second operand "
				+ mSndOperand.sizeInformation() + " Result " + mResult.sizeInformation() + " Complement of second has "
				+ getSndComplemented().size() + " states.";
	}
	
	@Override
	protected INestedWordAutomatonSimple<LETTER, STATE> getFirstOperand() {
		return mFstOperand;
	}
	
	@Override
	protected INestedWordAutomatonSimple<LETTER, STATE> getSecondOperand() {
		return mSndOperand;
	}
	
	@Override
	public boolean checkResult(final IStateFactory<STATE> stateFactory) throws AutomataLibraryException {
		final boolean underApproximationOfComplement = false;
		if (mLogger.isInfoEnabled()) {
			mLogger.info("Start testing correctness of " + operationName());
		}
		
		final List<NestedLassoWord<LETTER>> lassoWords = new ArrayList<>();
		final BuchiIsEmpty<LETTER, STATE> fstOperandEmptiness = new BuchiIsEmpty<>(mServices, mFstOperand);
		final boolean fstOperandEmpty = fstOperandEmptiness.getResult();
		if (!fstOperandEmpty) {
			lassoWords.add(fstOperandEmptiness.getAcceptingNestedLassoRun().getNestedLassoWord());
		}
		final BuchiIsEmpty<LETTER, STATE> sndOperandEmptiness = new BuchiIsEmpty<>(mServices, mSndOperand);
		final boolean sndOperandEmpty = sndOperandEmptiness.getResult();
		if (!sndOperandEmpty) {
			lassoWords.add(sndOperandEmptiness.getAcceptingNestedLassoRun().getNestedLassoWord());
		}
		final BuchiIsEmpty<LETTER, STATE> resultEmptiness = new BuchiIsEmpty<>(mServices, mResult);
		final boolean resultEmpty = resultEmptiness.getResult();
		if (!resultEmpty) {
			lassoWords.add(resultEmptiness.getAcceptingNestedLassoRun().getNestedLassoWord());
		}
		boolean correct = true;
		correct &= (!fstOperandEmpty || resultEmpty);
		assert correct;
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, mResult.size()));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, mFstOperand.size()));
		lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, mSndOperand.size()));
		lassoWords.addAll((new LassoExtractor<>(mServices, mFstOperand)).getResult());
		lassoWords.addAll((new LassoExtractor<>(mServices, mSndOperand)).getResult());
		lassoWords.addAll((new LassoExtractor<>(mServices, mResult)).getResult());
		
		for (final NestedLassoWord<LETTER> nlw : lassoWords) {
			correct &= checkAcceptance(nlw, mFstOperand, mSndOperand, underApproximationOfComplement);
			assert correct;
		}
		if (!correct) {
			AutomatonDefinitionPrinter.writeToFileIfPreferred(mServices, operationName() + "Failed",
					"language is different", mFstOperand, mSndOperand);
		}
		if (mLogger.isInfoEnabled()) {
			mLogger.info("Finished testing correctness of " + operationName());
		}
		return correct;
	}
	
	private boolean checkAcceptance(final NestedLassoWord<LETTER> nlw,
			final INestedWordAutomatonSimple<LETTER, STATE> operand1,
			final INestedWordAutomatonSimple<LETTER, STATE> operand2, final boolean underApproximationOfComplement)
			throws AutomataLibraryException {
		boolean correct;
		final boolean op1 = (new BuchiAccepts<>(mServices, operand1, nlw)).getResult();
		final boolean op2 = (new BuchiAccepts<>(mServices, operand2, nlw)).getResult();
		final boolean res = (new BuchiAccepts<>(mServices, mResult, nlw)).getResult();
		if (res) {
			correct = op1 && !op2;
		} else {
			correct = !(!underApproximationOfComplement && op1 && !op2);
		}
		assert correct : operationName() + " wrong result!";
		return correct;
	}
}
