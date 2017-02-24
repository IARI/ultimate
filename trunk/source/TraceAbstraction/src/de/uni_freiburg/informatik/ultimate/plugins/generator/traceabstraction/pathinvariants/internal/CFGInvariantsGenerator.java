/*
 * Copyright (C) 2015 Dirk Steinmetz
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
package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.pathinvariants.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.core.lib.exceptions.ToolchainCanceledException;
import de.uni_freiburg.informatik.ultimate.core.lib.results.BenchmarkResult;
import de.uni_freiburg.informatik.ultimate.core.model.results.IResult;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IProgressAwareTimer;
import de.uni_freiburg.informatik.ultimate.core.model.services.IToolchainStorage;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;
import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.IcfgUtils;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IInternalAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgInternalTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.TransFormulaBuilder;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVarOrConst;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.SimplificationTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.XnfConversionTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SolverBuilder.Settings;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicateUnifier;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.nonrelational.livevariable.LiveVariableState;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.tool.AbstractInterpreter;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.tool.IAbstractInterpretationResult;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.Activator;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.pathinvariants.InvariantSynthesisSettings;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.pathinvariants.LargeBlockEncodingIcfgTransformer;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.pathinvariants.NonInductiveAnnotationGenerator;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.pathinvariants.NonInductiveAnnotationGenerator.Approximation;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.pathinvariants.internal.LinearInequalityInvariantPatternProcessor.LinearInequalityPatternProcessorStatistics;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.HashRelation;
import de.uni_freiburg.informatik.ultimate.util.statistics.StatisticsData;

/**
 * A generator for a map of invariants to {@link ControlFlowGraph.Location}s within a {@link ControlFlowGraph}, using a
 * {@link IInvariantPatternProcessor} .
 */
public final class CFGInvariantsGenerator {

	// There are two different ways to add an additional predicate to the invariant templates/patterns.
	// 1. We add the predicate to each disjunct as an additional conjunct, or
	// 2. we add the predicate as an additional disjunct.
	private static final boolean ADD_WP_TO_EACH_CONJUNCT = true;
	
	private static final boolean USE_UNSAT_CORES_FOR_DYNAMIC_PATTERN_CHANGES = true;
	private static final boolean USE_DYNAMIC_PATTERN_WITH_BOUNDS = false;
	
	/**
	 * @see {@link DynamicPatternSettingsStrategyWithGlobalTemplateLevel}
	 */
	private static final boolean USE_DYNAMIC_PATTERN_CHANGES_WITH_GLOBAL_TEMPLATE_LEVEL = false;
	

	private static final boolean USE_UNDER_APPROX_FOR_MAX_CONJUNCTS = false;
	private static final boolean USE_OVER_APPROX_FOR_MIN_DISJUNCTS = false;

	/**
	 * If set to true, we always construct two copies of each invariant pattern, one strict inequality and one
	 * non-strict inequality. If set to false we use only one non-strict inequality.
	 */
	private static final boolean ALWAYS_STRICT_AND_NON_STRICT_COPIES = false;
	/**
	 * If a template contains more than 1 conjunct, then use alternatingly strict and non-strict inequalities. I.e. the
	 * even conjuncts are strict whereas the odd conjuncts are non-strict inequalities.
	 */
	private static final boolean USE_STRICT_INEQUALITIES_ALTERNATINGLY = false;
	/**
	 * Transform the path program by applying large block encoding. Synthesize invariants only for the large block
	 * encoded program and use less expensive techniques to obtain the remaining invariants.
	 */
	private static final boolean APPLY_LARGE_BLOCK_ENCODING = true;

	private static final int MAX_ROUNDS = Integer.MAX_VALUE;
	
	private final ILogger mLogger;
	private final IUltimateServiceProvider mServices;


	private static final boolean USE_LIVE_VARIABLES = true;
	
	
	private final IToolchainStorage mStorage;
	private IPredicateUnifier mPredicateUnifier;
	private IPredicate mPrecondition;
	private IPredicate mPostcondition;
	private IIcfg<IcfgLocation> mPathProgram;
	
	private final PathInvariantsStatisticsGenerator mPathInvariantsStatistics;
	private final Map<Integer, PathInvariantsStatisticsGenerator> mRound2PathInvariantsStatistics;
	private InvariantSynthesisSettings mInvariantSynthesisSettings;
	private CfgSmtToolkit mCsToolKit;
	/**
	 * Report a {@link BenchmarkResult} for every round.
	 */
	private static final boolean TEMPLATE_STATISTICS_MODE = true;

	private static boolean INIT_USE_EMPTY_PATTERNS = true;
	private static boolean USE_VARS_FROM_UNSAT_CORE_FOR_EACH_LOC = true;

	/**
	 * 
	 * @param pathProgram
	 * @param services
	 * @param storage
	 * @param precondition
	 * @param postcondition
	 * @param predicateUnifier
	 * @param invariantSynthesisSettings
	 * @param csToolkit
	 */
	public CFGInvariantsGenerator(final IIcfg<IcfgLocation> pathProgram, final IUltimateServiceProvider services, final IToolchainStorage storage, 
			final IPredicate precondition, final IPredicate postcondition, 
			final IPredicateUnifier predicateUnifier, 
			final InvariantSynthesisSettings invariantSynthesisSettings,
			final CfgSmtToolkit csToolkit) {
		mStorage = storage;
		mServices = services;
		mLogger = services.getLoggingService().getLogger(Activator.PLUGIN_ID);
		mCsToolKit = csToolkit;
		
		mPredicateUnifier = predicateUnifier;
		mPrecondition = precondition;
		mPostcondition = postcondition;
		mPathProgram = pathProgram;
		mInvariantSynthesisSettings = invariantSynthesisSettings;
		mPathInvariantsStatistics = new PathInvariantsStatisticsGenerator();
		// Initialize statistics
		mPathInvariantsStatistics.initializeStatistics();
		mRound2PathInvariantsStatistics = new HashMap<>();
	}
	
