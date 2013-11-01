package de.uni_freiburg.informatik.ultimate.result;

import de.uni_freiburg.informatik.ultimate.model.AbstractAnnotations;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.wrapper.ASTNode;

/**
 * Specification that should be checked at position 
 * @author Markus Lindenmann
 * @author Stefan Wissert
 * @author Oleksii Saukh
 * @author Matthias Heizmann
 */
public class Check extends AbstractAnnotations {
	private static final long serialVersionUID = -3753413284642976683L;

	public static String getIdentifier() {
		return Check.class.getName();
	}
	
	public enum Spec {
	    /**
	     * Array Index out of bounds error.
	     */
	    ARRAY_INDEX,
	    /**
	     * Pre condition violated.
	     */
	    PRE_CONDITION,
	    /**
	     * Post condition violated.
	     */
	    POST_CONDITION,
	    /**
	     * Invariant violated.
	     */
	    INVARIANT,
	    /**
	     * Assert statement violated.
	     */
	    ASSERT,
	    /**
	     * Devision by zero error.
	     */
	    DIVISION_BY_ZERO,
	    /**
	     * Integer overflow error.
	     */
	    INTEGER_OVERFLOW,
	    /**
	     * Tried to access unallocated memory.
	     */
	    MEMORY_DEREFERENCE,
	    /**
	     * Memory leak detected. I.e. missing free!
	     */
	    MEMORY_LEAK,
	    /**
	     * Free of unallocated pointer.
	     */
	    MEMORY_FREE,
	    /**
	     * Error label reachable. 
	     */
	    ERROR_LABEL,
	    /**
	     * Not further specified or unknown.
	     */
	    UNKNOWN
	    // add missing failure types...
	}
	
	Spec m_Spec;
	
	/**
	 * The published attributes.  Update this and getFieldValue()
	 * if you add new attributes.
	 */
	private final static String[] s_AttribFields = {
		"Check"
	};

	
	public Check(Check.Spec spec) {
		m_Spec = spec;
	}
	
	public String getPositiveMessage() {
		switch (m_Spec) {
		case ARRAY_INDEX:
			return "array index is always in bounds";
		case PRE_CONDITION:
			return "procedure precondition always holds";
		case POST_CONDITION:
			return "procedure postcondition always holds";
		case INVARIANT:
			return "loop invariant is valid";
		case ASSERT:
			return "assertion always holds";
		case DIVISION_BY_ZERO:
			return "division by zero can never occur";
		case INTEGER_OVERFLOW:
			return "integer overflow can never occur";
		case MEMORY_DEREFERENCE:
			return "pointer dereference always succeeds";
		case MEMORY_LEAK:
			return "all allocated memory was freed";
		case MEMORY_FREE:
			return "free always succeeds";
		case ERROR_LABEL:
			return "ERROR label is not reachable";
		case UNKNOWN:
			return "unknown kind of specification holds";
		default:
			throw new AssertionError();
		}
	}
	
	public String getNegativeMessage() {
		switch (m_Spec) {
		case ARRAY_INDEX:
			return "array index can be out of bounds";
		case PRE_CONDITION:
			return "procedure precondition can be violated";
		case POST_CONDITION:
			return "procedure postcondition can be violated";
		case INVARIANT:
			return "loop invariant can be violated";
		case ASSERT:
			return "assertion can be violated";
		case DIVISION_BY_ZERO:
			return "possible division by zero";
		case INTEGER_OVERFLOW:
			return "integer overflow possible";
		case MEMORY_DEREFERENCE:
			return "pointer dereference may fail";
		case MEMORY_LEAK:
			return "not all allocated memory was freed";
		case MEMORY_FREE:
			return "free of unallocated memory possible";
		case ERROR_LABEL:
			return "ERROR label is reachable";
		case UNKNOWN:
			return "unknown kind of specification may be violated";
		default:
			throw new AssertionError();
		}
	}

	@Override
	protected String[] getFieldNames() {
		return s_AttribFields;
	}

	@Override
	protected Object getFieldValue(String field) {
		if (field == "Check")
			return m_Spec.toString();
		else
			throw new UnsupportedOperationException("Unknown field "+field);
	}

    /**
     * Adds this Check object to the annotations of an ASTNode.
     * 
     * @param node the ASTNode
     * @author Christian
     */
    public final void addToNodeAnnot(ASTNode node) {
        node.getPayload().getAnnotations().put(getIdentifier(), this);
    }
}
