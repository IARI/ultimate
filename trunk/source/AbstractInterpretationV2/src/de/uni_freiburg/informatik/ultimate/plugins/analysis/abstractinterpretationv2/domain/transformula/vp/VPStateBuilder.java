package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVarOrConst;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.HashRelation;

public class VPStateBuilder extends IVPStateOrTfStateBuilder<VPState> {

	protected Set<IProgramVar> mVars;
//	protected Set<VPDomainSymmetricPair<EqNode>> mDisEqualitySet;
	protected final VPDomain mDomain;
	protected boolean mIsTop;
	protected EqGraph mEqGraph;
	
	private Map<EqNode, EqGraphNode> mEqNodeToEqGraphNodeMap;

	public VPStateBuilder(VPDomain domain) {
		mDomain = domain;
		mEqGraph = new EqGraph();
		createEqGraphNodes();
		mVars = new HashSet<>();
	}
	
	protected VPStateBuilder(VPDomain domain, boolean dontCreateEqGraphNodes) {
		assert dontCreateEqGraphNodes;
		mDomain = domain;
		mEqGraph = new EqGraph();
		mVars = new HashSet<>();
	}
	
	private void createEqGraphNodes() {
		/*
		 * Create fresh EqGraphNodes from EqNodes.
		 */
		Map<EqNode, EqGraphNode> eqNodeToEqGraphNodeMap = new HashMap<>();
		for (EqNode eqNode : mDomain.getTermToEqNodeMap().values()) {
			getOrConstructEqGraphNode(eqNode, eqNodeToEqGraphNodeMap);
		}
		mEqNodeToEqGraphNodeMap = Collections.unmodifiableMap(eqNodeToEqGraphNodeMap);
	}

	private EqGraphNode getOrConstructEqGraphNode(
			EqNode eqNode, 
			Map<EqNode, EqGraphNode> eqNodeToEqGraphNode) {

		if (eqNodeToEqGraphNode.containsKey(eqNode)) {
			return eqNodeToEqGraphNode.get(eqNode);
		}

		final EqGraphNode graphNode = new EqGraphNode(eqNode);
		List<EqGraphNode> argNodes = new ArrayList<>();

		if (eqNode instanceof EqFunctionNode) {

			for (EqNode arg : ((EqFunctionNode)eqNode).getArgs()) {
				EqGraphNode argNode = getOrConstructEqGraphNode(arg, eqNodeToEqGraphNode);
				//				argNode.addToInitCcpar(graphNode);
				argNode.addToCcpar(graphNode);
				argNodes.add(argNode);
			}
			//			graphNode.addToInitCcchild(argNodes);
			graphNode.getCcchild().addPair(
					new VPArrayIdentifier(((EqFunctionNode)eqNode).getFunction()), 
					argNodes);
		}
		eqNodeToEqGraphNode.put(eqNode, graphNode);
		return graphNode;
	}

	public VPStateBuilder setVars(Set<IProgramVar> vars) { 
		mVars = new HashSet<>(vars);
		return this;
	}

	public VPStateBuilder setEqGraphNodes(Map<EqNode, EqGraphNode> map) {
		assert map != null;
		mEqNodeToEqGraphNodeMap = map;
		return this;
	}
	
	@Override
	VPState build() {
		assert mEqNodeToEqGraphNodeMap != null;
//		Set<VPDomainSymmetricPair<VPNodeIdentifier>> disEqualitySet = mDisEqualitySet.stream()
//				.map(pair -> new VPDomainSymmetricPair<>(pair.getFirst().getEqNode(), pair.getFirst().getEqNode()))
//				.collect(Collectors.toSet());
		return new VPState(mEqNodeToEqGraphNodeMap, mDisEqualitySet, mVars, mDomain, mIsTop);
	}

//	public VPStateBuilder setDisEqualites(Set<VPDomainSymmetricPair<EqNode>> disEqualitySet) {
//		mDisEqualitySet = disEqualitySet;
//		return this;
//	}

//	public Map<EqNode, EqGraphNode> getEqNodeToEqGraphNodeMap() {
//		return mEqGraph.getEqNodeToEqGraphNodeMap();
//	}

//	public Set<VPDomainSymmetricPair<EqNode>> getDisEqualitySet() {
//		return mDisEqualitySet;
//	}
	
	@Override
	public VPStateBuilder setIsTop(boolean b) {
		mIsTop = b;
		return this;
	}
	