	/**
	 * Creates a default factory.
	 *
	 * @param services
	 *            Service provider to use, for example for logging and timeouts
	 * @param storage
	 *            IToolchainstorage of the current Ultimate toolchain.
	 * @param predicateUnifier
	 *            the predicate unifier to unify final predicates with
	 * @param csToolkit
	 *            the smt manager for constructing the default {@link IInvariantPatternProcessorFactory}
	 * @param simplicationTechnique
	 * @param xnfConversionTechnique
	 * @param axioms
	 * @return a default invariant pattern processor factory
	 */
	private static IInvariantPatternProcessorFactory<?> createDefaultFactory(final IUltimateServiceProvider services,
			final IToolchainStorage storage, final IPredicateUnifier predicateUnifier, final CfgSmtToolkit csToolkit,
			final boolean useNonlinerConstraints, final boolean useVarsFromUnsatCore, final Settings solverSettings,
			final SimplificationTechnique simplicationTechnique, final XnfConversionTechnique xnfConversionTechnique,
			final ILinearInequalityInvariantPatternStrategy<Collection<Collection<AbstractLinearInvariantPattern>>> strategy,
			final Map<IcfgLocation, UnmodifiableTransFormula> loc2underApprox,
			final Map<IcfgLocation, UnmodifiableTransFormula> loc2overApprox) {

		return new LinearInequalityInvariantPatternProcessorFactory(services, storage, predicateUnifier, csToolkit,
				strategy, useNonlinerConstraints, useVarsFromUnsatCore, solverSettings, simplicationTechnique, xnfConversionTechnique, 
				csToolkit.getAxioms(), loc2underApprox, loc2overApprox);
	}

	private static ILinearInequalityInvariantPatternStrategy<Collection<Collection<AbstractLinearInvariantPattern>>>
	getStrategy(final boolean useVarsFromUnsatCore, final boolean useLiveVars,
			final Set<IProgramVar> allProgramVariables,
			final Map<IcfgLocation, Set<IProgramVar>> locations2LiveVariables) {
		if (useVarsFromUnsatCore) {
			if (USE_UNSAT_CORES_FOR_DYNAMIC_PATTERN_CHANGES) {
				if (USE_DYNAMIC_PATTERN_WITH_BOUNDS) {
					return new DynamicPatternSettingsStrategyWithBounds(1, 1, 1, 1, MAX_ROUNDS, allProgramVariables,
							locations2LiveVariables, ALWAYS_STRICT_AND_NON_STRICT_COPIES,
							USE_STRICT_INEQUALITIES_ALTERNATINGLY);
				}
				if (USE_DYNAMIC_PATTERN_CHANGES_WITH_GLOBAL_TEMPLATE_LEVEL) {
					return new DynamicPatternSettingsStrategyWithGlobalTemplateLevel(1, 1, 1, 1, MAX_ROUNDS,
							allProgramVariables, locations2LiveVariables, ALWAYS_STRICT_AND_NON_STRICT_COPIES,
							USE_STRICT_INEQUALITIES_ALTERNATINGLY);
				}
				return new DynamicPatternSettingsStrategy(1, 2, 1, 1, MAX_ROUNDS, allProgramVariables,
						locations2LiveVariables, ALWAYS_STRICT_AND_NON_STRICT_COPIES,
						USE_STRICT_INEQUALITIES_ALTERNATINGLY);
			}
			return new VarsInUnsatCoreStrategy(1, 1, 1, 1, MAX_ROUNDS, allProgramVariables, locations2LiveVariables,
					ALWAYS_STRICT_AND_NON_STRICT_COPIES, USE_STRICT_INEQUALITIES_ALTERNATINGLY);
		} else if (useLiveVars) {
			return new LiveVariablesStrategy(1, 1, 1, 1, MAX_ROUNDS, allProgramVariables, locations2LiveVariables,
					ALWAYS_STRICT_AND_NON_STRICT_COPIES, USE_STRICT_INEQUALITIES_ALTERNATINGLY);
		}
		return new AllProgramVariablesStrategy(1, 1, 1, 1, MAX_ROUNDS, allProgramVariables, allProgramVariables,
				ALWAYS_STRICT_AND_NON_STRICT_COPIES, USE_STRICT_INEQUALITIES_ALTERNATINGLY);
	}
	

