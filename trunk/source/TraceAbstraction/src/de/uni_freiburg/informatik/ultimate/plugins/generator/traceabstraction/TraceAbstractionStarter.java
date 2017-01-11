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
package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomatonSimple;
import de.uni_freiburg.informatik.ultimate.boogie.ast.BoogieASTNode;
import de.uni_freiburg.informatik.ultimate.core.lib.exceptions.IRunningTaskStackProvider;
import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.Check;
import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.Check.Spec;
import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.WitnessInvariant;
import de.uni_freiburg.informatik.ultimate.core.lib.results.AllSpecificationsHoldResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.BenchmarkResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.CounterExampleResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.InvariantResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.PositiveResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.ProcedureContractResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.ResultUtil;
import de.uni_freiburg.informatik.ultimate.core.lib.results.TimeoutResultAtElement;
import de.uni_freiburg.informatik.ultimate.core.lib.results.UnprovabilityReason;
import de.uni_freiburg.informatik.ultimate.core.lib.results.UnprovableResult;
import de.uni_freiburg.informatik.ultimate.core.model.models.IElement;
import de.uni_freiburg.informatik.ultimate.core.model.models.ILocation;
import de.uni_freiburg.informatik.ultimate.core.model.models.annotation.IAnnotations;
import de.uni_freiburg.informatik.ultimate.core.model.results.IResult;
import de.uni_freiburg.informatik.ultimate.core.model.services.IBacktranslationService;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IToolchainStorage;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.graphvr.server.IProtoServer;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.IcfgUtils;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgElement;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SolverBuilder.SolverMode;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.BoogieIcfgLocation;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.preferences.RcfgPreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.util.IcfgProgramExecution;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.AbstractCegarLoop.Result;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.interactive.Server;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.interactive.protobuf.Converter;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.interactive.protobuf.TraceAbstractionProtos;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.interactive.protobuf.TraceAbstractionProtos.CegarResult;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.HoareAnnotationChecker;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.PredicateFactory;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TAPreferences;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.InterpolantAutomaton;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.LanguageOperation;
import de.uni_freiburg.informatik.ultimate.util.csv.ICsvProviderProvider;
import de.uni_freiburg.informatik.ultimate.witnessparser.graph.WitnessEdge;
import de.uni_freiburg.informatik.ultimate.witnessparser.graph.WitnessNode;

public class TraceAbstractionStarter {

	private final ILogger mLogger;
	private final IUltimateServiceProvider mServices;
	private final IToolchainStorage mToolchainStorage;

	/**
	 * Root Node of this Ultimate model. I use this to store information that should be passed to the next plugin. The
	 * Successors of this node exactly the initial nodes of procedures.
	 */
	private IElement mRootOfNewModel;
	private Result mOverallResult;
	private IElement mArtifact;

	public TraceAbstractionStarter(final IUltimateServiceProvider services, final IToolchainStorage storage,
			final IIcfg<BoogieIcfgLocation> rcfgRootNode,
			final INestedWordAutomatonSimple<WitnessEdge, WitnessNode> witnessAutomaton) {
		mServices = services;
		mToolchainStorage = storage;
		mLogger = mServices.getLoggingService().getLogger(Activator.PLUGIN_ID);
		Server.init(services);
		
		runCegarLoops(rcfgRootNode, witnessAutomaton);
	}

