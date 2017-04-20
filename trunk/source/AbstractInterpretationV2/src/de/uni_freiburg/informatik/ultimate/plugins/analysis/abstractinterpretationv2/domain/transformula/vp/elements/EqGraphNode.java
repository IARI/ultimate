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
package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.IEqNodeIdentifier;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.VPDomainSymmetricPair;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.states.IVPStateOrTfState;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.states.IVPStateOrTfStateBuilder;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.HashRelation;

/**
 * This class contain information such as representative, reverse
 * representative, ccpar and ccchild of @EqNode. Each @EqNode will map to
 * one @EqGraphNode, i.e., the relation between @EqNode and @EqGraphNode is one
 * to one mapping. Since @EqNode supposed to be immutable, all the mutable
 * information will be handled by this class.
 * 
 * @author Yu-Wen Chen (yuwenchen1105@gmail.com)
 * @author Alexander Nutz (nutz@informatik.uni-freiburg.de)
 *
 */
public class EqGraphNode<NODEID extends IEqNodeIdentifier<ARRAYID>, ARRAYID> {

	/**
	 * identifies an EqGraphNode uniquely _within one state or transitionstate_
	 */
	public final NODEID mNodeIdentifier;

	private EqGraphNode<NODEID, ARRAYID> mRepresentative;
	private Set<EqGraphNode<NODEID, ARRAYID>> mReverseRepresentative;
	private Set<EqGraphNode<NODEID, ARRAYID>> mCcpar;
	private HashRelation<ARRAYID, List<EqGraphNode<NODEID, ARRAYID>>> mCcchild;

	private Set<EqGraphNode<NODEID, ARRAYID>> mInitCcpar;
	private List<EqGraphNode<NODEID, ARRAYID>> mInitCcchild;

	public EqGraphNode(NODEID id) {
		assert id != null;
		
		this.mNodeIdentifier = id;
		this.mRepresentative = this;
		this.mReverseRepresentative = new HashSet<>();
		this.mCcpar = new HashSet<>();
		this.mCcchild = new HashRelation<>();
		this.mInitCcpar = null;
		this.mInitCcchild = null;
	}
	
	/**
	 * This may only be called when all EqGraphNodes for the given state (and thus mapping form Eqnodes to EqGraphNodes)
	 * have been created.
	 * Then this method sets up initCCpar and initCcchild according to the mapping and the parent/argument information in
	 * the EqNode
	 * @param eqNodeToEqGraphNode
	 */
	public void setupNode() {
		mInitCcpar = new HashSet<>(this.mCcpar);
		mInitCcpar = Collections.unmodifiableSet(mInitCcpar);
		
		if (mNodeIdentifier.isFunction()) {
			ARRAYID arrayId = mNodeIdentifier.getFunction();
			assert this.mCcchild.getImage(arrayId).size() == 1;
			mInitCcchild = new ArrayList<>(this.mCcchild.getImage(arrayId).iterator().next());
			mInitCcchild = Collections.unmodifiableList(mInitCcchild);
		}
	}

	public void setNodeToInitial() {
		this.mRepresentative = this;
		this.mReverseRepresentative.clear();
		this.mCcpar.clear();
		this.mCcpar.addAll(mInitCcpar);

		this.mCcchild = new HashRelation<>();
		/*
		 * Only function node have initCcchild.
		 */
		if (mNodeIdentifier.isFunction()) {
			this.mCcchild.addPair(mNodeIdentifier.getFunction(), mInitCcchild);
		}
	}	
	
	public EqGraphNode<NODEID, ARRAYID> find() {
		if (this.getRepresentative().equals(this)) {
			return this;
		}
		return this.getRepresentative().find();
	}

	public static <T extends IVPStateOrTfState<NODEID, ARRAYID>, NODEID extends IEqNodeIdentifier<ARRAYID>, ARRAYID> void copyFields(
			EqGraphNode<NODEID, ARRAYID> source, EqGraphNode<NODEID, ARRAYID> target, IVPStateOrTfStateBuilder<T, NODEID, ARRAYID> builder) {
		assert target.mNodeIdentifier.equals(source.mNodeIdentifier);
		
		EqGraphNode<NODEID, ARRAYID> targetRepresentative = builder.getEqGraphNode(source.getRepresentative().mNodeIdentifier);
		target.setRepresentative(targetRepresentative);
		if (target != targetRepresentative) {
			// we may have to update a disequality such that it talks about the representative
			HashSet<VPDomainSymmetricPair<NODEID>> diseqsetcopy = new HashSet<>(builder.getDisEqualitySet());
			for (VPDomainSymmetricPair<NODEID> diseq : diseqsetcopy) {
				if (diseq.contains(target.mNodeIdentifier)) {
					builder.removeDisEquality(diseq);
					builder.addDisEquality(targetRepresentative.mNodeIdentifier, 
									diseq.getOther(target.mNodeIdentifier));
				}
			}
		}
		
		target.getReverseRepresentative().clear();
		for (EqGraphNode<NODEID, ARRAYID> reverseRe : source.getReverseRepresentative()) {
			target.getReverseRepresentative().add(builder.getEqGraphNode(reverseRe.mNodeIdentifier));
		}
		target.getCcpar().clear();
		for (EqGraphNode<NODEID, ARRAYID> ccpar : source.getCcpar()) {
			target.getCcpar().add(builder.getEqGraphNode(ccpar.mNodeIdentifier));
		}
		
		target.mCcchild = new HashRelation<>();
		for (ARRAYID arrayId : source.getCcchild().getDomain()) {
			for (List<EqGraphNode<NODEID, ARRAYID>> nodes : source.getCcchild().getImage(arrayId)) {
				List<EqGraphNode<NODEID, ARRAYID>> newList = nodes.stream()
						.map(otherNode -> builder.getEqGraphNode(otherNode.mNodeIdentifier))
						.collect(Collectors.toList());
				target.getCcchild().addPair(arrayId, newList);
			}
		}
		
		
		assert !builder.isTop() || target.getRepresentative() == target;
	}

