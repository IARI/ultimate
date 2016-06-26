package de.uni_freiburg.informatik.ultimate.rcfg.serializer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.core.model.models.IElement;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.RCFGEdge;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.RCFGNode;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.RootNode;

public class RCFGTraverser {
	private ArrayList<RCFGNode> mNodes = new ArrayList<>();
	private ArrayList<RCFGEdge> mEdges = new ArrayList<>();

	public Iterable<RCFGNode> getNodes() {
		return mNodes;
	}

	public Iterable<RCFGEdge> getEdges() {
		return mEdges;
	}

	// Map<IElement, String> seenList = new HashMap<>();
	Set<IElement> mSeen = new HashSet<>();

	public RCFGTraverser(RootNode root) {
		traverse(root);
	}

	public void traverse(RCFGNode node) {
		mSeen.add(node);
		mNodes.add(node);
		for (RCFGEdge rcfgEdge : node.getOutgoingEdges()) {
			if (!mSeen.contains(rcfgEdge)) {
				mSeen.add(rcfgEdge);
				mEdges.add(rcfgEdge);
			}
		}
		for (RCFGNode rcfgNode : node.getOutgoingNodes()) {
			if (!mSeen.contains(rcfgNode)) {
				traverse(rcfgNode);
			}
		}
	}
}