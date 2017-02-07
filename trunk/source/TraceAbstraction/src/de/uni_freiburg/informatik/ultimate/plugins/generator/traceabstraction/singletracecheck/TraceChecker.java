/*
 * Copyright (C) 2014-2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2012-2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;

import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedWord;
import de.uni_freiburg.informatik.ultimate.core.lib.exceptions.ToolchainCanceledException;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.logic.QuotedObject;
import de.uni_freiburg.informatik.ultimate.logic.SMTLIBException;
import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.IIcfgSymbolTable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.TermTransferrer;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.CodeBlock;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.util.IcfgProgramExecution;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.Activator;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.AssertCodeBlockOrder;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singletracecheck.AnnotateAndAsserter.AbnormalSolverTerminationDuringFeasibilityCheck;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singletracecheck.AnnotateAndAsserter.AbnormalUnknownSolverTerminationDuringFeasibilityCheck;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singletracecheck.TraceCheckReasonUnknown.Reason;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.tracehandling.TraceAbstractionRefinementEngine.ExceptionHandlingCategory;

/**
 * Check if a trace fulfills a specification. Provides an execution (that violates the specification) if the check was
 * negative.
 * <p>
 * Given
 * <ul>
 * <li>a precondition stated by predicate φ_0
 * <li>a postcondition stated by predicate φ_n
 * <li>a trace (which is a word of CodeBlocks) cb_0 cb_2 ... cb_{n-1},
 * </ul>
 * check if the trace always fulfills the postcondition φ_n if the precondition φ_0 holds before the execution of the
 * trace, i.e. we check if the following inclusion of predicates is valid. post(φ_0, cb_1 cb_2 ... cb_n) ⊆ φ_n
 * <p>
 * A feasibility check of a trace can be seen as the special case of this trace check. A trace is feasible if and only
 * if the trace does not fulfill the specification given by the precondition <i>true</i> and the postcondition
 * <i>false</i>. See Example1.
 * <p>
 * Example1: If
 * <ul>
 * <li>the precondition is the predicate <i>true</i>,
 * <li>the postcondition is the predicate <i>false</i>,
 * <li>and the trace cb_0 cb_1 is x:=0; x!=-1;,
 * </ul>
 * <p>
 * then the trace fulfills its specification.
 * <p>
 * Example2: If
 * <ul>
 * <li>the precondition is the predicate x==0,
 * <li>the postcondition is the predicate x==1,
 * <li>and the trace cb_0 cb_1 is x++; x++;,
 * </ul>
 * <p>
 * then the trace does not fulfill its specification.
 * <p>
 *
 * @author heizmann@informatik.uni-freiburg.de
 */
public class TraceChecker implements ITraceChecker {

	protected final ILogger mLogger;
	protected final IUltimateServiceProvider mServices;
	/**
	 * After constructing a new TraceChecker satisfiability of the trace was checked. However, the trace check is not
	 * yet finished, and the SmtManager is still locked by this TraceChecker to allow the computation of an interpolants
	 * or an execution. The trace check is only finished after the unlockSmtManager() method was called.
	 */
	protected boolean mTraceCheckFinished;
	protected final CfgSmtToolkit mCsToolkit;
	/**
	 * Interface for query the SMT solver.
	 */
	protected final ManagedScript mCfgManagedScript;
	protected final ManagedScript mTcSmtManager;
	protected final TraceCheckerLock mTraceCheckerLock = new TraceCheckerLock();
	/**
	 * Maps a procedure name to the set of global variables which may be modified by the procedure. The set of variables
	 * is represented as a map where the identifier of the variable is mapped to the type of the variable.
	 */
	protected final NestedWord<? extends IIcfgTransition<?>> mTrace;
	protected final IPredicate mPrecondition;
	protected final IPredicate mPostcondition;
	/**
	 * If the trace contains "pending returns" (returns without corresponding calls) we have to provide a predicate for
	 * each pending return that specifies what held in the calling context to which we return. (If the trace would
	 * contain the corresponding call, this predicate would be the predecessor of the call). We call these predicates
	 * "pending contexts". These pending contexts are provided via a mapping from the position of the pending return
	 * (given as Integer) to the predicate.
	 */
	protected final SortedMap<Integer, IPredicate> mPendingContexts;
	protected AnnotateAndAsserter mAAA;
	protected final LBool mIsSafe;
	protected final boolean mProvidesIcfgProgramExecution;
	protected final IcfgProgramExecution mRcfgProgramExecution;
	protected final NestedFormulas<UnmodifiableTransFormula, IPredicate> mNestedFormulas;
	protected NestedSsaBuilder mNsb;
	protected final TraceCheckerStatisticsGenerator mTraceCheckerBenchmarkGenerator =
			new TraceCheckerStatisticsGenerator();
	protected final AssertCodeBlockOrder mAssertCodeBlocksIncrementally;
	protected ToolchainCanceledException mToolchainCanceledException;
	protected final IIcfgSymbolTable mBoogie2SmtSymbolTable;
	private final TraceCheckReasonUnknown mTraceCheckReasonUnknown;

