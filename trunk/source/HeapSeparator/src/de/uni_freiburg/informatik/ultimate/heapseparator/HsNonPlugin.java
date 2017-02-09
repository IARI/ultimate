/*
 * Copyright (C) 2016 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2016 University of Freiburg
 *
 * This file is part of the ULTIMATE HeapSeparator plug-in.
 *
 * The ULTIMATE HeapSeparator plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE HeapSeparator plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE HeapSeparator plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE HeapSeparator plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE HeapSeparator plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.heapseparator;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IProgressAwareTimer;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transformations.ReplacementVarFactory;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVarOrConst;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.VPDomain;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.VPDomainPreanalysis;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.VPDomainSymmetricPair;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.elements.EqNode;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.states.VPState;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.tool.AbstractInterpreter;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.tool.IAbstractInterpretationResult;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.irsdependencies.rcfg.walker.ObserverDispatcher;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.irsdependencies.rcfg.walker.ObserverDispatcherSequential;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.irsdependencies.rcfg.walker.RCFGWalkerBreadthFirst;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.BoogieIcfgContainer;
import de.uni_freiburg.informatik.ultimate.util.datastructures.UnionFind;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.HashRelation;

public class HsNonPlugin {
	
	private final IUltimateServiceProvider mServices;
	private final CfgSmtToolkit mCsToolkit;
	private final ILogger mLogger;
	private final ReplacementVarFactory mReplacementVarFactory;
	private final ManagedScript mManagedScript;
	
	public HsNonPlugin(final IUltimateServiceProvider services, final CfgSmtToolkit csToolkit, final ILogger logger) {
		
		mServices = services;
		mCsToolkit = csToolkit;
		mLogger = logger;
		mManagedScript = csToolkit.getManagedScript();
		mReplacementVarFactory = new ReplacementVarFactory(csToolkit, false);
	}
	
	public BoogieIcfgContainer separate(final BoogieIcfgContainer oldBoogieIcfg) {
		/*
		 * obtain partitioning from equality domain abstract interpretation run
		 */
		
		// TODO taken from CodeCheck, what timer is suitable here?
		final IProgressAwareTimer timer = mServices.getProgressMonitorService().getChildTimer(0.2);
		final IAbstractInterpretationResult<VPState<IcfgEdge>, IcfgEdge, IProgramVarOrConst, ?> abstractInterpretationResult =
				AbstractInterpreter.runFutureEqualityDomain(oldBoogieIcfg, timer, mServices, false, mLogger);
		
		final VPDomain<IcfgEdge> vpDomain = (VPDomain<IcfgEdge>) abstractInterpretationResult.getUsedDomain();
		
		printAIResult(abstractInterpretationResult);
		
		/*
		 * process AI result - bring result into partition-form (if it is not yet) - do sanity preprocessing: if, at any
		 * point in the program, two arrays are assigned to each other, then their partitionings must be made compatible
		 * (equal?, through union of partitions?)
		 */
		HeapSepPreAnalysisVisitor heapSepPreanalysis = null;
		{
			final ObserverDispatcher od = new ObserverDispatcherSequential(mLogger);
			final RCFGWalkerBreadthFirst walker = new RCFGWalkerBreadthFirst(od, mLogger);
			od.setWalker(walker);
			
			heapSepPreanalysis = new HeapSepPreAnalysisVisitor(mLogger, mManagedScript, vpDomain);
			walker.addObserver(heapSepPreanalysis);
			
			walker.run(BoogieIcfgContainer.extractStartEdges(oldBoogieIcfg));
		}
		
		final NewArrayIdProvider newArrayIdProvider =
				processAbstractInterpretationResult(abstractInterpretationResult, heapSepPreanalysis);
		
		mLogger.info("built NewArrayIdProvider: " + newArrayIdProvider);
		
		/*
		 * do the transformation itself..
		 */
		
		final ObserverDispatcher od = new ObserverDispatcherSequential(mLogger);
		final RCFGWalkerBreadthFirst walker = new RCFGWalkerBreadthFirst(od, mLogger);
		od.setWalker(walker);
		
		final HeapSepRcfgVisitor hsv =
				new HeapSepRcfgVisitor(mLogger, newArrayIdProvider, mCsToolkit.getManagedScript(), vpDomain);
		walker.addObserver(hsv);
		walker.run(BoogieIcfgContainer.extractStartEdges(oldBoogieIcfg));
		
		return oldBoogieIcfg;
	}
	
	private void printAIResult(
			final IAbstractInterpretationResult<VPState<IcfgEdge>, IcfgEdge, IProgramVarOrConst, ?> abstractInterpretationResult) {
		mLogger.debug("equality domain result");
		for (final Entry<?, Set<VPState<IcfgEdge>>> en : abstractInterpretationResult.getLoc2States().entrySet()) {
			mLogger.debug(en.getKey());
			for (final VPState<IcfgEdge> vps : en.getValue()) {
				mLogger.debug("");
				mLogger.debug(vps);
			}
		}
	}
	
	/**
	 *
	 * @param vpDomainResult
	 * @param hspav
	 * @return a map of the form (unseparated array --> index --> separated array)
	 */
	private NewArrayIdProvider processAbstractInterpretationResult(
			final IAbstractInterpretationResult<VPState<IcfgEdge>, IcfgEdge, IProgramVarOrConst, ?> vpDomainResult,
			final HeapSepPreAnalysisVisitor hspav) {
		final VPDomain<IcfgEdge> vpDomain = (VPDomain<IcfgEdge>) vpDomainResult.getUsedDomain();
		
		/*
		 * compute which arrays are equated somewhere in the program and thus need the same partitioning
		 */
		final UnionFind<IProgramVarOrConst> arrayGroupingUf = new UnionFind<>();
		for (final IProgramVarOrConst array : hspav.getArrayToAccessLocations().getDomain()) {
			arrayGroupingUf.findAndConstructEquivalenceClassIfNeeded(array);
		}
		for (final VPDomainSymmetricPair<IProgramVarOrConst> pair : hspav.getArrayEqualities()) {
			if (arrayGroupingUf.find(pair.getFirst()) == null) {
				continue;
			}
			if (arrayGroupingUf.find(pair.getSecond()) == null) {
				continue;
			}
			arrayGroupingUf.union(pair.getFirst(), pair.getSecond());
		}
		arrayGroupingUf.getAllEquivalenceClasses();
		
		final HashRelation<Set<IProgramVarOrConst>, IcfgLocation> arrayGroupToAccessLocations = new HashRelation<>();
		
		for (final Set<IProgramVarOrConst> ec : arrayGroupingUf.getAllEquivalenceClasses()) {
			for (final IProgramVarOrConst array : ec) {
				for (final IcfgLocation loc : hspav.getArrayToAccessLocations().getImage(array)) {
					arrayGroupToAccessLocations.addPair(ec, loc);
				}
			}
		}
		
		/*
		 * Compute the mapping array to VPState: The HeapSepPreAnalysisVisitor can tell us which arrays are accessed at
		 * which locations. For each array take only the VPStates intro account that belong to a location directly
		 * before an access to that array. Those are disjoined.
		 */
		final Map<Set<IProgramVarOrConst>, VPState<IcfgEdge>> arrayGroupToVPState = new HashMap<>();
		for (final Set<IProgramVarOrConst> ec : arrayGroupingUf.getAllEquivalenceClasses()) {
			final Set<VPState<IcfgEdge>> statesForCurrentEc = new HashSet<>();
			for (final IcfgLocation loc : arrayGroupToAccessLocations.getImage(ec)) {
				final Set<VPState<IcfgEdge>> statesAtLoc = vpDomainResult.getLoc2States().get(loc);
				if (statesAtLoc == null) {
					continue;
				}
				statesForCurrentEc.addAll(statesAtLoc);
			}
			
			VPState<IcfgEdge> disjoinedState;
			if (statesForCurrentEc.isEmpty()) {
				disjoinedState = vpDomain.getVpStateFactory().getTopState(Collections.emptySet());
			} else {
				disjoinedState = vpDomain.getVpStateFactory().disjoinAll(statesForCurrentEc);
			}
			arrayGroupToVPState.put(ec, disjoinedState);
		}
		
		/*
		 * Compute the actual partitioning for each array.
		 */
		final VPDomainPreanalysis vpPreAnalysis =
				((VPDomain<IcfgEdge>) vpDomainResult.getUsedDomain()).getPreAnalysis();
		final NewArrayIdProvider newArrayIdProvider = new NewArrayIdProvider(mCsToolkit);
		for (final Entry<Set<IProgramVarOrConst>, VPState<IcfgEdge>> en : arrayGroupToVPState.entrySet()) {
			final Set<IProgramVarOrConst> arrayGroup = en.getKey();
			final VPState<IcfgEdge> state = en.getValue();
			
			final UnionFind<EqNode> uf = new UnionFind<>();
			for (final EqNode accessingNode : vpPreAnalysis.getAccessingIndicesForArrays(arrayGroup)) {
				uf.findAndConstructEquivalenceClassIfNeeded(accessingNode);
			}
			// TODO: optimization: compute partitioning on the equivalence class representatives instead
			// of all nodes
			for (final EqNode accessingNode1 : vpPreAnalysis.getAccessingIndicesForArrays(arrayGroup)) {
				for (final EqNode accessingNode2 : vpPreAnalysis.getAccessingIndicesForArrays(arrayGroup)) {
					if (state.mayEqual(accessingNode1, accessingNode2)) {
						uf.union(accessingNode1, accessingNode2);
					}
				}
			}
			for (final Set<EqNode> ec : uf.getAllEquivalenceClasses()) {
				newArrayIdProvider.registerEquivalenceClass(arrayGroup, ec);
			}
		}
		
		return newArrayIdProvider;
	}
}
