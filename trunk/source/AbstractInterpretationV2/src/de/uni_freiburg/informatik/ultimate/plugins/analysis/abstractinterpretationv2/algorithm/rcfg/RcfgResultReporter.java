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

package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.rcfg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.uni_freiburg.informatik.ultimate.core.lib.results.AllSpecificationsHoldResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.UnprovableResult;
import de.uni_freiburg.informatik.ultimate.core.model.results.IResult;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.core.model.translation.IProgramExecution.ProgramState;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.AbstractMultiState;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractState;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.Activator;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.IResultReporter;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.tool.AbstractCounterexample;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.util.IcfgProgramExecution;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Triple;

/**
 *
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 *
 */
public class RcfgResultReporter<STATE extends IAbstractState<STATE, VARDECL>, ACTION extends IcfgEdge, VARDECL, LOC extends IcfgLocation>
		implements IResultReporter<STATE, ACTION, VARDECL, LOC> {

	protected final IUltimateServiceProvider mServices;

	public RcfgResultReporter(final IUltimateServiceProvider services) {
		mServices = services;
	}

	@Override
	public void reportPossibleError(
			final AbstractCounterexample<AbstractMultiState<STATE, VARDECL>, ACTION, ?, LOC> cex) {
		final Map<Integer, ProgramState<Term>> programStates = new HashMap<>();
		final List<IcfgEdge> trace = new ArrayList<>();

		programStates.put(-1, computeProgramState(cex.getInitialState()));

		int i = 0;
		for (final Triple<AbstractMultiState<STATE, VARDECL>, LOC, ACTION> elem : cex.getAbstractExecution()) {
			trace.add(elem.getThird().getLabel());
			programStates.put(i, computeProgramState(elem.getFirst()));
			++i;
		}
		final IcfgProgramExecution pex = new IcfgProgramExecution(trace, programStates);

		final IResult result = new UnprovableResult<>(Activator.PLUGIN_ID, getLast(cex),
				mServices.getBacktranslationService(), pex, "abstract domain could reach this error location");

		mServices.getResultService().reportResult(Activator.PLUGIN_ID, result);
	}

	private ProgramState<Term>
			computeProgramState(final AbstractMultiState<STATE, VARDECL> abstractMultiState) {
		// TODO: Compute program state
		return new ProgramState<>(Collections.emptyMap());
	}

	private LOC getLast(final AbstractCounterexample<AbstractMultiState<STATE, VARDECL>, ACTION, ?, LOC> cex) {
		final int size = cex.getAbstractExecution().size();
		return cex.getAbstractExecution().get(size - 1).getSecond();
	}

	@Override
	public void reportSafe(final ACTION first) {
		reportSafe(first, "No error locations were reached.");
	}

	@Override
	public void reportSafe(final ACTION first, final String msg) {
		mServices.getResultService().reportResult(Activator.PLUGIN_ID,
				new AllSpecificationsHoldResult(Activator.PLUGIN_NAME, msg));
	}

}
