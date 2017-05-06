/*
 * Copyright (C) 2017 Christian Schilling (schillic@informatik.uni-freiburg.de)
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
package de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.minimization.maxsat.collections;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.AutomataOperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.LibraryIdentifiers;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.minimization.NwaApproximateSimulation;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.minimization.NwaApproximateXsimulation.SimulationType;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.IncomingInternalTransition;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.OutgoingInternalTransition;
import de.uni_freiburg.informatik.ultimate.automata.util.ISetOfPairs;
import de.uni_freiburg.informatik.ultimate.automata.util.MapBackedSetOfPairs;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Pair;

/**
 * Underapproximation of delayed simulation for nested word automata.
 * <p>
 * The result is a set of pairs that contains a pair (p, q) only if q delayed simulates p. If the pair (p, q) does not
 * occur, we know nothing.
 * <p>
 * For computing the underapproximation, we compute an overapproximation of the complement. The game theoretical
 * characterization in this case states that spoiler has a winning strategy, i.e., a strategy to visit an accepting
 * state and henceforth prevent duplicator from visiting an accepting state.<br>
 * We compute the approximation of this set by considering the game graph and starting from those nodes where duplicator
 * visits an accepting state. Then we propagate this information in a backward manner.
 * <p>
 * TODO Christian 2017-04-20: There are many possibilities for optimization.
 * <ul>
 * <li>caching letters and only considering respective successors</li>
 * <li>caching successors and only considering unmarked ones</li>
 * </ul>
 * 
 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
 * @param <LETTER>
 *            letter type
 * @param <STATE>
 *            state type
 */
public class NwaApproximateDelayedSimulation<LETTER, STATE> {
	private final AutomataLibraryServices mServices;
	private final ILogger mLogger;
	private final INestedWordAutomaton<LETTER, STATE> mOperand;
	private final ISetOfPairs<STATE, ?> mDuplicatorEventuallyAccepting;
	private final ISetOfPairs<STATE, ?> mSpoilerWinningStates;

	/**
	 * @param services
	 *            Ultimate services.
	 * @param operand
	 *            operand
	 * @param areStatesMerged
	 *            predicate expressing whether two states/stack symbols are merged
	 * @throws AutomataOperationCanceledException
	 *             if operation is canceled
	 */
	public NwaApproximateDelayedSimulation(final AutomataLibraryServices services,
			final INestedWordAutomaton<LETTER, STATE> operand, final BiPredicate<STATE, STATE> areStatesMerged)
			throws AutomataOperationCanceledException {
		mServices = services;
		mLogger = services.getLoggingService().getLogger(LibraryIdentifiers.PLUGIN_ID);
		mOperand = operand;

		// FIXME: Currently this operation only works for finite automata, in which case it is exact.
		if (!mOperand.getCallAlphabet().isEmpty() || !mOperand.getReturnAlphabet().isEmpty()) {
			throw new IllegalArgumentException("Currently this operation only supports finite automata");
		}

		// FIXME somehow dead ends in the game graph have to be removed first
		final ISetOfPairs<STATE, ?> ordinarySimulation = computeOrdinarySimulation();
		mDuplicatorEventuallyAccepting = computeDuplicatorFollowing(ordinarySimulation);
		mLogger.info("mDuplicatorEventuallyAccepting: \n" + mDuplicatorEventuallyAccepting);
		mSpoilerWinningStates = computeSpoilerWinning(mDuplicatorEventuallyAccepting);
		mLogger.info("mSpoilerWinningStates: \n" + mSpoilerWinningStates);
	}

	public ISetOfPairs<STATE, ?> getDuplicatorEventuallyAcceptingStates() {
		return mDuplicatorEventuallyAccepting;
	}

	public ISetOfPairs<STATE, ?> getSpoilerWinningStates() {
		return mSpoilerWinningStates;
	}

	private MapBackedSetOfPairs<STATE> computeOrdinarySimulation() throws AutomataOperationCanceledException {
		return new NwaApproximateSimulation<>(mServices, mOperand, SimulationType.ORDINARY).getResult();
	}

	private ISetOfPairs<STATE, ?> computeDuplicatorFollowing(final ISetOfPairs<STATE, ?> ordinarySimulation) {
		final Map<STATE, Set<STATE>> marked = new HashMap<>();
		final Set<Pair<STATE, STATE>> visit = new LinkedHashSet<>();

		// initialize with ordinary simulation pairs where duplicator is accepting
		for (final Pair<STATE, STATE> pair : ordinarySimulation) {
			if (mOperand.isFinal(pair.getSecond())) {
				markPair(marked, visit, pair);
			}
		}

		// propagate until fixpoint is reached
		pair: while (!visit.isEmpty()) {
			final Pair<STATE, STATE> pair = visit.iterator().next();
			visit.remove(pair);
			assert !isMarked(pair, marked) : pair + " is already marked";

			letter: for (final Pair<STATE, LETTER> gameLetter : getOutgoingGameLetters(pair)) {
				for (final Pair<STATE, STATE> succPair : getSuccessors(pair, gameLetter, ordinarySimulation)) {
					if (isMarked(succPair, marked)) {
						// marked successor found, try next letter
						continue letter;
					}
				}
				// letter with no marked successor found, try next pair
				continue pair;
			}

			// for all letters there was a marked successor; mark this pair
			markPair(marked, visit, pair);
		}

		return new MapBackedSetOfPairs<>(marked);
	}

