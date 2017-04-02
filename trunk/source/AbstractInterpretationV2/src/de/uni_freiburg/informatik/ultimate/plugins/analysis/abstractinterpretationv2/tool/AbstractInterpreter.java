/*
 * Copyright (C) 2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2015 Marius Greitschus (greitsch@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 *
 * This file is part of the ULTIMATE AbstractInterpretationV2 plug-in.
 *
 * The ULTIMATE AbstractInterpretationV2 plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE AbstractInterpretationV2 plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE AbstractInterpretationV2 plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE AbstractInterpretationV2 plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE AbstractInterpretationV2 plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.tool;

import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomatonSimple;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedRun;
import de.uni_freiburg.informatik.ultimate.core.lib.exceptions.ToolchainCanceledException;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IProgressAwareTimer;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractDomain;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractState;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.IBoogieVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocationIterator;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVarOrConst;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.Activator;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.AbstractInterpretationResult;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.BackwardFixpointEngine;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.FixpointEngine;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.FixpointEngineParameters;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.IFixpointEngine;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.ILoopDetector;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.IResultReporter;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.ITransitionProvider;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.generic.SilentReporter;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.nwa.NWAPathProgramTransitionProvider;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.rcfg.IcfgTransitionProvider;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.rcfg.RCFGLiteralCollector;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.rcfg.RcfgLibraryModeResultReporter;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.rcfg.RcfgLoopDetector;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.rcfg.RcfgResultReporter;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.rcfg.RcfgTransitionProvider;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.nonrelational.dataflow.DataflowDomain;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.nonrelational.dataflow.DataflowState;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.nonrelational.livevariable.LiveVariableDomain;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.nonrelational.livevariable.LiveVariableState;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.states.VPState;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.tool.initializer.FixpointEngineFutureParameterFactory;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.tool.initializer.FixpointEngineParameterFactory;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.util.AbsIntUtil;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.BoogieIcfgLocation;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.CodeBlock;

/**
 * Should be used by other tools to run abstract interpretation on various parts of the RCFG.
 *
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 */
public final class AbstractInterpreter {

	private AbstractInterpreter() {
		// do not instantiate AbstractInterpreter; its a facade
	}

	/**
	 * Run abstract interpretation as independent analysis on a whole {@link IIcfg}.
	 *
	 */
	public static <STATE extends IAbstractState<STATE, IBoogieVar>>
			IAbstractInterpretationResult<STATE, CodeBlock, IBoogieVar, IcfgLocation>
			run(final IIcfg<? extends IcfgLocation> root, final IProgressAwareTimer timer,
					final IUltimateServiceProvider services) {
		if (timer == null) {
			throw new IllegalArgumentException("timer is null");
		}

		final ITransitionProvider<CodeBlock, IcfgLocation> transProvider = new RcfgTransitionProvider();

		final Script script = root.getCfgSmtToolkit().getManagedScript().getScript();
		final FixpointEngineParameterFactory domFac =
				new FixpointEngineParameterFactory(root, () -> new RCFGLiteralCollector(root), services);
		final ILoopDetector<CodeBlock> loopDetector = new RcfgLoopDetector<>();

		final FixpointEngineParameters<STATE, CodeBlock, IBoogieVar, IcfgLocation> params =
				domFac.createParams(timer, transProvider, loopDetector);

		final FixpointEngine<STATE, CodeBlock, IBoogieVar, IcfgLocation> fxpe = new FixpointEngine<>(params);
		final AbstractInterpretationResult<STATE, CodeBlock, IBoogieVar, IcfgLocation> result =
				fxpe.run(root.getInitialNodes(), script);

		final ILogger logger = services.getLoggingService().getLogger(Activator.PLUGIN_ID);
		return postProcessResult(services, logger, false, root.getInitialNodes().size() > 1, result);
	}

