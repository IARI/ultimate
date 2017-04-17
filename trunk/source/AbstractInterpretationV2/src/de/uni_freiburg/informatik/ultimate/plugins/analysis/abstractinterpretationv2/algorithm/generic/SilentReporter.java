package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.generic;

import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.AbstractMultiState;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractState;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.IResultReporter;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.tool.AbstractCounterexample;

/**
 * This {@link IResultReporter} does not generate any results.
 *
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 *
 */
public final class SilentReporter<STATE extends IAbstractState<STATE, VARDECL>, ACTION, VARDECL, LOCATION>
		implements IResultReporter<STATE, ACTION, VARDECL, LOCATION> {
	
	@Override
	public void reportSafe(final ACTION elem) {
		// do nothing to stay silent
	}

	@Override
	public void reportSafe(final ACTION elem, final String msg) {
		// do nothing to stay silent
	}

	@Override
	public void reportPossibleError(
			final AbstractCounterexample<AbstractMultiState<STATE, VARDECL>, ACTION, ?, LOCATION> cex) {
		// do nothing to stay silent
	}
}