	private Map<IcfgLocation, IPredicate> generateInvariantsForPathProgram(final IIcfg<IcfgLocation> pathProgram, final SimplificationTechnique simplificationTechnique, 
			final XnfConversionTechnique xnfConversionTechnique, final CfgSmtToolkit csToolkit,
			final InvariantSynthesisSettings invSynthSettings) {

		final IcfgLocation startLocation = new ArrayList<>(pathProgram.getInitialNodes()).get(0);
		final IcfgLocation errorLocation = extractErrorLocationFromPathProgram(pathProgram);
		final List<IcfgLocation> locationsAsList = new ArrayList<>();
		final List<IcfgInternalTransition> transitionsAsList = new ArrayList<>();
		final Set<IProgramVar> allProgramVars = new HashSet<>();
		// Get locations, transitions and program variables from the path program
		extractLocationsTransitionsAndVariablesFromPathProgram(pathProgram, locationsAsList, transitionsAsList,
				allProgramVars);
		mLogger.info("Built projected CFG, " + locationsAsList.size() + " states and "
				+ transitionsAsList.size() + " transitions.");
		Map<IcfgLocation, Set<IProgramVar>> pathprogramLocs2LiveVars = null;

		if (USE_LIVE_VARIABLES) {
			pathprogramLocs2LiveVars = generateLiveVariables(pathProgram);
			// At the initial location no variable is live
			pathprogramLocs2LiveVars.put(startLocation, new HashSet<IProgramVar>());
			if (mLogger.isDebugEnabled()) {
				mLogger.debug("Live variables computed: " + pathprogramLocs2LiveVars);
			}
		}
		Map<IcfgLocation, UnmodifiableTransFormula> loc2underApprox = null;
		Map<IcfgLocation, UnmodifiableTransFormula> loc2overApprox = null;

		if (invSynthSettings.useUnsatCores()) {
			// Compute under-/overapproximation only if we use unsat cores during invariant synthesis
			final NonInductiveAnnotationGenerator underApprox = new NonInductiveAnnotationGenerator(mServices,
					mPredicateUnifier.getPredicateFactory(), pathProgram, Approximation.UNDERAPPROXIMATION);
			loc2underApprox =
					convertHashRelation(underApprox.getResult(), csToolkit.getManagedScript());
		}
		if (invSynthSettings.useUnsatCores() || invSynthSettings.useWeakestPrecondition()) {
			final NonInductiveAnnotationGenerator overApprox = new NonInductiveAnnotationGenerator(mServices,
					mPredicateUnifier.getPredicateFactory(), pathProgram, Approximation.OVERAPPROXIMATION);
			loc2overApprox =
					convertHashRelation(overApprox.getResult(), csToolkit.getManagedScript());
		}
		final Map<IcfgLocation, UnmodifiableTransFormula> pathprogramLocs2Predicates = new HashMap<>();
		if (invSynthSettings.useWeakestPrecondition()) {
			pathprogramLocs2Predicates.putAll(loc2overApprox);
		}

		if (invSynthSettings.useAbstractInterpretation()) {
			// TODO: Implement the computation of predicates via abstract interpretation
//			pathprogramLocs2Predicates.putAll(extractAbstractInterpretationPredicates(mAbstractInterpretationResult,
//					csToolkit.getManagedScript()));
		}

		final ILinearInequalityInvariantPatternStrategy<Collection<Collection<AbstractLinearInvariantPattern>>> strategy =
				getStrategy(invSynthSettings.useUnsatCores(), USE_LIVE_VARIABLES, allProgramVars, pathprogramLocs2LiveVars);

		if (USE_UNDER_APPROX_FOR_MAX_CONJUNCTS) {
			for (final Map.Entry<IcfgLocation, UnmodifiableTransFormula> entry : loc2underApprox.entrySet()) {
				final List<Integer> maxDisjunctsMaxConjuncts =
						getDisjunctsAndConjunctsFromTerm(entry.getValue().getFormula());
				strategy.setNumOfConjunctsForLocation(entry.getKey(), maxDisjunctsMaxConjuncts.get(1));
			}
		} else if (USE_OVER_APPROX_FOR_MIN_DISJUNCTS) {
			for (final Map.Entry<IcfgLocation, UnmodifiableTransFormula> entry : loc2underApprox.entrySet()) {
				final List<Integer> maxDisjunctsMaxConjuncts =
						getDisjunctsAndConjunctsFromTerm(entry.getValue().getFormula());
				strategy.setNumOfDisjunctsForLocation(entry.getKey(), maxDisjunctsMaxConjuncts.get(0));
			}
		}
		final IInvariantPatternProcessorFactory<?> invPatternProcFactory = createDefaultFactory(mServices, mStorage,
				mPredicateUnifier, csToolkit, invSynthSettings.useNonLinearConstraints(), invSynthSettings.useUnsatCores(),
				invSynthSettings.getSolverSettings(), simplificationTechnique, xnfConversionTechnique,
				strategy, loc2underApprox, loc2overApprox);

		Map<IcfgLocation, IPredicate> invariants = generateInvariantsForTransitions(locationsAsList, transitionsAsList, mPrecondition,
				mPostcondition, startLocation, errorLocation, invPatternProcFactory, invSynthSettings.useUnsatCores(),
				allProgramVars, pathprogramLocs2LiveVars, pathprogramLocs2Predicates,
				invSynthSettings.useWeakestPrecondition() || invSynthSettings.useAbstractInterpretation(), ADD_WP_TO_EACH_CONJUNCT);
		mLogger.info("Generated invariant map.");

		return invariants;
	}
	
	private Map<IcfgLocation, UnmodifiableTransFormula> extractAbstractInterpretationPredicates(
			final IAbstractInterpretationResult<LiveVariableState<IcfgEdge>, IcfgEdge, IProgramVarOrConst, IcfgLocation> abstractInterpretationResult,
			final ManagedScript managedScript) {
		final Map<IcfgLocation, UnmodifiableTransFormula> result = new HashMap<>();
		final Map<IcfgLocation, Term> locs2term = abstractInterpretationResult.getLoc2Term();
		final ArrayList<Term> termsAsList = new ArrayList<>(abstractInterpretationResult.getTerms());
		// If the only predicate found by Abstract Interpretation is 'true', then return the empty map, as the predicate
		// 'true' is not helpful.
		if (termsAsList.isEmpty() || termsAsList.size() == 1 && "true".equals(termsAsList.get(0).toString())) {
			return result;
		}
		for (final Map.Entry<IcfgLocation, Term> entry : locs2term.entrySet()) {
			result.put(entry.getKey(), TransFormulaBuilder.constructTransFormulaFromPredicate(
					mPredicateUnifier.getOrConstructPredicate(entry.getValue()), managedScript));
		}
		return result;
	}

	private static IcfgLocation extractErrorLocationFromPathProgram(final IIcfg<IcfgLocation> pathProgram) {
		final Set<IcfgLocation> errorLocs = IcfgUtils.getErrorLocations(pathProgram);
		assert errorLocs.size() == 1 : "CFGInvariantsGenerator currently supports CFGs with only one error location";
		return errorLocs.iterator().next();
	}


