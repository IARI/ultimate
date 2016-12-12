package de.uni_freiburg.informatik.ultimate.rcfg.serializer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.core.model.models.IElement;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.RootNode;

public class RCFGTraverser {
	private ArrayList<IcfgLocation> mNodes = new ArrayList<>();
	private ArrayList<IcfgEdge> mEdges = new ArrayList<>();

	public Iterable<IcfgLocation> getNodes() {
		return mNodes;
	}

	public Iterable<IcfgEdge> getEdges() {
		return mEdges;
	}

	// Map<IElement, String> seenList = new HashMap<>();
	Set<IElement> mSeen = new HashSet<>();

	public RCFGTraverser(RootNode root) {
		traverse(root);
	}

	public void traverse(IcfgLocation node) {
		mSeen.add(node);
		mNodes.add(node);
		for (IcfgEdge IcfgEdge : node.getOutgoingEdges()) {
			if (!mSeen.contains(IcfgEdge)) {
				mSeen.add(IcfgEdge);
				mEdges.add(IcfgEdge);
			}
		}
		for (IcfgLocation IcfgLocation : node.getOutgoingNodes()) {
			if (!mSeen.contains(IcfgLocation)) {
				traverse(IcfgLocation);
			}
		}
	}
}