	/**
	 * Check if trace fulfills specification given by precondition, postcondition and pending contexts. The
	 * pendingContext maps the positions of pending returns to predicates which define possible variable valuations in
	 * the context to which the return leads the trace.
	 *
	 * @param assertCodeBlocksIncrementally
	 *            If set to false, check-sat is called after all CodeBlocks are asserted. If set to true we use Betim's
	 *            heuristic an incrementally assert CodeBlocks and do check-sat until all CodeBlocks are asserted or the
	 *            result to a check-sat is UNSAT.
	 * @param services
	 *            Ultimate services
	 * @param logger
	 *            logger
	 */
	public TraceChecker(final IPredicate precondition, final IPredicate postcondition,
			final SortedMap<Integer, IPredicate> pendingContexts, final NestedWord<? extends IIcfgTransition<?>> trace,
			final CfgSmtToolkit csToolkit, final AssertCodeBlockOrder assertCodeBlocksIncrementally,
			final IUltimateServiceProvider services, final boolean computeRcfgProgramExecution) {
		this(precondition, postcondition, pendingContexts, trace, csToolkit,
				new DefaultTransFormulas(trace, precondition, postcondition, pendingContexts,
						csToolkit.getOldVarsAssignmentCache(), false),
				assertCodeBlocksIncrementally, services, computeRcfgProgramExecution, true);
	}

	protected TraceChecker(final IPredicate precondition, final IPredicate postcondition,
			final SortedMap<Integer, IPredicate> pendingContexts, final NestedWord<? extends IIcfgTransition<?>> trace,
			final CfgSmtToolkit csToolkit, final NestedFormulas<UnmodifiableTransFormula, IPredicate> rv,
			final AssertCodeBlockOrder assertCodeBlocksIncrementally, final IUltimateServiceProvider services,
			final boolean computeRcfgProgramExecution, final boolean unlockSmtSolverAlsoIfUnsat) {
		this(precondition, postcondition, pendingContexts, trace, csToolkit, rv, assertCodeBlocksIncrementally,
				services, computeRcfgProgramExecution, unlockSmtSolverAlsoIfUnsat, csToolkit.getManagedScript());
	}

