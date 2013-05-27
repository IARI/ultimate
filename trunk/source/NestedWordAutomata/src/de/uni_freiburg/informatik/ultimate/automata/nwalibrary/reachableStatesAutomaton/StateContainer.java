package de.uni_freiburg.informatik.ultimate.automata.nwalibrary.reachableStatesAutomaton;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.IncomingCallTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.IncomingInternalTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.IncomingReturnTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.OutgoingCallTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.OutgoingInternalTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.OutgoingReturnTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.reachableStatesAutomaton.NestedWordAutomatonReachableStates.ReachProp;

public abstract class StateContainer<LETTER, STATE> {

	protected final STATE m_State;
	protected ReachProp m_ReachProp;
	protected final Map<STATE, ReachProp> m_DownStates;
	protected final boolean m_CanHaveOutgoingReturn;

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(m_State.toString());
		sb.append(System.getProperty("line.separator"));
		for (OutgoingInternalTransition<LETTER, STATE> trans : internalSuccessors()) {
			sb.append(trans).append("  ");
		}
		sb.append(System.getProperty("line.separator"));
		for (IncomingInternalTransition<LETTER, STATE> trans : internalPredecessors()) {
			sb.append(trans).append("  ");
		}
		sb.append(System.getProperty("line.separator"));
		for (OutgoingCallTransition<LETTER, STATE> trans : callSuccessors()) {
			sb.append(trans).append("  ");
		}
		sb.append(System.getProperty("line.separator"));
		for (IncomingCallTransition<LETTER, STATE> trans : callPredecessors()) {
			sb.append(trans).append("  ");
		}
		sb.append(System.getProperty("line.separator"));
		for (OutgoingReturnTransition<LETTER, STATE> trans : returnSuccessors()) {
			sb.append(trans).append("  ");
		}
		sb.append(System.getProperty("line.separator"));
		for (IncomingReturnTransition<LETTER, STATE> trans : returnPredecessors()) {
			sb.append(trans).append("  ");
		}
		sb.append(System.getProperty("line.separator"));
		return sb.toString();
	}

	public StateContainer(STATE state, HashMap<STATE,ReachProp> downStates, boolean canHaveOutgoingReturn) {
		m_State = state;
		m_DownStates = downStates;
		m_ReachProp = ReachProp.REACHABLE;
		m_CanHaveOutgoingReturn = canHaveOutgoingReturn;
	}

	public ReachProp getReachProp() {
		return m_ReachProp;
	}

	public void setReachProp(ReachProp reachProp) {
		m_ReachProp = reachProp;
	}

	protected  Map<STATE, ReachProp> getDownStates() {
		return m_DownStates;
	}

	@Override
	public int hashCode() {
		return m_State.hashCode();
	}

	protected STATE getState() {
		return m_State;
	}

	protected boolean containsInternalTransition(LETTER letter, STATE succ) {
		for (OutgoingInternalTransition<LETTER, STATE> trans : internalSuccessors(letter)) {
			if (succ.equals(trans.getSucc())) {
				return true;
			}
		}
		return false;
	}

	protected boolean containsCallTransition(LETTER letter, STATE succ) {
		for (OutgoingCallTransition<LETTER, STATE> trans : callSuccessors(letter)) {
			if (succ.equals(trans.getSucc())) {
				return true;
			}
		}
		return false;
	}

	protected boolean containsReturnTransition(STATE hier, LETTER letter, STATE succ) {
		for (OutgoingReturnTransition<LETTER, STATE> trans : returnSuccessors(hier, letter)) {
			if (succ.equals(trans.getSucc())) {
				return true;
			}
		}
		return false;
	}
	
	
	public abstract Collection<LETTER> lettersInternal();

	public abstract Collection<LETTER> lettersInternalIncoming();

	public abstract Collection<LETTER> lettersCall();

	public abstract Collection<LETTER> lettersCallIncoming();

	public abstract Collection<LETTER> lettersReturn();

	public abstract Collection<LETTER> lettersReturnIncoming();

	public abstract Collection<STATE> succInternal(LETTER letter);

	public abstract Collection<STATE> predInternal(LETTER letter);

	public abstract Collection<STATE> succCall(LETTER letter);

	public abstract Collection<STATE> predCall(LETTER letter);

	public abstract Collection<STATE> hierPred(LETTER letter);

	public abstract Collection<STATE> succReturn(STATE hier, LETTER letter);

	public abstract Collection<STATE> predReturnLin(LETTER letter, STATE hier);

	public abstract Collection<STATE> predReturnHier(LETTER letter);


	public abstract Iterable<OutgoingInternalTransition<LETTER, STATE>> internalSuccessors(final LETTER letter);

	public abstract Iterable<OutgoingInternalTransition<LETTER, STATE>> internalSuccessors();

	public abstract Iterable<IncomingInternalTransition<LETTER, STATE>> internalPredecessors(final LETTER letter);

	public abstract Iterable<IncomingInternalTransition<LETTER, STATE>> internalPredecessors();
	
	public abstract Iterable<OutgoingCallTransition<LETTER, STATE>> callSuccessors(final LETTER letter);

	public abstract Iterable<OutgoingCallTransition<LETTER, STATE>> callSuccessors();

	public abstract Iterable<IncomingCallTransition<LETTER, STATE>> callPredecessors(final LETTER letter);

	public abstract Iterable<IncomingCallTransition<LETTER, STATE>> callPredecessors();
	
	public abstract Iterable<OutgoingReturnTransition<LETTER, STATE>> returnSuccessors(final STATE hier, final LETTER letter);

	public abstract Iterable<OutgoingReturnTransition<LETTER, STATE>> returnSuccessors(final LETTER letter);

	public abstract Iterable<OutgoingReturnTransition<LETTER, STATE>> returnSuccessorsGivenHier(final STATE hier);

	public abstract Iterable<OutgoingReturnTransition<LETTER, STATE>> returnSuccessors();

	public abstract Iterable<IncomingReturnTransition<LETTER, STATE>> returnPredecessors(final STATE hier, final LETTER letter);

	public abstract Iterable<IncomingReturnTransition<LETTER, STATE>> returnPredecessors(final LETTER letter);

	public abstract Iterable<IncomingReturnTransition<LETTER, STATE>> returnPredecessors();

	
	
	
	abstract void addInternalOutgoing(OutgoingInternalTransition<LETTER, STATE> internalOutgoing);

	abstract void addInternalIncoming(IncomingInternalTransition<LETTER, STATE> internalIncoming);

	abstract void addCallOutgoing(OutgoingCallTransition<LETTER, STATE> callOutgoing);

	abstract void addCallIncoming(IncomingCallTransition<LETTER, STATE> callIncoming);

	abstract void addReturnOutgoing(OutgoingReturnTransition<LETTER, STATE> returnOutgoing);

	abstract void addReturnIncoming(IncomingReturnTransition<LETTER, STATE> returnIncoming);

}
