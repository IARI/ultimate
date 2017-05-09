/*
 * Copyright (C) 2016 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2017 Dennis Wölfing
 * Copyright (C) 2016-2017 University of Freiburg
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
package de.uni_freiburg.informatik.ultimate.icfgtransformer.loopacceleration.woelfing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.Overapprox;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.icfgtransformer.IBacktranslationTracker;
import de.uni_freiburg.informatik.ultimate.icfgtransformer.IIcfgTransformer;
import de.uni_freiburg.informatik.ultimate.icfgtransformer.ILocationFactory;
import de.uni_freiburg.informatik.ultimate.icfgtransformer.ITransformulaTransformer;
import de.uni_freiburg.informatik.ultimate.icfgtransformer.TransformedIcfgBuilder;
import de.uni_freiburg.informatik.ultimate.logic.Rational;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Sort;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.logic.Util;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.BasicIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgInternalTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgInternalTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.TransFormula;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.TransFormulaBuilder;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.TransFormulaUtils;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula.Infeasibility;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.PartialQuantifierElimination;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.SimplificationTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.XnfConversionTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.Substitution;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.TermClassifier;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;

/**
 * An IcfgTransformer that accelerates loops.
 *
 * @param <INLOC>
 *            The type of the locations of the old IIcfg.
 * @param <OUTLOC>
 *            The type of the locations of the transformed IIcfg.
 *
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * @author Dennis Wölfing
 *
 */
