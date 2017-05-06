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
package de.uni_freiburg.informatik.ultimate.automata.nestedword;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.AutomataOperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.AutomatonDefinitionPrinter;
import de.uni_freiburg.informatik.ultimate.automata.AutomatonDefinitionPrinter.Format;
import de.uni_freiburg.informatik.ultimate.automata.LibraryIdentifiers;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.reachablestates.NestedWordAutomatonReachableStates;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.IncomingCallTransition;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.IncomingInternalTransition;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.IncomingReturnTransition;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.OutgoingCallTransition;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.OutgoingInternalTransition;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.OutgoingReturnTransition;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.StateBasedTransitionFilterPredicateProvider;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.SummaryReturnTransition;
import de.uni_freiburg.informatik.ultimate.automata.statefactory.IStateFactory;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.util.datastructures.FilteredIterable;

/**
 * undocumented!
 * <p>
 * NOTE: The interface INestedWordAutomatonOldApi can be removed when also removing the respective overridden methods.
 *
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * @param <LETTER>
 *            letter type
 * @param <STATE>
 *            state type
 */
public class NestedWordAutomatonFilteredStates<LETTER, STATE> implements INestedWordAutomaton<LETTER, STATE> {
	protected final AutomataLibraryServices mServices;
	protected final ILogger mLogger;
	protected final NestedWordAutomatonReachableStates<LETTER, STATE> mNwa;
	protected final Set<STATE> mRemainingStates;
	protected final Set<STATE> mNewInitials;
	protected final Set<STATE> mNewFinals;
	protected final NestedWordAutomatonReachableStates<LETTER, STATE>.AncestorComputation mAncestorComputation;
	protected final boolean mFilterCallTransitionsBasedOnDoubleDeckerInformation;
	private final StateBasedTransitionFilterPredicateProvider<LETTER, STATE> mTransitionFilter;

	/**
	 * Constructor without ancestor computation.
	 *
	 * @param services
	 *            Ultimate services
	 * @param automaton
	 *            automaton
	 * @param remainingStates
	 *            remaining states
	 * @param newInitials
	 *            new initial states
	 * @param newFinals
	 *            new final states
	 */
	public NestedWordAutomatonFilteredStates(final AutomataLibraryServices services,
			final NestedWordAutomatonReachableStates<LETTER, STATE> automaton, final Set<STATE> remainingStates,
			final Set<STATE> newInitials, final Set<STATE> newFinals) {
		this(services, automaton, remainingStates, newInitials, newFinals, null);
	}

	/**
	 * Constructor with ancestor computation.
	 *
	 * @param services
	 *            Ultimate services
	 * @param automaton
	 *            automaton
	 * @param ancestorComputation
	 *            ancestor computation object
	 */
	public NestedWordAutomatonFilteredStates(final AutomataLibraryServices services,
			final NestedWordAutomatonReachableStates<LETTER, STATE> automaton,
			final NestedWordAutomatonReachableStates<LETTER, STATE>.AncestorComputation ancestorComputation) {
		this(services, automaton, ancestorComputation.getStates(), ancestorComputation.getInitials(),
				ancestorComputation.getFinals(), ancestorComputation);
	}

	/**
	 * Private full constructor.
	 *
	 * @param services
	 *            Ultimate services
	 * @param automaton
	 *            automaton
	 * @param remainingStates
	 *            remaining states
	 * @param newInitials
	 *            new initial states
	 * @param newFinals
	 *            new final states
	 * @param ancestorComputation
	 *            ancestor computation object
	 * @throws AutomataOperationCanceledException
	 *             if operation is canceled
	 */
	private NestedWordAutomatonFilteredStates(final AutomataLibraryServices services,
			final NestedWordAutomatonReachableStates<LETTER, STATE> automaton, final Set<STATE> remainingStates,
			final Set<STATE> initials, final Set<STATE> finals,
			final NestedWordAutomatonReachableStates<LETTER, STATE>.AncestorComputation ancestorComputation) {
		mServices = services;
		mLogger = mServices.getLoggingService().getLogger(LibraryIdentifiers.PLUGIN_ID);
		mNwa = automaton;
		mRemainingStates = remainingStates;
		mNewInitials = initials;
		mNewFinals = finals;
		mAncestorComputation = ancestorComputation;
		mFilterCallTransitionsBasedOnDoubleDeckerInformation = (ancestorComputation != null);
		mTransitionFilter = new StateBasedTransitionFilterPredicateProvider<>(mRemainingStates);
	}