	public void addToDisEqSet(final EqNode node1, final EqNode node2) {
//		getDisEqualitySet().add(new VPDomainSymmetricPair<>(node1, node2));
		mDisEqualitySet.add(
				new VPDomainSymmetricPair<VPNodeIdentifier>(
						new VPNodeIdentifier(node1),
						new VPNodeIdentifier(node2)));
	}
	
//	/**
//	 * An additional process after a function node is havoc, in order to restore the propagation.
//	 * For example, we have two nodes a[i] and a[j], if i = j, by equality propagation,
//	 * we also know a[i] = a[j]. When a[i] is being havoc, we lose the information of a[i] = a[j], 
//	 * which is the result of equality propagation of (i = j). This method is to restore this 
//	 * information.
//	 * 
//	 * @param functionNode
//	 */
//	void restorePropagation(final EqFunctionNode functionNode) {
//
//		EqNode firstIndex = functionNode.getArgs().get(0);
//		EqGraphNode firstIndexGN = getEqNodeToEqGraphNodeMap().get(firstIndex);
//		
//		final Set<EqFunctionNode> fnNodeSet = mDomain.getArrayIdToEqFnNodeMap().getImage(functionNode.getFunction());
//		for (final EqFunctionNode fnNode : fnNodeSet) {
//			if (find(getEqNodeToEqGraphNodeMap().get(fnNode.getArgs().get(0))).equals(find(firstIndexGN))) {
//				if (mEqGraph.congruent(getEqNodeToEqGraphNodeMap().get(fnNode), getEqNodeToEqGraphNodeMap().get(functionNode))) {
//					merge(getEqNodeToEqGraphNodeMap().get(fnNode), getEqNodeToEqGraphNodeMap().get(functionNode));
//				}
//			}
//		}
//		
////		for (final EqFunctionNode fnNode1 : fnNodeSet) {
////			for (final EqFunctionNode fnNode2 : fnNodeSet) {
////				if (!fnNode1.equals(fnNode2) && mEqGraph.congruent(getEqNodeToEqGraphNodeMap().get(fnNode1), getEqNodeToEqGraphNodeMap().get(fnNode2))) {
////					merge(getEqNodeToEqGraphNodeMap().get(fnNode1), getEqNodeToEqGraphNodeMap().get(fnNode2));
////				}
////			}
////		}
//	}
	
	public void addVariable(IProgramVar pv) {
		mVars.add(pv);
	}

	public void removeVariable(IProgramVar pv) {
		mVars.remove(pv);
	}

