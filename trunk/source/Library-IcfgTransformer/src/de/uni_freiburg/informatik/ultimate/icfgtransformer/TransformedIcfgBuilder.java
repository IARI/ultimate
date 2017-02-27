/*
 * Copyright (C) 2017 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2017 University of Freiburg
 *
 * This file is part of the ULTIMATE IcfgTransformer library.
 *
 * The ULTIMATE IcfgTransformer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE IcfgTransformer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE IcfgTransformer library. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE IcfgTransformer library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE IcfgTransformer grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.icfgtransformer;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.Overapprox;
import de.uni_freiburg.informatik.ultimate.core.model.models.IElement;
import de.uni_freiburg.informatik.ultimate.core.model.models.IPayload;
import de.uni_freiburg.informatik.ultimate.icfgtransformer.ITransformulaTransformer.TransforumlaTransformationResult;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.BasicIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgCallTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgInternalTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgReturnTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgCallTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgInternalTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgReturnTransition;

/**
 * 
 * The {@link TransformedIcfgBuilder} constructs and adds locations and transitions to a {@link BasicIcfg} based on some
 * input {@link IIcfg}.
 * 
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 *
 * @param <INLOC>
 *            The type of locations of the input {@link IIcfg}
 * @param <OUTLOC>
 *            The type of locations of the output {@link BasicIcfg}
 */
public final class TransformedIcfgBuilder<INLOC extends IcfgLocation, OUTLOC extends IcfgLocation> {
	private final Map<INLOC, OUTLOC> mOldLoc2NewLoc;
	private final Map<IIcfgCallTransition<INLOC>, IcfgCallTransition> mOldCalls2NewCalls;
	private final ILocationFactory<INLOC, OUTLOC> mLocationFactory;
	private final IBacktranslationTracker mBacktranslationTracker;
	private final ITransformulaTransformer mTransformer;
	private final IIcfg<INLOC> mOriginalIcfg;
	private final BasicIcfg<OUTLOC> mResultIcfg;

	/**
	 * Default constructor of {@link TransformedIcfgBuilder}.
	 * 
	 * @param funLocFac
	 *            A function that actually creates new locations.
	 * @param backtranslationTracker
	 *            A function that is used to track the dependencies between input and output {@link IIcfg}.
	 * @param transformer
	 *            The {@link ITransformulaTransformer} that is applied to transformulas of transitions of the input
	 *            {@link IIcfg} before new transitions for the output {@link IIcfg} are created.
	 * @param originalIcfg
	 *            The input {@link IIcfg}.
	 * @param resultIcfg
	 *            The output {@link IIcfg}.
	 */
	public TransformedIcfgBuilder(final ILocationFactory<INLOC, OUTLOC> funLocFac,
			final IBacktranslationTracker backtranslationTracker, final ITransformulaTransformer transformer,
			final IIcfg<INLOC> originalIcfg, final BasicIcfg<OUTLOC> resultIcfg) {
		mLocationFactory = Objects.requireNonNull(funLocFac);
		mBacktranslationTracker = Objects.requireNonNull(backtranslationTracker);
		mTransformer = Objects.requireNonNull(transformer);
		mOriginalIcfg = Objects.requireNonNull(originalIcfg);
		mResultIcfg = Objects.requireNonNull(resultIcfg);
		mOldLoc2NewLoc = new HashMap<>();
		mOldCalls2NewCalls = new HashMap<>();
	}

	/**
	 * Create a fresh transition between a given source and target by transforming any transformula of an old transition
	 * and copying all annotations.
	 * 
	 * @param newSource
	 *            A location in the new result Icfg that will act as the new source of the transition.
	 * @param newTarget
	 *            A location in the new result Icfg that will act as the new transition of the transition.
	 * @param oldTransition
	 *            The old transition
	 * @return A new transition with transformulas according to the {@link ITransformulaTransformer} of this instance.
	 */
	@SuppressWarnings("unchecked")
	public IcfgEdge createNewTransition(final OUTLOC newSource, final OUTLOC newTarget, final IcfgEdge oldTransition) {
		final IcfgEdge newTransition;
		if (oldTransition instanceof IIcfgInternalTransition) {
			newTransition =
					createNewLocalTransition(newSource, newTarget, (IIcfgInternalTransition<INLOC>) oldTransition);
		} else if (oldTransition instanceof IIcfgCallTransition) {
			newTransition = createNewCallTransition(newSource, newTarget, (IIcfgCallTransition<INLOC>) oldTransition);
		} else if (oldTransition instanceof IIcfgReturnTransition) {
			newTransition =
					createNewReturnTransition(newSource, newTarget, (IIcfgReturnTransition<INLOC, ?>) oldTransition);
		} else {
			throw new IllegalArgumentException("Unknown edge type " + oldTransition.getClass().getSimpleName());
		}
		newSource.addOutgoing(newTransition);
		newTarget.addIncoming(newTransition);
		mBacktranslationTracker.rememberRelation(oldTransition, newTransition);
		return newTransition;
	}

