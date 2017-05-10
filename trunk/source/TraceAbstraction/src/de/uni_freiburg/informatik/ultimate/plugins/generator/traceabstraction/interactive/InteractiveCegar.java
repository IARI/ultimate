package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.interactive;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryException;
import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.AutomataOperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomatonSimple;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedRun;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.IntersectNwa;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.IsEmpty;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.IsEmpty.SearchStrategy;
import de.uni_freiburg.informatik.ultimate.automata.statefactory.IIntersectionStateFactory;
import de.uni_freiburg.informatik.ultimate.core.lib.exceptions.ToolchainCanceledException;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.interactive.IInteractive;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.BasicCegarLoop;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.PredicateFactoryForInterpolantAutomata;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.PredicateFactory;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TAPreferences;

public class InteractiveCegar {
	private final IInteractive<Object> mInteractiveInterface;
	/**
	 * This variable was merely introduced to avoid frequent null checks on mInteractive for better readability.
	 */
	private final boolean mInteractiveMode;
	private Preferences mPreferences;
	private CompletableFuture<Void> mContinue;
	private final ILogger mLogger;

	public static class Preferences {

		private final boolean mCEXS;

		public boolean ismCEXS() {
			return mCEXS;
		}

		public boolean isIPS() {
			return mIPS;
		}

		public boolean isRSS() {
			return mRSS;
		}

		public boolean isPaused() {
			return mPaused;
		}

		private final boolean mIPS;
		private final boolean mRSS;
		private final boolean mPaused;

		public Preferences(boolean cexs, boolean ips, boolean rss, boolean paused) {
			mCEXS = cexs;
			mIPS = ips;
			mRSS = rss;
			mPaused = paused;
		}
	}

	public InteractiveCegar(final IUltimateServiceProvider services, final ILogger logger) {
		mPreferences = new Preferences(false, false, false, true);
		mInteractiveInterface = services.getServiceInstance(TAConverterFactory.class);
		mLogger = logger;

		mInteractiveMode = mInteractiveInterface != null;
		if (mInteractiveMode)
			registerHandlers();
	}

	private void registerHandlers() {
		mInteractiveInterface.register(Preferences.class, this::setPreferences);
	}

	public IInteractive<Object> getInterface() {
		return mInteractiveInterface;
	}

	public boolean isInteractiveMode() {
		return mInteractiveMode;
	}

	public void send(final Object data) {
		if (isInteractiveMode()) {
			mInteractiveInterface.send(data);
		}
	}

	private synchronized void setPreferences(Preferences preferences) {
		mLogger.info("Received Live Preferences");
		mPreferences = preferences;
		if (!mPreferences.mPaused && mContinue != null && !mContinue.isDone()) {
			mContinue.complete(null);
		}
	}

	public Preferences getPreferences() {
		return mPreferences;
	}

	public void waitIfPaused() {
		final boolean paused;
		synchronized (this) {
			paused = isInteractiveMode() && mPreferences.isPaused();
			if (paused)
				mContinue = new CompletableFuture<Void>();
		}
		if (paused) {
			mLogger.info("Client has paused Trace Abstraction - waiting for resume");
			try {
				mContinue.get();
			} catch (InterruptedException | ExecutionException e) {
				mLogger.error("Failed to get user automaton", e);
				getInterface().common().send(e);
				throw new ToolchainCanceledException(InteractiveCegar.class);
			}
		}
	}

	public void reportStartCegar(final TAPreferences prefs) {
		if (isInteractiveMode()) {
			mLogger.info("Interactive Client connected.");
			getInterface().send(prefs);
		}
	}

	public void reportIteration(final int iteration) {
		if (isInteractiveMode()) {
			getInterface().send(IterationInfo.newInstance().setIteration(iteration));
			// .setBenchmark(mCegarLoopBenchmark))
		}
	}

	public void reportSizeInfo(final String abstraction, final String interpolantAutomaton) {
		if (isInteractiveMode()) {
			IterationInfo.instance.mIteration = null;
			IterationInfo.instance.mAbstraction = abstraction;
			IterationInfo.instance.mInterpolantAutomaton = interpolantAutomaton;
			getInterface().send(IterationInfo.instance);
		}
	}

