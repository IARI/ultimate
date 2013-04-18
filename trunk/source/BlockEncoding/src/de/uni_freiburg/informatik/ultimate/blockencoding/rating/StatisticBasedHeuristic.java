/**
 * 
 */
package de.uni_freiburg.informatik.ultimate.blockencoding.rating;

import java.util.ArrayList;

import de.uni_freiburg.informatik.ultimate.blockencoding.rating.RatingFactory.RatingStrategy;
import de.uni_freiburg.informatik.ultimate.blockencoding.rating.util.EncodingStatistics;

/**
 * To determine a good boundary, which is later used to estimate a good edge
 * level, we use here statistic values of the minimized program. These
 * statistical values are determined during the minimization process.
 * 
 * @author Stefan Wissert
 * 
 */
public class StatisticBasedHeuristic extends ConfigurableHeuristic {

	/**
	 * TODO: Strategies for which we can use these statistics, should be entered
	 * in this list!
	 */
	private ArrayList<RatingStrategy> supportedStrategies;

	/**
	 * @param strategy
	 */
	public StatisticBasedHeuristic(RatingStrategy strategy) {
		super(strategy);
		supportedStrategies = new ArrayList<RatingStrategy>();
		supportedStrategies.add(RatingStrategy.DISJUNCTIVE_STMTCOUNT);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uni_freiburg.informatik.ultimate.blockencoding.rating.
	 * ConfigurableHeuristic#init(java.lang.String)
	 */
	@Override
	public void init(String givenPref) {
		switch (this.strategy) {
		case DISJUNCTIVE_STMTCOUNT:
			givenPref = computeDisStmtBoundary();
			break;
		default:
			throw new IllegalArgumentException(
					"Statistic Based Heuristic is not supported for this kind of rating");
		}
		super.init(givenPref);
	}

	/**
	 * @param strategy
	 * @return
	 */
	public boolean isRatingStrategySupported(RatingStrategy strategy) {
		return supportedStrategies.contains(strategy);
	}
	
	/**
	 * @return
	 */
	private String computeDisStmtBoundary() {
		StringBuilder sb = new StringBuilder();
		// TODO: validate that
		// we take half of the maximum disjunctions in the graph
		sb.append(EncodingStatistics.maxDisjunctionsInOneEdge / 2);
		sb.append("-");
		// TODO: determine good under/upper bounds here based on data!
		sb.append("5-30");
		return sb.toString();
	}

}
