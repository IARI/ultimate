/*
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
package de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.simulation.multipebble;

import java.util.Iterator;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.AutomataOperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.AutomataOperationStatistics;
import de.uni_freiburg.informatik.ultimate.automata.StatisticsType;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.IDoubleDeckerAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedWordAutomataUtils;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.minimization.AbstractMinimizeNwaDd;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.minimization.IMinimizationStateFactory;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.minimization.MinimizeNwaMaxSat2;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.minimization.MinimizeNwaPmaxSat;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.minimization.MinimizeNwaPmaxSatAsymmetric;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.minimization.NwaApproximateBisimulation;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.minimization.NwaApproximateSimulation;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.minimization.NwaApproximateXsimulation.SimulationType;
import de.uni_freiburg.informatik.ultimate.automata.util.ISetOfPairs;
import de.uni_freiburg.informatik.ultimate.automata.util.NestedMapBackedSetOfPairs;
import de.uni_freiburg.informatik.ultimate.automata.util.PartitionAndMapBackedSetOfPairs;
import de.uni_freiburg.informatik.ultimate.automata.util.PartitionBackedSetOfPairs;
import de.uni_freiburg.informatik.ultimate.automata.util.UnionFindBackedSetOfPairs;
import de.uni_freiburg.informatik.ultimate.core.lib.exceptions.RunningTaskInfo;
import de.uni_freiburg.informatik.ultimate.util.datastructures.UnionFind;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.NestedMap2;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Pair;

/**
 * TODO: documentation.
 * 
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
 * @param <LETTER>
 *            letter type
 * @param <STATE>
 *            state type
 * @param <GS>
 *            game state type
 */
