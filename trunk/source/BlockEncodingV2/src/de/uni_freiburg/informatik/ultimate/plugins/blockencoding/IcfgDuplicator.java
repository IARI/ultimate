/*
 * Copyright (C) 2017 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2017 University of Freiburg
 *
 * This file is part of the ULTIMATE BlockEncodingV2 plug-in.
 *
 * The ULTIMATE BlockEncodingV2 plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE BlockEncodingV2 plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE BlockEncodingV2 plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE BlockEncodingV2 plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE BlockEncodingV2 plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.blockencoding;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.core.model.models.ModelUtils;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.BasicIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.ActionUtils;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.ICallAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgCallTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgInternalTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgReturnTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IReturnAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgCallTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgInternalTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocationIterator;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgReturnTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.Summary;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Pair;

/**
 * The {@link IcfgDuplicator} copies any {@link IIcfg} and provides a new {@link BasicIcfg}.
 *
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 */
public class IcfgDuplicator {

	private final ILogger mLogger;
	private final IUltimateServiceProvider mServices;
	private final BlockEncodingBacktranslator mBacktranslator;
	private final Map<IIcfgCallTransition<IcfgLocation>, IIcfgCallTransition<IcfgLocation>> mCallCache;
	private final ManagedScript mManagedScript;

	public IcfgDuplicator(final ILogger logger, final IUltimateServiceProvider services,
			final ManagedScript managedScript, final BlockEncodingBacktranslator backtranslator) {
		mLogger = logger;
		mServices = services;
		mBacktranslator = backtranslator;
		mCallCache = new HashMap<>();
		mManagedScript = Objects.requireNonNull(managedScript);
	}

	public BasicIcfg<IcfgLocation> copy(final IIcfg<?> originalIcfg) {
		final BasicIcfg<IcfgLocation> newIcfg =
				new BasicIcfg<>(((IIcfg<? extends IcfgLocation>) originalIcfg).getIdentifier() + "_BEv2",
						originalIcfg.getCfgSmtToolkit(), IcfgLocation.class);
		ModelUtils.copyAnnotations(originalIcfg, newIcfg);

		final Map<IcfgLocation, IcfgLocation> old2new = new HashMap<>();
		final IcfgLocationIterator<?> iter = new IcfgLocationIterator<>(originalIcfg);
		final Set<Pair<IcfgLocation, IcfgEdge>> openReturns = new HashSet<>();

		// first, copy all locations
		while (iter.hasNext()) {
			final IcfgLocation oldLoc = iter.next();
			final String proc = oldLoc.getProcedure();
			final IcfgLocation newLoc = createLocCopy(oldLoc);

			final boolean isError = originalIcfg.getProcedureErrorNodes().get(proc) != null
					&& originalIcfg.getProcedureErrorNodes().get(proc).contains(oldLoc);
			newIcfg.addLocation(newLoc, originalIcfg.getInitialNodes().contains(oldLoc), isError,
					oldLoc.equals(originalIcfg.getProcedureEntryNodes().get(proc)),
					oldLoc.equals(originalIcfg.getProcedureExitNodes().get(proc)),
					originalIcfg.getLoopLocations().contains(oldLoc));
			old2new.put(oldLoc, newLoc);
		}

		assert noEdges(newIcfg) : "Icfg contains edges but should not";

		// second, add all non-return edges
		for (final Entry<IcfgLocation, IcfgLocation> nodePair : old2new.entrySet()) {
			final IcfgLocation newSource = nodePair.getValue();
			for (final IcfgEdge oldEdge : nodePair.getKey().getOutgoingEdges()) {
				if (oldEdge instanceof IIcfgReturnTransition<?, ?>) {
					// delay creating returns until everything else is processed
					openReturns.add(new Pair<>(newSource, oldEdge));
					continue;
				}
				if (oldEdge instanceof Summary) {
					// hack to prevent copying "useless" summary edges
					final Summary oldSummary = (Summary) oldEdge;
					if (oldSummary.calledProcedureHasImplementation()) {
						continue;
					}
				}
				createEdgeCopy(old2new, newSource, oldEdge);
			}
		}

		// third, add all previously ignored return edges
		openReturns.stream().forEach(a -> createEdgeCopy(old2new, a.getFirst(), a.getSecond()));

		return newIcfg;
	}

