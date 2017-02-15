/*
 * Copyright (C) 2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 *
 * This file is part of the ULTIMATE TraceAbstraction plug-in.
 *
 * The ULTIMATE TraceAbstraction plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE TraceAbstraction plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE TraceAbstraction plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE TraceAbstraction plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE TraceAbstraction plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singletracecheck;

import java.util.Arrays;
import java.util.Set;
import java.util.SortedMap;

import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedRun;
import de.uni_freiburg.informatik.ultimate.core.lib.exceptions.ToolchainCanceledException;
import de.uni_freiburg.informatik.ultimate.core.model.services.IToolchainStorage;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.SimplificationTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.XnfConversionTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SolverBuilder.Settings;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.CegarLoopStatisticsGenerator;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.pathinvariants.PathInvariantsGenerator;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.pathinvariants.internal.PathInvariantsStatisticsGenerator;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.AssertCodeBlockOrder;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.InterpolationTechnique;

/**
 * InterpolatingTraceChecker that returns path invariants as interpolants. If no path invariants are available, Craig
 * interpolation is used as a fallback.
 *
 * @author Matthias Heizmann
 */
public class InterpolatingTraceCheckerPathInvariantsWithFallback extends InterpolatingTraceChecker {

	private final IToolchainStorage mStorage;
	private final NestedRun<? extends IAction, IPredicate> mNestedRun;
	private final boolean mUseNonlinerConstraints;
	private final boolean mUseVarsFromUnsatCore;
	private final Settings mSolverSettings;
	private final IIcfg<?> mIcfg;
	private final boolean mUseLiveVariables;
	private final boolean mUseWPForPathInvariants;
	private final boolean mUseAbstractInterpretationPredicates;
	private PathInvariantsStatisticsGenerator mPathInvariantsStats;
	
	private InterpolantComputationStatus mInterpolantComputationStatus;

	public InterpolatingTraceCheckerPathInvariantsWithFallback(final IPredicate precondition,
			final IPredicate postcondition, final SortedMap<Integer, IPredicate> pendingContexts,
			final NestedRun<? extends IIcfgTransition<?>, IPredicate> run, final CfgSmtToolkit csToolkit,
			final AssertCodeBlockOrder assertCodeBlocksIncrementally, final IUltimateServiceProvider services,
			final IToolchainStorage storage, final boolean computeRcfgProgramExecution,
			final PredicateUnifier predicateUnifier, final boolean useNonlinerConstraints,
			final boolean useVarsFromUnsatCore, final boolean useLiveVariables, final boolean useAbstractInterpretationPredicates,
			final boolean useWeakestPrecondition,
			final Settings solverSettings, final XnfConversionTechnique xnfConversionTechnique,
			final SimplificationTechnique simplificationTechnique, final IIcfg<?> icfgContainer, 
			final CegarLoopStatisticsGenerator cegarLoopBenchmark) {
		super(precondition, postcondition, pendingContexts, run.getWord(), csToolkit, assertCodeBlocksIncrementally,
				services, computeRcfgProgramExecution, predicateUnifier, csToolkit.getManagedScript(),
				simplificationTechnique, xnfConversionTechnique, run.getStateSequence());
		mStorage = storage;
		mNestedRun = run;
		mUseNonlinerConstraints = useNonlinerConstraints;
		mUseVarsFromUnsatCore = useVarsFromUnsatCore;
		mUseLiveVariables = useLiveVariables;
		mUseAbstractInterpretationPredicates = useAbstractInterpretationPredicates;
		mUseWPForPathInvariants = useWeakestPrecondition;
		mSolverSettings = solverSettings;
		mIcfg = icfgContainer;
		if (super.isCorrect() == LBool.UNSAT) {
			mTraceCheckFinished = true;
			super.unlockSmtManager();
			computeInterpolants(new AllIntegers(), InterpolationTechnique.PathInvariants);
			// Add benchmarks from PathInvariants
			cegarLoopBenchmark.addPathInvariantsData(mPathInvariantsStats);
			if (!mInterpolantComputationStatus.wasComputationSuccesful()) {
				final String message = "invariant synthesis failed";
				final String taskDescription = "trying to synthesize invariant for path program " + mPathInvariantsStats;
				throw new ToolchainCanceledException(message, getClass(), taskDescription);
			}
		}
		
	}

	@Override
	protected void computeInterpolants(final Set<Integer> interpolatedPositions,
			final InterpolationTechnique interpolation) {

		final PathInvariantsGenerator pathInvariantsGenerator = new PathInvariantsGenerator(super.mServices, mStorage,
				mNestedRun, super.getPrecondition(), super.getPostcondition(), mPredicateUnifier, mIcfg,
				mUseNonlinerConstraints, mUseVarsFromUnsatCore, mUseLiveVariables, mUseAbstractInterpretationPredicates, 
				mUseWPForPathInvariants,
				mSolverSettings, mSimplificationTechnique, mXnfConversionTechnique);
		mInterpolantComputationStatus = pathInvariantsGenerator.getInterpolantComputationStatus();
		final IPredicate[] interpolants = pathInvariantsGenerator.getInterpolants();
		if (interpolants == null) {
			assert !pathInvariantsGenerator.getInterpolantComputationStatus().wasComputationSuccesful() : 
				"null only allowed if computation was not successful";
		} else {
			if (interpolants.length != mTrace.length() - 1) {
				throw new AssertionError("inkorrekt number of interpolants. "
						+ "There should be one interpolant between each " + "two successive CodeBlocks");
			}
			assert TraceCheckerUtils.checkInterpolantsInductivityForward(Arrays.asList(interpolants), mTrace, mPrecondition,
					mPostcondition, mPendingContexts, "invariant map", mCsToolkit, mLogger,
					mCfgManagedScript) : "invalid Hoare triple in invariant map";
		}
		mInterpolants = interpolants;
		// Store path invariants benchmarks
		mPathInvariantsStats = pathInvariantsGenerator.getPathInvariantsBenchmarks();
	}

	@Override
	public InterpolantComputationStatus getInterpolantComputationStatus() {
		return mInterpolantComputationStatus;
	}

}