public abstract class ReduceNwaFullMultipebbleSimulation<LETTER, STATE, GS extends FullMultipebbleGameState<STATE>>
		extends AbstractMinimizeNwaDd<LETTER, STATE> {
	private enum Metrie {
		SYM,
		ASYM
	}

	private final IDoubleDeckerAutomaton<LETTER, STATE> mOperand;
	private final AutomataOperationStatistics mStatistics;
	private final Metrie mMetriePreprocessing = Metrie.ASYM;
	private final Metrie mMetriePostprocessing = Metrie.ASYM;

	/**
	 * @param services
	 *            Ultimate services.
	 * @param stateFactory
	 *            state factory
	 * @param operand
	 *            operand
	 * @throws AutomataOperationCanceledException
	 *             if suboperations fail
	 */
	public ReduceNwaFullMultipebbleSimulation(final AutomataLibraryServices services,
			final IMinimizationStateFactory<STATE> stateFactory, final IDoubleDeckerAutomaton<LETTER, STATE> operand,
			final boolean allowToMergeFinalAndNonFinalStates) throws AutomataOperationCanceledException {
		super(services, stateFactory);
		mOperand = operand;

		printStartMessage();

		final ISetOfPairs<STATE, ?> initialPairs;
		switch (mMetriePreprocessing) {
			case SYM:
				final PartitionAndMapBackedSetOfPairs<STATE> partition =
						new PartitionAndMapBackedSetOfPairs<>(new NwaApproximateBisimulation<>(services, operand,
								allowToMergeFinalAndNonFinalStates ? SimulationType.ORDINARY : SimulationType.DIRECT)
										.getResult().getRelation());
				mLogger.info("Initial partition has " + partition.getOrConstructPartitionSizeInformation().toString());
				initialPairs = partition;
				break;
			case ASYM:
				initialPairs = new NwaApproximateSimulation<>(services, operand,
						allowToMergeFinalAndNonFinalStates ? SimulationType.ORDINARY : SimulationType.DIRECT)
								.getResult();
				break;
			default:
				throw new AssertionError("illegal value " + mMetriePreprocessing);
		}
		final FullMultipebbleStateFactory<STATE, GS> gameFactory = constructGameFactory(initialPairs);

		try {
			final FullMultipebbleGameAutomaton<LETTER, STATE, GS> gameAutomaton =
					new FullMultipebbleGameAutomaton<>(mServices, gameFactory, initialPairs, operand);
			final Pair<IDoubleDeckerAutomaton<LETTER, GS>, Integer> simRes = computeSimulation(gameAutomaton);
			final int maxGameAutomatonSize = simRes.getSecond();
			final NestedMap2<STATE, STATE, GS> gsm = gameAutomaton.getGameStateMapping();

			// TODO make this an option?
			final boolean addMapOldState2NewState = false;
			final MinimizeNwaMaxSat2.Settings<STATE> settings = new MinimizeNwaMaxSat2.Settings<STATE>()
					.setFinalStateConstraints(!allowToMergeFinalAndNonFinalStates)
					.setAddMapOldState2NewState(addMapOldState2NewState);

			final MinimizeNwaMaxSat2<LETTER, STATE, ?> maxSatMinimizer;
			switch (mMetriePostprocessing) {
				case ASYM:
					maxSatMinimizer = new MinimizeNwaPmaxSatAsymmetric<>(mServices, stateFactory, mOperand,
							readoutExactSimulationRelation(initialPairs, gsm, simRes.getFirst(), gameFactory)
									.getRelation(),
							settings);
					break;
				case SYM:
					maxSatMinimizer = new MinimizeNwaPmaxSat<>(mServices, stateFactory, mOperand,
							readoutSymmetricCoreOfSimulationRelation(initialPairs, gsm, simRes.getFirst(), gameFactory),
							settings);
					break;
				default:
					throw new AssertionError("illegal value " + mMetriePostprocessing);
			}

			super.directResultConstruction(maxSatMinimizer.getResult());
			if (addMapOldState2NewState) {
				super.setOld2NewStateMap(maxSatMinimizer.getOldState2newState());
			}

			mStatistics = addStatistics(initialPairs, gameFactory, maxGameAutomatonSize, maxSatMinimizer);

		} catch (final AutomataOperationCanceledException aoce) {
			if (initialPairs instanceof PartitionBackedSetOfPairs<?>) {
				final PartitionBackedSetOfPairs<STATE> partition = (PartitionBackedSetOfPairs<STATE>) initialPairs;
				final RunningTaskInfo rti = new RunningTaskInfo(getClass(),
						NestedWordAutomataUtils.generateGenericMinimizationRunningTaskDescription(operationName(),
								mOperand, partition.getOrConstructPartitionSizeInformation()));
				aoce.addRunningTaskInfo(rti);
			} else {
				addGenericRunningTaskInfo(aoce);
			}
			throw aoce;
		}
		printExitMessage();
	}

	private AutomataOperationStatistics addStatistics(final ISetOfPairs<STATE, ?> initialPairs,
			final FullMultipebbleStateFactory<STATE, GS> gameFactory, final int maxGameAutomatonSize,
			final MinimizeNwaMaxSat2<LETTER, STATE, ?> maxSatMinimizer) {
		final AutomataOperationStatistics statistics = super.getAutomataOperationStatistics();
		statistics.addKeyValuePair(StatisticsType.MAX_NUMBER_OF_DOUBLEDECKER_PEBBLES,
				gameFactory.getMaxNumberOfDoubleDeckerPebbles());
		if (initialPairs instanceof PartitionBackedSetOfPairs<?>) {
			final PartitionBackedSetOfPairs<?> initialPartition = (PartitionBackedSetOfPairs<?>) initialPairs;
			statistics.addKeyValuePair(StatisticsType.SIZE_MAXIMAL_INITIAL_BLOCK,
					initialPartition.getOrConstructPartitionSizeInformation().getSizeOfLargestBlock());
			statistics.addKeyValuePair(StatisticsType.NUMBER_INITIAL_PAIRS,
					initialPartition.getOrConstructPartitionSizeInformation().getNumberOfPairs());
			statistics.addKeyValuePair(StatisticsType.SIZE_INITIAL_PARTITION,
					initialPartition.getOrConstructPartitionSizeInformation().getNumberOfBlocks());
		} else {
			long numberOfPairs = 0;
			for (final Iterator<Pair<STATE, STATE>> it = initialPairs.iterator(); it.hasNext(); it.next()) {
				++numberOfPairs;
			}
			statistics.addKeyValuePair(StatisticsType.NUMBER_INITIAL_PAIRS, numberOfPairs);
		}
		statistics.addKeyValuePair(StatisticsType.SIZE_GAME_AUTOMATON, maxGameAutomatonSize);
		statistics.addKeyValuePair(StatisticsType.STATES_INPUT, mOperand.size());
		statistics.addKeyValuePair(StatisticsType.STATES_OUTPUT, getResult().size());

		maxSatMinimizer.addStatistics(statistics);

		return statistics;
	}

	protected abstract Pair<IDoubleDeckerAutomaton<LETTER, GS>, Integer> computeSimulation(
			FullMultipebbleGameAutomaton<LETTER, STATE, GS> gameAutomaton) throws AutomataOperationCanceledException;

	protected abstract FullMultipebbleStateFactory<STATE, GS>
			constructGameFactory(final ISetOfPairs<STATE, ?> initialPairs);

	private boolean isInSimulationRelation(final STATE q0, final STATE q1,
			final FullMultipebbleStateFactory<STATE, ?> gameFactory,
			final NestedMap2<STATE, STATE, GS> gameStateMapping, final IDoubleDeckerAutomaton<LETTER, GS> mRemoved) {
		if (gameFactory.isImmediatelyWinningForSpoiler(q0, q1, mOperand)) {
			return false;
		}
		final GS s1 = gameStateMapping.get(q0, q1);
		if (mRemoved.isInitial(s1)) {
			return false;
		}
		return true;
	}

	private UnionFindBackedSetOfPairs<STATE> readoutSymmetricCoreOfSimulationRelation(
			final ISetOfPairs<STATE, ?> initialPairs, final NestedMap2<STATE, STATE, GS> gameStateMapping,
			final IDoubleDeckerAutomaton<LETTER, GS> removed, final FullMultipebbleStateFactory<STATE, ?> gameFactory) {
		final UnionFindBackedSetOfPairs<STATE> result = new UnionFindBackedSetOfPairs<>();
		for (final Pair<STATE, STATE> pair : initialPairs) {
			final STATE q0 = pair.getFirst();
			final STATE q1 = pair.getSecond();
			if (!initialPairs.containsPair(q1, q0)) {
				// can happen if asymmetric preprocessing was used
				continue;
			}
			if (isInSimulationRelation(q0, q1, gameFactory, gameStateMapping, removed)
					&& isInSimulationRelation(q1, q0, gameFactory, gameStateMapping, removed)) {
				result.addPair(q0, q1);
			}
		}
		return result;
	}

	private NestedMapBackedSetOfPairs<STATE> readoutExactSimulationRelation(final ISetOfPairs<STATE, ?> initialPairs,
			final NestedMap2<STATE, STATE, GS> gameStateMapping, final IDoubleDeckerAutomaton<LETTER, GS> removed,
			final FullMultipebbleStateFactory<STATE, ?> gameFactory) {
		final NestedMapBackedSetOfPairs<STATE> result = new NestedMapBackedSetOfPairs<>();
		for (final Pair<STATE, STATE> pair : initialPairs) {
			final STATE q0 = pair.getFirst();
			final STATE q1 = pair.getSecond();
			if (isInSimulationRelation(q0, q1, gameFactory, gameStateMapping, removed)) {
				result.addPair(q0, q1);
			}
		}
		return result;
	}

	@Override
	protected INestedWordAutomaton<LETTER, STATE> getOperand() {
		return mOperand;
	}

	@Override
	public AutomataOperationStatistics getAutomataOperationStatistics() {
		return mStatistics;
	}

	private class ReadoutSimulation extends InitialPartitionProcessor<STATE> {
		private final NestedMap2<STATE, STATE, GS> mGameStateMapping;
		private final IDoubleDeckerAutomaton<LETTER, GS> mRemoved;
		private final UnionFind<STATE> mMutuallySimulating;
		private final FullMultipebbleStateFactory<STATE, ?> mGameFactory;

		public ReadoutSimulation(final NestedMap2<STATE, STATE, GS> gsm,
				final IDoubleDeckerAutomaton<LETTER, GS> removed,
				final FullMultipebbleStateFactory<STATE, ?> gameFactory) {
			super(mServices);
			mGameStateMapping = gsm;
			mRemoved = removed;
			mGameFactory = gameFactory;
			mMutuallySimulating = new UnionFind<>();
		}

		@Override
		public boolean shouldBeProcessed(final STATE q0, final STATE q1) {
			return isInSimulationRelation(q0, q1, mGameFactory, mGameStateMapping, mRemoved)
					&& isInSimulationRelation(q1, q0, mGameFactory, mGameStateMapping, mRemoved);
		}

		@Override
		public void doProcess(final STATE q0, final STATE q1) {
			final STATE rep0 = mMutuallySimulating.findAndConstructEquivalenceClassIfNeeded(q0);
			final STATE rep1 = mMutuallySimulating.findAndConstructEquivalenceClassIfNeeded(q1);
			mMutuallySimulating.union(rep0, rep1);
		}

		public UnionFind<STATE> getMutuallySimulating() {
			return mMutuallySimulating;
		}
	}
}