	private void runCegarLoops(final IIcfg<BoogieIcfgLocation> icfg,
			final INestedWordAutomatonSimple<WitnessEdge, WitnessNode> witnessAutomaton) {
		final TAPreferences taPrefs = new TAPreferences(mServices);

		String settings = "Automizer settings:";
		settings += " Hoare:" + taPrefs.computeHoareAnnotation();
		settings += " " + (taPrefs.differenceSenwa() ? "SeNWA" : "NWA");
		settings += " Interpolation:" + taPrefs.interpolation();
		settings += " Determinization: " + taPrefs.interpolantAutomatonEnhancement();
		mLogger.info(settings);

		final CfgSmtToolkit csToolkit = icfg.getCfgSmtToolkit();
		final PredicateFactory predicateFactory = new PredicateFactory(mServices, csToolkit.getManagedScript(),
				csToolkit.getSymbolTable(), taPrefs.getSimplificationTechnique(), taPrefs.getXnfConversionTechnique());
		final TraceAbstractionBenchmarks traceAbstractionBenchmark = new TraceAbstractionBenchmarks(icfg);

		final Map<String, Set<BoogieIcfgLocation>> proc2errNodes = icfg.getProcedureErrorNodes();
		final Collection<BoogieIcfgLocation> errNodesOfAllProc = new ArrayList<>();
		for (final Collection<BoogieIcfgLocation> errNodeOfProc : proc2errNodes.values()) {
			errNodesOfAllProc.addAll(errNodeOfProc);
		}

		mOverallResult = Result.SAFE;
		mArtifact = null;

		if (taPrefs.allErrorLocsAtOnce()) {
			final String name = "AllErrorsAtOnce";
			iterate(name, icfg, taPrefs, csToolkit, predicateFactory, traceAbstractionBenchmark, errNodesOfAllProc,
					witnessAutomaton);
		} else {
			for (final BoogieIcfgLocation errorLoc : errNodesOfAllProc) {
				final String name = errorLoc.getDebugIdentifier();
				final List<BoogieIcfgLocation> errorLocs = new ArrayList<>(1);
				errorLocs.add(errorLoc);
				mServices.getProgressMonitorService().setSubtask(errorLoc.toString());
				iterate(name, icfg, taPrefs, csToolkit, predicateFactory, traceAbstractionBenchmark, errorLocs,
						witnessAutomaton);
			}
		}
		logNumberOfWitnessInvariants(errNodesOfAllProc);
		if (mOverallResult == Result.SAFE) {
			final String longDescription;
			if (errNodesOfAllProc.isEmpty()) {
				longDescription = "We were not able to verify any"
						+ " specifiation because the program does not contain any specification.";
			} else {
				longDescription = errNodesOfAllProc.size() + " specifications checked. All of them hold";
			}
			final AllSpecificationsHoldResult result =
					new AllSpecificationsHoldResult(Activator.PLUGIN_NAME, longDescription);
			reportResult(result);
		}

		mLogger.debug("Compute Hoare Annotation: " + taPrefs.computeHoareAnnotation());
		mLogger.debug("Overall result: " + mOverallResult);
		mLogger.debug("Continue processing: " + mServices.getProgressMonitorService().continueProcessing());
		if (taPrefs.computeHoareAnnotation() && mOverallResult != Result.TIMEOUT
				&& mServices.getProgressMonitorService().continueProcessing()) {
			final IBacktranslationService backTranslatorService = mServices.getBacktranslationService();
			createInvariantResults(icfg, csToolkit, backTranslatorService);
			createProcedureContractResults(icfg, backTranslatorService);
		}
		reportBenchmark(traceAbstractionBenchmark);
		switch (mOverallResult) {
		case SAFE:
		case UNSAFE:
			break;
		case TIMEOUT:
			mLogger.warn("Timeout");
			break;
		case UNKNOWN:
			mLogger.warn("Unable to decide correctness. Please check the following counterexample manually.");
			break;
		default:
			throw new UnsupportedOperationException("Unknown overall result " + mOverallResult);
		}

		mRootOfNewModel = mArtifact;
	}

	private void createInvariantResults(final IIcfg<BoogieIcfgLocation> icfg, final CfgSmtToolkit csToolkit,
			final IBacktranslationService backTranslatorService) {
		assert new HoareAnnotationChecker(mServices, icfg, csToolkit).isInductive() : "incorrect Hoare annotation";

		final Term trueterm = csToolkit.getManagedScript().getScript().term("true");

		final Set<BoogieIcfgLocation> locsForLoopLocations = new HashSet<>();

		locsForLoopLocations.addAll(IcfgUtils.getPotentialCycleProgramPoints(icfg));
		locsForLoopLocations.addAll(icfg.getLoopLocations());
		// find all locations that have outgoing edges which are annotated with LoopEntry, i.e., all loop candidates

		for (final BoogieIcfgLocation locNode : locsForLoopLocations) {
			final HoareAnnotation hoare = HoareAnnotation.getAnnotation(locNode);
			if (hoare == null) {
				continue;
			}
			final Term formula = hoare.getFormula();
			final InvariantResult<IIcfgElement, Term> invResult =
					new InvariantResult<>(Activator.PLUGIN_NAME, locNode, backTranslatorService, formula);
			reportResult(invResult);

			if (formula.equals(trueterm)) {
				continue;
			}
			final String inv = backTranslatorService.translateExpressionToString(formula, Term.class);
			new WitnessInvariant(inv).annotate(locNode);
		}
	}