public class LoopAccelerationIcfgTransformer<INLOC extends IcfgLocation, OUTLOC extends IcfgLocation>
		implements IIcfgTransformer<OUTLOC> {

	private final ILogger mLogger;
	private final IIcfg<OUTLOC> mResultIcfg;
	private final ITransformulaTransformer mTransformer;

	private final Set<IcfgEdge> mLoopEntryTransitions;
	private final Map<INLOC, List<Backbone>> mBackbones;
	private final Set<Backbone> mExitBackbones;
	private final Map<Backbone, TransFormula> mBackboneTransformulas;
	private final ManagedScript mScript;
	private final IUltimateServiceProvider mServices;

	/**
	 * Constructs a LoopAccelerationIcfgTransformer.
	 *
	 * @param logger
	 *            A {@link ILogger} instance that is used for debug logging.
	 * @param originalIcfg
	 *            an input {@link IIcfg}.
	 * @param funLocFac
	 *            A location factory.
	 * @param backtranslationTracker
	 *            A backtranslation tracker.
	 * @param outLocationClass
	 *            The class object of the type of locations of the output {@link IIcfg}.
	 * @param newIcfgIdentifier
	 *            The identifier of the new {@link IIcfg}
	 * @param transformer
	 *            The transformer that should be applied to each transformula of each transition of the input
	 *            {@link IIcfg} to create a new {@link IIcfg}.
	 * @param services
	 */
	public LoopAccelerationIcfgTransformer(final ILogger logger, final IIcfg<INLOC> originalIcfg,
			final ILocationFactory<INLOC, OUTLOC> funLocFac, final IBacktranslationTracker backtranslationTracker,
			final Class<OUTLOC> outLocationClass, final String newIcfgIdentifier,
			final ITransformulaTransformer transformer, final IUltimateServiceProvider services) {
		final IIcfg<INLOC> origIcfg = Objects.requireNonNull(originalIcfg);
		mLogger = Objects.requireNonNull(logger);
		mTransformer = Objects.requireNonNull(transformer);

		mLoopEntryTransitions = new HashSet<>();
		mBackbones = new HashMap<>();
		mExitBackbones = new HashSet<>();
		mBackboneTransformulas = new HashMap<>();
		mScript = origIcfg.getCfgSmtToolkit().getManagedScript();
		mServices = services;

		// perform transformation last
		final BasicIcfg<OUTLOC> resultIcfg =
				new BasicIcfg<>(newIcfgIdentifier, originalIcfg.getCfgSmtToolkit(), outLocationClass);
		final TransformedIcfgBuilder<INLOC, OUTLOC> lst =
				new TransformedIcfgBuilder<>(funLocFac, backtranslationTracker, transformer, origIcfg, resultIcfg);
		mResultIcfg = transform(origIcfg, resultIcfg, lst, backtranslationTracker);
		lst.finish();
	}

	/**
	 * Transforms the Icfg.
	 *
	 * @param lst
	 */
	private IIcfg<OUTLOC> transform(final IIcfg<INLOC> origIcfg, final BasicIcfg<OUTLOC> resultIcfg,
			final TransformedIcfgBuilder<INLOC, OUTLOC> lst, final IBacktranslationTracker backtranslationTracker) {

		findAllBackbones(origIcfg.getInitialNodes());

		if (mLogger.isDebugEnabled()) {
			mLogger.debug("Found the following backbones:");
			mLogger.debug(mBackbones);
			mLogger.debug(mExitBackbones);
		}

		// Create a new Icfg.

		mTransformer.preprocessIcfg(origIcfg);

		final Set<INLOC> init = origIcfg.getInitialNodes();
		final Deque<INLOC> open = new ArrayDeque<>(init);
		final Set<INLOC> closed = new HashSet<>();

		while (!open.isEmpty()) {
			final INLOC oldSource = open.removeFirst();
			if (!closed.add(oldSource)) {
				continue;
			}

			final OUTLOC newSource = lst.createNewLocation(oldSource);

			for (final IcfgEdge oldTransition : oldSource.getOutgoingEdges()) {
				if (mLoopEntryTransitions.contains(oldTransition)) {
					continue;
				}

				final INLOC oldTarget = (INLOC) oldTransition.getTarget();
				open.add(oldTarget);
				final OUTLOC newTarget = lst.createNewLocation(oldTarget);

				if (mBackbones.containsKey(oldSource)) {
					final IcfgEdge newTransition = getAcceleratedTransition(oldTransition, newSource, newTarget);
					backtranslationTracker.rememberRelation(oldTransition, newTransition);
				} else {
					lst.createNewTransition(newSource, newTarget, oldTransition);
				}
			}
		}

		final Set<IcfgEdge> transformedEdges = new HashSet<>();

		for (final Backbone backbone : mExitBackbones) {
			final List<IcfgEdge> transitions = backbone.getTransitions();

			for (int i = 0; i < transitions.size(); i++) {
				final IcfgEdge edge = transitions.get(i);
				final INLOC oldSource = (INLOC) edge.getSource();
				if (i > 0 && closed.contains(oldSource)) {
					break;
				}
				if (transformedEdges.contains(edge)) {
					continue;
				}

				final INLOC oldTarget = (INLOC) edge.getTarget();
				final OUTLOC newSource = lst.createNewLocation(oldSource);
				final OUTLOC newTarget = lst.createNewLocation(oldTarget);
				if (i == 0) {
					final IcfgEdge newTransition = getAcceleratedTransition(edge, newSource, newTarget);
					backtranslationTracker.rememberRelation(edge, newTransition);
				} else {
					lst.createNewTransition(newSource, newTarget, edge);
				}

				transformedEdges.add(edge);
			}
		}
		return resultIcfg;
	}

	private IcfgEdge getAcceleratedTransition(final IcfgEdge oldTransition, final OUTLOC newSource,
			final OUTLOC newTarget) {
		final INLOC oldSource = (INLOC) oldTransition.getSource();
		final IteratedSymbolicMemory iteratedSymbolicMemory = getIteratedSymbolicMemoryForLoop(oldSource);
		final UnmodifiableTransFormula loopTf = getLoopTransFormula(iteratedSymbolicMemory, mBackbones.get(oldSource));
		final UnmodifiableTransFormula tf = TransFormulaUtils.sequentialComposition(mLogger, mServices, mScript, true,
				true, false, XnfConversionTechnique.BOTTOM_UP_WITH_LOCAL_SIMPLIFICATION,
				SimplificationTechnique.SIMPLIFY_DDA, Arrays.asList(loopTf, oldTransition.getTransformula()));
		assert oldTransition instanceof IIcfgInternalTransition;
		final IcfgInternalTransition newTransition = new IcfgInternalTransition(newSource, newTarget, null, tf);
		if (iteratedSymbolicMemory.isOverapproximation() || iteratedSymbolicMemory.getLoopCounters().size() > 1) {
			// When the iterated symbolic memory cannot represent the values of all variables or when there
			// are multiple backbones the calculated loop transformula might be an overapproximation.
			new Overapprox(getClass().getSimpleName(), null).annotate(newTransition);
		}
		newSource.addOutgoing(newTransition);
		newTarget.addIncoming(newTransition);
		return newTransition;
	}

	/**
	 * Finds all backbones and adds them into mBackbones.
	 *
	 * @param initialNodes
	 *            The initial nodes of the Icfg.
	 */
	private void findAllBackbones(final Set<INLOC> initialNodes) {
		final List<Backbone> incompleteBackbones = new ArrayList<>();

		for (final INLOC location : initialNodes) {
			for (final IcfgEdge edge : location.getOutgoingEdges()) {
				checkTransition(edge);
				incompleteBackbones.add(new Backbone(edge));
			}
			mBackbones.put(location, new ArrayList<>());
		}

		while (!incompleteBackbones.isEmpty()) {
			for (int i = incompleteBackbones.size() - 1; i >= 0; i--) {
				final Backbone backbone = incompleteBackbones.get(i);
				final IcfgLocation lastLocation = backbone.getLastLocation();
				final IcfgLocation entryLocation = backbone.getTransitions().get(0).getSource();

				if (lastLocation != entryLocation && backbone.endsInLoop()) {
					incompleteBackbones.remove(i);
					final IcfgEdge loopEntryTransition = backbone.getLoopEntryTransition();
					if (mLoopEntryTransitions.contains(loopEntryTransition)) {
						continue;
					}
					mLoopEntryTransitions.add(loopEntryTransition);
					incompleteBackbones.add(new Backbone(loopEntryTransition));
					mBackbones.putIfAbsent((INLOC) loopEntryTransition.getSource(), new ArrayList<>());
				} else if (lastLocation == entryLocation) {
					assert backbone.endsInLoop();
					incompleteBackbones.remove(i);
					mBackbones.get(entryLocation).add(backbone);
				} else if (lastLocation.getOutgoingEdges().isEmpty()) {
					// This backbone leaves the loop
					assert !backbone.endsInLoop();
					incompleteBackbones.remove(i);
					if (initialNodes.contains(entryLocation)) {
						continue;
					}
					mExitBackbones.add(backbone);
				}
			}

			final int backboneSize = incompleteBackbones.size();
			for (int i = 0; i < backboneSize; i++) {
				final Backbone backbone = incompleteBackbones.get(i);
				final INLOC location = (INLOC) backbone.getLastLocation();

				final List<IcfgEdge> outgoingEdges = location.getOutgoingEdges();
				for (int j = 0; j < outgoingEdges.size(); j++) {
					final IcfgEdge edge = outgoingEdges.get(j);
					checkTransition(edge);
					if (j == outgoingEdges.size() - 1) {
						backbone.addTransition(edge);
					} else {
						final Backbone newBackbone = new Backbone(backbone);
						newBackbone.addTransition(edge);
						incompleteBackbones.add(newBackbone);
					}
				}
			}
		}

		for (final INLOC location : initialNodes) {
			if (mBackbones.get(location).isEmpty()) {
				mBackbones.remove(location);
			}
		}
	}

	private void checkTransition(final IcfgEdge transition) {

		final TermClassifier tclassifier = new TermClassifier();
		tclassifier.checkTerm(transition.getTransformula().getFormula());
		if (tclassifier.hasArrays()) {
			throw new UnsupportedOperationException("Cannot handle arrays");
		}
	}

	/**
	 * Calculates an iterated symbolic memory for a given loop entry.
	 *
	 * @param loopEntry
	 *            The loop entry location.
	 * @return An IteratedSymbolicMemory.
	 */
	private IteratedSymbolicMemory getIteratedSymbolicMemoryForLoop(final INLOC loopEntry) {
		final List<Backbone> backbones = mBackbones.get(loopEntry);
		final List<SymbolicMemory> symbolicMemories = new ArrayList<>();

		for (final Backbone backbone : backbones) {
			boolean overapproximation = false;
			final List<UnmodifiableTransFormula> transFormulas = new ArrayList<>();

			for (final IcfgEdge edge : backbone.getTransitions()) {
				final UnmodifiableTransFormula tf = edge.getTransformula();
				transFormulas.add(tf);

				final INLOC target = (INLOC) edge.getTarget();
				if (target != backbone.getLastLocation() && mBackbones.containsKey(target)) {
					final IteratedSymbolicMemory iteratedSymbolicMemory = getIteratedSymbolicMemoryForLoop(target);
					overapproximation |= iteratedSymbolicMemory.isOverapproximation()
							|| iteratedSymbolicMemory.getLoopCounters().size() > 1;
					final UnmodifiableTransFormula loopTf =
							getLoopTransFormula(iteratedSymbolicMemory, mBackbones.get(target));
					transFormulas.add(loopTf);
				}
			}

			final TransFormula tf = TransFormulaUtils.sequentialComposition(mLogger, mServices, mScript, true, true,
					false, XnfConversionTechnique.BOTTOM_UP_WITH_LOCAL_SIMPLIFICATION,
					SimplificationTechnique.SIMPLIFY_DDA, transFormulas);
			mBackboneTransformulas.put(backbone, tf);

			final SymbolicMemory symbolicMemory = new SymbolicMemory(mScript, tf, overapproximation);
			symbolicMemories.add(symbolicMemory);
		}
		return new IteratedSymbolicMemory(mScript, symbolicMemories);
	}

	/**
	 * Calculates a TransFormula that holds when each backbone can be taken as often as specified by its loopCounter.
	 *
	 * @param iteratedSymbolicMemory
	 *            The IteratedSymbolicMemory of the loop.
	 * @param backbones
	 *            The backbones of the loop.
	 * @return The loop TransFormula (containing loopCounters).
	 */
	private UnmodifiableTransFormula getLoopTransFormula(final IteratedSymbolicMemory iteratedSymbolicMemory,
			final List<Backbone> backbones) {
		final int numLoops = backbones.size();
		final List<TermVariable> loopCounters = iteratedSymbolicMemory.getLoopCounters();
		assert loopCounters.size() == numLoops;
		final Set<TermVariable> auxVars = new HashSet<>(loopCounters);

		final List<TermVariable> loopIterators = new ArrayList<>(numLoops);
		final Map<Term, Term> substitutionMapping = new HashMap<>();
		final Sort sort = mScript.getScript().sort("Int");

		for (int i = 0; i < numLoops; i++) {
			final TermVariable loopIterator = mScript.constructFreshTermVariable("loopIterator", sort);
			loopIterators.add(loopIterator);
			substitutionMapping.put(loopCounters.get(i), loopIterator);
		}

		final Term[] terms = new Term[numLoops];
		final Term zeroTerm = Rational.ZERO.toTerm(sort);

		for (int i = 0; i < numLoops; i++) {
			final TransFormula tf = mBackboneTransformulas.get(backbones.get(i));
			auxVars.addAll(tf.getAuxVars());
			Term term = tf.getFormula();
			term = iteratedSymbolicMemory.getSymbolicMemory(i).replaceTermVars(term, null);
			term = iteratedSymbolicMemory.replaceTermVars(term, tf.getInVars());
			term = new Substitution(mScript, substitutionMapping).transform(term);

			final List<TermVariable> quantifiers = new ArrayList<>();
			for (int j = 0; j < numLoops; j++) {
				if (i == j) {
					continue;
				}

				quantifiers.add(loopIterators.get(j));

				final Term iteratorCondition =
						Util.and(mScript.getScript(), mScript.getScript().term("<=", zeroTerm, loopIterators.get(j)),
								mScript.getScript().term("<=", loopIterators.get(j), loopCounters.get(j)));

				term = Util.and(mScript.getScript(), iteratorCondition, term);
			}

			if (!quantifiers.isEmpty()) {
				term = mScript.getScript().quantifier(Script.EXISTS, quantifiers.toArray(new TermVariable[0]), term);
			}

			final Term iteratorCondition =
					Util.and(mScript.getScript(), mScript.getScript().term("<=", zeroTerm, loopIterators.get(i)),
							mScript.getScript().term("<", loopIterators.get(i), loopCounters.get(i)));
			term = Util.implies(mScript.getScript(), iteratorCondition, term);
			term = mScript.getScript().quantifier(Script.FORALL, new TermVariable[] { loopIterators.get(i) }, term);

			term = PartialQuantifierElimination.tryToEliminate(mServices, mLogger, mScript, term,
					SimplificationTechnique.SIMPLIFY_DDA, XnfConversionTechnique.BOTTOM_UP_WITH_LOCAL_SIMPLIFICATION);
			terms[i] = term;
		}

		Term resultTerm = Util.and(mScript.getScript(), terms);

		for (int i = 0; i < numLoops; i++) {
			resultTerm = Util.and(mScript.getScript(), resultTerm,
					mScript.getScript().term(">=", loopCounters.get(i), zeroTerm));
		}

		resultTerm = Util.and(mScript.getScript(), resultTerm, iteratedSymbolicMemory.toTerm());

		resultTerm = PartialQuantifierElimination.tryToEliminate(mServices, mLogger, mScript, resultTerm,
				SimplificationTechnique.SIMPLIFY_DDA, XnfConversionTechnique.BOTTOM_UP_WITH_LOCAL_SIMPLIFICATION);

		final TransFormulaBuilder builder = new TransFormulaBuilder(iteratedSymbolicMemory.getInVars(),
				iteratedSymbolicMemory.getOutVars(), true, null, true, null, false);
		builder.setFormula(resultTerm);
		builder.addAuxVarsButRenameToFreshCopies(auxVars, mScript);
		builder.setInfeasibility(Infeasibility.NOT_DETERMINED);
		return builder.finishConstruction(mScript);
	}

	@Override
	public IIcfg<OUTLOC> getResult() {
		return mResultIcfg;
	}
}