	private static Map<IcfgLocation, UnmodifiableTransFormula> convertHashRelation(
			final HashRelation<IcfgLocation, IPredicate> loc2SetOfPreds, final ManagedScript managedScript) {

		final Map<IcfgLocation, IPredicate> loc2Predicate = new HashMap<>(loc2SetOfPreds.getDomain().size());
		for (final IcfgLocation loc : loc2SetOfPreds.getDomain()) {
			final List<IPredicate> preds = new ArrayList<>(loc2SetOfPreds.getImage(loc).size());
			preds.addAll(loc2SetOfPreds.getImage(loc));
			// Currently, we use only one predicate
			loc2Predicate.put(loc, preds.get(0));
		}
		return convertMapToPredsToMapToUnmodTrans(loc2Predicate, managedScript);
	}

	private static void extractLocationsTransitionsAndVariablesFromPathProgram(final IIcfg<IcfgLocation> pathProgram,
			final List<IcfgLocation> locationsOfPP, final List<IcfgInternalTransition> transitionsOfPP,
			final Set<IProgramVar> allVariablesFromPP) {
		final LinkedList<IcfgLocation> locs2visit = new LinkedList<>();
		locs2visit.addAll(pathProgram.getInitialNodes());
		final LinkedHashSet<IcfgLocation> visitedLocs = new LinkedHashSet<>();
		final LinkedList<IcfgInternalTransition> edges = new LinkedList<>();
		while (!locs2visit.isEmpty()) {
			final IcfgLocation loc = locs2visit.removeFirst();
			if (visitedLocs.add(loc)) {
				for (final IcfgEdge e : loc.getOutgoingEdges()) {
					locs2visit.addLast(e.getTarget());
					if (!(e instanceof IInternalAction)) {
						throw new UnsupportedOperationException("interprocedural path programs are not supported (yet)");
					}
					final UnmodifiableTransFormula tf = ((IInternalAction) e).getTransformula();
					allVariablesFromPP.addAll(tf.getInVars().keySet());
					allVariablesFromPP.addAll(tf.getOutVars().keySet());
					edges.addLast(new IcfgInternalTransition(e.getSource(), e.getTarget(), e.getPayload(), tf));
				}
			}
		}
		locationsOfPP.addAll(visitedLocs);
		transitionsOfPP.addAll(edges);
	}

	private static List<Integer> getDisjunctsAndConjunctsFromTerm(final Term term) {
		final List<Integer> result = new ArrayList<>(2);
		int maxNumOfConjuncts = 1;
		int maxNumOfDisjuncts = 1;
		final ArrayList<Term> termsToCheck = new ArrayList<>();
		termsToCheck.add(term);
		while (!termsToCheck.isEmpty()) {
			final Term t = termsToCheck.remove(0);
			if (t instanceof ApplicationTerm) {
				final ApplicationTerm at = (ApplicationTerm) t;
				if ("and".equals(at.getFunction().getName())) {
					if (at.getParameters().length > maxNumOfConjuncts) {
						maxNumOfConjuncts = at.getParameters().length;
					}
				} else if ("or".equals(at.getFunction().getName())) {
					if (at.getParameters().length > maxNumOfDisjuncts) {
						maxNumOfDisjuncts = at.getParameters().length;
					}
				}
				for (final Term param : at.getParameters()) {
					termsToCheck.add(param);
				}

			}
		}
		result.add(0, maxNumOfDisjuncts);
		result.add(1, maxNumOfConjuncts);
		return result;
	}
	

//	private IAbstractInterpretationResult<LiveVariableState<IcfgEdge>, IcfgEdge, IProgramVarOrConst, IcfgLocation>
//	applyAbstractInterpretationOnPathProgram(final IIcfg<IcfgLocation> pathProgram) {
//
//	}
	
	/**
	 * Computes for each location of the given path program a set of variables which are <emph> live </emph>.
	 *
	 * @param pathProgram
	 * @return
	 */
	private Map<IcfgLocation, Set<IProgramVar>> generateLiveVariables(final IIcfg<IcfgLocation> pathProgram) {
		// allow for 20% of the remaining time
		final IProgressAwareTimer timer = mServices.getProgressMonitorService().getChildTimer(0.2);
		final Map<IcfgLocation, LiveVariableState<IcfgEdge>> loc2states = 
				AbstractInterpreter.runFutureLiveVariableDomain(pathProgram, timer, mServices, true, mLogger).getLoc2SingleStates();
		Map<IcfgLocation, Set<IProgramVar>> pathprogramLocs2LiveVars = new HashMap<>();

		for (final Entry<IcfgLocation, LiveVariableState<IcfgEdge>> entry : loc2states.entrySet()) {
			pathprogramLocs2LiveVars.put(entry.getKey(), entry.getValue().getLiveVariablesAsProgramVars());
		}
		return pathprogramLocs2LiveVars;
	}