	private void createProcedureContractResults(final IIcfg<BoogieIcfgLocation> icfg,
			final IBacktranslationService backTranslatorService) {
		final Map<String, BoogieIcfgLocation> finalNodes = icfg.getProcedureExitNodes();
		for (final Entry<String, BoogieIcfgLocation> proc : finalNodes.entrySet()) {
			final String procName = proc.getKey();
			if (isAuxilliaryProcedure(procName)) {
				continue;
			}
			final BoogieIcfgLocation finalNode = proc.getValue();
			final HoareAnnotation hoare = HoareAnnotation.getAnnotation(finalNode);
			if (hoare != null) {
				final Term formula = hoare.getFormula();
				final ProcedureContractResult<IIcfgElement, Term> result = new ProcedureContractResult<>(
						Activator.PLUGIN_NAME, finalNode, backTranslatorService, procName, formula);

				reportResult(result);
				// TODO: Add setting that controls the generation of those witness invariants
			}
		}
	}

	// private void computeHoareAnnotation(final Set<? extends IcfgLocation> locsForHoareAnnotation) {
	// final HoareAnnotationStatisticsGenerator hoareAnnotationStatisticsGenerator = new
	// HoareAnnotationStatisticsGenerator();
	// for (final IcfgLocation locNode : locsForHoareAnnotation) {
	// final HoareAnnotation hoare = getHoareAnnotation(locNode);
	// if (hoare == null) {
	// continue;
	// }
	// hoare.computeFormula(hoareAnnotationStatisticsGenerator);
	// }
	// hoareAnnotationStatisticsGenerator.toString();
	// }

	private void logNumberOfWitnessInvariants(final Collection<BoogieIcfgLocation> errNodesOfAllProc) {
		int numberOfCheckedInvariants = 0;
		for (final BoogieIcfgLocation err : errNodesOfAllProc) {
			final BoogieASTNode boogieASTNode = err.getBoogieASTNode();
			final IAnnotations annot = boogieASTNode.getPayload().getAnnotations().get(Check.class.getName());
			if (annot != null) {
				final Check check = (Check) annot;
				if (check.getSpec() == Spec.WITNESS_INVARIANT) {
					numberOfCheckedInvariants++;
				}
			}
		}
		if (numberOfCheckedInvariants > 0) {
			mLogger.info("Automizer considered " + numberOfCheckedInvariants + " witness invariants");
			mLogger.info("WitnessConsidered=" + numberOfCheckedInvariants);
		}
	}

	private void iterate(final String name, final IIcfg<BoogieIcfgLocation> root, final TAPreferences taPrefs,
			final CfgSmtToolkit csToolkit, final PredicateFactory predicateFactory,
			final TraceAbstractionBenchmarks taBenchmark, final Collection<BoogieIcfgLocation> errorLocs,
			final INestedWordAutomatonSimple<WitnessEdge, WitnessNode> witnessAutomaton) {
		final BasicCegarLoop<?> basicCegarLoop =
				constructCegarLoop(name, root, taPrefs, csToolkit, predicateFactory, taBenchmark, errorLocs);
		basicCegarLoop.setWitnessAutomaton(witnessAutomaton);

		final IProtoServer server = Server.get();
		if (taPrefs.interactive()) {
			mLogger.info("Interactive Verification enabled. Serializing initial abstraction"
					+ " and wait for confirmation from client...");

			server.start();
			server.waitForConnection();
			mLogger.info("client connected.");

			server.hello(null);
			final TraceAbstractionProtos.TAPreferences pmsg = Converter.fromTAPreferences(taPrefs);
			server.send(pmsg);
			// final TraceAbstractionProtos.IterationInfo.Builder info =
			// TraceAbstractionProtos.IterationInfo.newBuilder();
			// info.setIteration(0).setRun(Converter.fromNestedRun(mCounterexample));
			// mServer.send(info.build());
		}

		final Result result = basicCegarLoop.iterate();
		basicCegarLoop.finish();

		mOverallResult = computeOverallResult(errorLocs, basicCegarLoop, result);

		if (taPrefs.computeHoareAnnotation() && mOverallResult == Result.SAFE) {
			mLogger.debug("Computing Hoare annotation of CFG");
			basicCegarLoop.computeCFGHoareAnnotation();

			// final Set<? extends IcfgLocation> locsForHoareAnnotation =
			// TraceAbstractionUtils.getLocationsForWhichHoareAnnotationIsComputed(
			// root, taPrefs.getHoareAnnotationPositions());
			// computeHoareAnnotation(locsForHoareAnnotation);

			writeHoareAnnotationToLogger(root);
		} else {
			mLogger.debug("Ommiting computation of Hoare annotation");

		}

		final CegarLoopStatisticsGenerator cegarLoopBenchmarkGenerator = basicCegarLoop.getCegarLoopBenchmark();
		cegarLoopBenchmarkGenerator.stop(CegarLoopStatisticsDefinitions.OverallTime.toString());
		// TODO: Stop AI clock
		taBenchmark.aggregateBenchmarkData(cegarLoopBenchmarkGenerator);

		mArtifact = basicCegarLoop.getArtifact();
		
		server.stop();
	}

