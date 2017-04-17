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
package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.elements;

import java.util.Set;

import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.VPDomainSymmetricPair;

/**
 *
 * @author Alexander Nutz (nutz@informatik.uni-freiburg.de)
 */
public class NodeIdWithSideCondition implements INodeOrArrayWithSideCondition {
	
	final VPTfNodeIdentifier mNodeId;
	
	final Set<VPDomainSymmetricPair<VPTfNodeIdentifier>> mEqualities;

	final Set<VPDomainSymmetricPair<VPTfNodeIdentifier>> mDisEqualities;

	public NodeIdWithSideCondition(VPTfNodeIdentifier nodeId,
			Set<VPDomainSymmetricPair<VPTfNodeIdentifier>> equalities,
			Set<VPDomainSymmetricPair<VPTfNodeIdentifier>> disEqualities) {
		this.mNodeId = nodeId;
		this.mEqualities = equalities;
		this.mDisEqualities = disEqualities;
	}

	public VPTfNodeIdentifier getNodeId() {
		return mNodeId;
	}

	public Set<VPDomainSymmetricPair<VPTfNodeIdentifier>> getEqualities() {
		return mEqualities;
	}

	public Set<VPDomainSymmetricPair<VPTfNodeIdentifier>> getDisEqualities() {
		return mDisEqualities;
	}
	
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		sb.append("NodeIdWithSideCondition: ");
		
		sb.append(mNodeId);

		//sb.append("\n");
		sb.append(", if: ");
		
		String sep = "";
		
		for (VPDomainSymmetricPair<VPTfNodeIdentifier> eq : mEqualities) {
			sb.append(sep);
			sb.append(eq.getFirst().getEqNode() + "=" + eq.getSecond().getEqNode());
			sep = ", ";
		}

		for (VPDomainSymmetricPair<VPTfNodeIdentifier> deq : mDisEqualities) {
			sb.append(sep);
			sb.append(deq.getFirst().getEqNode() + "!=" + deq.getSecond().getEqNode());
			sep = ", ";
		}
		
		return sb.toString();
	}
}
