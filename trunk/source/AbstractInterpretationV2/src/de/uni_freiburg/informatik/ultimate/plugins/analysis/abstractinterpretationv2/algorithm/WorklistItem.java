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
package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.model.IAbstractState;
import de.uni_freiburg.informatik.ultimate.util.relation.Pair;

/**
 * 
 * @author dietsch@informatik.uni-freiburg.de
 */
final class WorklistItem<STATE extends IAbstractState<STATE, ACTION, VARDECL>, ACTION, VARDECL, LOCATION> {

	private final STATE mPreState;
	private final ACTION mAction;
	private final Deque<IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION>> mScopedStorages;
	private final Deque<LOCATION> mActiveLoops;
	private final Map<LOCATION, Integer> mLoopCounters;
	private final WorklistItem<STATE, ACTION, VARDECL, LOCATION> mPredecessor;

	private Deque<ACTION> mScopes;

	protected WorklistItem(final STATE pre, final ACTION action,
			final IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION> globalStorage) {
		assert action != null;
		assert pre != null;
		assert globalStorage != null;

		mPreState = pre;
		mAction = action;
		mScopedStorages = new ArrayDeque<IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION>>();
		mScopedStorages.addFirst(globalStorage);
		mActiveLoops = new ArrayDeque<>();
		mLoopCounters = new HashMap<>();
		mPredecessor = null;
	}

	protected WorklistItem(final STATE pre, final ACTION action,
			WorklistItem<STATE, ACTION, VARDECL, LOCATION> oldItem) {
		assert pre != null;
		assert action != null;
		assert oldItem != null;
		mPredecessor = oldItem;
		mPreState = pre;
		mAction = action;
		mScopes = oldItem.getScopesCopy();
		mScopedStorages = oldItem.getStoragesCopy();
		mActiveLoops = new ArrayDeque<>(oldItem.mActiveLoops);
		mLoopCounters = new HashMap<>(oldItem.mLoopCounters);
	}

	public ACTION getAction() {
		return mAction;
	}

	public STATE getPreState() {
		return mPreState;
	}

	public void addScope(ACTION scope) {
		assert scope != null;
		if (mScopes == null) {
			mScopes = new ArrayDeque<ACTION>();
		}
		mScopes.addFirst(scope);
		mScopedStorages.addFirst(getCurrentStorage().createStorage());
	}

	public ACTION getCurrentScope() {
		if (mScopes == null || mScopes.isEmpty()) {
			return null;
		}
		return mScopes.peek();
	}

	public ACTION removeCurrentScope() {
		if (mScopes == null || mScopes.isEmpty()) {
			return null;
		}
		mScopedStorages.removeFirst();
		return mScopes.removeFirst();
	}

	public IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION> getCurrentStorage() {
		assert !mScopedStorages.isEmpty();
		return mScopedStorages.peek();
	}

	public int getCallStackDepth() {
		if (mScopes == null || mScopes.isEmpty()) {
			return 0;
		}
		return mScopes.size();
	}


	/**
	 * 
	 * @return A {@link Deque} that contains pairs of scopes and the corresponding state storage.
	 */
	public Deque<Pair<ACTION, IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION>>> getStack() {
		final ArrayDeque<Pair<ACTION, IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION>>> rtr = new ArrayDeque<>();
		final Iterator<IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION>> storageIter = mScopedStorages
				.iterator();
		// first, add the global storage
		rtr.add(new Pair<ACTION, IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION>>(null, storageIter.next()));
		if (mScopes == null || mScopes.isEmpty()) {
			return rtr;
		}

		final Iterator<ACTION> scopeIter = mScopes.iterator();

		while (scopeIter.hasNext() && storageIter.hasNext()) {
			rtr.add(new Pair<ACTION, IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION>>(scopeIter.next(),
					storageIter.next()));
		}
		assert !scopeIter.hasNext();
		assert !storageIter.hasNext();

		return rtr;
	}

	public boolean hasActiveLoop() {
		return !mActiveLoops.isEmpty();
	}

	public boolean isActiveLoopHead(LOCATION currentLoopHead) {
		return mActiveLoops.peek() == currentLoopHead;
	}

	public int leaveCurrentLoop() {
		assert !mActiveLoops.isEmpty() : "Active loops is empty";
		final LOCATION lastLoopHead = mActiveLoops.pop();
		final Integer loopCounterValue = mLoopCounters.get(lastLoopHead);
		assert loopCounterValue != null;
		mLoopCounters.put(lastLoopHead, loopCounterValue + 1);
		return loopCounterValue + 1;
	}

	public int enterLoop(final LOCATION loopHead) {
		mActiveLoops.push(loopHead);
		Integer loopCounterValue = mLoopCounters.get(loopHead);
		if (loopCounterValue == null) {
			loopCounterValue = 0;
			mLoopCounters.put(loopHead, loopCounterValue);
		}
		return loopCounterValue;
	}
	
	public WorklistItem<STATE, ACTION, VARDECL, LOCATION> getPredecessor(){
		return mPredecessor;
	}
	
	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder().append("[").append(mPreState.hashCode()).append("]--[")
				.append(mAction.hashCode()).append("]--> ? (Scope={");
		for (final ACTION scope : mScopes) {
			builder.append("[").append(scope.hashCode()).append("]");
		}
		builder.append("})");
		return builder.toString();
	}
	
	private Deque<ACTION> getScopesCopy() {
		if (mScopes == null || mScopes.isEmpty()) {
			return null;
		}
		return new ArrayDeque<>(mScopes);
	}

	private Deque<IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION>> getStoragesCopy() {
		assert !mScopedStorages.isEmpty();
		return new ArrayDeque<>(mScopedStorages);
	}
}
