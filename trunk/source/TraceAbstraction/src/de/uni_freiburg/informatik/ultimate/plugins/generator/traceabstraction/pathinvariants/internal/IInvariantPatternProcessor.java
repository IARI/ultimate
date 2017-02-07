/*
 * Copyright (C) 2015 Dirk Steinmetz
 * Copyright (C) 2015 University of Freiburg
 * 
 * This file is part of the ULTIMATE TraceAbstraction plug-in.
 * 
 * The ULTIMATE TraceAbstraction plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * The ULTIMATE TraceAbstraction plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE TraceAbstraction plug-in. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE TraceAbstraction plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP), 
 * containing parts covered by the terms of the Eclipse Public License, the 
 * licensors of the ULTIMATE TraceAbstraction plug-in grant you additional permission 
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.pathinvariants.internal;

import java.util.Collection;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.pathinvariants.internal.ControlFlowGraph.Location;

/**
 * A processor for invariant patterns. The processor generates invariant
 * patterns for each {@link ControlFlowGraph.Location} in an
 * {@link ControlFlowGraph}, and solves the system of all corresponding
 * {@link InvariantTransitionPredicate}s.
 * 
 * For each round, methods are invoked in the following order:
 * <ol>
 *   <li>{@link #startRound(int)}</li>
 *   <li>
 *     {@link #getInvariantPatternForLocation(Location, int)} for all locations
 *   </li>
 *   <li>{@link #checkForValidConfiguration(Collection, int)}</li>
 * </ol>
 * 
 * @param <IPT>
 *            Invariant Pattern Type: Type used for invariant patterns
 */
public interface IInvariantPatternProcessor<IPT> {
	
	/**
	 * Called when a new round is entered.
	 * 
	 * @param round the round that is entered
	 */
	public void startRound(final int round);

	/**
	 * Returns an invariant pattern for the given location.
	 * 
	 * @param location
	 *            the location to generate an invariant pattern for
	 * @param round
	 *            attempt number, initialized with 0 and increased on each
	 *            attempt; see {@link #getMaxRounds()}
	 * @return invariant pattern for location
	 */
	public IPT getInvariantPatternForLocation(final IcfgLocation location,
			final int round);
	
	
	/**
	 * Returns an invariant pattern for the given location, containing coefficients for each variable in the given set.
	 * 
	 * @param location
	 *            the location to generate an invariant pattern for
	 * @param round
	 *            attempt number, initialized with 0 and increased on each
	 *            attempt; see {@link #getMaxRounds()}
	 * @return invariant pattern for location
	 */
	public IPT getInvariantPatternForLocation(final IcfgLocation location,
			final int round, Set<IProgramVar> vars);
	
	/**
	 * Returns an empty invariant pattern that is equivalent to 'true'.
	 * @return
	 */
	public IPT getEmptyInvariantPattern();

	/**
	 * Attempts to find a valid configuration for all pattern variables,
	 * satisfying any of the given {@link InvariantTransitionPredicate}s.
	 * 
	 * @param predicates
	 *            the predicates to satisfy
	 * @param round
	 *            attempt number, initialized with 0 and increased on each
	 *            attempt; see {@link #getMaxRounds()}
	 * @return true if a valid configuration pattern has been found, false
	 *         otherwise.
	 */
	public LBool checkForValidConfiguration(
			final Collection<InvariantTransitionPredicate<IPT>> predicates,
			final int round);
	
	/**
	 * Applies the configuration found with
	 * {@link #checkForValidConfiguration(Collection, int)} to a given invariant
	 * pattern.
	 * 
	 * The behaviour of this method is undefined, when the last call to
	 * {@link #checkForValidConfiguration(Collection, int)} returned false or if
	 * {@link #checkForValidConfiguration(Collection, int)} has not yet been called
	 * at all.
	 * 
	 * @param pattern the pattern to apply the configuration to
	 * @return the predicate representing the invariant found
	 */
	public IPredicate applyConfiguration(IPT pattern);

	/**
	 * Returns the maximal number of attempts to re-generate the invariant
	 * pattern map.
	 * 
	 * The round parameter will get for each integer between 0 and
	 * <code>getMaxRounds() - 1</code>. The value might change to a smaller
	 * value.
	 * 
	 * @return maximal number of attempts to re-generate the invariant map
	 */
	public int getMaxRounds();
	
	/**
	 * Sets the entry pattern to 'true' and the exit pattern to 'false'.
	 */
	public void initializeEntryAndExitPattern();

	public IPT getEntryInvariantPattern();

	public IPT getExitInvariantPattern();
	
	public void extractValuesForPatternCoefficients();

	/**
	 *
	 * @param pattern
	 * @param p
	 * @return the pattern, where the formula represented by the given TransFormula has been added to each disjunct as an additional conjunct.
	 */
	public IPT addTransFormulaToEachConjunctInPattern(IPT pattern, UnmodifiableTransFormula tf);
	
	
	/**
	 *
	 * @param pattern
	 * @param p
	 * @return the pattern, where the formula represented by the given TransFormula has been added as an additional disjunct.
	 */
	public IPT addTransFormulaAsAdditionalDisjunctToPattern(IPT pattern, UnmodifiableTransFormula tf);
	
	public Set<IProgramVar> getVariablesForInvariantPattern(IcfgLocation l, int round);

}
