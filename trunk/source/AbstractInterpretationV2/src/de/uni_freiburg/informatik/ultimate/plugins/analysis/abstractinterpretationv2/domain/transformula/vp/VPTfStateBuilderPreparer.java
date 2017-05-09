/*
 * Copyright (C) 2016 Yu-Wen Chen
 * Copyright (C) 2016 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2016 University of Freiburg
 *
 * This file is part of the ULTIMATE AbstractInterpretationV2 plug-in.
 *
 * The ULTIMATE AbstractInterpretationV2 plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE AbstractInterpretationV2 plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE AbstractInterpretationV2 plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE AbstractInterpretationV2 plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE AbstractInterpretationV2 plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.IIcfgSymbolTable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.ICallAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IInternalAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IReturnAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVarOrConst;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.rcfg.RcfgUtils;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.elements.EqFunctionNode;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.elements.EqNode;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.states.VPTfStateBuilder;

/**
 * 
 * @author Alexander Nutz (nutz@informatik.uni-freiburg.de)
 *
 */
public class VPTfStateBuilderPreparer {
	
	private final VPDomainPreanalysis mPreAnalysis;

	private final Set<EqNode> mAllConstantEqNodes;
//	private final Map<TransFormula, VPTfStateBuilder> mTransFormulaToVPTfStateBuilder = 
	private final Map<IAction, VPTfStateBuilder> mActionToVPTfStateBuilder = 
			new HashMap<>();
	private final ILogger mLogger;

	private final IIcfgSymbolTable mSymbolTable;
	
	public VPTfStateBuilderPreparer(VPDomainPreanalysis preAnalysis, IIcfg<?> root, ILogger logger) {
		mPreAnalysis = preAnalysis;
		mLogger = logger;
		mSymbolTable = preAnalysis.getSymbolTable();
		
		Collection<EqNode> allEqNodes = preAnalysis.getAllEqNodes();
		Set<EqFunctionNode> allEqFunctionNodes = 
				allEqNodes.stream()
				.filter(node -> node instanceof EqFunctionNode)
				.map(node -> (EqFunctionNode) node)
				.collect(Collectors.toSet());
		allEqFunctionNodes.removeAll(allEqFunctionNodes);

		Set<EqNode> allConstantEqNodes = 
				allEqNodes.stream()
				.filter(EqNode::isConstant)
				.collect(Collectors.toSet());
		mAllConstantEqNodes = Collections.unmodifiableSet(allConstantEqNodes);

		process(RcfgUtils.getInitialEdges(root));
	}

	private <T extends IcfgEdge> void process(final Collection<T> edges) {
		mLogger.info("started VPDomainPreAnalysis");
		

		final Deque<IcfgEdge> worklist = new ArrayDeque<>();
		final Set<IcfgEdge> finished = new HashSet<>();

		worklist.addAll(edges);
		while (!worklist.isEmpty()) {
			final IcfgEdge current = worklist.removeFirst();
			if (!finished.add(current)) {
				continue;
			}
			if (current instanceof IAction) {
				visit((IAction) current);
			}
			worklist.addAll(current.getTarget().getOutgoingEdges());
		}
	}
	
	protected void visit(IAction c) {
		if (c instanceof ICallAction) {
			visit((ICallAction) c);
		} else if (c instanceof IReturnAction) {
			visit((IReturnAction) c);
		} else if (c instanceof IInternalAction) {
			visit((IInternalAction) c);
		} else {
			assert false : "forgot a case?";
		}
	}
	
	protected void visit(ICallAction c) {
//		TransFormula tf = c.getLocalVarsAssignment();

		final Set<IProgramVarOrConst> inVars = new HashSet<>();
		inVars.addAll(mSymbolTable.getGlobals());
		inVars.addAll(mSymbolTable.getConstants());
		inVars.addAll(mSymbolTable.getLocals(c.getPrecedingProcedure()));
		
		final Set<IProgramVarOrConst> outVars = new HashSet<>();
		outVars.addAll(mSymbolTable.getGlobals());
		outVars.addAll(mSymbolTable.getConstants());
		outVars.addAll(c.getTransformula().getAssignedVars());
		
		handleTransFormula(c, Collections.unmodifiableSet(inVars), Collections.unmodifiableSet(outVars));
	}

	protected void visit(IReturnAction c) {
		
		final Set<IProgramVarOrConst> inVars = new HashSet<>();
		inVars.addAll(mSymbolTable.getGlobals());
		inVars.addAll(mSymbolTable.getConstants());
		inVars.addAll(mSymbolTable.getLocals(c.getPrecedingProcedure()));
		
		final Set<IProgramVarOrConst> outVars = new HashSet<>();
		outVars.addAll(mSymbolTable.getGlobals());
		outVars.addAll(mSymbolTable.getConstants());
		outVars.addAll(c.getTransformula().getAssignedVars());
		
		handleTransFormula(c, Collections.unmodifiableSet(inVars), Collections.unmodifiableSet(outVars));
	}

	protected void visit(IInternalAction c) {
		assert c.getPrecedingProcedure().equals(c.getSucceedingProcedure()) : "have I understood this wrong?";

		final String proc = c.getPrecedingProcedure();
		Set<IProgramVarOrConst> varsInScope = new HashSet<>();
		varsInScope.addAll(mSymbolTable.getGlobals());
		varsInScope.addAll(mSymbolTable.getConstants());
		varsInScope.addAll(mSymbolTable.getLocals(proc));
		varsInScope = Collections.unmodifiableSet(varsInScope);
		
		handleTransFormula(c, varsInScope, varsInScope);
	}

	private void handleTransFormula(final IAction tf, final Set<IProgramVarOrConst> inVars, 
			final Set<IProgramVarOrConst> outVars) {
//		VPTfStateBuilder vptsb = new VPTfStateBuilder(mPreAnalysis, this, tf, mAllConstantEqNodes);
		final VPTfStateBuilder vptsb = 
				new CreateVanillaTfStateBuilder(mPreAnalysis, this, tf, mAllConstantEqNodes, inVars, outVars).create();
		
		mActionToVPTfStateBuilder.put(tf, vptsb);
	}
	
	public VPTfStateBuilder getVPTfStateBuilder(IAction a) {
		VPTfStateBuilder result = mActionToVPTfStateBuilder.get(a);
		assert result != null : "we should have a VPTransitionStateBuidler for every Transformula in the program";
		assert result.isTopConsistent();
		return result;
	}
//
//	public Set<EqNode> getAllConstantEqNodes() {
//		return mAllConstantEqNodes;
//	}
}