	/**
	 * Attempts to generate an invariant map for a given CFG (pair of locations and transitions) using a
	 * {@link IInvariantPatternProcessor} from the given {@link IInvariantPatternProcessorFactory}.
	 * 
	 * The {@link IInvariantPatternProcessor} will be used for up to {@link IInvariantPatternProcessor#getMaxRounds()}
	 * attempts to generate such an invariant map. If all attempts fail, this method returns null.
	 * 
	 * @param <IPT>
	 *            Invariant Pattern Type: Type used for invariant patterns
	 * @param precondition

	 * @param postcondition

	 * @param invPatternProcFactory
	 *            the factory to produce the {@link IInvariantPatternProcessor} with
	 * @return the invariant map for the set of given locations or null if the processor failed to process its invariant
	 *         patterns up to its final run.
	 */
	private <IPT> Map<IcfgLocation, IPredicate> generateInvariantsForTransitions(final List<IcfgLocation> locationsAsList, 
			final List<IcfgInternalTransition> transitions,
			final IPredicate precondition, final IPredicate postcondition, final IcfgLocation startLocation, final IcfgLocation errorLocation,
			final IInvariantPatternProcessorFactory<IPT> invPatternProcFactory, final boolean useUnsatCore, 
			final Set<IProgramVar> allProgramVars, final Map<IcfgLocation, Set<IProgramVar>> locs2LiveVariables,
			final Map<IcfgLocation, UnmodifiableTransFormula> pathprogramLocs2Predicates, final boolean usePredicates,
			final boolean addWPToEeachDisjunct) {
		final IInvariantPatternProcessor<IPT> processor = invPatternProcFactory.produce(locationsAsList, transitions, precondition,
				postcondition, startLocation, errorLocation);
		mLogger.info("(Path)program has " + locationsAsList.size() + " locations");
		// Set statistics data
		mPathInvariantsStatistics.setNumOfPathProgramLocations(locationsAsList.size());
		mPathInvariantsStatistics.setNumOfVars(allProgramVars.size());

		final Map<IcfgLocation, IPT> locs2Patterns = new HashMap<IcfgLocation, IPT>(locationsAsList.size());
		final Map<IcfgLocation, Set<IProgramVar>> locs2PatternVariables = new HashMap<IcfgLocation, Set<IProgramVar>>(locationsAsList.size());

		final Collection<InvariantTransitionPredicate<IPT>> predicates = new ArrayList<InvariantTransitionPredicate<IPT>>(
				transitions.size() + 2);

		final Map<TermVariable, IProgramVar> smtVars2ProgramVars = new HashMap<>();
		if (useUnsatCore) {
			// Compute map smt-variables to program variables
			for (final IcfgInternalTransition t : transitions) {
				// Add value-key-pairs from transitions invars to smtVars2ProgramVars
				for (final Map.Entry<IProgramVar, TermVariable> entry : t.getTransformula().getInVars().entrySet()) {
					smtVars2ProgramVars.put(entry.getValue(), entry.getKey());
				}
				// Add value-key-pairs from transitions outvars to smtVars2ProgramVars
				for (final Map.Entry<IProgramVar, TermVariable> entry : t.getTransformula().getOutVars().entrySet()) {
					smtVars2ProgramVars.put(entry.getValue(), entry.getKey());
				}
			}
		}
		Set<IProgramVar> varsFromUnsatCore = new HashSet<>();
		if (useUnsatCore && INIT_USE_EMPTY_PATTERNS) {
			// Execute pre-round with empty patterns for intermediate locations, so we can use the variables from the unsat core
			final Map<IcfgLocation, IPredicate> resultFromPreRound = executePreRoundWithEmptyPatterns(processor, 0, varsFromUnsatCore, locs2Patterns, locs2PatternVariables,
					predicates, smtVars2ProgramVars, startLocation, errorLocation, locationsAsList, transitions, allProgramVars,
					pathprogramLocs2Predicates, usePredicates, addWPToEeachDisjunct);
			if (resultFromPreRound != null) {
				return resultFromPreRound;
			}
		}
		for (int round = 1; round < processor.getMaxRounds(); round++) {

			// Die if we run into timeouts or are otherwise requested to cancel.
			if (!mServices.getProgressMonitorService().continueProcessing()) {
				throw new ToolchainCanceledException(CFGInvariantsGenerator.class);
			}

			// Start round
			processor.startRound(round);
			mLogger.info("Round " + round + " started");

			// Build pattern map
			locs2Patterns.clear();
			locs2PatternVariables.clear();
			// Init the entry pattern with 'true' and the exit pattern with 'false'
			processor.initializeEntryAndExitPattern();
			for (final IcfgLocation location : locationsAsList) {
				if(useUnsatCore && USE_VARS_FROM_UNSAT_CORE_FOR_EACH_LOC && round > 0) {
					locs2Patterns.put(location, processor.getInvariantPatternForLocation(location, round, varsFromUnsatCore));
				} else {
					locs2Patterns.put(location, processor.getInvariantPatternForLocation(location, round));
				}
				locs2PatternVariables.put(location, processor.getVariablesForInvariantPattern(location, round));
			}
			// add the weakest precondition of the last transition to each pattern
			if (usePredicates && pathprogramLocs2Predicates != null) {
				//				addWeakestPreconditinoOfLastTransitionToPatterns(locationsAsList, processor, patterns, pathprogramLocs2WP, addWPToEeachDisjunct);
				addWP_PredicatesToInvariantPatterns(processor, locs2Patterns, locs2PatternVariables, pathprogramLocs2Predicates, addWPToEeachDisjunct);
			}
			mLogger.info("Built pattern map.");

			// Build transition predicates
			predicates.clear();
			int sumOfTemplateConjuncts = 0;
			int minimalTemplateSizeOfThisRound = Integer.MAX_VALUE;
			int maximalTemplateSizeOfThisRound = 0;
			for (final IcfgInternalTransition transition : transitions) {
				final IPT invStart = locs2Patterns.get(transition.getSource());
				final IPT invEnd = locs2Patterns.get(transition.getTarget());
				predicates.add(new InvariantTransitionPredicate<IPT>(invStart, invEnd, transition.getSource(), transition.getTarget(), 
						locs2PatternVariables.get(transition.getSource()),
						locs2PatternVariables.get(transition.getTarget()), transition.getTransformula()));
				// Compute the benchmarks
				@SuppressWarnings("unchecked")
				final int sizeOfTemplate2 = ((LinearInequalityInvariantPatternProcessor)processor).getTotalNumberOfConjunctsInPattern(
						(Collection<Collection<AbstractLinearInvariantPattern>>) invEnd);
				// Compute the total size of all non-trivial templates
				sumOfTemplateConjuncts = sumOfTemplateConjuncts + sizeOfTemplate2;
				if (transition.getTarget() != errorLocation) {
					if (sizeOfTemplate2 < minimalTemplateSizeOfThisRound) {
						minimalTemplateSizeOfThisRound = sizeOfTemplate2;
					}
					if (sizeOfTemplate2 > maximalTemplateSizeOfThisRound) {
						maximalTemplateSizeOfThisRound = sizeOfTemplate2;
					}
				}
			}
			mLogger.info("Built " + predicates.size() + " predicates.");

			// Set statistics before check sat
			prepareAndSetPathInvariantsStatisticsBeforeCheckSat(locationsAsList, startLocation, errorLocation, allProgramVars, locs2LiveVariables, 
					sumOfTemplateConjuncts, minimalTemplateSizeOfThisRound, maximalTemplateSizeOfThisRound, round);


			// Attempt to find a valid configuration
			final LBool constraintsResult = processor.checkForValidConfiguration(predicates, round);

			Set<IcfgLocation> locsInUnsatCore = null;
			varsFromUnsatCore = null;

			if (constraintsResult == LBool.UNSAT) {
				if (useUnsatCore) {
					// Set benchmarks
					locsInUnsatCore = ((LinearInequalityInvariantPatternProcessor)processor).getLocationsInUnsatCore();
					// If no configuration could have been found, the constraints may be unsatisfiable
					//				if (useVariablesFromUnsatCore) {
					final Collection<TermVariable> smtVarsFromUnsatCore = ((LinearInequalityInvariantPatternProcessor)processor).getVarsFromUnsatCore();
					if (smtVarsFromUnsatCore != null) {
						mLogger.info(smtVarsFromUnsatCore.size() + " out of " + smtVars2ProgramVars.size() + " SMT variables in unsat core");
						// The result in pattern processor was 'unsat'
						varsFromUnsatCore = new HashSet<>(smtVarsFromUnsatCore.size());
						for (final TermVariable smtVar : smtVarsFromUnsatCore) {
							if (smtVars2ProgramVars.get(smtVar) != null) {
								varsFromUnsatCore.add(smtVars2ProgramVars.get(smtVar));
							}
						}
						if (mLogger.isDebugEnabled()) {
							mLogger.debug("Vars in unsat core: " + varsFromUnsatCore);
						}
						mLogger.info(varsFromUnsatCore.size() + " out of " + (new HashSet<>(smtVars2ProgramVars.values())).size() + " program variables in unsat core");
						mLogger.info(locsInUnsatCore.size() + " out of " + locationsAsList.size() + " locations in unsat core");
					}
				} else {
					// The result in pattern processor was 'unknown', so reset varsFromUnsatCore to null
					varsFromUnsatCore = null;
				}

			} 
			// Set statistics after check sat
			final Map<LinearInequalityPatternProcessorStatistics, Object> stats = ((LinearInequalityInvariantPatternProcessor)processor).getStatistics();			
			prepareAndSetPathInvariantsStatisticsAfterCheckSat(locationsAsList, locsInUnsatCore, startLocation, errorLocation, allProgramVars, 
					varsFromUnsatCore,  round, constraintsResult.toString(), stats);

			if (TEMPLATE_STATISTICS_MODE) {
				final StatisticsData stat = new StatisticsData();
				stat.aggregateBenchmarkData(mRound2PathInvariantsStatistics.get(round));
				final IResult benchmarkResult =	new BenchmarkResult<>(Activator.PLUGIN_ID, "InvariantSynthesisStatistics", stat);
				mServices.getResultService().reportResult(Activator.PLUGIN_ID, benchmarkResult);
			}

			if (constraintsResult == LBool.SAT) {
				mLogger.info("Found valid " + "configuration in round " + round + ".");

				final Map<IcfgLocation, IPredicate> result = new HashMap<IcfgLocation, IPredicate>(
						locationsAsList.size());
				// Extract the values for all pattern coefficients
				processor.extractValuesForPatternCoefficients();
				// Apply configuration for each pair (location, pattern) in order to obtain a predicate for each location.
				for (final IcfgLocation location : locationsAsList) {
					result.put(location, processor.applyConfiguration(locs2Patterns.get(location)));
				}
				return result;
			} else if (constraintsResult == LBool.UNKNOWN) {
				mLogger.info("Got \"UNKNOWN\" in round " + round + ", give up the invariant search.");
				break;
			}
		}
		return null;
	}
	