	/**
	 * Create a new location in the new {@link IIcfg} based on the old location's attributes in the original
	 * {@link IIcfg}
	 * 
	 * @param oldLoc
	 *            The old location.
	 * @return A new location.
	 */
	public OUTLOC createNewLocation(final INLOC oldLoc) {
		final OUTLOC alreadyCreated = mOldLoc2NewLoc.get(oldLoc);
		if (alreadyCreated != null) {
			// was already created, no need to re-add to the result icfg
			return alreadyCreated;
		}

		final String proc = oldLoc.getProcedure();
		final OUTLOC freshLoc = mLocationFactory.createLocation(oldLoc, oldLoc.getDebugIdentifier(), proc);

		// determine attributes of location
		final boolean isInitial = mOriginalIcfg.getInitialNodes().contains(oldLoc);
		final Set<INLOC> errorLocs = mOriginalIcfg.getProcedureErrorNodes().get(proc);
		final boolean isError = errorLocs != null && errorLocs.contains(oldLoc);
		final boolean isProcEntry = oldLoc.equals(mOriginalIcfg.getProcedureEntryNodes().get(proc));
		final boolean isProcExit = oldLoc.equals(mOriginalIcfg.getProcedureExitNodes().get(proc));
		final boolean isLoopLocation = mOriginalIcfg.getLoopLocations().contains(oldLoc);

		// add fresh location to resultIcfg
		mResultIcfg.addLocation(freshLoc, isInitial, isError, isProcEntry, isProcExit, isLoopLocation);

		// cache created IcfgLocation
		mOldLoc2NewLoc.put(oldLoc, freshLoc);

		return freshLoc;
	}

	/**
	 * Finalize the creation of the new {@link IIcfg}.
	 */
	public void finish() {
		final CfgSmtToolkit oldToolkit = mOriginalIcfg.getCfgSmtToolkit();
		final CfgSmtToolkit csToolkit =
				new CfgSmtToolkit(oldToolkit.getModifiableGlobalsTable(), oldToolkit.getManagedScript(),
						mTransformer.getNewIcfgSymbolTable(), oldToolkit.getAxioms(), oldToolkit.getProcedures());
		mResultIcfg.setCfgSmtToolkit(csToolkit);
	}

	private IcfgReturnTransition createNewReturnTransition(final IcfgLocation source, final IcfgLocation target,
			final IIcfgReturnTransition<INLOC, ?> oldTransition) {
		final IIcfgCallTransition<INLOC> oldCorrespondingCall = oldTransition.getCorrespondingCall();
		final IcfgCallTransition newCorrespondingCall = mOldCalls2NewCalls.get(oldCorrespondingCall);
		assert newCorrespondingCall != null : "The Icfg has been traversed out of order "
				+ "(found return before having found the corresponding call)";
		final TransforumlaTransformationResult retAssign =
				mTransformer.transform(oldTransition.getAssignmentOfReturn());
		final TransforumlaTransformationResult localVarAssign =
				mTransformer.transform(oldTransition.getLocalVarsAssignmentOfCall());
		final IcfgReturnTransition newTrans = new IcfgReturnTransition(source, target, newCorrespondingCall,
				getPayloadIfAvailable(oldTransition), retAssign.getTransformula(), localVarAssign.getTransformula());
		if (retAssign.isOverapproximation() || localVarAssign.isOverapproximation()) {
			annotateOverapprox(newTrans);
		}
		return newTrans;
	}

	private IcfgCallTransition createNewCallTransition(final IcfgLocation source, final IcfgLocation target,
			final IIcfgCallTransition<INLOC> oldTransition) {
		final TransforumlaTransformationResult unmodTf = mTransformer.transform(oldTransition.getLocalVarsAssignment());
		final IcfgCallTransition newTrans =
				new IcfgCallTransition(source, target, getPayloadIfAvailable(oldTransition), unmodTf.getTransformula());
		// cache the created call for usage during return creation
		mOldCalls2NewCalls.put(oldTransition, newTrans);
		if (unmodTf.isOverapproximation()) {
			annotateOverapprox(newTrans);
		}
		return newTrans;
	}

	private IcfgInternalTransition createNewLocalTransition(final IcfgLocation source, final IcfgLocation target,
			final IIcfgInternalTransition<INLOC> oldTransition) {
		final TransforumlaTransformationResult unmodTf = mTransformer.transform(oldTransition.getTransformula());
		final IcfgInternalTransition newTrans = new IcfgInternalTransition(source, target,
				getPayloadIfAvailable(oldTransition), unmodTf.getTransformula());
		if (unmodTf.isOverapproximation()) {
			annotateOverapprox(newTrans);
		}
		return newTrans;
	}

	private void annotateOverapprox(final IElement newTrans) {
		new Overapprox(mTransformer.getName(), null).annotate(newTrans);
	}

	private static IPayload getPayloadIfAvailable(final IElement elem) {
		if (elem == null) {
			return null;
		}
		if (elem.hasPayload()) {
			return elem.getPayload();
		}
		return null;
	}

}