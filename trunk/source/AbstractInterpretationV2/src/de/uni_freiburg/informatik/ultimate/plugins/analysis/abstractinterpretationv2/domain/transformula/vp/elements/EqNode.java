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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.Substitution;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.IEqNodeIdentifier;

/**
 * 
 * @author Yu-Wen Chen (yuwenchen1105@gmail.com)
 * @author Alexander Nutz (nutz@informatik.uni-freiburg.de)
 *
 */
public abstract class EqNode implements IEqNodeIdentifier<EqNode, EqFunction> {
	
	protected final EqNodeAndFunctionFactory mEqNodeFactory;

	@Deprecated
	protected Set<IProgramVar> mVariables;
	
	/**
	 * Is true iff this EqNode's term only uses global program symbols.
	 */
	@Deprecated
	protected boolean mIsGlobal;
	
	protected boolean mIsConstant;

	@Deprecated
	private String mProcedure;
	
	private Set<EqNode> mParents = new HashSet<>();

	protected Term mTerm;
	
	/**
	 * indicates whether the Term only contains the standard TermVariables belonging to the IProgramVars, or if it
	 * is "versioned".
	 */
	@Deprecated
	protected boolean mTermIsVersioned;
	
	
	
	public EqNode(Term term, EqNodeAndFunctionFactory eqNodeFactory) {
		mTerm = term;
		mEqNodeFactory = eqNodeFactory;
	}


	@Deprecated
	EqNode(boolean isGlobal, boolean isConstant, String procedure, EqNodeAndFunctionFactory eqNodeFactory) {
		assert isGlobal || procedure != null;
		mIsGlobal = isGlobal;
		mIsConstant = isConstant;
		mProcedure = procedure;
		mEqNodeFactory = eqNodeFactory;
		mTermIsVersioned = false;
	}

	@Deprecated
	public EqNode(boolean isGlobal, boolean isConstant, String procedure, Term versionedTerm, 
			EqNodeAndFunctionFactory eqNodeFactory) {
		assert isGlobal || procedure != null;
		mIsGlobal = isGlobal;
		mIsConstant = isConstant;
		mProcedure = procedure;
		mEqNodeFactory = eqNodeFactory;
		mTerm = versionedTerm;
		mTermIsVersioned = true;
	}

	/**
	 * Yields the parents of this node in the EqNode graph (where the edges mean "is applied to"/"is a function argument
	 *  of"). Can be used to obtain initial ccParents for the corresponding EqGraphNode.
	 */
	@Deprecated
	Set<EqNode> getParents() {
		return mParents;
	}
	
	@Deprecated
	public void addParent(EqNode parent) {
		mParents.add(parent);
	}

	public abstract boolean isLiteral();

	/**
	 * Is true iff this EqNode's term only uses global program symbols.
	 */
	@Deprecated
	public boolean isGlobal() {
		return mIsGlobal;
	}
	
	
	public boolean isConstant() {
		return mIsConstant;
	}
	
	public Collection<TermVariable> getFreeVariables() {
		return Arrays.asList(mTerm.getFreeVars());
	}

	@Deprecated
	public Set<IProgramVar> getVariables() {
		return mVariables;
	}

	public Term getTerm() {
		return mTerm;
	}
	
	/**
	 * Returns procedure that this EqNode is local to. null if it is global.
	 */
	public String getProcedure() {
		return mProcedure;
	}
	
	public final EqNode renameVariables(Map<Term, Term> substitutionMapping) {
		final Term substitutedTerm = 
				new Substitution(mEqNodeFactory.mMgdScript, substitutionMapping).transform(getTerm());
		return mEqNodeFactory.getOrConstructEqNode(substitutedTerm);
	}
}
