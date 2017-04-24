package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.pathinvariants.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Term;
//import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVar;

/**
 * This strategy computes invariant patterns (templates) depending on the location
 * (node of the graph), the current round and dimensions strategy (see {@link AbstractTemplateIncreasingDimensionsStrategy}).
 * @author Betim Musa <musab@informatik.uni-freiburg.de>
 *
 */
public abstract class LocationDependentLinearInequalityInvariantPatternStrategy
implements ILinearInequalityInvariantPatternStrategy<Collection<Collection<AbstractLinearInvariantPattern>>> {

	private final int maxRounds;
	protected final Set<IProgramVar> mAllProgramVariables;
	protected int mPrefixCounter;
	protected Map<IcfgLocation, Set<Term>> mLoc2PatternCoefficents;
	protected boolean mAlwaysStrictAndNonStrictCopies;
	protected boolean mUseStrictInequalitiesAlternatingly;
	protected AbstractTemplateIncreasingDimensionsStrategy mDimensionsStrategy;

	/**
	 * Generates a simple linear inequality invariant pattern strategy.
	 * 
	 * @param maxRounds
	 *            maximal number of rounds to be announced by
	 *            {@link #getMaxRounds()}.
	 * @param allProgramVariables 
	 */
	public LocationDependentLinearInequalityInvariantPatternStrategy(final AbstractTemplateIncreasingDimensionsStrategy dimensionsStrat,
			final int maxRounds, Set<IProgramVar> allProgramVariables, boolean alwaysStrictAndNonStrictCopies,
			boolean useStrictInequalitiesAlternatingly) {
		mDimensionsStrategy = dimensionsStrat;
		this.maxRounds = maxRounds;
		mAllProgramVariables = allProgramVariables;
		mPrefixCounter = 0;
		mLoc2PatternCoefficents = new HashMap<>();
		mAlwaysStrictAndNonStrictCopies = alwaysStrictAndNonStrictCopies;
		mUseStrictInequalitiesAlternatingly = useStrictInequalitiesAlternatingly;
	}

	public Collection<Collection<AbstractLinearInvariantPattern>> getInvariantPatternForLocation(IcfgLocation location, int round, Script solver, String prefix) {
		final int[] dimensions = getDimensions(location, round);
		Set<Term> patternCoefficients = new HashSet<>();
		// Build invariant pattern
		final Collection<Collection<AbstractLinearInvariantPattern>> disjunction = new ArrayList<>(dimensions[0]);
		for (int i = 0; i < dimensions[0]; i++) {
			final Collection<AbstractLinearInvariantPattern> conjunction = new ArrayList<>(
					dimensions[1]);
			for (int j = 0; j < dimensions[1]; j++) {
				boolean[] invariantPatternCopies = new boolean[] { false };
				if (mUseStrictInequalitiesAlternatingly) {
					// if it is an odd conjunct, then construct a strict inequality
					if (j % 2 == 1) { 
						invariantPatternCopies = new boolean[] { true };
					} 
				}
				if (mAlwaysStrictAndNonStrictCopies) {
					invariantPatternCopies = new boolean[] { false, true };
				}
				for (final boolean strict : invariantPatternCopies) {
					final LinearPatternBase inequality = new LinearPatternBase (
							solver, getPatternVariablesForLocation(location, round), prefix + "_" + newPrefix(), strict);
					conjunction.add(inequality);
					// Add the coefficients of the inequality to our set of pattern coefficients
					patternCoefficients.addAll(inequality.getCoefficients());
				}
			}
			disjunction.add(conjunction);
		}
		mLoc2PatternCoefficents.put(location, patternCoefficients);
		return disjunction;
	}
	
	@Override
	public void setNumOfConjunctsForLocation(final IcfgLocation location, int numOfConjuncts) {
//		mLoc2MaxNumOfConjuncts.put(location, maxNumOfConjuncts);
		throw new UnsupportedOperationException("not yet implemented");
	}
	
	public void setNumOfDisjunctsForLocation(final IcfgLocation location, int numOfDisjuncts) {
		throw new UnsupportedOperationException("not yet implemented");
	}
	


	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMaxRounds() {
		return maxRounds;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int[] getDimensions(IcfgLocation location, int round) {
		return mDimensionsStrategy.getDimensions(location, round);
	}

	public void resetSettings() {
		mPrefixCounter = 0;
	}

	protected String newPrefix() {
		return Integer.toString(mPrefixCounter++);
	}

	@Override
	public Set<Term> getPatternCoefficientsForLocation(IcfgLocation location) {
		assert mLoc2PatternCoefficents.containsKey(location) : "No coefficients available for the location: " + location;
		return Collections.unmodifiableSet(mLoc2PatternCoefficents.get(location));
	}

}