	public Map<EqNode, EqGraphNode> getEqNodeToEqGraphNodeMap() {
		return mEqNodeToEqGraphNodeMap;
	}

//	protected class EqGraph {
//		/**
//		 * Union of two equivalence classes. 
//		 * The representative of node1 will become the representative of node2.
//		 *
//		 * @param node1
//		 * @param node2
//		 */
//		protected void union(final EqGraphNode node1, final EqGraphNode node2) {
//	
//			final EqGraphNode graphNode1Find = find(node1);
//			final EqGraphNode graphNode2Find = find(node2);
//	
//			if (!graphNode1Find.equals(graphNode2Find)) {
//				graphNode2Find.addToReverseRepresentative(graphNode1Find);
//				graphNode1Find.setRepresentative(graphNode2Find);
//				graphNode2Find.addToCcpar(graphNode1Find.getCcpar());
//				for (final Entry<IProgramVarOrConst, List<EqGraphNode>> entry : graphNode1Find.getCcchild().entrySet()) {
//					graphNode2Find.getCcchild().addPair(entry.getKey(), entry.getValue());
//				}
//				
//				/*
//				 * Because of the change of representative, the disequality set also need to be updated.
//				 */
//				Set<VPDomainSymmetricPair<EqNode>> copyOfDisEqSet = new HashSet<>(mDisEqualitySet);
//				for (VPDomainSymmetricPair<EqNode> pair : copyOfDisEqSet) {
//					if (pair.contains(graphNode1Find.eqNode)) {
//						EqNode first = pair.getFirst();
//						EqNode second = pair.getSecond();
//						
//						/*
//						 * TODO check: If both nodes in pair are constant, ignore it.
//						 */
//						if (first.isLiteral() && second.isLiteral()) {
//							continue;
//						}
//						
//						mDisEqualitySet.remove(pair);
//						if (first.equals(graphNode1Find.eqNode)) {
//							mDisEqualitySet.add(
//									new VPDomainSymmetricPair<EqNode>(graphNode2Find.eqNode, second));
//						} else {
//							mDisEqualitySet.add(
//									new VPDomainSymmetricPair<EqNode>(first, graphNode2Find.eqNode));
//						}
//					}
//				}
//			}
//		}
//
//		private void equalityPropagation(final EqGraphNode node1, final EqGraphNode node2) {
//			final Set<EqGraphNode> p1 = ccpar(node1);
//			final Set<EqGraphNode> p2 = ccpar(node2);
//
//			for (final EqGraphNode t1 : p1) {
//				for (final EqGraphNode t2 : p2) {
//					if (!(find(t1).equals(find(t2))) && congruent(t1, t2)) {
//						merge(t1, t2);
//					}
//				}
//			}
//		}	
//
//		/**
//		 * Check whether @param node1 and @param node2 are congruent.
//		 *
//		 * @param node1
//		 * @param node2
//		 * @return true if they are congruent
//		 */
//		private boolean congruent(final EqGraphNode node1, final EqGraphNode node2) {
//			if (!(node1.eqNode instanceof EqFunctionNode) || !(node2.eqNode instanceof EqFunctionNode)) {
//				return false;
//			}
//
//			final EqFunctionNode fnNode1 = (EqFunctionNode) node1.eqNode;
//			final EqFunctionNode fnNode2 = (EqFunctionNode) node2.eqNode;
//
//			if (!(fnNode1.getFunction().equals(fnNode2.getFunction()))) {
//				return false;
//			}
//			return congruentIgnoreFunctionSymbol(fnNode1, fnNode2);
//		}
//		
//		/* Returns the parents of all nodes in @param node's congruence class.
//		 *
//		 * @param node
//		 * @return
//		 */
//		private Set<EqGraphNode> ccpar(final EqGraphNode node) {
//			return find(node).getCcpar();
//		}
//	}

	public void addVariables(Collection<IProgramVar> variables) {
		mVars.addAll(variables);
	}

	public void removeVariables(Collection<IProgramVar> variables) {
		mVars.removeAll(variables);
	}
	
//	/**
//	 * Checks if the arguments of the given EqFunctionNodes are all congruent.
//	 *
//	 * @param fnNode1
//	 * @param fnNode2
//	 * @return
//	 */
//	boolean congruentIgnoreFunctionSymbol(final EqFunctionNode fnNode1, final EqFunctionNode fnNode2) {
//		assert fnNode1.getArgs() != null && fnNode2.getArgs() != null;
//		assert fnNode1.getArgs().size() == fnNode2.getArgs().size();
//
//		for (int i = 0; i < fnNode1.getArgs().size(); i++) {
//			final EqNode fnNode1Arg = fnNode1.getArgs().get(i);
//			final EqNode fnNode2Arg = fnNode2.getArgs().get(i);
//			if (!find(getEqNodeToEqGraphNodeMap().get(fnNode1Arg))
//					.equals(find(getEqNodeToEqGraphNodeMap().get(fnNode2Arg)))) {
//				return false;
//			}
//		}
//		return true;
//	}

	public HashRelation<VPArrayIdentifier, List<EqGraphNode>> ccchild(EqGraphNode representative1) {
		return representative1.find().getCcchild();
	}

	@Override
	EqGraphNode getEqGraphNode(VPNodeIdentifier id) {
		assert id.getEqNode() != null;
		EqGraphNode result = mEqNodeToEqGraphNodeMap.get(id.getEqNode());
		assert result != null;
		return result;
	}

//	@Override
//	void addToDisEqSet(VPNodeIdentifier nodeIdentifier, VPNodeIdentifier nodeIdentifier2) {
//		assert nodeIdentifier.getEqNode() != null;
//		assert nodeIdentifier2.getEqNode() != null;
//		
//		EqNode f1 = find(nodeIdentifier.getEqNode());
//		EqNode f2 = find(nodeIdentifier2.getEqNode());
//		
//		mDisEqualitySet.add(new VPDomainSymmetricPair<EqNode>(f1, f2));
//	}

	private EqNode find(EqNode eqNode) {
		EqGraphNode gn = mEqNodeToEqGraphNodeMap.get(eqNode);
		assert gn != null;
		return gn.find().nodeIdentifier.getEqNode();
	}
}