	@Override
	public int size() {
		return getStates().size();
	}

	@Override
	public Set<LETTER> getAlphabet() {
		return mNwa.getAlphabet();
	}

	@Override
	public String sizeInformation() {
		return mRemainingStates.size() + " states.";
	}

	@Override
	public Set<LETTER> getInternalAlphabet() {
		return mNwa.getInternalAlphabet();
	}

	@Override
	public Set<LETTER> getCallAlphabet() {
		return mNwa.getCallAlphabet();
	}

	@Override
	public Set<LETTER> getReturnAlphabet() {
		return mNwa.getReturnAlphabet();
	}

	@Override
	public IStateFactory<STATE> getStateFactory() {
		return mNwa.getStateFactory();
	}

	@Override
	public Set<STATE> getStates() {
		return mRemainingStates;
	}

	@Override
	public Set<STATE> getInitialStates() {
		return mNewInitials;
	}

	@Override
	public Collection<STATE> getFinalStates() {
		return mNewFinals;
	}

	@Override
	public boolean isInitial(final STATE state) {
		return mNewInitials.contains(state);
	}

	@Override
	public boolean isFinal(final STATE state) {
		return mNewFinals.contains(state);
	}

	@Override
	public STATE getEmptyStackState() {
		return mNwa.getEmptyStackState();
	}

	@Override
	public Set<LETTER> lettersInternal(final STATE state) {
		final Set<LETTER> letters = new HashSet<>();
		for (final OutgoingInternalTransition<LETTER, STATE> outTrans : internalSuccessors(state)) {
			letters.add(outTrans.getLetter());
		}
		return letters;
	}

	@Override
	public Set<LETTER> lettersCall(final STATE state) {
		final Set<LETTER> letters = new HashSet<>();
		for (final OutgoingCallTransition<LETTER, STATE> outTrans : callSuccessors(state)) {
			letters.add(outTrans.getLetter());
		}
		return letters;
	}
	
	@Override
	public Set<LETTER> lettersReturn(final STATE state, final STATE hier) {
		final Set<LETTER> letters = new HashSet<>();
		for (final OutgoingReturnTransition<LETTER, STATE> outTrans : returnSuccessorsGivenHier(state, hier)) {
			letters.add(outTrans.getLetter());
		}
		return letters;
	}

	@Override
	public Set<LETTER> lettersReturn(final STATE state) {
		final Set<LETTER> letters = new HashSet<>();
		for (final OutgoingReturnTransition<LETTER, STATE> outTrans : returnSuccessors(state)) {
			letters.add(outTrans.getLetter());
		}
		return letters;
	}

	@Override
	public Set<LETTER> lettersInternalIncoming(final STATE state) {
		final Set<LETTER> letters = new HashSet<>();
		for (final IncomingInternalTransition<LETTER, STATE> outTrans : internalPredecessors(state)) {
			letters.add(outTrans.getLetter());
		}
		return letters;
	}

	@Override
	public Set<LETTER> lettersCallIncoming(final STATE state) {
		final Set<LETTER> letters = new HashSet<>();
		for (final IncomingCallTransition<LETTER, STATE> outTrans : callPredecessors(state)) {
			letters.add(outTrans.getLetter());
		}
		return letters;
	}

	@Override
	public Set<LETTER> lettersReturnIncoming(final STATE state) {
		final Set<LETTER> letters = new HashSet<>();
		for (final IncomingReturnTransition<LETTER, STATE> outTrans : returnPredecessors(state)) {
			letters.add(outTrans.getLetter());
		}
		return letters;
	}

	@Override
	public Set<LETTER> lettersSummary(final STATE state) {
		final Set<LETTER> letters = new HashSet<>();
		for (final SummaryReturnTransition<LETTER, STATE> outTrans : summarySuccessors(state)) {
			letters.add(outTrans.getLetter());
		}
		return letters;
	}

//	private Iterable<STATE> succInternal(final STATE state, final LETTER letter) {
//		return new FilteredIterable<>(mNwa.succInternal(state, letter), mRemainingStates::contains);
//	}