	/**
	 * Commit additionally the DefaultTransFormulas
	 *
	 * @param services
	 */
	protected TraceChecker(final IPredicate precondition, final IPredicate postcondition,
			final SortedMap<Integer, IPredicate> pendingContexts, final NestedWord<? extends IIcfgTransition<?>> trace,
			final CfgSmtToolkit csToolkit, final NestedFormulas<UnmodifiableTransFormula, IPredicate> rv,
			final AssertCodeBlockOrder assertCodeBlocksIncrementally, final IUltimateServiceProvider services,
			final boolean computeRcfgProgramExecution, final boolean unlockSmtSolverAlsoIfUnsat,
			final ManagedScript managedScriptTc) {
		mServices = services;
		mLogger = mServices.getLoggingService().getLogger(Activator.PLUGIN_ID);
		mCfgManagedScript = csToolkit.getManagedScript();
		mTcSmtManager = managedScriptTc;
		mCsToolkit = csToolkit;
		mBoogie2SmtSymbolTable = csToolkit.getSymbolTable();
		mTrace = trace;
		mPrecondition = precondition;
		mPostcondition = postcondition;
		if (pendingContexts == null) {
			throw new IllegalArgumentException(
					"pendingContexts must not be null; if there are no pending contexts, use an empty map");
		}
		mPendingContexts = pendingContexts;
		mNestedFormulas = rv;
		mAssertCodeBlocksIncrementally = assertCodeBlocksIncrementally;
		LBool isSafe = null;
		boolean providesIcfgProgramExecution = false;
		IcfgProgramExecution icfgProgramExecution = null;
		TraceCheckReasonUnknown traceCheckReasonUnknown = null;
		try {
			isSafe = checkTrace();
			if (isSafe == LBool.UNSAT) {
				if (unlockSmtSolverAlsoIfUnsat) {
					mTraceCheckFinished = true;
					unlockSmtManager();
				}
			} else {
				if (LBool.UNKNOWN == isSafe) {
					// solver response was 'unknown' and no Exception was thrown.
					traceCheckReasonUnknown = new TraceCheckReasonUnknown(Reason.SOLVER_RESPONSE_OTHER, null, null);
				}
				if (computeRcfgProgramExecution && isSafe == LBool.SAT) {
					icfgProgramExecution = computeRcfgProgramExecutionAndDecodeBranches();
					if (icfgProgramExecution != null) {
						providesIcfgProgramExecution = true;
					}
					mTraceCheckFinished = true;
				} else {
					mTraceCheckFinished = true;
					unlockSmtManager();
				}
			}
		} catch (final ToolchainCanceledException tce) {
			mToolchainCanceledException = tce;
		} catch (final SMTLIBException e) {
			isSafe = LBool.UNKNOWN;
			final String message = e.getMessage();
			final Reason reason;
			final ExceptionHandlingCategory exceptionCategory;
			if (message == null) {
				reason = Reason.SOLVER_CRASH_OTHER;
				exceptionCategory = ExceptionHandlingCategory.UNKNOWN;
			} else if ("Unsupported non-linear arithmetic".equals(message)) {
				// SMTInterpol does not support non-linear arithmetic
				reason = Reason.UNSUPPORTED_NON_LINEAR_ARITHMETIC;
				exceptionCategory = ExceptionHandlingCategory.KNOWN_IGNORE;
			} else if (message.endsWith("Connection to SMT solver broken")) {
				// broken SMT solver connection can have various reasons such as misconfiguration or solver crashes
				reason = Reason.SOLVER_CRASH_OTHER;
				exceptionCategory = ExceptionHandlingCategory.KNOWN_DEPENDING;
			} else if (message.endsWith("Received EOF on stdin. No stderr output.")) {
				// problem with Z3
				reason = Reason.SOLVER_CRASH_OTHER;
				exceptionCategory = ExceptionHandlingCategory.KNOWN_IGNORE;
			} else if (message.contains("Received EOF on stdin. stderr output:")) {
				// problem with CVC4
				reason = Reason.SOLVER_CRASH_OTHER;
				exceptionCategory = ExceptionHandlingCategory.KNOWN_THROW;
			} else if (message.startsWith("Logic does not allow numerals")) {
				// wrong usage of external solver, tell the user
				reason = Reason.SOLVER_CRASH_WRONG_USAGE;
				exceptionCategory = ExceptionHandlingCategory.KNOWN_THROW;
			} else if (message.startsWith("Timeout exceeded")) {
				// timeout
				reason = Reason.SOLVER_RESPONSE_TIMEOUT;
				exceptionCategory = ExceptionHandlingCategory.KNOWN_IGNORE;
			} else if (message.startsWith("A non-linear fact")) {
				// CVC4 complains about non-linear arithmetic although logic was set to linear arithmetic
				reason = Reason.UNSUPPORTED_NON_LINEAR_ARITHMETIC;
				exceptionCategory = ExceptionHandlingCategory.KNOWN_IGNORE;
			} else {
				reason = Reason.SOLVER_CRASH_OTHER;
				exceptionCategory = ExceptionHandlingCategory.UNKNOWN;
			}
			traceCheckReasonUnknown = new TraceCheckReasonUnknown(reason, e, exceptionCategory);
		} finally {
			mIsSafe = isSafe;
			mProvidesIcfgProgramExecution = providesIcfgProgramExecution;
			mRcfgProgramExecution = icfgProgramExecution;
			mTraceCheckReasonUnknown = traceCheckReasonUnknown;
		}
	}

	protected TraceCheckerStatisticsGenerator getBenchmarkGenerator() {
		return new TraceCheckerStatisticsGenerator();
	}

	@Override
	public LBool isCorrect() {
		return mIsSafe;
	}

	public TraceCheckReasonUnknown getTraceCheckReasonUnknown() {
		if (isCorrect() == LBool.UNKNOWN) {
			return mTraceCheckReasonUnknown;
		}
		throw new IllegalStateException("only available trace feasibility result is unknown.");
	}

