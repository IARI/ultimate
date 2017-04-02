package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.nwa;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedRun;
import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.LoopEntryAnnotation;
import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.LoopExitAnnotation;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.ILoopDetector;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.rcfg.RcfgTransitionProvider;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.BoogieIcfgLocation;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.CodeBlock;

/**
 *
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 *
 */
public class NWAPathProgramTransitionProvider extends RcfgTransitionProvider implements ILoopDetector<CodeBlock> {

	private final NestedRun<CodeBlock, ?> mCex;
	private final Set<CodeBlock> mLetters;
	private final IcfgLocation mPostErrorLoc;

	public NWAPathProgramTransitionProvider(final NestedRun<CodeBlock, ?> counterexample,
			final Set<CodeBlock> pathProgramProjection, final IUltimateServiceProvider services,
			final IIcfg<BoogieIcfgLocation> annotation) {
		super();
		mCex = counterexample;
		mLetters = pathProgramProjection;
		// words count their states, so 0 is first state, length is last state
		mPostErrorLoc = mCex.getSymbol(mCex.getLength() - 2).getTarget();
	}

	@Override
	public Collection<CodeBlock> getSuccessors(final CodeBlock elem, final CodeBlock scope) {
		return super.getSuccessors(elem, scope).stream().filter(mLetters::contains).collect(Collectors.toSet());
	}

	@Override
	public Collection<CodeBlock> getPredecessors(final CodeBlock elem, final CodeBlock scope) {
		return super.getPredecessors(elem, scope).stream().filter(mLetters::contains).collect(Collectors.toSet());
	}

	@Override
	public boolean isErrorLocation(final IcfgLocation loc) {
		return loc.equals(mPostErrorLoc);
	}

	@Override
	public Collection<CodeBlock> getSuccessorActions(final IcfgLocation loc) {
		return super.getSuccessorActions(loc).stream().filter(mLetters::contains).collect(Collectors.toSet());
	}

	@Override
	public boolean isEnteringLoop(final CodeBlock transition) {
		assert transition != null;
		return null != LoopEntryAnnotation.getAnnotation(transition);
	}

	@Override
	public boolean isLeavingLoop(final CodeBlock transition) {
		assert transition != null;
		return null != LoopExitAnnotation.getAnnotation(transition);
	}
}