	private Iterable<STATE> succCall(final STATE state, final LETTER letter) {
		final Set<STATE> result = new HashSet<>();
		for (final OutgoingCallTransition<LETTER, STATE> outTrans : callSuccessors(state, letter)) {
			result.add(outTrans.getSucc());
		}
		return result;
	}

	private Iterable<STATE> succReturn(final STATE state, final STATE hier, final LETTER letter) {
		final Set<STATE> result = new HashSet<>();
		for (final OutgoingReturnTransition<LETTER, STATE> outTrans : returnSuccessors(state, hier, letter)) {
			if (mRemainingStates.contains(outTrans.getHierPred()) && mRemainingStates.contains(outTrans.getSucc())) {
				result.add(outTrans.getSucc());
			}
		}
		return result;
	}

//	private Iterable<STATE> predInternal(final STATE state, final LETTER letter) {
//		return new FilteredIterable<>(mNwa.predInternal(state, letter), mRemainingStates::contains);
//	}

	private Iterable<STATE> predCall(final STATE state, final LETTER letter) {
		final Set<STATE> result = new HashSet<>();
		for (final IncomingCallTransition<LETTER, STATE> inTrans : callPredecessors(state, letter)) {
			result.add(inTrans.getPred());
		}
		return result;
	}

	@Override
	public Iterable<IncomingInternalTransition<LETTER, STATE>> internalPredecessors(final STATE succ,
			final LETTER letter) {
		final Predicate<IncomingInternalTransition<LETTER, STATE>> predicate =
				mTransitionFilter.getInternalPredecessorsPredicate();
		return new FilteredIterable<>(mNwa.internalPredecessors(succ, letter), predicate);
	}

	@Override
	public Iterable<IncomingInternalTransition<LETTER, STATE>> internalPredecessors(final STATE succ) {
		final Predicate<IncomingInternalTransition<LETTER, STATE>> predicate =
				mTransitionFilter.getInternalPredecessorsPredicate();
		return new FilteredIterable<>(mNwa.internalPredecessors(succ), predicate);
	}

	@Override
	public Iterable<IncomingCallTransition<LETTER, STATE>> callPredecessors(final STATE succ, final LETTER letter) {
		// filter out also transitions that are not contained any more
		// because (succ, trans.getPred()) is not a DoubleDecker of the
		// resulting automaton
		final Predicate<IncomingCallTransition<LETTER, STATE>> predicate =
				trans -> mRemainingStates.contains(trans.getPred()) && isDoubleDecker(succ, trans.getPred());
		return new FilteredIterable<>(mNwa.callPredecessors(succ, letter), predicate);
	}

	@Override
	public Iterable<IncomingCallTransition<LETTER, STATE>> callPredecessors(final STATE succ) {
		// filter out also transitions that are not contained any more
		// because (succ, trans.getPred()) is not a DoubleDecker of the
		// resulting automaton
		final Predicate<IncomingCallTransition<LETTER, STATE>> predicate =
				trans -> mRemainingStates.contains(trans.getPred()) && isDoubleDecker(succ, trans.getPred());
		return new FilteredIterable<>(mNwa.callPredecessors(succ), predicate);
	}

	@Override
	public Iterable<OutgoingInternalTransition<LETTER, STATE>> internalSuccessors(final STATE state,
			final LETTER letter) {
		final Predicate<OutgoingInternalTransition<LETTER, STATE>> predicate =
				mTransitionFilter.getInternalSuccessorPredicate();
		return new FilteredIterable<>(mNwa.internalSuccessors(state, letter), predicate);
	}

	@Override
	public Iterable<OutgoingInternalTransition<LETTER, STATE>> internalSuccessors(final STATE state) {
		final Predicate<OutgoingInternalTransition<LETTER, STATE>> predicate =
				mTransitionFilter.getInternalSuccessorPredicate();
		return new FilteredIterable<>(mNwa.internalSuccessors(state), predicate);
	}

	@Override
	public Iterable<OutgoingCallTransition<LETTER, STATE>> callSuccessors(final STATE state, final LETTER letter) {
		// filter out also transitions that are not contained any more
		// because (trans.getSucc(), state) is not a DoubleDecker of the
		// resulting automaton
		final Predicate<OutgoingCallTransition<LETTER, STATE>> predicate = trans -> mRemainingStates
				.contains(trans.getSucc())
				&& (!mFilterCallTransitionsBasedOnDoubleDeckerInformation || isDoubleDecker(trans.getSucc(), state));
		return new FilteredIterable<>(mNwa.callSuccessors(state, letter), predicate);
	}