	private static Map<IcfgLocation, UnmodifiableTransFormula> convertMapToPredsToMapToUnmodTrans(
			final Map<IcfgLocation, IPredicate> locs2Preds, final ManagedScript managedScript) {

		final Map<IcfgLocation, UnmodifiableTransFormula> result =
				locs2Preds.keySet().stream().collect(Collectors.toMap(loc -> loc, loc -> TransFormulaBuilder
						.constructTransFormulaFromPredicate(locs2Preds.get(loc), managedScript)));
		return result;
	}

	public Map<Integer, PathInvariantsStatisticsGenerator> getRound2PathInvariantsStatistics() {
		return mRound2PathInvariantsStatistics;
	}
	
	public final PathInvariantsStatisticsGenerator getInvariantSynthesisStatistics() {
		return mPathInvariantsStatistics;
	}

	private void prepareAndSetPathInvariantsStatisticsBeforeCheckSat(final List<IcfgLocation> locationsAsList, final IcfgLocation startLoc, final IcfgLocation errorLoc, final Set<IProgramVar> allProgramVars, 
			final Map<IcfgLocation, Set<IProgramVar>> locs2LiveVariables, final int numOfTemplateInequalitiesForThisRound, 
			final int minimalTemplateSizeOfThisRound, final int maximalTemplateSizeOfThisRound,
			final int round) {
		final int sumOfVarsPerLoc = allProgramVars.size() * (locationsAsList.size() - 2);
		int numOfNonLiveVariables = 0;
		for (final IcfgLocation loc : locationsAsList) {
			if (loc != startLoc && loc != errorLoc) {
				if (locs2LiveVariables != null) {
					if (locs2LiveVariables.containsKey(loc)) {
						numOfNonLiveVariables += allProgramVars.size() - locs2LiveVariables.get(loc).size();
					} 
				}
			}
		}
		mPathInvariantsStatistics.addStatisticsDataBeforeCheckSat(numOfTemplateInequalitiesForThisRound, maximalTemplateSizeOfThisRound, minimalTemplateSizeOfThisRound, 
				sumOfVarsPerLoc, numOfNonLiveVariables, round);
		final PathInvariantsStatisticsGenerator pathInvariantsStatisticsForThisRound = new PathInvariantsStatisticsGenerator();
		pathInvariantsStatisticsForThisRound.initializeStatistics();
		pathInvariantsStatisticsForThisRound.setNumOfPathProgramLocations(locationsAsList.size());
		pathInvariantsStatisticsForThisRound.setNumOfVars(allProgramVars.size());
		pathInvariantsStatisticsForThisRound.addStatisticsDataBeforeCheckSat(numOfTemplateInequalitiesForThisRound, maximalTemplateSizeOfThisRound, minimalTemplateSizeOfThisRound,
				sumOfVarsPerLoc, numOfNonLiveVariables, round);
		mRound2PathInvariantsStatistics.put(round, pathInvariantsStatisticsForThisRound);
	}

