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

import java.math.BigDecimal;

import de.uni_freiburg.informatik.ultimate.boogie.symboltable.BoogieSymbolTable;
import de.uni_freiburg.informatik.ultimate.core.model.preferences.IPreferenceProvider;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractDomain;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractPostOperator;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractStateBinaryOperator;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.Boogie2SmtSymbolTable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.IBoogieVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.Activator;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.generic.LiteralCollection;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.preferences.AbsIntPrefInitializer;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.util.AbsIntUtil;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.BoogieIcfgContainer;

/**
 * This abstract domain stores intervals for all variable valuations. Intervals can be of the form [num; num], where num
 * is of type {@link BigDecimal}, or of type -&infin; or &infin;, respectively. An interval may also be "{}" which
 * corresponds to the abstract state of &bot;.
 *
 * @author Marius Greitschus (greitsch@informatik.uni-freiburg.de)
 *
 */
public class IntervalDomain implements IAbstractDomain<IntervalDomainState<IBoogieVar>, IcfgEdge, IBoogieVar> {

	private final ILogger mLogger;
	private final LiteralCollection mLiteralCollection;
	private final IUltimateServiceProvider mServices;
	private final BoogieIcfgContainer mRootAnnotation;
	private final BoogieSymbolTable mSymbolTable;

	private IAbstractStateBinaryOperator<IntervalDomainState<IBoogieVar>> mWideningOperator;
	private IAbstractPostOperator<IntervalDomainState<IBoogieVar>, IcfgEdge, IBoogieVar> mPostOperator;

	public IntervalDomain(final ILogger logger, final BoogieSymbolTable symbolTable,
			final LiteralCollection literalCollector, final IUltimateServiceProvider services, final IIcfg<?> icfg) {
		mLogger = logger;
		mLiteralCollection = literalCollector;
		mServices = services;
		mRootAnnotation = AbsIntUtil.getBoogieIcfgContainer(icfg);
		mSymbolTable = symbolTable;
	}

	@Override
	public IntervalDomainState<IBoogieVar> createFreshState() {
		return createTopState();
	}

	@Override
	public IntervalDomainState<IBoogieVar> createTopState() {
		return new IntervalDomainState<>(mLogger, false);
	}

	@Override
	public IntervalDomainState<IBoogieVar> createBottomState() {
		return new IntervalDomainState<>(mLogger, true);
	}

	@Override
	public IAbstractStateBinaryOperator<IntervalDomainState<IBoogieVar>> getWideningOperator() {
		if (mWideningOperator == null) {
			final IPreferenceProvider ups = mServices.getPreferenceProvider(Activator.PLUGIN_ID);
			final String wideningOperator = ups.getString(IntervalDomainPreferences.LABEL_INTERVAL_WIDENING_OPERATOR);

			if (wideningOperator.equals(IntervalDomainPreferences.VALUE_WIDENING_OPERATOR_SIMPLE)) {
				mWideningOperator = new IntervalSimpleWideningOperator<>();
			} else if (wideningOperator.equals(IntervalDomainPreferences.VALUE_WIDENING_OPERATOR_LITERALS)) {
				final IAbstractStateBinaryOperator<IntervalDomainState<IBoogieVar>> rtr =
						new IntervalLiteralWideningOperator<>(mLiteralCollection);
				if (mLogger.isDebugEnabled()) {
					mLogger.debug("Using the following literals during widening: " + mLiteralCollection);
				}
				mWideningOperator = rtr;
			} else {
				throw new UnsupportedOperationException(
						"The widening operator " + wideningOperator + " is not implemented.");
			}
		}

		return mWideningOperator;
	}

	@Override
	public IAbstractPostOperator<IntervalDomainState<IBoogieVar>, IcfgEdge, IBoogieVar> getPostOperator() {
		if (mPostOperator == null) {
			final Boogie2SmtSymbolTable bpl2SmtSymbolTable = mRootAnnotation.getBoogie2SMT().getBoogie2SmtSymbolTable();
			final IPreferenceProvider prefs = mServices.getPreferenceProvider(Activator.PLUGIN_ID);
			final int maxParallelStates = prefs.getInt(AbsIntPrefInitializer.LABEL_MAX_PARALLEL_STATES);
			mPostOperator = new IntervalPostOperator(mLogger, mSymbolTable, bpl2SmtSymbolTable, maxParallelStates,
					mRootAnnotation.getBoogie2SMT(), mRootAnnotation);
		}
		return mPostOperator;
	}
}