	private IcfgLocation createLocCopy(final IcfgLocation oldLoc) {
		final IcfgLocation newLoc = new IcfgLocation(oldLoc.getDebugIdentifier(), oldLoc.getProcedure());
		ModelUtils.copyAnnotations(oldLoc, newLoc);
		mBacktranslator.mapLocations(newLoc, oldLoc);
		return newLoc;
	}

	private boolean noEdges(final IIcfg<IcfgLocation> icfg) {

		final Set<IcfgLocation> programPoints =
				icfg.getProgramPoints().entrySet().stream().flatMap(a -> a.getValue().entrySet().stream())
						.map(Entry<String, IcfgLocation>::getValue).collect(Collectors.toSet());
		for (final IcfgLocation loc : programPoints) {
			if (loc.getOutgoingEdges().isEmpty() && loc.getIncomingEdges().isEmpty()) {
				continue;
			}
			mLogger.fatal("Location " + loc + " contains incoming or outgoing edges");
			mLogger.fatal("Incoming: " + loc.getIncomingEdges());
			mLogger.fatal("Outgoing: " + loc.getOutgoingEdges());
			return false;
		}

		return true;
	}

	private IcfgEdge createEdgeCopy(final Map<IcfgLocation, IcfgLocation> old2new, final IcfgLocation newSource,
			final IcfgEdge oldEdge) {
		final IcfgLocation newTarget = old2new.get(oldEdge.getTarget());
		assert newTarget != null;
		final IcfgEdge newEdge = createUnconnectedCopy(newSource, newTarget, oldEdge);
		newSource.addOutgoing(newEdge);
		newTarget.addIncoming(newEdge);
		ModelUtils.copyAnnotations(oldEdge, newEdge);
		mBacktranslator.mapEdges(newEdge, oldEdge);
		return newEdge;
	}

	@SuppressWarnings("unchecked")
	private IcfgEdge createUnconnectedCopy(final IcfgLocation newSource, final IcfgLocation newTarget,
			final IIcfgTransition<?> oldEdge) {
		// contains transformula copy
		final IAction newAction = ActionUtils.constructCopy(mManagedScript, oldEdge);

		final IcfgEdge rtr;
		if (oldEdge instanceof IIcfgInternalTransition<?>) {
			rtr = new IcfgInternalTransition(newSource, newTarget, null, newAction.getTransformula());
		} else if (oldEdge instanceof IIcfgCallTransition<?>) {
			rtr = createCopyCall(newSource, newTarget, oldEdge, newAction);
		} else if (oldEdge instanceof IIcfgReturnTransition<?, ?>) {
			final IIcfgReturnTransition<?, ?> oldReturn = (IIcfgReturnTransition<?, ?>) oldEdge;
			final IIcfgCallTransition<?> oldCorrespondingCall = oldReturn.getCorrespondingCall();
			IIcfgCallTransition<IcfgLocation> correspondingCall = mCallCache.get(oldCorrespondingCall);
			if (correspondingCall == null) {
				mLogger.warn("Creating raw copy for unreachable call because return is reachable in graph view: "
						+ oldCorrespondingCall);
				correspondingCall =
						(IIcfgCallTransition<IcfgLocation>) createUnconnectedCopy(null, null, oldCorrespondingCall);
			}
			final IReturnAction rAction = (IReturnAction) newAction;
			rtr = new IcfgReturnTransition(newSource, newTarget, correspondingCall, null,
					rAction.getAssignmentOfReturn(), rAction.getLocalVarsAssignmentOfCall());
		} else {
			throw new UnsupportedOperationException("Unknown IcfgEdge subtype: " + oldEdge.getClass());
		}
		return rtr;
	}

	private IcfgEdge createCopyCall(final IcfgLocation source, final IcfgLocation target,
			final IIcfgTransition<?> oldEdge, final IAction newAction) {
		final IcfgEdge rtr;
		final ICallAction cAction = (ICallAction) newAction;
		rtr = new IcfgCallTransition(source, target, null, cAction.getLocalVarsAssignment());
		mCallCache.put((IIcfgCallTransition<IcfgLocation>) oldEdge, (IIcfgCallTransition<IcfgLocation>) rtr);
		return rtr;
	}

}