	/**
	 * Run abstract interpretation as independent analysis on a whole {@link IIcfg} and suppress
	 * {@link ToolchainCanceledException}s (return null instead).
	 *
	 */
	public static <STATE extends IAbstractState<STATE, IBoogieVar>>
			IAbstractInterpretationResult<STATE, CodeBlock, IBoogieVar, IcfgLocation>
			runWithoutTimeout(final IIcfg<BoogieIcfgLocation> root, final IProgressAwareTimer timer,
					final IUltimateServiceProvider services) {
		assert root != null;
		assert services != null;
		assert timer != null;

		final ILogger logger = services.getLoggingService().getLogger(Activator.PLUGIN_ID);
		try {
			final ITransitionProvider<CodeBlock, IcfgLocation> transProvider = new RcfgTransitionProvider();
			final Script script = root.getCfgSmtToolkit().getManagedScript().getScript();
			final FixpointEngineParameterFactory domFac =
					new FixpointEngineParameterFactory(root, () -> new RCFGLiteralCollector(root), services);
			final ILoopDetector<CodeBlock> loopDetector = new RcfgLoopDetector<>();
			final FixpointEngineParameters<STATE, CodeBlock, IBoogieVar, IcfgLocation> params =
					domFac.createParams(timer, transProvider, loopDetector);
			final FixpointEngine<STATE, CodeBlock, IBoogieVar, IcfgLocation> fxpe = new FixpointEngine<>(params);
			final Set<BoogieIcfgLocation> initial = root.getInitialNodes();
			final AbstractInterpretationResult<STATE, CodeBlock, IBoogieVar, IcfgLocation> result =
					fxpe.run(initial, script);
			if (logger.isDebugEnabled()) {
				logger.debug("Found the following predicates:");
				AbsIntUtil.logPredicates(Collections.singletonMap(initial, result.getLoc2Term()), script,
						logger::debug);
			}
			return postProcessResult(services, logger, true, false, result);
		} catch (final ToolchainCanceledException tce) {
			// suppress timeout results / timeouts
			logger.warn("Abstract interpretation run out of time");
			return null;
		}
	}

	/**
	 * Run abstract interpretation on a path program constructed from a counterexample.
	 * 
	 * Suppress timeout / cancel exceptions and instead return null.
	 */
	public static <STATE extends IAbstractState<STATE, IBoogieVar>>
			IAbstractInterpretationResult<STATE, CodeBlock, IBoogieVar, IcfgLocation> runOnPathProgram(
					final IIcfg<BoogieIcfgLocation> root, final INestedWordAutomatonSimple<CodeBlock, ?> abstraction,
					final NestedRun<CodeBlock, ?> counterexample, final Set<CodeBlock> pathProgramProjection,
					final IProgressAwareTimer timer, final IUltimateServiceProvider services) {
		assert counterexample != null && counterexample.getLength() > 0 : "Invalid counterexample";
		assert abstraction != null;
		assert root != null;
		assert services != null;
		assert timer != null;

		final ILogger logger = services.getLoggingService().getLogger(Activator.PLUGIN_ID);
		try {
			final NWAPathProgramTransitionProvider transProvider =
					new NWAPathProgramTransitionProvider(counterexample, pathProgramProjection, services, root);
			final Set<IcfgLocation> initial =
					Collections.singleton((BoogieIcfgLocation) counterexample.getSymbol(0).getSource());
			final Script script = root.getCfgSmtToolkit().getManagedScript().getScript();
			final FixpointEngineParameterFactory domFac =
					new FixpointEngineParameterFactory(root, () -> new RCFGLiteralCollector(root), services);
			final FixpointEngineParameters<STATE, CodeBlock, IBoogieVar, IcfgLocation> params =
					domFac.createParamsPathProgram(timer, transProvider, transProvider);
			final FixpointEngine<STATE, CodeBlock, IBoogieVar, IcfgLocation> fxpe = new FixpointEngine<>(params);
			final AbstractInterpretationResult<STATE, CodeBlock, IBoogieVar, IcfgLocation> result =
					fxpe.run(initial, script);
			if (logger.isDebugEnabled()) {
				logger.debug("Found the following predicates:");
				AbsIntUtil.logPredicates(Collections.singletonMap(initial, result.getLoc2Term()), script,
						logger::debug);
			}
			return postProcessResult(services, logger, true, false, result);
		} catch (final ToolchainCanceledException tce) {
			// suppress timeout results / timeouts
			logger.warn("Abstract interpretation run out of time");
			return null;
		}
	}

