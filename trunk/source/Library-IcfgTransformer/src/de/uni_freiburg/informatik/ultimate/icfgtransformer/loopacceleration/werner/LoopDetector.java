package de.uni_freiburg.informatik.ultimate.icfgtransformer.loopacceleration.werner;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;

/**
 * Extracts the loops from an {@link IIcfg}. And calculates its backbones, which are acyclic paths in the loop.
 *
 * @param <INLOC>
 *
 * @author Jonas Werner (jonaswerner95@gmail.com)
 */

public class LoopDetector<INLOC extends IcfgLocation> {
	private final ILogger mLogger;
	private final Deque<Loop> mLoopBodies;

	/**
	 * Loop Detector for retrieving loops in an {@link IIcfg}.
	 *
	 * @param logger
	 * @param originalIcfg
	 */
	public LoopDetector(final ILogger logger, final IIcfg<INLOC> originalIcfg) {
		mLogger = Objects.requireNonNull(logger);
		mLogger.debug("Loop detector constructed.");
		mLoopBodies = getLoop(originalIcfg);
		for (final Loop loop : mLoopBodies) {
			final Deque<Backbone> backbones = getBackbonePath(loop.getPath());
			for (final Backbone backbone : backbones) {
				loop.addBackbone(backbone);
			}
			mLogger.debug(loop.getBackbones());
		}
	}

	private Deque<Loop> getLoop(final IIcfg<INLOC> originalIcfg) {
		final Set<INLOC> loopHeads = originalIcfg.getLoopLocations();
		final Deque<Loop> loopBodies = new ArrayDeque<>();

		if (!loopHeads.isEmpty()) {
			mLogger.debug("Loops found.");

			for (final INLOC loopHead : loopHeads) {
				final Deque<IcfgEdge> path = getPath(loopHead);
				final Loop loop = new Loop(path, loopHead);
				loopBodies.add(loop);
			}

		} else {
			mLogger.debug("No Loops found.");
		}

		mLogger.debug("Found Loopbodies: " + loopBodies.toString());
		return loopBodies;
	}

	private Deque<IcfgEdge> getPath(final IcfgLocation loopHead) {
		Deque<IcfgEdge> loopPath = new ArrayDeque<>();
		final Deque<IcfgEdge> stack = new ArrayDeque<>();

		for (final IcfgEdge edge : loopHead.getOutgoingEdges()) {
			stack.push(edge);
		}

		while (!stack.isEmpty()) {
			final IcfgEdge edge = stack.pop();
			final Deque<IcfgEdge> newPath = new ArrayDeque<>(loopPath);
			newPath.addLast(edge);

			if (findLoopHeader(newPath, loopHead)) {
				loopPath = newPath;
				if (edge.getTarget() != loopHead) {

					// TODO something for nested loops and more than one loop.

					for (final IcfgEdge transition : edge.getTarget().getOutgoingEdges()) {
						stack.push(transition);
					}
				}
			}
		}
		return loopPath;
	}

	/**
	 * Try to find a path back to the loopheader. If there is one return true, else false.
	 *
	 * @param path
	 *            path to check
	 * @param loopHead
	 *            loopHeader
	 */
	private boolean findLoopHeader(final Deque<IcfgEdge> path, final IcfgLocation loopHead) {
		final Deque<IcfgEdge> stack = new ArrayDeque<>();
		final Deque<IcfgLocation> visited = new ArrayDeque<>();
		stack.push(path.getLast());

		while (!stack.isEmpty()) {

			final IcfgLocation node = stack.pop().getTarget();

			for (final IcfgLocation successor : node.getOutgoingNodes()) {
				if (successor == loopHead || node == loopHead) {
					return true;
				}
				if (!visited.contains(successor)) {
					visited.addLast(successor);
					for (final IcfgEdge edge : successor.getOutgoingEdges()) {
						stack.push(edge);
					}
				}
			}
		}
		return false;
	}

	private Deque<Backbone> getBackbonePath(final Deque<IcfgEdge> loopBody) {
		final Deque<Backbone> backbones = new ArrayDeque<>();
		final IcfgLocation loopHead = loopBody.getFirst().getSource();
		final IcfgEdge initialEdge = loopBody.getFirst();
		final Deque<Deque<IcfgEdge>> possibleBackbones = new ArrayDeque<>();
		final Deque<IcfgEdge> first = new ArrayDeque<>();

		first.addLast(initialEdge);
		possibleBackbones.addFirst(first);

		while (!possibleBackbones.isEmpty()) {

			final Deque<IcfgLocation> visited = new ArrayDeque<>();
			final Deque<IcfgEdge> backbone = possibleBackbones.pop();

			while (backbone.getLast().getTarget() != loopHead && !visited.contains(backbone.getLast().getTarget())) {

				final IcfgLocation target = backbone.getLast().getTarget();
				visited.addLast(target);

				// in case of multiple outgoing edges create more possible backbones.
				if (target.getOutgoingEdges().size() > 1) {
					mLogger.debug("Branching node found: " + target.toString());
					for (int i = 1; i < target.getOutgoingEdges().size(); i++) {
						final Deque<IcfgEdge> newPossibleBackbone = new ArrayDeque<>(backbone);
						newPossibleBackbone.addLast(target.getOutgoingEdges().get(i));
						mLogger.debug("New Possible Backbone: " + newPossibleBackbone.toString());
						possibleBackbones.addLast(newPossibleBackbone);
					}
				}
				backbone.add(target.getOutgoingEdges().get(0));
			}
			final Backbone newBackbone = new Backbone(backbone);
			backbones.addLast(newBackbone);
		}
		mLogger.debug("Found Backbones: " + backbones.toString());
		return backbones;
	}

	/**
	 * Get Loop bodies of an {@link IIcfg}. As a Queue of loop datastructures
	 */
	public Deque<Loop> getLoopBodies() {
		return mLoopBodies;
	}
}