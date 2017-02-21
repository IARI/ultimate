/*
 * Copyright (C) 2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
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

package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.empty;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractState;

/**
 * This is an abstract state of the {@link EmptyDomain}. It does save variable declarations, but no values or value
 * representations. It is equal to other {@link EmptyDomainState} instances with the same variable declarations.
 *
 * This state is never bottom but always a fixpoint.
 *
 * @param <ACTION>
 *            The action (i.e., the type of statements or transitions) on which this empty domain should operate.
 * @param <IBoogieVar>
 *            The variable declaration type of the current model.
 *
 * @author dietsch@informatik.uni-freiburg.de
 *
 *
 */
public final class EmptyDomainState<VARDECL> implements IAbstractState<EmptyDomainState<VARDECL>, VARDECL> {

	private static int sId;
	private final Set<VARDECL> mVarDecls;
	private final int mId;
	private final boolean mIsBottom;

	protected EmptyDomainState() {
		this(new HashSet<>());
	}

	protected EmptyDomainState(final boolean isBottom) {
		this(new HashSet<>(), isBottom);
	}

	protected EmptyDomainState(final Set<VARDECL> varDecls) {
		this(varDecls, false);
	}

	protected EmptyDomainState(final Set<VARDECL> varDecls, final boolean isBottom) {
		mVarDecls = varDecls;
		sId++;
		mId = sId;
		mIsBottom = isBottom;
	}

	@Override
	public EmptyDomainState<VARDECL> addVariable(final VARDECL variable) {
		assert variable != null;

		final Set<VARDECL> newMap = new HashSet<>(mVarDecls);
		if (!newMap.add(variable)) {
			throw new UnsupportedOperationException("Variable names have to be disjoint");
		}
		return new EmptyDomainState<>(newMap);
	}

	@Override
	public EmptyDomainState<VARDECL> removeVariable(final VARDECL variable) {
		assert variable != null;
		final Set<VARDECL> newMap = new HashSet<>(mVarDecls);
		final boolean result = newMap.remove(variable);
		assert result;
		return new EmptyDomainState<>(newMap);
	}

	@Override
	public EmptyDomainState<VARDECL> addVariables(final Collection<VARDECL> variables) {
		assert variables != null;
		assert !variables.isEmpty();

		final Set<VARDECL> newMap = new HashSet<>(mVarDecls);
		for (final VARDECL entry : variables) {
			if (!newMap.add(entry)) {
				throw new UnsupportedOperationException("Variable names have to be disjoint");
			}
		}
		return new EmptyDomainState<>(newMap);
	}

	@Override
	public EmptyDomainState<VARDECL> removeVariables(final Collection<VARDECL> variables) {
		assert variables != null;
		assert !variables.isEmpty();

		final Set<VARDECL> newMap = new HashSet<>(mVarDecls);
		for (final VARDECL entry : variables) {
			newMap.remove(entry);
		}
		return new EmptyDomainState<>(newMap);
	}

	@Override
	public boolean isEmpty() {
		return mVarDecls.isEmpty();
	}

	@Override
	public boolean isBottom() {
		return mIsBottom;
	}

	@Override
	public String toLogString() {
		final StringBuilder sb = new StringBuilder();
		for (final VARDECL entry : mVarDecls) {
			sb.append(entry).append("; ");
		}
		return sb.toString();
	}

	@Override
	public boolean isEqualTo(final EmptyDomainState<VARDECL> other) {
		if (other == null) {
			return false;
		}

		if (other.equals(this)) {
			return true;
		}

		if (other.mVarDecls.size() != mVarDecls.size()) {
			return false;
		}

		for (final VARDECL entry : mVarDecls) {
			if (!other.mVarDecls.contains(entry)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public String toString() {
		return toLogString();
	}

	@Override
	public int hashCode() {
		return mId;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		@SuppressWarnings("unchecked")
		final EmptyDomainState<VARDECL> other = (EmptyDomainState<VARDECL>) obj;
		return mId == other.mId;
	}

	/**
	 * This method compares if this state contains the same variable declarations than the other state.
	 *
	 * @param other
	 *            another state
	 * @return true iff this state has the same variables than other
	 */
	boolean hasSameVariables(final EmptyDomainState<VARDECL> other) {
		return isEqualTo(other);
	}

	@Override
	public Set<VARDECL> getVariables() {
		return Collections.unmodifiableSet(mVarDecls);
	}

	@Override
	public boolean containsVariable(final VARDECL var) {
		return mVarDecls.contains(var);
	}

	@Override
	public Term getTerm(final Script script) {
		return script.term("true");
	}

	@Override
	public EmptyDomainState<VARDECL> patch(final EmptyDomainState<VARDECL> dominator) {
		if (dominator.isEmpty()) {
			return this;
		} else if (isEmpty()) {
			return dominator;
		}

		final Set<VARDECL> newVarDecls = new HashSet<>();
		newVarDecls.addAll(mVarDecls);
		newVarDecls.addAll(dominator.mVarDecls);

		if (newVarDecls.size() == mVarDecls.size()) {
			return this;
		}

		return new EmptyDomainState<>(newVarDecls);
	}

	@Override
	public SubsetResult isSubsetOf(final EmptyDomainState<VARDECL> other) {
		assert hasSameVariables(other);
		return isEqualTo(other) ? SubsetResult.EQUAL : SubsetResult.NONE;
	}
}