	private ISetOfPairs<STATE, ?>
			computeSpoilerWinning(final ISetOfPairs<STATE, ?> duplicatorEventuallyAcceptingStates) {
		final Map<STATE, Set<STATE>> marked = new HashMap<>();
		final Set<Pair<STATE, STATE>> visit = new LinkedHashSet<>();

		// initialize with states s.t. spoiler is accepting and can force duplicator to avoid an accepting state
		// FIXME: we also consider non-ordinary simulation pairs here; this should be correct; but what about dead ends?
		for (final STATE lhs : mOperand.getFinalStates()) {
			for (final STATE rhs : mOperand.getStates()) {
				if (!duplicatorEventuallyAcceptingStates.containsPair(lhs, rhs)) {
					markPair(marked, visit, new Pair<>(lhs, rhs));
				}
			}
		}

		// propagate until fixpoint is reached
		while (!visit.isEmpty()) {
			final Pair<STATE, STATE> pair = visit.iterator().next();
			visit.remove(pair);
			assert !isMarked(pair, marked) : pair + " is already marked";

			letter: for (final Pair<STATE, LETTER> gameLetter : getOutgoingGameLetters(pair)) {
				final Collection<Pair<STATE, STATE>> successors = getSuccessors(pair, gameLetter, null);
				assert (!successors.isEmpty());
				for (final Pair<STATE, STATE> succPair : successors) {
					if (!isMarked(succPair, marked)) {
						// unmarked successor found, try next letter
						continue letter;
					}
				}
				markPair(marked, visit, pair);
				break;
			}
		}
		return new MapBackedSetOfPairs<>(marked);
	}

	private void markPair(final Map<STATE, Set<STATE>> marked, final Set<Pair<STATE, STATE>> visit,
			final Pair<STATE, STATE> pair) {
		assert !isMarked(pair, marked) : pair + " is already marked";
		mark(pair, marked);
		visit.remove(pair);
		for (final Pair<STATE, STATE> newPair : getPredecessors(pair)) {
			if (!isMarked(newPair, marked)) {
				visit.add(newPair);
			}
		}
	}

	private boolean isMarked(final Pair<STATE, STATE> pair, final Map<STATE, Set<STATE>> marked) {
		final Set<STATE> rhs = marked.get(pair.getFirst());
		if (rhs == null) {
			return false;
		}
		return rhs.contains(pair.getSecond());
	}

	private void mark(final Pair<STATE, STATE> pair, final Map<STATE, Set<STATE>> marked) {
		if (mLogger.isDebugEnabled()) {
			mLogger.debug("marking " + pair);
		}
		final STATE lhs = pair.getFirst();
		Set<STATE> rhs = marked.get(lhs);
		if (rhs == null) {
			rhs = new HashSet<>();
			marked.put(lhs, rhs);
		}
		rhs.add(pair.getSecond());
	}

	private Collection<Pair<STATE, STATE>> getPredecessors(final Pair<STATE, STATE> pair) {
		final Set<Pair<STATE, STATE>> result = new HashSet<>();
		final STATE lhs = pair.getFirst();
		final STATE rhs = pair.getSecond();
		final Set<LETTER> rhsIncoming = mOperand.lettersInternalIncoming(rhs);
		for (final LETTER letter : mOperand.lettersInternalIncoming(lhs)) {
			if (!rhsIncoming.contains(letter)) {
				continue;
			}
			for (final IncomingInternalTransition<LETTER, STATE> lhsPred : mOperand.internalPredecessors(lhs, letter)) {
				for (final IncomingInternalTransition<LETTER, STATE> rhsPred : mOperand.internalPredecessors(rhs,
						letter)) {
					result.add(new Pair<>(lhsPred.getPred(), rhsPred.getPred()));
				}
			}
		}
		return result;
	}

	private Collection<Pair<STATE, LETTER>> getOutgoingGameLetters(final Pair<STATE, STATE> pair) {
		final Set<Pair<STATE, LETTER>> result = new HashSet<>();
		final STATE lhs = pair.getFirst();
		final STATE rhs = pair.getSecond();
		final Set<LETTER> rhsOutgoing = mOperand.lettersInternal(rhs);
		for (final LETTER letter : mOperand.lettersInternal(lhs)) {
			if (!rhsOutgoing.contains(letter)) {
				continue;
			}
			for (final OutgoingInternalTransition<LETTER, STATE> lhsSucc : mOperand.internalSuccessors(lhs, letter)) {
				result.add(new Pair<>(lhsSucc.getSucc(), letter));
			}
		}
		return result;
	}

	private Collection<Pair<STATE, STATE>> getSuccessors(final Pair<STATE, STATE> pair,
			final Pair<STATE, LETTER> gameLetter, final ISetOfPairs<STATE, ?> allowedPairsFilter) {
		final Set<Pair<STATE, STATE>> result = new HashSet<>();
		for (final OutgoingInternalTransition<LETTER, STATE> rhsSucc : mOperand.internalSuccessors(pair.getSecond(),
				gameLetter.getSecond())) {
			final STATE lhs = gameLetter.getFirst();
			final STATE rhs = rhsSucc.getSucc();
			if (allowedPairsFilter == null || allowedPairsFilter.containsPair(lhs, rhs)) {
				result.add(new Pair<>(lhs, rhs));
			}
		}
		return result;
	}
}