	/**
	 * Like three-argument-checkTrace-Method above but for traces which contain pending returns. The pendingContext maps
	 * the positions of pending returns to predicates which define possible variable valuations in the context to which
	 * the return leads the trace.
	 */
	protected LBool checkTrace() {
		LBool isSafe;
		startTraceCheck();
		final boolean transferToDifferentScript = mTcSmtManager != mCfgManagedScript;
		mTraceCheckerBenchmarkGenerator.start(TraceCheckerStatisticsDefinitions.SsaConstructionTime.toString());
		mNsb = new NestedSsaBuilder(mTrace, mTcSmtManager, mNestedFormulas, mCsToolkit.getModifiableGlobalsTable(),
				mLogger, transferToDifferentScript);
		final NestedFormulas<Term, Term> ssa = mNsb.getSsa();
		mTraceCheckerBenchmarkGenerator.stop(TraceCheckerStatisticsDefinitions.SsaConstructionTime.toString());

		mTraceCheckerBenchmarkGenerator.start(TraceCheckerStatisticsDefinitions.SatisfiabilityAnalysisTime.toString());
		if (mAssertCodeBlocksIncrementally != AssertCodeBlockOrder.NOT_INCREMENTALLY) {
			mAAA = new AnnotateAndAsserterWithStmtOrderPrioritization(mTcSmtManager, ssa,
					getAnnotateAndAsserterCodeBlocks(ssa), mTraceCheckerBenchmarkGenerator,
					mAssertCodeBlocksIncrementally, mServices);
		} else {
			mAAA = new AnnotateAndAsserter(mTcSmtManager, ssa, getAnnotateAndAsserterCodeBlocks(ssa),
					mTraceCheckerBenchmarkGenerator, mServices);
			// Report the asserted code blocks
			// mTraceCheckerBenchmarkGenerator.reportnewAssertedCodeBlocks(mTrace.length());
		}
		try {
			mAAA.buildAnnotatedSsaAndAssertTerms();
			isSafe = mAAA.isInputSatisfiable();
		} catch (final AbnormalSolverTerminationDuringFeasibilityCheck e) {
			mLogger.warn("Trace check result unknown due to an abnormal solver termination.");
			isSafe = LBool.UNKNOWN;
		} catch (final AbnormalUnknownSolverTerminationDuringFeasibilityCheck e) {
			mLogger.warn("Trace check result unknown due to an abnormal and unknown solver termination.");
			if (mLogger.isDebugEnabled()) {
				mLogger.debug(e.getMessage());
			}
			isSafe = LBool.UNKNOWN;
		} finally {
			mTraceCheckerBenchmarkGenerator
					.stop(TraceCheckerStatisticsDefinitions.SatisfiabilityAnalysisTime.toString());
		}
		return isSafe;
	}

	/**
	 * Compute a program execution for the checked trace. If the checked trace violates its specification (result of
	 * trace check is SAT), we compute a program execution that contains program states that witness the violation of
	 * the specification (however, this can still be partial program states e.g., no values assigned to arrays) and that
	 * contains information which branch of a parallel composed CodeBlock violates the specification.
	 * 
	 * @return
	 */
	private IcfgProgramExecution computeRcfgProgramExecutionAndDecodeBranches() {
		if (!(mNestedFormulas instanceof DefaultTransFormulas)) {
			throw new AssertionError(
					"program execution only computable if " + "mNestedFormulas instanceof DefaultTransFormulas");
		}
		if (!((DefaultTransFormulas) mNestedFormulas).hasBranchEncoders()) {
			unlockSmtManager();
			final DefaultTransFormulas withBE = new DefaultTransFormulas(mNestedFormulas.getTrace(),
					mNestedFormulas.getPrecondition(), mNestedFormulas.getPostcondition(), mPendingContexts,
					mCsToolkit.getOldVarsAssignmentCache(), true);
			final TraceChecker tc = new TraceChecker(mNestedFormulas.getPrecondition(),
					mNestedFormulas.getPostcondition(), mPendingContexts, mNestedFormulas.getTrace(), mCsToolkit,
					withBE, AssertCodeBlockOrder.NOT_INCREMENTALLY, mServices, true, true, mTcSmtManager);
			if (tc.getToolchainCanceledExpection() != null) {
				throw tc.getToolchainCanceledExpection();
			}
			assert tc.isCorrect() == LBool.SAT : "result of second trace check is different";
			return tc.getRcfgProgramExecution();
		}
		return computeRcfgProgramExecution(mNsb);
	}