	public EqGraphNode<NODEID, ARRAYID> getRepresentative() {
		return mRepresentative;
	}

	public void setRepresentative(EqGraphNode<NODEID, ARRAYID> representative) {
		this.mRepresentative = representative;
		//TODO check
        // if (eqNodes are identical) then (graphnodes must be identical)
        assert this.mRepresentative.mNodeIdentifier != this.mNodeIdentifier || this.mRepresentative == this;
	}

	public Set<EqGraphNode<NODEID, ARRAYID>> getReverseRepresentative() {
		return mReverseRepresentative;
	}

	public void setReverseRepresentative(Set<EqGraphNode<NODEID, ARRAYID>> reverseRepresentative) {
		this.mReverseRepresentative = reverseRepresentative;
	}

	public void addToReverseRepresentative(EqGraphNode<NODEID, ARRAYID> reverseRepresentative) {
		this.mReverseRepresentative.add(reverseRepresentative);
	}

	public Set<EqGraphNode<NODEID, ARRAYID>> getCcpar() {
		return mCcpar;
	}

	public void setCcpar(Set<EqGraphNode<NODEID, ARRAYID>> ccpar) {
		this.mCcpar = ccpar;
	}

	public void addToCcpar(EqGraphNode<NODEID, ARRAYID> ccpar) {
		this.mCcpar.add(ccpar);
	}
	
	public void addToCcpar(Set<EqGraphNode<NODEID, ARRAYID>> ccpar) {
		this.mCcpar.addAll(ccpar);
	}

	public HashRelation<ARRAYID, List<EqGraphNode<NODEID, ARRAYID>>> getCcchild() {
		return mCcchild;
	}
	
	public void addToCcchild(ARRAYID pVorC, List<EqGraphNode<NODEID, ARRAYID>> ccchild) {
		this.mCcchild.addPair(pVorC, ccchild);
	}
	
	public void addToCcchild(HashRelation<ARRAYID, List<EqGraphNode<NODEID, ARRAYID>>> ccchild2) {
		for (final Entry<ARRAYID, List<EqGraphNode<NODEID, ARRAYID>>> entry : ccchild2.entrySet()) {
			addToCcchild(entry.getKey(), entry.getValue());
		}
	}

	public Set<EqGraphNode<NODEID, ARRAYID>> getInitCcpar() {
		return mInitCcpar;
	}

	public List<EqGraphNode<NODEID, ARRAYID>> getInitCcchild() {
		return mInitCcchild;
	}

	public void setInitCcchild(List<EqGraphNode<NODEID, ARRAYID>> initCcchild) {
		this.mInitCcchild = initCcchild;
	}

	public String toString() {

		final StringBuilder sb = new StringBuilder();

		sb.append(mNodeIdentifier.toString());
		if (mRepresentative != this) {
			sb.append(" ||| representative: ");
			sb.append(mRepresentative.mNodeIdentifier.toString());
		}
		
//		sb.append(" ||| reverseRepresentative: ");
//		for (EqGraphNode node : reverseRepresentative) {
//			sb.append(node.eqNode.toString());
//			sb.append("  ");
//		}
//		sb.append(" ||| ccpar: ");
//		for (EqGraphNode node : ccpar) {
//			sb.append(node.eqNode.toString());
//			sb.append("  ");
//		}
//		sb.append(" ||| ccchild: ");
//		for (final Entry<IProgramVarOrConst, List<EqGraphNode>> entry : ccchild.entrySet()) {
//			sb.append(entry.getKey().toString() + ": {");
//			for (EqGraphNode node : entry.getValue()) {
//				sb.append(node.toString());
//				sb.append("  ");
//			}
//			sb.append("}, ");
//		}

		return sb.toString();
	}
}
