/*
 * Copyright (C) 2016-2017 Marius Greitschus (greitsch@informatik.uni-freiburg.de)
 * Copyright (C) 2017 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2016-2017 University of Freiburg
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

package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.logic.QuantifiedFormula;
import de.uni_freiburg.informatik.ultimate.logic.QuotedObject;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.AbstractMultiState;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractDomain;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractPostOperator;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractState;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractState.SubsetResult;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IVariableProvider;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.IcfgUtils;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.ICallAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgReturnTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IInternalAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IReturnAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.hoaretriple.HoareTripleCheckerStatisticsGenerator;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.hoaretriple.IHoareTripleChecker;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.hoaretriple.IncrementalHoareTripleChecker;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.SimplificationTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.AbsIntPredicate;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicateUnifier;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.util.AbsIntUtil;
import de.uni_freiburg.informatik.ultimate.util.InCaReCounter;

/**
 * {@link IHoareTripleChecker} that performs hoare triple checks using an abstract post operator.
 *
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * @author Marius Greitschus (greitsch@informatik.uni-freiburg.de)
 *
 */
public class AbsIntHoareTripleChecker<STATE extends IAbstractState<STATE, VARDECL>, ACTION extends IIcfgTransition<?>, VARDECL>
		implements IHoareTripleChecker {

	private static final String MSG_BOTTOM_WAS_LOST = "Bottom was lost";
	private static final String MSG_IS_SUBSET_OF_IS_UNSOUND = "isSubsetOf is unsound";
	private static final String MSG_TRACKED_VARIABLES_DIFFER = "Tracked variables differ";
	private static final String MSG_INVALID_HOARE_TRIPLE_CHECK = "Invalid hoare triple check";

	private final ILogger mLogger;
	private final IAbstractPostOperator<STATE, ACTION, VARDECL> mPostOp;
	private final IAbstractDomain<STATE, ACTION, VARDECL> mDomain;
	private final IPredicateUnifier mPredicateUnifier;
	private final HoareTripleCheckerStatisticsGenerator mBenchmark;
	private final IPredicate mTruePred;
	private final IPredicate mFalsePred;
	private final AbstractMultiState<STATE, VARDECL> mTopState;
	private final AbstractMultiState<STATE, VARDECL> mBottomState;
	private final IVariableProvider<STATE, ACTION, VARDECL> mVarProvider;
	private final IncrementalHoareTripleChecker mHtcSmt;
	private final IUltimateServiceProvider mServices;
	private final CfgSmtToolkit mCsToolkit;
	private final SimplificationTechnique mSimplificationTechnique;
	private final ManagedScript mManagedScript;
	private final SdHoareTripleChecker mHtcSd;
	private final boolean mOnlyAbsInt;

	public AbsIntHoareTripleChecker(final ILogger logger, final IUltimateServiceProvider services,
			final IAbstractDomain<STATE, ACTION, VARDECL> domain,
			final IVariableProvider<STATE, ACTION, VARDECL> varProvider, final IPredicateUnifier predicateUnifer,
			final CfgSmtToolkit csToolkit, final boolean onlyAbsInt) {
		mServices = services;
		mLogger = Objects.requireNonNull(logger);
		mDomain = Objects.requireNonNull(domain);
		mPostOp = Objects.requireNonNull(mDomain.getPostOperator());
		mPredicateUnifier = Objects.requireNonNull(predicateUnifer);
		mVarProvider = Objects.requireNonNull(varProvider.createNewVariableProvider(csToolkit));
		mCsToolkit = Objects.requireNonNull(csToolkit);
		mManagedScript = Objects.requireNonNull(mCsToolkit.getManagedScript());

		mSimplificationTechnique = SimplificationTechnique.SIMPLIFY_DDA;
		mBenchmark = new HoareTripleCheckerStatisticsGenerator();
		mTruePred = mPredicateUnifier.getTruePredicate();
		mFalsePred = mPredicateUnifier.getFalsePredicate();
		mTopState = new AbstractMultiState<>(5, mDomain.createTopState());
		mBottomState = new AbstractMultiState<>(5, mDomain.createBottomState());
		mHtcSmt = new IncrementalHoareTripleChecker(mCsToolkit);
		mHtcSd = new SdHoareTripleChecker(mCsToolkit, predicateUnifer, mBenchmark);
		mOnlyAbsInt = onlyAbsInt;

	}

	@Override
	public void releaseLock() {
		// no lock needed
	}

	@Override
	public Validity checkInternal(final IPredicate prePred, final IInternalAction act, final IPredicate succPred) {
		if (mOnlyAbsInt) {
			return checkInternalAbsInt(prePred, act, succPred);
		}
		final Validity sdResult = mHtcSd.checkInternal(prePred, act, succPred);
		if (isFinalResult(sdResult)) {
			return sdResult;
		}
		final Validity absIntResult = checkInternalAbsInt(prePred, act, succPred);
		if (isFinalResult(absIntResult)) {
			return absIntResult;
		}
		final Validity result = mHtcSmt.checkInternal(prePred, act, succPred);
		mHtcSmt.releaseLock();
		return result;
	}

	@Override
	public Validity checkCall(final IPredicate prePred, final ICallAction act, final IPredicate succPred) {
		if (mOnlyAbsInt) {
			return checkCallAbsInt(prePred, act, succPred);
		}
		final Validity sdResult = mHtcSd.checkCall(prePred, act, succPred);
		mHtcSd.releaseLock();
		if (isFinalResult(sdResult)) {
			return sdResult;
		}
		final Validity absIntResult = checkCallAbsInt(prePred, act, succPred);
		if (isFinalResult(absIntResult)) {
			return absIntResult;
		}
		final Validity result = mHtcSmt.checkCall(prePred, act, succPred);
		mHtcSmt.releaseLock();
		return result;
	}

	@Override
	public Validity checkReturn(final IPredicate preLinPred, final IPredicate preHierPred, final IReturnAction act,
			final IPredicate succPred) {
		if (mOnlyAbsInt) {
			return checkReturnAbsInt(preLinPred, preHierPred, act, succPred);
		}
		final Validity sdResult = mHtcSd.checkReturn(preLinPred, preHierPred, act, succPred);
		if (isFinalResult(sdResult)) {
			return sdResult;
		}
		final Validity absIntResult = checkReturnAbsInt(preLinPred, preHierPred, act, succPred);
		if (isFinalResult(absIntResult)) {
			return absIntResult;
		}
		final Validity result = mHtcSmt.checkReturn(preLinPred, preHierPred, act, succPred);
		mHtcSmt.releaseLock();
		return result;
	}

	private static boolean isFinalResult(final Validity result) {
		return result != Validity.UNKNOWN && result != Validity.NOT_CHECKED;
	}

	private Validity checkInternalAbsInt(final IPredicate prePred, final IInternalAction act,
			final IPredicate succPred) {
		mBenchmark.continueEdgeCheckerTime();
		final AbstractMultiState<STATE, VARDECL> pre = getState(prePred);
		final AbstractMultiState<STATE, VARDECL> succ = getState(succPred);
		final ACTION action = getAction(act);

		final AbstractMultiState<STATE, VARDECL> validPreState = createValidPostOpStateAfterLeaving(action, pre, null);
		final AbstractMultiState<STATE, VARDECL> reducedPostState = succ.compact();
		final AbstractMultiState<STATE, VARDECL> reducedPreState =
				reducePreState(validPreState, reducedPostState, action);

		if (mLogger.isDebugEnabled()) {
			mLogger.debug("Pre : " + reducedPreState.toLogString());
			mLogger.debug("Act : " + action);
			mLogger.debug("Post: " + reducedPostState.toLogString());
		}

		final Validity result = checkInternalTransitionWithValidState(reducedPreState, action, reducedPostState);
		if (mLogger.isDebugEnabled()) {
			mLogger.debug("Result: " + result);
		}
		assert assertValidity(validPreState, null, action, succ, result) : MSG_INVALID_HOARE_TRIPLE_CHECK;
		mLogger.debug("--");
		final Validity rtr = result;
		mBenchmark.stopEdgeCheckerTime();
		return rtr;
	}

	private Set<VARDECL> getVars(final ACTION action) {
		return mVarProvider.getRequiredVars(action);
	}

	private Validity checkCallAbsInt(final IPredicate prePred, final ICallAction act, final IPredicate succPred) {
		mBenchmark.continueEdgeCheckerTime();
		final AbstractMultiState<STATE, VARDECL> pre = getState(prePred);
		final AbstractMultiState<STATE, VARDECL> succ = getState(succPred);
		final ACTION action = getAction(act);

		final AbstractMultiState<STATE, VARDECL> validPreBL = createValidPostOpStateBeforeLeaving(action, pre);
		final AbstractMultiState<STATE, VARDECL> validPreAL = createValidPostOpStateAfterLeaving(action, pre, null);
		final AbstractMultiState<STATE, VARDECL> reducedPreBL = reducePreState(validPreBL, succ, action);
		final AbstractMultiState<STATE, VARDECL> reducedPreAL = reducePreState(validPreAL, succ, action);
		if (mLogger.isDebugEnabled()) {
			mLogger.debug("PSBL: " + reducedPreBL.toLogString());
			mLogger.debug("PSAL: " + reducedPreAL.toLogString());
			mLogger.debug("Act : " + action);
			mLogger.debug("Post: " + succ.toLogString());
		}

		final Validity result = checkScopeChangingTransitionWithValidState(reducedPreBL, reducedPreAL, action, succ);
		if (mLogger.isDebugEnabled()) {
			mLogger.debug("Result: " + result);
		}
		assert assertValidity(validPreBL, null, action, succ, result) : MSG_INVALID_HOARE_TRIPLE_CHECK;
		mLogger.debug("--");
		mBenchmark.stopEdgeCheckerTime();
		return result;
	}

	private Validity checkReturnAbsInt(final IPredicate preLinPred, final IPredicate preHierPred,
			final IReturnAction act, final IPredicate succPred) {
		mBenchmark.continueEdgeCheckerTime();

		final AbstractMultiState<STATE, VARDECL> pre = getState(preLinPred);
		final AbstractMultiState<STATE, VARDECL> preHier = getState(preHierPred);
		final AbstractMultiState<STATE, VARDECL> succ = getState(succPred);
		final ACTION action = getAction(act);
		final ACTION correspondingCall = getCorrespondingCall(action);

		final AbstractMultiState<STATE, VARDECL> validPreBL = createValidPostOpStateBeforeLeaving(action, pre);
		final AbstractMultiState<STATE, VARDECL> validPreHier =
				createValidPostOpStateBeforeLeaving(correspondingCall, preHier);
		final AbstractMultiState<STATE, VARDECL> validPreAL =
				createValidPostOpStateAfterLeaving(action, validPreBL, validPreHier);

		if (mLogger.isDebugEnabled()) {
			mLogger.debug("PreBL: " + validPreBL.toLogString());
			mLogger.debug("PreH : " + validPreHier.toLogString());
			mLogger.debug("PreAL: " + validPreAL.toLogString());
			mLogger.debug("Act  : (" + action.getPrecedingProcedure() + ") " + action + " ("
					+ action.getSucceedingProcedure() + ")");
			mLogger.debug("Post : " + succ.toLogString());
		}

		final Validity result = checkScopeChangingTransitionWithValidState(validPreBL, validPreAL, action, succ);
		if (mLogger.isDebugEnabled()) {
			mLogger.debug("Result: " + result);
		}
		assert assertValidity(validPreBL, validPreAL, action, succ, result) : MSG_INVALID_HOARE_TRIPLE_CHECK;
		mLogger.debug("--");
		final Validity rtr = result;
		mBenchmark.stopEdgeCheckerTime();
		return rtr;
	}

	private ACTION getCorrespondingCall(final ACTION action) {
		assert action instanceof IIcfgReturnTransition<?, ?>;
		final IIcfgReturnTransition<?, ?> retAct = (IIcfgReturnTransition<?, ?>) action;
		final ACTION correspondingCall = (ACTION) retAct.getCorrespondingCall();
		return correspondingCall;
	}

	@Override
	public HoareTripleCheckerStatisticsGenerator getEdgeCheckerBenchmark() {
		return mBenchmark;
	}

	private Validity checkInternalTransitionWithValidState(final AbstractMultiState<STATE, VARDECL> preState,
			final ACTION act, final AbstractMultiState<STATE, VARDECL> postState) {
		if (preState.isBottom()) {
			return Validity.VALID;
		}

		final AbstractMultiState<STATE, VARDECL> calculatedPost = preState.apply(mPostOp, act);
		return comparePostAndCalculatedPost(act, postState, calculatedPost);
	}

	private Validity checkScopeChangingTransitionWithValidState(
			final AbstractMultiState<STATE, VARDECL> stateBeforeLeaving,
			final AbstractMultiState<STATE, VARDECL> stateAfterLeaving, final ACTION act,
			final AbstractMultiState<STATE, VARDECL> postState) {

		if (stateBeforeLeaving.isBottom()) {
			return Validity.VALID;
		}

		if (stateAfterLeaving.isBottom()) {
			return Validity.VALID;
		}

		final AbstractMultiState<STATE, VARDECL> calculatedPost =
				stateAfterLeaving.apply(mPostOp, stateBeforeLeaving, act);
		return comparePostAndCalculatedPost(act, postState, calculatedPost);
	}

	private Validity comparePostAndCalculatedPost(final ACTION act, final AbstractMultiState<STATE, VARDECL> postState,
			final AbstractMultiState<STATE, VARDECL> calculatedPost) {
		if (mLogger.isDebugEnabled()) {
			mLogger.debug("Calculated post: " + calculatedPost.toLogString());
		}
		if (calculatedPost.isBottom() && postState.isBottom()) {
			return trackPost(Validity.VALID, act);
		}

		final AbstractMultiState<STATE, VARDECL> synchronizedCalculatedPost =
				synchronizeState(postState, calculatedPost);
		if (mLogger.isDebugEnabled()) {
			mLogger.debug("Synchronized calculated post: " + calculatedPost.toLogString());
		}
		assert synchronizedCalculatedPost.isBottom() || postState.getVariables()
				.equals(synchronizedCalculatedPost.getVariables()) : MSG_TRACKED_VARIABLES_DIFFER;
		final SubsetResult included = synchronizedCalculatedPost.isSubsetOf(postState);
		assert assertIsSubsetOf(synchronizedCalculatedPost, postState, included) : MSG_IS_SUBSET_OF_IS_UNSOUND;
		if (mLogger.isDebugEnabled()) {
			mLogger.debug("Inclusion (NO): " + included);

		}
		if (included != SubsetResult.NONE) {
			return trackPost(Validity.VALID, act);
		}
		final SubsetResult excluded = postState.isSubsetOf(synchronizedCalculatedPost);
		assert assertIsSubsetOf(postState, synchronizedCalculatedPost, excluded) : MSG_IS_SUBSET_OF_IS_UNSOUND;
		if (mLogger.isDebugEnabled()) {
			mLogger.debug("Exclusion (ON): " + excluded);
		}
		if (excluded == SubsetResult.NONE) {
			assert !synchronizedCalculatedPost.isBottom() : "Nothing is a subset of bottom";
			return trackPost(Validity.INVALID, act);
		}
		return Validity.UNKNOWN;
	}

	private Validity trackPost(final Validity valid, final ACTION act) {
		if (act instanceof ICallAction) {
			return trackPost(valid, InCaReCounter::incCa);
		} else if (act instanceof IReturnAction) {
			return trackPost(valid, InCaReCounter::incRe);
		} else {
			return trackPost(valid, InCaReCounter::incIn);
		}
	}

	private Validity trackPost(final Validity valid, final Consumer<InCaReCounter> inc) {
		if (valid == Validity.UNKNOWN) {
			inc.accept(mBenchmark.getSolverCounterUnknown());
		} else if (valid == Validity.VALID) {
			inc.accept(mBenchmark.getSolverCounterUnsat());
		} else if (valid == Validity.INVALID) {
			inc.accept(mBenchmark.getSolverCounterSat());
		}
		return valid;
	}

	@SuppressWarnings("unchecked")
	private AbstractMultiState<STATE, VARDECL> getState(final IPredicate pred) {
		if (pred instanceof AbsIntPredicate<?, ?>) {
			final Set<STATE> states = ((AbsIntPredicate<STATE, ?>) pred).getAbstractStates();
			if (states.size() <= 1) {
				return AbstractMultiState.flatten(states);
			}
			final Set<VARDECL> vars = new HashSet<>();
			states.stream().forEach(a -> vars.addAll(a.getVariables()));
			final Set<STATE> synchronizedStates =
					states.stream().map(a -> AbsIntUtil.synchronizeVariables(a, vars)).collect(Collectors.toSet());
			return new AbstractMultiState<>(synchronizedStates);
		} else if (pred.equals(mTruePred)) {
			return mTopState;
		} else if (pred.equals(mFalsePred)) {
			return mBottomState;
		} else {
			throw new UnsupportedOperationException(
					"Cannot handle non-absint predicates: " + pred.hashCode() + " (" + pred.getClass() + ")");
		}
	}

	@SuppressWarnings("unchecked")
	private ACTION getAction(final IAction act) {
		return (ACTION) act;
	}

	private AbstractMultiState<STATE, VARDECL> synchronizeState(final AbstractMultiState<STATE, VARDECL> template,
			final AbstractMultiState<STATE, VARDECL> toSynchronize) {

		final AbstractMultiState<STATE, VARDECL> unifiedToSynchronize = unifyBottom(toSynchronize);
		if (unifiedToSynchronize == mBottomState) {
			return unifiedToSynchronize;
		}
		final AbstractMultiState<STATE, VARDECL> rtr = AbsIntUtil.synchronizeVariables(template, unifiedToSynchronize);
		assert assertBottomRetained(unifiedToSynchronize, null, rtr,
				() -> AbsIntUtil.synchronizeVariables(template, unifiedToSynchronize)) : MSG_BOTTOM_WAS_LOST;
		return rtr;
	}

	private AbstractMultiState<STATE, VARDECL> createValidPostOpStateAfterLeaving(final ACTION act,
			final AbstractMultiState<STATE, VARDECL> preState, final AbstractMultiState<STATE, VARDECL> preHierState) {

		final AbstractMultiState<STATE, VARDECL> unifiedPreState = unifyBottom(preState);
		if (unifiedPreState == mBottomState) {
			return unifiedPreState;
		}
		final AbstractMultiState<STATE, VARDECL> unifiedPreHierState = unifyBottom(preHierState);

		final AbstractMultiState<STATE, VARDECL> rtr =
				unifiedPreState.createValidPostOpStateAfterLeaving(mVarProvider, act, unifiedPreHierState);

		assert assertBottomRetained(preState, preHierState, rtr, () -> unifiedPreState
				.createValidPostOpStateAfterLeaving(mVarProvider, act, unifiedPreHierState)) : MSG_BOTTOM_WAS_LOST;
		return rtr;
	}

	private AbstractMultiState<STATE, VARDECL> reducePreState(final AbstractMultiState<STATE, VARDECL> validPreState,
			final AbstractMultiState<STATE, VARDECL> succ, final ACTION action) {
		final Set<VARDECL> requiredVars = new HashSet<>();
		requiredVars.addAll(getVars(action));
		requiredVars.addAll(succ.getVariables());
		final Set<VARDECL> preVars = validPreState.getVariables();
		final Set<VARDECL> toRemove = AbsIntUtil.difference(preVars, requiredVars);

		if (mLogger.isDebugEnabled()) {
			mLogger.debug(String.format("Removing %s of %s vars", toRemove.size(), preVars.size()));
			mLogger.debug(String.format("Removing %s", toRemove));
		}

		return validPreState.removeVariables(toRemove);
	}

	private AbstractMultiState<STATE, VARDECL> createValidPostOpStateBeforeLeaving(final ACTION act,
			final AbstractMultiState<STATE, VARDECL> preState) {

		final AbstractMultiState<STATE, VARDECL> unifiedPreState = unifyBottom(preState);
		if (unifiedPreState == mBottomState) {
			return unifiedPreState;
		}
		final AbstractMultiState<STATE, VARDECL> rtr =
				unifiedPreState.createValidPostOpStateBeforeLeaving(mVarProvider, act);

		assert assertBottomRetained(preState, null, rtr,
				() -> unifiedPreState.createValidPostOpStateBeforeLeaving(mVarProvider, act)) : MSG_BOTTOM_WAS_LOST;
		return rtr;
	}

	private AbstractMultiState<STATE, VARDECL> unifyBottom(final AbstractMultiState<STATE, VARDECL> state) {
		if (state == null) {
			return null;
		}
		if (state.isBottom()) {
			return mBottomState;
		}
		return state;
	}

	private IPredicate createPredicateFromState(final AbstractMultiState<STATE, VARDECL> preState) {
		return mPredicateUnifier.getPredicateFactory().newPredicate(preState.getTerm(mManagedScript.getScript()));
	}

	private boolean assertBottomRetained(final AbstractMultiState<STATE, VARDECL> pre,
			final AbstractMultiState<STATE, VARDECL> preHierState,
			final AbstractMultiState<STATE, VARDECL> synchronizedState, final IFunPointer funReplay) {
		final boolean rtr =
				!pre.isBottom() && (preHierState == null || !preHierState.isBottom()) || synchronizedState.isBottom();
		if (!rtr) {
			funReplay.run();
		}
		return rtr;
	}

	private boolean assertValidity(final AbstractMultiState<STATE, VARDECL> preState,
			final AbstractMultiState<STATE, VARDECL> validPreLinState, final ACTION transition,
			final AbstractMultiState<STATE, VARDECL> succ, final Validity result) {

		final IPredicate precond = createPredicateFromState(preState);
		final IPredicate postcond = createPredicateFromState(succ);
		final IPredicate precondHier;
		if (validPreLinState == null) {
			precondHier = null;
		} else {
			precondHier = createPredicateFromState(validPreLinState);
		}

		final Validity checkedResult = assertIsPostSound(precond, precondHier, transition, postcond);
		if (checkedResult == result) {
			mLogger.debug("HTC assert ok");
			return true;
		}
		if (result == Validity.UNKNOWN || result == Validity.NOT_CHECKED) {
			mLogger.debug("HTC assert ok");
			return true;
		}
		mLogger.fatal("Check was " + result + " but should have been " + checkedResult);
		mLogger.fatal("Failing Hoare triple:");
		final String simplePre = SmtUtils
				.simplify(mManagedScript, precond.getFormula(), mServices, mSimplificationTechnique).toStringDirect();
		if (precondHier == null) {
			mLogger.fatal("Pre: {" + simplePre + "}");
		} else {
			mLogger.fatal("PreBefore: {" + simplePre + "}");
			mLogger.fatal("PreAfter: {"
					+ SmtUtils.simplify(mManagedScript, precondHier.getFormula(), mServices, mSimplificationTechnique)
							.toStringDirect()
					+ "}");
		}
		mLogger.fatal(
				IcfgUtils.getTransformula(transition).getClosedFormula().toStringDirect() + " (" + transition + ")");
		mLogger.fatal("Post: {" + SmtUtils
				.simplify(mManagedScript, postcond.getFormula(), mServices, mSimplificationTechnique).toStringDirect()
				+ "}");
		return false;

	}

	private Validity assertIsPostSound(final IPredicate precond, final IPredicate precondHier, final ACTION transition,
			final IPredicate postcond) {
		final Validity result;
		if (transition instanceof ICallAction) {
			result = mHtcSmt.checkCall(precond, (ICallAction) transition, postcond);
		} else if (transition instanceof IReturnAction) {
			result = mHtcSmt.checkReturn(precond, precondHier, (IReturnAction) transition, postcond);
		} else {
			result = mHtcSmt.checkInternal(precond, (IInternalAction) transition, postcond);
		}
		mHtcSmt.releaseLock();
		return result;
	}

	private boolean assertIsSubsetOf(final AbstractMultiState<STATE, VARDECL> leftState,
			final AbstractMultiState<STATE, VARDECL> rightState, final SubsetResult subResult) {
		final Script script = mManagedScript.getScript();
		mHtcSmt.releaseLock();

		script.echo(new QuotedObject("Start isSubsetOf assertion"));
		final Term left = leftState.getTerm(script);
		final Term right = rightState.getTerm(script);

		final LBool result;
		final LBool expected;
		final Term checkedTerm;
		if (subResult == SubsetResult.EQUAL) {
			checkedTerm = script.term("distinct", left, right);
			expected = LBool.UNSAT;
		} else {
			final Term baseTerm = script.term("=>", left, right);
			if (baseTerm.getFreeVars().length > 0) {
				checkedTerm = script.quantifier(QuantifiedFormula.FORALL, baseTerm.getFreeVars(), baseTerm);
			} else {
				checkedTerm = baseTerm;
			}
			expected = subResult == SubsetResult.NONE ? LBool.UNSAT : LBool.SAT;
		}
		result = SmtUtils.checkSatTerm(script, checkedTerm);

		if (result == LBool.UNKNOWN || result == expected) {
			script.echo(new QuotedObject("End isSubsetOf assertion"));
			return true;
		}

		if (mLogger.isDebugEnabled()) {
			final Term leftSimpl =
					SmtUtils.simplify(mManagedScript, left, mServices, SimplificationTechnique.SIMPLIFY_DDA);
			final Term rightSimpl =
					SmtUtils.simplify(mManagedScript, right, mServices, SimplificationTechnique.SIMPLIFY_DDA);
			final Term checkSimpl =
					SmtUtils.simplify(mManagedScript, checkedTerm, mServices, SimplificationTechnique.SIMPLIFY_DDA);

			mLogger.debug("Checking left isSubsetOrEqual right = " + subResult);
			mLogger.debug("leftState  : " + leftState.toLogString());
			mLogger.debug("rightState : " + rightState.toLogString());
			mLogger.debug("left       : " + left.toStringDirect());
			mLogger.debug("right      : " + right.toStringDirect());
			mLogger.debug("leftSim    : " + leftSimpl.toStringDirect());
			mLogger.debug("rightSim   : " + rightSimpl.toStringDirect());
			mLogger.debug("checking   : " + checkedTerm.toStringDirect());
			mLogger.debug("checkingSim: " + checkSimpl.toStringDirect());
			mLogger.debug("Result is " + result + " and should be " + expected);
			mLogger.debug("Solver was " + script.getInfo(":name") + " in version " + script.getInfo(":version"));

		}

		final SubsetResult reComputeForDebug = leftState.isSubsetOf(rightState);
		mLogger.debug(reComputeForDebug);
		script.echo(new QuotedObject("End isSubsetOf assertion"));
		return false;
	}

	@FunctionalInterface
	private static interface IFunPointer {
		void run();
	}

}
