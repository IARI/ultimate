/*
 * Copyright (C) 2017 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2017 University of Freiburg
 *
 * This file is part of the ULTIMATE ModelCheckerUtils Library.
 *
 * The ULTIMATE ModelCheckerUtils Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE ModelCheckerUtils Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE ModelCheckerUtils Library. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE ModelCheckerUtils Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE ModelCheckerUtils Library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt;

import de.uni_freiburg.informatik.ultimate.logic.Sort;


/**
 * Simplify handling of {@link Sort}s that are often used, e.g., because
 * there sorts are defined in standard SMT theories.
 * 
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 *
 */
public final class SmtSortUtils {

	public static String BOOL_SORT = "Bool";
	public static String INT_SORT = "Int";
	public static String REAL_SORT = "Real";
	public static String BITVECTOR_SORT = "BitVec";
	public static String FLOATINGPOINT_SORT = "FloatingPoint";

	private SmtSortUtils() {
		// Prevent instantiation of this utility class
	}

	public static boolean isBoolSort(final Sort sort) {
		return BOOL_SORT.equals(sort.getRealSort().getName());
	}
	
	public static boolean isIntSort(final Sort sort) {
		return INT_SORT.equals(sort.getRealSort().getName());
	}

	public static boolean isRealSort(final Sort sort) {
		return REAL_SORT.equals(sort.getRealSort().getName());
	}
	
	public static boolean isBitvecSort(final Sort sort) {
		return BITVECTOR_SORT.equals(sort.getRealSort().getName());
	}

	public static boolean isFloatingpointSort(final Sort sort) {
		return FLOATINGPOINT_SORT.equals(sort.getRealSort().getName());
	}
}