	private void prepareAndSetPathInvariantsStatisticsAfterCheckSat(final List<IcfgLocation> locationsAsList, final Set<IcfgLocation> locationsInUnsatCore, final IcfgLocation startLoc, final IcfgLocation errorLoc, final Set<IProgramVar> allProgramVars, 
			final Set<IProgramVar> varsFromUnsatCore,  final int round, final String constraintsResult,
			final Map<LinearInequalityPatternProcessorStatistics, Object> linearInequalityStatistics) {
		int numOfNonUnsatCoreVars = 0;
		int numOfNonUnsatCoreLocs = 0;
		if (locationsInUnsatCore != null && !locationsInUnsatCore.isEmpty()) {
			numOfNonUnsatCoreLocs = locationsAsList.size() - locationsInUnsatCore.size();
		}
		for (final IcfgLocation loc : locationsAsList) {
			if (loc != startLoc && loc != errorLoc) {
				if (varsFromUnsatCore != null) {
					numOfNonUnsatCoreVars += allProgramVars.size() - varsFromUnsatCore.size();
				}
			}
		}
		// Add statistics data to global path invariants statistics
		mPathInvariantsStatistics.addStatisticsDataAfterCheckSat(numOfNonUnsatCoreLocs, numOfNonUnsatCoreVars, constraintsResult,
				linearInequalityStatistics);
		// Add statistics data to path invariants statistics for the current round
		mRound2PathInvariantsStatistics.get(round).addStatisticsDataAfterCheckSat(numOfNonUnsatCoreLocs, numOfNonUnsatCoreVars, constraintsResult,
				linearInequalityStatistics);
	}

	private <IPT> void addWP_PredicatesToInvariantPatterns(final IInvariantPatternProcessor<IPT> processor, final Map<IcfgLocation, IPT> patterns,
			final Map<IcfgLocation, Set<IProgramVar>> locs2PatternVariables,
			final Map<IcfgLocation, UnmodifiableTransFormula> pathprogramLocs2WP,
			final boolean addWPToEeachDisjunct) {
		mLogger.info("Add weakest precondition to invariant patterns.");
		if (addWPToEeachDisjunct) {
			for (final Map.Entry<IcfgLocation, UnmodifiableTransFormula> entry : pathprogramLocs2WP.entrySet()) {
				if (mLogger.isDebugEnabled()) {
					mLogger.debug("Loc: " + entry.getKey() +  " WP: " + entry.getValue());
				}
				final IPT newPattern = processor.addTransFormulaToEachConjunctInPattern(patterns.get(entry.getKey()), entry.getValue());
				patterns.put(entry.getKey(), newPattern);
				final Set<IProgramVar> varsInWP = new HashSet<>(entry.getValue().getInVars().keySet());
				varsInWP.addAll(entry.getValue().getOutVars().keySet());
				// Add variables that are already assoc. with this location.
				varsInWP.addAll(locs2PatternVariables.get(entry.getKey()));
				locs2PatternVariables.put(entry.getKey(), varsInWP);
			}
		} else {
			for (final Map.Entry<IcfgLocation, UnmodifiableTransFormula> entry : pathprogramLocs2WP.entrySet()) {
				final IPT newPattern = processor.addTransFormulaAsAdditionalDisjunctToPattern(patterns.get(entry.getKey()), entry.getValue());
				patterns.put(entry.getKey(), newPattern);
				final Set<IProgramVar> varsInWP = new HashSet<>(entry.getValue().getInVars().keySet());
				varsInWP.addAll(entry.getValue().getOutVars().keySet());
				// Add variables that are already assoc. with this location.
				varsInWP.addAll(locs2PatternVariables.get(entry.getKey()));
				locs2PatternVariables.put(entry.getKey(), varsInWP);
			}
		}
	}