	private BasicCegarLoop<?> constructCegarLoop(final String name, final IIcfg<BoogieIcfgLocation> root,
			final TAPreferences taPrefs, final CfgSmtToolkit csToolkit, final PredicateFactory predicateFactory,
			final TraceAbstractionBenchmarks taBenchmark, final Collection<BoogieIcfgLocation> errorLocs) {
		final LanguageOperation languageOperation = mServices.getPreferenceProvider(Activator.PLUGIN_ID)
				.getEnum(TraceAbstractionPreferenceInitializer.LABEL_LANGUAGE_OPERATION, LanguageOperation.class);
		if (languageOperation == LanguageOperation.DIFFERENCE) {
			if (taPrefs.interpolantAutomaton() == InterpolantAutomaton.TOTALINTERPOLATION) {
				return new CegarLoopSWBnonRecursive<>(name, root, csToolkit, predicateFactory, taBenchmark, taPrefs,
						errorLocs, taPrefs.interpolation(), taPrefs.computeHoareAnnotation(), mServices,
						mToolchainStorage);
			}
			return new BasicCegarLoop<>(name, root, csToolkit, predicateFactory, taPrefs, errorLocs,
					taPrefs.interpolation(), taPrefs.computeHoareAnnotation(), mServices, mToolchainStorage);
		}
		return new IncrementalInclusionCegarLoop<>(name, root, csToolkit, predicateFactory, taPrefs, errorLocs,
				taPrefs.interpolation(), taPrefs.computeHoareAnnotation(), mServices, mToolchainStorage,
				languageOperation);
	}

	private Result computeOverallResult(final Collection<BoogieIcfgLocation> errorLocs,
			final BasicCegarLoop<?> basicCegarLoop, final Result result) {
		switch (result) {
		case SAFE:
			reportPositiveResults(errorLocs);
			return mOverallResult;
		case UNSAFE:
			reportCounterexampleResult(basicCegarLoop.getRcfgProgramExecution());
			return result;
		case TIMEOUT:
			reportTimeoutResult(errorLocs, basicCegarLoop.getRunningTaskStackProvider());
			return mOverallResult != Result.UNSAFE ? result : mOverallResult;
		case UNKNOWN:
			final IcfgProgramExecution pe = basicCegarLoop.getRcfgProgramExecution();
			final List<UnprovabilityReason> unprovabilityReasons = new ArrayList<>();
			unprovabilityReasons.add(basicCegarLoop.getReasonUnknown());
			unprovabilityReasons.addAll(pe.getUnprovabilityReasons());
			reportUnproveableResult(pe, unprovabilityReasons);
			return mOverallResult != Result.UNSAFE ? result : mOverallResult;
		default:
			throw new IllegalArgumentException();
		}
	}

	private void writeHoareAnnotationToLogger(final IIcfg<BoogieIcfgLocation> root) {
		for (final Entry<String, Map<String, BoogieIcfgLocation>> proc2label2pp : root.getProgramPoints().entrySet()) {
			for (final BoogieIcfgLocation pp : proc2label2pp.getValue().values()) {
				final HoareAnnotation hoare = HoareAnnotation.getAnnotation(pp);
				if (hoare == null) {
					mLogger.info("For program point  " + prettyPrintProgramPoint(pp)
							+ "  no Hoare annotation was computed.");
				} else {
					mLogger.info("At program point  " + prettyPrintProgramPoint(pp) + "  the Hoare annotation is:  "
							+ hoare.getFormula());
				}
			}
		}
	}

