/*
 * Copyright (C) 2016 Matthias Heizmann <heizmann@informatik.uni-freiburg.de>
 * Copyright (C) 2016 University of Freiburg
 * 
 * This file is part of the ULTIMATE Automata Library.
 * 
 * The ULTIMATE Automata Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * The ULTIMATE Automata Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Automata Library. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Automata Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP), 
 * containing parts covered by the terms of the Eclipse Public License, the 
 * licensors of the ULTIMATE Automata Library grant you additional permission 
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.automata.nwalibrary.operations.minimization.maxsat2;

/**
 * Clause condition.
 * 
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 */
class ClauseCondition {
	private final EClauseStatus mClauseStatus;
	private final int mUnsetAtoms;
	
	
	public ClauseCondition(final EClauseStatus clauseStatus, final int unsetAtoms) {
		mClauseStatus = clauseStatus;
		mUnsetAtoms = unsetAtoms;
	}
	
	public EClauseStatus getClauseStatus() {
		return mClauseStatus;
	}
	
	public int getUnsetAtoms() {
		return mUnsetAtoms;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((mClauseStatus == null) ? 0 : mClauseStatus.hashCode());
		result = prime * result + mUnsetAtoms;
		return result;
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
		final ClauseCondition other = (ClauseCondition) obj;
		if (mClauseStatus != other.mClauseStatus) {
			return false;
		}
		if (mUnsetAtoms != other.mUnsetAtoms) {
			return false;
		}
		return true;
	}
	
	@Override
	public String toString() {
		return "ClauseCondition [mClauseStatus=" + mClauseStatus + ", mUnsetAtoms=" + mUnsetAtoms + "]";
	}
}