	/**
	 * Check if you can find an invariant with empty patterns for intermediate locations.
	 * @return
	 */
	private <IPT> Map<IcfgLocation, IPredicate> executePreRoundWithEmptyPatterns(final IInvariantPatternProcessor<IPT> processor, int round, Set<IProgramVar> varsFromUnsatCore,
			final Map<IcfgLocation, IPT> locs2Patterns, final Map<IcfgLocation, Set<IProgramVar>> locs2PatternVariables,
			final Collection<InvariantTransitionPredicate<IPT>> predicates,
			final Map<TermVariable, IProgramVar> smtVars2ProgramVars, final IcfgLocation startLocation, final IcfgLocation errorLocation, 
			final List<IcfgLocation> locationsAsList, final List<IcfgInternalTransition> transitions, 
			final Set<IProgramVar> allProgramVars,
			final Map<IcfgLocation, UnmodifiableTransFormula> pathprogramLocs2Predicates, final boolean usePredicates,
			final boolean addWPToEeachDisjunct) {
		// Start round 0 (because it's the round with empty pattern for each location)
		round = 0;
		processor.startRound(round);
		mLogger.info("Pre-round with empty patterns for intermediate locations started...");


		// Build pattern map
		locs2Patterns.clear();
		locs2PatternVariables.clear();
		// Init the entry pattern with 'true' and the exit pattern with 'false'
		processor.initializeEntryAndExitPattern();
		for (final IcfgLocation location : locationsAsList) {
			final IPT invPattern;
			if (location.equals(startLocation)) {
				invPattern = processor.getEntryInvariantPattern();
			} else if (location.equals(errorLocation)) {
				invPattern = processor.getExitInvariantPattern();
			} else {
				// Use for intermediate locations an empty pattern
				invPattern = processor.getEmptyInvariantPattern();
			}
			locs2Patterns.put(location, invPattern);
			locs2PatternVariables.put(location, Collections.emptySet());
		}
		mLogger.info("Built (empty) pattern map");
		// add the weakest precondition of the last transition to each pattern
		if (usePredicates && pathprogramLocs2Predicates != null) {
			addWP_PredicatesToInvariantPatterns(processor, locs2Patterns, locs2PatternVariables, pathprogramLocs2Predicates, addWPToEeachDisjunct);
		}

		// Build transition predicates
		predicates.clear();
		for (final IcfgInternalTransition transition : transitions) {
			final IPT invStart = locs2Patterns.get(transition.getSource());
			final IPT invEnd = locs2Patterns.get(transition.getTarget());
			predicates.add(new InvariantTransitionPredicate<IPT>(invStart, invEnd, transition.getSource(), transition.getTarget(), 
					locs2PatternVariables.get(transition.getSource()), locs2PatternVariables.get(transition.getTarget()),
					transition.getTransformula()));
		}
		mLogger.info("Built " + predicates.size() + " transition predicates.");

		// Attempt to find a valid configuration
		final LBool constraintsResult = processor.checkForValidConfiguration(predicates, round);
		if (constraintsResult == LBool.SAT) {
			mLogger.info("Found valid configuration in pre-round.");
			final Map<IcfgLocation, IPredicate> result = new HashMap<IcfgLocation, IPredicate>(
					locationsAsList.size());
			// Extract the values for all pattern coefficients
			processor.extractValuesForPatternCoefficients();
			// Apply configuration for each pair (location, pattern) in order to obtain a predicate for each location.
			for (final IcfgLocation location : locationsAsList) {
				final IPredicate p = processor.applyConfiguration(locs2Patterns.get(location));
				result.put(location, p);
			}
			return result;
		} else {
			// If no configuration could have been found, the constraints may be unsatisfiable
			final Collection<TermVariable> smtVarsFromUnsatCore = ((LinearInequalityInvariantPatternProcessor)processor).getVarsFromUnsatCore();
			// Set benchmarks
			final Set<IcfgLocation> locsInUnsatCore = ((LinearInequalityInvariantPatternProcessor)processor).getLocationsInUnsatCore();

			if (smtVarsFromUnsatCore != null) {
				mLogger.info(smtVarsFromUnsatCore.size() + " out of " + smtVars2ProgramVars.size() + " SMT variables in unsat core");
				// The result in pattern processor was 'unsat'
				// varsFromUnsatCore = new HashSet<>(smtVarsFromUnsatCore.size());
				for (final TermVariable smtVar : smtVarsFromUnsatCore) {
					if (smtVars2ProgramVars.get(smtVar) != null) {
						varsFromUnsatCore.add(smtVars2ProgramVars.get(smtVar));
					}
				}
				if (locsInUnsatCore != null && !locsInUnsatCore.isEmpty()) {
					//					mPathInvariantsStatistics.setLocationAndVariablesData(locationsAsList.size() - locsInUnsatCore.size(), 
					//							allProgramVars.size() - varsFromUnsatCore.size());
				}
				mLogger.info(varsFromUnsatCore.size() + " out of " + (new HashSet<>(smtVars2ProgramVars.values())).size() + " program variables in unsat core");
				mLogger.info(locsInUnsatCore.size() + " out of " + locationsAsList.size() + " locations in unsat core");
			} else {
				// The result in pattern processor was 'unknown', so reset varsFromUnsatCore to null
				varsFromUnsatCore = null;
			}
		}
		mLogger.info("No valid configuration found in pre-round.");
		return null;
	}


	public Map<IcfgLocation, IPredicate> synthesizeInvariants() {
		LargeBlockEncodingIcfgTransformer lbeTransformer;
		IIcfg<IcfgLocation> lbePathProgram;
		if (APPLY_LARGE_BLOCK_ENCODING) {
			lbeTransformer = new LargeBlockEncodingIcfgTransformer(mServices, mPredicateUnifier);
			lbePathProgram = lbeTransformer.transform(mPathProgram);
		} else {
			lbePathProgram = mPathProgram;
		}
		
		Map<IcfgLocation, IPredicate> invariants = generateInvariantsForPathProgram(lbePathProgram, SimplificationTechnique.SIMPLIFY_DDA,
				XnfConversionTechnique.BOTTOM_UP_WITH_LOCAL_SIMPLIFICATION, mCsToolKit, 
				mInvariantSynthesisSettings);
		
		if (invariants != null) {
			if (APPLY_LARGE_BLOCK_ENCODING) {
				invariants = lbeTransformer.transform(invariants);
			}
			return invariants;
		} else {
			return null;
		}
	}
}