	@Override
	public Iterable<OutgoingCallTransition<LETTER, STATE>> callSuccessors(final STATE state) {
		// filter out also transitions that are not contained any more
		// because (trans.getSucc(), state) is not a DoubleDecker of the
		// resulting automaton
		final Predicate<OutgoingCallTransition<LETTER, STATE>> predicate = trans -> mRemainingStates
				.contains(trans.getSucc())
				&& (!mFilterCallTransitionsBasedOnDoubleDeckerInformation || isDoubleDecker(trans.getSucc(), state));
		return new FilteredIterable<>(mNwa.callSuccessors(state), predicate);
	}

	@Override
	public Iterable<IncomingReturnTransition<LETTER, STATE>> returnPredecessors(final STATE succ, final STATE hier,
			final LETTER letter) {
		final Predicate<IncomingReturnTransition<LETTER, STATE>> predicate =
				mTransitionFilter.getReturnPredecessorPredicate();
		return new FilteredIterable<>(mNwa.returnPredecessors(succ, hier, letter), predicate);
	}

	@Override
	public Iterable<IncomingReturnTransition<LETTER, STATE>> returnPredecessors(final STATE succ, final LETTER letter) {
		final Predicate<IncomingReturnTransition<LETTER, STATE>> predicate =
				mTransitionFilter.getReturnPredecessorPredicate();
		return new FilteredIterable<>(mNwa.returnPredecessors(succ, letter), predicate);
	}

	@Override
	public Iterable<IncomingReturnTransition<LETTER, STATE>> returnPredecessors(final STATE succ) {
		final Predicate<IncomingReturnTransition<LETTER, STATE>> predicate =
				mTransitionFilter.getReturnPredecessorPredicate();
		return new FilteredIterable<>(mNwa.returnPredecessors(succ), predicate);
	}

	@Override
	public Iterable<OutgoingReturnTransition<LETTER, STATE>> returnSuccessors(final STATE state, final STATE hier,
			final LETTER letter) {
		final Predicate<OutgoingReturnTransition<LETTER, STATE>> predicate =
				mTransitionFilter.getReturnSuccessorPredicate();
		return new FilteredIterable<>(mNwa.returnSuccessors(state, hier, letter), predicate);
	}

	@Override
	public Iterable<OutgoingReturnTransition<LETTER, STATE>> returnSuccessors(final STATE state) {
		final Predicate<OutgoingReturnTransition<LETTER, STATE>> predicate =
				mTransitionFilter.getReturnSuccessorPredicate();
		return new FilteredIterable<>(mNwa.returnSuccessors(state), predicate);
	}

	@Override
	public Iterable<OutgoingReturnTransition<LETTER, STATE>> returnSuccessorsGivenHier(final STATE state,
			final STATE hier) {
		final Predicate<OutgoingReturnTransition<LETTER, STATE>> predicate =
				mTransitionFilter.getReturnSuccessorPredicate();
		return new FilteredIterable<>(mNwa.returnSuccessorsGivenHier(state, hier), predicate);
	}

	@Override
	public Iterable<SummaryReturnTransition<LETTER, STATE>> summarySuccessors(final STATE hier, final LETTER letter) {
		final Predicate<SummaryReturnTransition<LETTER, STATE>> predicate =
				mTransitionFilter.getReturnSummaryPredicate();
		return new FilteredIterable<>(mNwa.summarySuccessors(hier, letter), predicate);
	}

	@Override
	public Iterable<SummaryReturnTransition<LETTER, STATE>> summarySuccessors(final STATE hier) {
		final Predicate<SummaryReturnTransition<LETTER, STATE>> predicate =
				mTransitionFilter.getReturnSummaryPredicate();
		return new FilteredIterable<>(mNwa.summarySuccessors(hier), predicate);
	}

	@Override
	public String toString() {
		return (new AutomatonDefinitionPrinter<String, String>(mServices, "nwa", Format.ATS, this))
				.getDefinitionAsString();
	}

	boolean isDoubleDecker(final STATE upState, final STATE downState) {
		return mAncestorComputation.isDownState(upState, downState);
	}
}