	private static String prettyPrintProgramPoint(final BoogieIcfgLocation pp) {
		final int startLine = pp.getPayload().getLocation().getStartLine();
		final int endLine = pp.getPayload().getLocation().getStartLine();
		final StringBuilder sb = new StringBuilder();
		sb.append(pp);
		if (startLine == endLine) {
			sb.append("(line " + startLine + ")");
		} else {
			sb.append("(lines " + startLine + " " + endLine + ")");
		}
		return sb.toString();
	}

	private void reportPositiveResults(final Collection<BoogieIcfgLocation> errorLocs) {
		for (final BoogieIcfgLocation errorLoc : errorLocs) {
			final PositiveResult<IIcfgElement> pResult =
					new PositiveResult<>(Activator.PLUGIN_NAME, errorLoc, mServices.getBacktranslationService());
			reportResult(pResult);
		}
	}

	private void reportCounterexampleResult(final IcfgProgramExecution pe) {
		if (!pe.getOverapproximations().isEmpty()) {
			reportUnproveableResult(pe, pe.getUnprovabilityReasons());
			return;
		}
		reportResult(new CounterExampleResult<>(getErrorPP(pe), Activator.PLUGIN_NAME,
				mServices.getBacktranslationService(), pe));
	}

	private void reportTimeoutResult(final Collection<BoogieIcfgLocation> errorLocs,
			final IRunningTaskStackProvider rtsp) {
		for (final BoogieIcfgLocation errorIpp : errorLocs) {
			final BoogieIcfgLocation errorLoc = errorIpp;
			final ILocation origin = errorLoc.getBoogieASTNode().getLocation().getOrigin();
			String timeOutMessage = "Unable to prove that ";
			timeOutMessage += ResultUtil.getCheckedSpecification(errorLoc).getPositiveMessage();
			timeOutMessage += " (line " + origin.getStartLine() + ").";
			if (rtsp != null) {
				timeOutMessage += " Cancelled " + rtsp.printRunningTaskMessage();
			}
			final TimeoutResultAtElement<IIcfgElement> timeOutRes = new TimeoutResultAtElement<>(errorLoc,
					Activator.PLUGIN_NAME, mServices.getBacktranslationService(), timeOutMessage);
			reportResult(timeOutRes);
		}
	}

	private void reportUnproveableResult(final IcfgProgramExecution pe,
			final List<UnprovabilityReason> unproabilityReasons) {
		final IcfgLocation errorPP = getErrorPP(pe);
		reportResult(new UnprovableResult<>(Activator.PLUGIN_NAME, errorPP, mServices.getBacktranslationService(), pe,
				unproabilityReasons));
	}

	private <T> void reportBenchmark(final ICsvProviderProvider<T> benchmark) {
		final String shortDescription = "Ultimate Automizer benchmark data";
		final BenchmarkResult<T> res = new BenchmarkResult<>(Activator.PLUGIN_NAME, shortDescription, benchmark);
		reportResult(res);
	}

	private static boolean isAuxilliaryProcedure(final String proc) {
		return "ULTIMATE.init".equals(proc) || "ULTIMATE.start".equals(proc);
	}

	private void reportResult(final IResult res) {
		mServices.getResultService().reportResult(Activator.PLUGIN_ID, res);
	}

	/**
	 * @return the root of the CFG.
	 */
	public IElement getRootOfNewModel() {
		return mRootOfNewModel;
	}

	public static IcfgLocation getErrorPP(final IcfgProgramExecution rcfgProgramExecution) {
		final int lastPosition = rcfgProgramExecution.getLength() - 1;
		final IIcfgTransition<?> last = rcfgProgramExecution.getTraceElement(lastPosition).getTraceElement();
		return last.getTarget();
	}

	private boolean interpolationModeSwitchNeeded() {
		final SolverMode solver = mServices.getPreferenceProvider(Activator.PLUGIN_ID)
				.getEnum(RcfgPreferenceInitializer.LABEL_Solver, SolverMode.class);
		return solver == SolverMode.External_PrincessInterpolationMode;
	}
}