	/**
	 * Compute program execution in the case that the checked specification is violated (result of trace check is SAT).
	 */
	private IcfgProgramExecution computeRcfgProgramExecution(final NestedSsaBuilder nsb) {
		final RelevantVariables relVars =
				new RelevantVariables(mNestedFormulas, mCsToolkit.getModifiableGlobalsTable());
		final IcfgProgramExecutionBuilder rpeb = new IcfgProgramExecutionBuilder(mCsToolkit.getModifiableGlobalsTable(),
				(NestedWord<IIcfgTransition<?>>) mTrace, relVars, mBoogie2SmtSymbolTable);
		for (int i = 0; i < mTrace.length(); i++) {
			if (mTrace.getSymbolAt(i) instanceof CodeBlock) {
				final CodeBlock cb = (CodeBlock) mTrace.getSymbolAt(i);
				final UnmodifiableTransFormula tf = cb.getTransitionFormulaWithBranchEncoders();
				if (!tf.getBranchEncoders().isEmpty()) {
					final Map<TermVariable, Boolean> beMapping = new HashMap<>();
					for (final TermVariable tv : tf.getBranchEncoders()) {
						final String nameOfConstant = NestedSsaBuilder.branchEncoderConstantName(tv, i);
						final Term indexedBe = mTcSmtManager.getScript().term(nameOfConstant);
						final Term value = getValue(indexedBe);
						final Boolean booleanValue = getBooleanValue(value);
						beMapping.put(tv, booleanValue);
					}
					rpeb.setBranchEncoders(i, beMapping);
				}
			}
		}
		for (final IProgramVar bv : nsb.getIndexedVarRepresentative().keySet()) {
			if (SmtUtils.isSortForWhichWeCanGetValues(bv.getTermVariable().getSort())) {
				for (final Integer index : nsb.getIndexedVarRepresentative().get(bv).keySet()) {
					final Term indexedVar = nsb.getIndexedVarRepresentative().get(bv).get(index);
					Term valueT = getValue(indexedVar);
					if (mCfgManagedScript != mTcSmtManager) {
						valueT = new TermTransferrer(mCfgManagedScript.getScript()).transform(valueT);
					}
					rpeb.addValueAtVarAssignmentPosition(bv, index, valueT);
				}
			}
		}
		unlockSmtManager();
		return rpeb.getIcfgProgramExecution();
	}

	protected AnnotateAndAssertCodeBlocks getAnnotateAndAsserterCodeBlocks(final NestedFormulas<Term, Term> ssa) {
		return new AnnotateAndAssertCodeBlocks(mTcSmtManager, mTraceCheckerLock, ssa, mLogger);

		// AnnotateAndAssertCodeBlocks aaacb =
		// return new AnnotateAndAsserter(mCsToolkit, ssa, aaacb);
	}

	private Term getValue(final Term term) {
		return SmtUtils.getValues(mTcSmtManager.getScript(), Collections.singleton(term)).get(term);
	}

	private static Boolean getBooleanValue(final Term term) {
		Boolean result;
		if (SmtUtils.isTrue(term)) {
			result = Boolean.TRUE;
		} else {
			if (SmtUtils.isFalse(term)) {
				result = Boolean.FALSE;
			} else {
				throw new AssertionError();
			}
		}
		return result;
	}

	public NestedWord<? extends IAction> getTrace() {
		return mTrace;
	}

	@Override
	public IPredicate getPrecondition() {
		return mPrecondition;
	}

	@Override
	public IPredicate getPostcondition() {
		return mPostcondition;
	}

	@Override
	public Map<Integer, IPredicate> getPendingContexts() {
		return mPendingContexts;
	}

	@Override
	public boolean providesRcfgProgramExecution() {
		return mProvidesIcfgProgramExecution;
	}

	@Override
	public IcfgProgramExecution getRcfgProgramExecution() {
		if (mRcfgProgramExecution == null) {
			throw new AssertionError("program execution has not yet been computed");
		}
		return mRcfgProgramExecution;
	}

	protected final void unlockSmtManager() {
		endTraceCheck();
	}

	@Override
	public TraceCheckerStatisticsGenerator getTraceCheckerBenchmark() {
		if (mTraceCheckFinished || mToolchainCanceledException != null) {
			return mTraceCheckerBenchmarkGenerator;
		}
		throw new AssertionError("Benchmark is only available after the trace check is finished.");
	}

	@Override
	public ToolchainCanceledException getToolchainCanceledExpection() {
		return mToolchainCanceledException;
	}

	private void startTraceCheck() {
		mTcSmtManager.lock(mTraceCheckerLock);
		mTcSmtManager.echo(mTraceCheckerLock, new QuotedObject("starting trace check"));
		mTcSmtManager.push(mTraceCheckerLock, 1);
	}

	private void endTraceCheck() {
		mTcSmtManager.echo(mTraceCheckerLock, new QuotedObject("finished trace check"));
		mTcSmtManager.pop(mTraceCheckerLock, 1);
		mTcSmtManager.unlock(mTraceCheckerLock);
	}

	/**
	 * Package private class used by trace checker to lock the {@link ManagedScript}.
	 */
	class TraceCheckerLock {
		// this abomination helps Matthias debug
	}

	public boolean wasTracecheckFinished() {
		return mTraceCheckFinished;
	}
}
