/*
 * Copyright (C) 2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2015 Marius Greitschus (greitsch@informatik.uni-freiburg.de)
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

package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.nonrelational.interval;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.nonrelational.BooleanValue;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.nonrelational.NonrelationalState;

/**
 * Implementation of an abstract state of the {@link IntervalDomain}.
 *
 * @author Marius Greitschus (greitsch@informatik.uni-freiburg.de)
 *
 */
public class IntervalDomainState<VARDECL>
		extends NonrelationalState<IntervalDomainState<VARDECL>, IntervalDomainValue, VARDECL> {
	
	/**
	 * Default constructor of an {@link IntervalDomainState}.
	 *
	 * @param logger
	 *            The current logger object in the current context.
	 */
	public IntervalDomainState(final ILogger logger, final Class<VARDECL> variableDeclarationType) {
		super(logger, variableDeclarationType);
	}
	
	/**
	 * Constructor of an {@link IntervalDomainState} that is either &bot;, or &top;.
	 *
	 * @param logger
	 *            The current logger object in the current context.
	 * @param isBottom
	 *            If <code>true</code>, the created state corresponds to &bot;, &top; otherwise.
	 */
	public IntervalDomainState(final ILogger logger, final boolean isBottom,
			final Class<VARDECL> variableDeclarationType) {
		super(logger, isBottom, variableDeclarationType);
	}

	/**
	 * Creates a new instance of {@link IntervalDomainState} with given logger, variables map, values map and boolean
	 * values map.
	 *
	 * @param logger
	 *            The current logger object in the current context.
	 * @param variablesMap
	 *            The map with all variable identifiers and their types.
	 * @param valuesMap
	 *            The values of all variables.
	 * @param booleanValuesMap
	 *            The values of all boolean variables.
	 * @param variableDeclarationType
	 *            The type of variables stored by this state.
	 */
	public IntervalDomainState(final ILogger logger, final Set<VARDECL> variablesMap,
			final Map<VARDECL, IntervalDomainValue> valuesMap, final Map<VARDECL, BooleanValue> booleanValuesMap,
			final Class<VARDECL> variableDeclarationType) {
		super(logger, variablesMap, valuesMap, booleanValuesMap, variableDeclarationType);
	}

	@Override
	protected IntervalDomainState<VARDECL> createCopy() {
		return new IntervalDomainState<VARDECL>(getLogger(), getVariables(), new HashMap<>(getVar2ValueNonrelational()),
				new HashMap<>(getVar2ValueBoolean()), mVariablesType);
	}

	@Override
	protected IntervalDomainState<VARDECL> createState(final ILogger logger, final Set<VARDECL> newVarMap,
			final Map<VARDECL, IntervalDomainValue> newValMap, final Map<VARDECL, BooleanValue> newBooleanValMap) {
		return new IntervalDomainState<VARDECL>(logger, newVarMap, newValMap, newBooleanValMap, mVariablesType);
	}

	@Override
	protected IntervalDomainValue createBottomValue() {
		return new IntervalDomainValue(true);
	}

	@Override
	protected IntervalDomainValue createTopValue() {
		return new IntervalDomainValue(false);
	}

	@Override
	protected IntervalDomainValue[] getArray(final int size) {
		return new IntervalDomainValue[size];
	}
}