	public <LETTER> NestedRun<LETTER, IPredicate> getUserRun(
			final INestedWordAutomatonSimple<LETTER, IPredicate> abstraction, final int iteration,
			final IUltimateServiceProvider services, final SearchStrategy searchStrategy,
			final PredicateFactoryForInterpolantAutomata taContentFactory, PredicateFactory predicateFactory,
			ManagedScript script) throws AutomataOperationCanceledException {
		mLogger.info("Asking the user for a trace...");
		NestedRun<LETTER, IPredicate> userRun = null;
		/*
		 * protected DoubleDecker<IPredicate> interactiveCounterexampleSearchStrategy( Deque<DoubleDecker<IPredicate>>
		 * callQueue, Deque<DoubleDecker<IPredicate>> queue) { PredicateQueuePair data = new
		 * PredicateQueuePair(callQueue, queue); Future<PredicateQueueResult> userChoice =
		 * mInteractive.request(PredicateQueueResult.class, data); try { return userChoice.get().mResult; } catch
		 * (InterruptedException | ExecutionException e) { // e.printStackTrace(); } return
		 * IsEmptyInteractive.bfsDequeue(callQueue, queue); }
		 * 
		 * 
		 * INestedWordAutomatonSimple<LETTER, IPredicate> userAutomaton = null;
		 * 
		 * while (userRun == null) { try { userAutomaton = mInteractive.request(INestedWordAutomatonSimple.class).get();
		 * } catch (InterruptedException | ExecutionException e) { mLogger.error("Failed to get user automaton", e);
		 * mInteractive.common().send(e); }
		 * 
		 * // mCounterexample = new IsEmptyInteractive<LETTER, IPredicate>(new AutomataLibraryServices(mServices), //
		 * abstraction, this::interactiveCounterexampleSearchStrategy).getNestedRun();
		 * 
		 * // last arg finalIsTrap could be !mComputeHoareAnnotation; try { final IntersectNwa<LETTER, IPredicate>
		 * intersect = new IntersectNwa<>(abstraction, userAutomaton, mStateFactoryForRefinement, false); } catch
		 * (AutomataLibraryException e) { mLogger.error("Intersection with user automaton failed", e);
		 * mInteractive.common().send(e); }
		 * 
		 * userRun = new IsEmpty<>(new AutomataLibraryServices(mServices), abstraction, mSearchStrategy).getNestedRun();
		 * }
		 */
		while (true) {
			try {
				PreNestedWord preWord = getInterface()
						.request(PreNestedWord.class, IterationInfo.instance.setIteration(iteration)).get();
				// userRun = mInteractive.request(NestedRun.class).get();

				INestedWordAutomatonSimple<LETTER, IPredicate> userAutomaton =
						preWord.getAutomaton(services, abstraction, taContentFactory, predicateFactory, script);

				// mInteractive.send(userAutomaton);

				try {
					final IntersectNwa<LETTER, IPredicate> intersect =
							new IntersectNwa<>(abstraction, userAutomaton, taContentFactory, false);

					// mInteractive.send(intersect);

					userRun = new IsEmpty<>(new AutomataLibraryServices(services), intersect, searchStrategy)
							.getNestedRun();

					if (userRun != null)
						break;
					send("Infeasible Trace: Iteration " + iteration
							+ ": The Trace you have selected is not accepted by the "
							+ "current abstraction. Please select anther trace.");
					mLogger.info("intersection of the automaton that accepts the user-trace with abstraction is empty. "
							+ "Asking for another user run.");
				} catch (AutomataLibraryException e) {
					mLogger.error("Intersection with user automaton failed", e);
					getInterface().common().send(e);
				}

				// Accepts<LETTER, IPredicate> accepted = new Accepts<LETTER, IPredicate>(
				// new AutomataLibraryServices(mServices), abstraction, userRun.getWord());
				// if (accepted.getResult()) {
				// break;
				// }
			} catch (InterruptedException | ExecutionException e) {
				mLogger.error("Failed to get user automaton", e);
				getInterface().common().send(e);
				throw new ToolchainCanceledException(BasicCegarLoop.class);
				// } catch (AutomataLibraryException e) {
				// mLogger.error("Could not validate User Run", e);
				// mInteractive.common().send(e);
			}
		}

		return userRun;
	}
}