	/**
	 * Run abstract interpretation on the RCFG of the future (experimental).
	 *
	 * @param logger
	 *
	 */
	public static <STATE extends IAbstractState<STATE, IProgramVarOrConst>>
			IAbstractInterpretationResult<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation>
			runFuture(final IIcfg<?> root, final IProgressAwareTimer timer, final IUltimateServiceProvider services,
					final boolean isSilent, final ILogger logger) {
		final ITransitionProvider<IcfgEdge, IcfgLocation> transProvider = new IcfgTransitionProvider(root);
		final ILoopDetector<IcfgEdge> loopDetector = new RcfgLoopDetector<>();
		final FixpointEngineFutureParameterFactory domFac = new FixpointEngineFutureParameterFactory(root, services);
		final IAbstractDomain<STATE, IcfgEdge, IProgramVarOrConst> domain = domFac.selectDomainFutureCfg();
		final FixpointEngineParameters<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation> params =
				domFac.createParamsFuture(timer, transProvider, loopDetector, domain);

		final Script script = root.getCfgSmtToolkit().getManagedScript().getScript();
		final Set<IcfgLocation> initialNodes;
		final IFixpointEngine<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation> fxpe;
		if (domain instanceof LiveVariableDomain<?>) {
			// run backwards is hacky if run as stand-alone analysis
			initialNodes = getSinks(root);
			fxpe = new BackwardFixpointEngine<>(params.setMaxParallelStates(1));
			final AbstractInterpretationResult<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation> result =
					fxpe.run(initialNodes, script);
			return postProcessResult(services, logger, true, initialNodes.size() > 1, result);
		}
		initialNodes = root.getInitialNodes().stream().collect(Collectors.toSet());
		fxpe = new FixpointEngine<>(params);
		final AbstractInterpretationResult<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation> result =
				fxpe.run(initialNodes, script);
		return postProcessResult(services, logger, isSilent, initialNodes.size() > 1, result);
	}

	/**
	 * so far, this is a copy of runFuture(..), except some parameters (e.g. abstract domain) are not taken from the
	 * settings but hardcoded
	 *
	 * @param logger
	 *
	 */
	public static IAbstractInterpretationResult<VPState<IcfgEdge>, IcfgEdge, IProgramVarOrConst, IcfgLocation>
			runFutureEqualityDomain(final IIcfg<?> root, final IProgressAwareTimer timer,
					final IUltimateServiceProvider services, final boolean isSilent, final ILogger logger) {
		final FixpointEngineParameters<VPState<IcfgEdge>, IcfgEdge, IProgramVarOrConst, IcfgLocation> params =
				new FixpointEngineParameters<>(services, IProgramVarOrConst.class);
		return runFuture(root, services, logger, isSilent,
				params.setDomain(FixpointEngineFutureParameterFactory.createEqualityDomain(logger, root, services))
						.setTimer(timer),
				p -> new FixpointEngine<>(p));
	}

	public static IAbstractInterpretationResult<DataflowState<IcfgEdge>, IcfgEdge, IProgramVarOrConst, IcfgLocation>
			runFutureDataflowDomain(final IIcfg<?> root, final IProgressAwareTimer timer,
					final IUltimateServiceProvider services, final boolean isSilent, final ILogger logger) {
		final FixpointEngineParameters<DataflowState<IcfgEdge>, IcfgEdge, IProgramVarOrConst, IcfgLocation> params =
				new FixpointEngineParameters<>(services, IProgramVarOrConst.class);
		return runFuture(root, services, logger, isSilent,
				params.setDomain(new DataflowDomain<>(logger)).setTimer(timer), p -> new FixpointEngine<>(p));
	}

	public static IAbstractInterpretationResult<LiveVariableState<IcfgEdge>, IcfgEdge, IProgramVarOrConst, IcfgLocation>
			runFutureLiveVariableDomain(final IIcfg<?> root, final IProgressAwareTimer timer,
					final IUltimateServiceProvider services, final boolean isSilent, final ILogger logger) {
		final FixpointEngineParameters<LiveVariableState<IcfgEdge>, IcfgEdge, IProgramVarOrConst, IcfgLocation> params =
				new FixpointEngineParameters<>(services, IProgramVarOrConst.class);
		return runFuture(root, services, logger, isSilent,
				params.setDomain(new LiveVariableDomain<>(logger)).setTimer(timer).setMaxParallelStates(1),
				p -> new BackwardFixpointEngine<>(p));
	}

