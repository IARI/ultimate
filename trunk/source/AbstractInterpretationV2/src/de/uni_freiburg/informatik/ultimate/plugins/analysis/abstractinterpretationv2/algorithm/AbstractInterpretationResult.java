package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.model.IAbstractState;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.tool.AbstractCounterexample;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.tool.IAbstractInterpretationResult;
import de.uni_freiburg.informatik.ultimate.util.relation.Triple;

/**
 * 
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 *
 */
public final class AbstractInterpretationResult<STATE extends IAbstractState<STATE, ACTION, VARDECL>, ACTION, VARDECL, LOCATION>
		implements IAbstractInterpretationResult<STATE, ACTION, VARDECL, LOCATION> {

	private final List<AbstractCounterexample<STATE, ACTION, VARDECL, LOCATION>> mCounterexamples;
	private final AbstractInterpretationBenchmark<ACTION, LOCATION> mBenchmark;
	private final Map<LOCATION, Term> mTerms;

	protected AbstractInterpretationResult() {
		mCounterexamples = new ArrayList<>();
		mBenchmark = new AbstractInterpretationBenchmark<>();
		mTerms = new HashMap<LOCATION, Term>();
	}

	protected void reachedError(final ITransitionProvider<ACTION, LOCATION> transitionProvider,
			final WorklistItem<STATE, ACTION, VARDECL, LOCATION> finalItem, final STATE postState) {

		final List<Triple<STATE, LOCATION, ACTION>> abstractExecution = new ArrayList<>();

		ACTION transition = finalItem.getAction();
		abstractExecution.add(new Triple<>(postState, transitionProvider.getTarget(transition), transition));

		STATE post = finalItem.getPreState();
		WorklistItem<STATE, ACTION, VARDECL, LOCATION> current = finalItem.getPredecessor();
		while (current != null) {
			transition = current.getAction();
			abstractExecution.add(new Triple<>(post, transitionProvider.getTarget(transition), transition));
			post = current.getPreState();
			current = current.getPredecessor();
		}

		Collections.reverse(abstractExecution);
		mCounterexamples
				.add(new AbstractCounterexample<>(post, transitionProvider.getSource(transition), abstractExecution));
	}

	protected void addTerms(final Map<LOCATION, Term> terms) {
		mTerms.putAll(terms);
	}

	public Map<LOCATION, Term> getTerms() {
		return mTerms;
	}

	public List<AbstractCounterexample<STATE, ACTION, VARDECL, LOCATION>> getCounterexamples() {
		return mCounterexamples;
	}

	public boolean hasReachedError() {
		return !mCounterexamples.isEmpty();
	}

	public AbstractInterpretationBenchmark<ACTION, LOCATION> getBenchmark() {
		return mBenchmark;
	}
}
