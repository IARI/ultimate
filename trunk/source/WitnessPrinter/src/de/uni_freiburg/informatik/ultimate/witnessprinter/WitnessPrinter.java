/*
 * Copyright (C) 2016 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2016 University of Freiburg
 *
 * This file is part of the ULTIMATE WitnessPrinter plug-in.
 *
 * The ULTIMATE WitnessPrinter plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE WitnessPrinter plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE WitnessPrinter plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE WitnessPrinter plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE WitnessPrinter plug-in grant you additional permission
 * to convey the resulting work.
 */

package de.uni_freiburg.informatik.ultimate.witnessprinter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import de.uni_freiburg.informatik.ultimate.core.lib.results.AllSpecificationsHoldResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.CounterExampleResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.ResultUtil;
import de.uni_freiburg.informatik.ultimate.core.lib.translation.BacktranslatedCFG;
import de.uni_freiburg.informatik.ultimate.core.model.IOutput;
import de.uni_freiburg.informatik.ultimate.core.model.models.ILocation;
import de.uni_freiburg.informatik.ultimate.core.model.models.ModelType;
import de.uni_freiburg.informatik.ultimate.core.model.observers.IObserver;
import de.uni_freiburg.informatik.ultimate.core.model.preferences.IPreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.core.model.results.IResult;
import de.uni_freiburg.informatik.ultimate.core.model.services.IBacktranslationService;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IToolchainStorage;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.core.model.translation.IBacktranslatedCFG;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgGraphProvider;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.BoogieIcfgContainer;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Triple;
import de.uni_freiburg.informatik.ultimate.witnessprinter.preferences.PreferenceInitializer;

/**
 *
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 *
 */
public class WitnessPrinter implements IOutput {

	private enum Mode {
		TRUE_WITNESS, FALSE_WITNESS, NO_WITNESS
	}

	private ILogger mLogger;
	private IUltimateServiceProvider mServices;
	private IToolchainStorage mStorage;
	private Mode mMode;
	private RCFGCatcher mRCFGCatcher;
	private boolean mMatchingModel;

	@Override
	public boolean isGuiRequired() {
		return false;
	}

	@Override
	public ModelQuery getModelQuery() {
		return ModelQuery.ALL;
	}

	@Override
	public List<String> getDesiredToolIds() {
		return Collections.emptyList();
	}

	@Override
	public void setInputDefinition(final ModelType graphType) {
		if ("de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder".equals(graphType.getCreator())) {
			mMatchingModel = true;
		} else {
			mMatchingModel = false;
		}
	}

	@Override
	public List<IObserver> getObservers() {
		if (mMode == Mode.TRUE_WITNESS && mMatchingModel) {
			// we should create this class somewere in cacsl s.t. we get the correct parameters -- perhaps translation
			// service
			mRCFGCatcher = new RCFGCatcher();
			return Collections.singletonList(mRCFGCatcher);
		}
		return Collections.emptyList();
	}

	@Override
	public void setToolchainStorage(final IToolchainStorage storage) {
		mStorage = storage;
	}

	@Override
	public void setServices(final IUltimateServiceProvider services) {
		mServices = services;
		mLogger = services.getLoggingService().getLogger(Activator.PLUGIN_ID);
	}

	@Override
	public void init() {
		mMode = Mode.NO_WITNESS;
		if (!mServices.getPreferenceProvider(Activator.PLUGIN_ID).getBoolean(PreferenceInitializer.LABEL_WITNESS_GEN)) {
			mLogger.info("Witness generation is disabled");
			return;
		}

		// determine if there are true or false witnesses
		final Map<String, List<IResult>> results = mServices.getResultService().getResults();
		if (!ResultUtil.filterResults(results, CounterExampleResult.class).isEmpty()) {
			mLogger.info("Generating witness for counterexample");
			mMode = Mode.FALSE_WITNESS;
		} else if (!ResultUtil.filterResults(results, AllSpecificationsHoldResult.class).isEmpty()) {
			mLogger.info("Generating witness for proof");
			mMode = Mode.TRUE_WITNESS;
		}
	}

	@Override
	public void finish() {
		final Collection<Supplier<Triple<IResult, String, String>>> supplier;
		switch (mMode) {
		case FALSE_WITNESS:
			supplier = generateFalseWitnessSupplier();
			break;
		case TRUE_WITNESS:
			supplier = generateTrueWitnessSupplier();
			break;
		case NO_WITNESS:
		default:
			// do nothing
			return;
		}

		final WitnessManager cexVerifier = new WitnessManager(mLogger, mServices, mStorage);
		try {
			cexVerifier.run(supplier);
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private Collection<Supplier<Triple<IResult, String, String>>> generateTrueWitnessSupplier() {
		final Collection<AllSpecificationsHoldResult> validResults =
				ResultUtil.filterResults(mServices.getResultService().getResults(), AllSpecificationsHoldResult.class);

		// we take only one AllSpecificationsHold result
		final AllSpecificationsHoldResult result = validResults.stream().findFirst().orElse(null);
		final IBacktranslationService backtrans = mServices.getBacktranslationService();
		final BoogieIcfgContainer root = mRCFGCatcher.getModel();
		final String filename = ILocation.getAnnotation(root).getFileName();
		final BacktranslatedCFG<?, IcfgEdge> origCfg =
				new BacktranslatedCFG<>(filename, IcfgGraphProvider.getVirtualRoot(root), IcfgEdge.class);
		final IBacktranslatedCFG<?, ?> translatedCFG = backtrans.translateCFG(origCfg);

		return Collections.singleton(() -> new Triple<>(result, filename, translatedCFG.getSVCOMPWitnessString()));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Collection<Supplier<Triple<IResult, String, String>>> generateFalseWitnessSupplier() {
		final Collection<Supplier<Triple<IResult, String, String>>> supplier = new ArrayList<>();
		final Collection<CounterExampleResult> cexResults =
				ResultUtil.filterResults(mServices.getResultService().getResults(), CounterExampleResult.class);
		final IBacktranslationService backtrans = mServices.getBacktranslationService();
		for (final CounterExampleResult cex : cexResults) {
			supplier.add(() -> new Triple<>(cex, cex.getLocation().getFileName(),
					backtrans.translateProgramExecution(cex.getProgramExecution()).getSVCOMPWitnessString()));
		}
		return supplier;
	}

	@Override
	public String getPluginName() {
		return Activator.PLUGIN_NAME;
	}

	@Override
	public String getPluginID() {
		return Activator.PLUGIN_ID;
	}

	@Override
	public IPreferenceInitializer getPreferences() {
		return new PreferenceInitializer();
	}
}