	/**
	 *
	 * @param services
	 * @param logger
	 * @param isSilent
	 * @param filteredInitialElements
	 * @param result
	 * @return
	 */
	private static <
			STATE extends IAbstractState<STATE, VARDECL>, VARDECL, ACTION extends IcfgEdge, LOC extends IcfgLocation>
			IAbstractInterpretationResult<STATE, ACTION, VARDECL, LOC>
			postProcessResult(final IUltimateServiceProvider services, final ILogger logger, final boolean isSilent,
					final boolean isLib, final AbstractInterpretationResult<STATE, ACTION, VARDECL, LOC> result) {
		if (result == null) {
			logger.error("Could not run because no initial element could be found");
			return null;
		}
		if (result.hasReachedError()) {
			logger.info("Some error location(s) were reachable");
			final IResultReporter<STATE, ACTION, VARDECL, LOC> reporter = getReporter(services, isLib, isSilent);
			result.getCounterexamples().forEach(reporter::reportPossibleError);
		} else {
			logger.info("Error location(s) were unreachable");
			getReporter(services, false, isSilent).reportSafe(null);
		}

		logger.info(result.getBenchmark());
		return result;
	}

	/**
	 * Expects initial params with domain already set.
	 *
	 */
	private static <STATE extends IAbstractState<STATE, IProgramVarOrConst>>
			IAbstractInterpretationResult<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation>
			runFuture(final IIcfg<?> root, final IUltimateServiceProvider services, final ILogger logger,
					final boolean isSilent,
					final FixpointEngineParameters<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation> initialParams,
					final FixpointEngineFactory<STATE> funCreateEngine) {

		final ITransitionProvider<IcfgEdge, IcfgLocation> transProvider = new IcfgTransitionProvider(root);

		final Script script = root.getCfgSmtToolkit().getManagedScript().getScript();
		final ILoopDetector<IcfgEdge> loopDetector = new RcfgLoopDetector<>();

		final FixpointEngineFutureParameterFactory paramFac = new FixpointEngineFutureParameterFactory(root, services);
		final FixpointEngineParameters<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation> params =
				paramFac.addDefaultParamsFuture(initialParams, transProvider, loopDetector);
		final IFixpointEngine<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation> fxpe = funCreateEngine.create(params);

		final Set<IcfgLocation> initialNodes;
		if (fxpe instanceof BackwardFixpointEngine<?, ?, ?, ?>) {
			initialNodes = getSinks(root);
		} else {
			initialNodes = root.getInitialNodes().stream().collect(Collectors.toSet());
		}

		final AbstractInterpretationResult<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation> result =
				fxpe.run(initialNodes, script);

		if (result == null) {
			logger.error("Could not run because no initial element could be found");
			return null;
		}

		final boolean isLib = initialNodes.size() > 1;
		if (result.hasReachedError()) {
			final IResultReporter<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation> reporter =
					getReporter(services, isLib, isSilent);
			result.getCounterexamples().forEach(reporter::reportPossibleError);
		} else {
			getReporter(services, false, isSilent).reportSafe(null);
		}

		logger.info(result.getBenchmark());
		return result;
	}

	private static <STATE extends IAbstractState<STATE, VARDECL>, VARDECL, LOC>
			IAbstractInterpretationResult<STATE, CodeBlock, VARDECL, LOC>

			runSilently(final Supplier<IAbstractInterpretationResult<STATE, CodeBlock, VARDECL, LOC>> fun,
					final ILogger logger) {
		try {
			return fun.get();
		} catch (final OutOfMemoryError oom) {
			throw oom;
		} catch (final IllegalArgumentException iae) {
			throw iae;
		} catch (final ToolchainCanceledException tce) {
			// suppress timeout results / timeouts
			return null;
		} catch (final Throwable t) {
			logger.fatal("Suppressed exception in AIv2: " + t.getMessage());
			return null;
		}
	}

	private static <
			STATE extends IAbstractState<STATE, VARDECL>, ACTION extends IcfgEdge, VARDECL, LOC extends IcfgLocation>
			IResultReporter<STATE, ACTION, VARDECL, LOC>
			getReporter(final IUltimateServiceProvider services, final boolean isLibrary, final boolean isSilent) {
		if (isSilent) {
			return new SilentReporter<>();
		}
		if (isLibrary) {
			return new RcfgLibraryModeResultReporter<>(services);
		}
		return new RcfgResultReporter<>(services);
	}

	/**
	 * Get sink nodes of the icfg
	 *
	 */
	private static Set<IcfgLocation> getSinks(final IIcfg<?> root) {
		return new IcfgLocationIterator<>(root).asStream().filter(a -> a.getOutgoingEdges().isEmpty())
				.collect(Collectors.toSet());
	}

	@FunctionalInterface
	private interface FixpointEngineFactory<STATE extends IAbstractState<STATE, IProgramVarOrConst>> {
		IFixpointEngine<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation>
				create(FixpointEngineParameters<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation> params);
	}
}
