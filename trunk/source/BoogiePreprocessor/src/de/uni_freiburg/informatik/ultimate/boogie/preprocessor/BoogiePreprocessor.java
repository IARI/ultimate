/*
 * Copyright (C) 2008-2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2015 Jochen Hoenicke (hoenicke@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 * 
 * This file is part of the ULTIMATE BoogiePreprocessor plug-in.
 * 
 * The ULTIMATE BoogiePreprocessor plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * The ULTIMATE BoogiePreprocessor plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE BoogiePreprocessor plug-in. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE BoogiePreprocessor plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP), 
 * containing parts covered by the terms of the Eclipse Public License, the 
 * licensors of the ULTIMATE BoogiePreprocessor plug-in grant you additional permission 
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.boogie.preprocessor;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import de.uni_freiburg.informatik.ultimate.access.IObserver;
import de.uni_freiburg.informatik.ultimate.boogie.preferences.PreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.boogie.symboltable.BoogieSymbolTableConstructor;
import de.uni_freiburg.informatik.ultimate.core.preferences.UltimatePreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.core.services.model.IToolchainStorage;
import de.uni_freiburg.informatik.ultimate.core.services.model.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.ep.interfaces.IAnalysis;
import de.uni_freiburg.informatik.ultimate.model.GraphType;

/**
 * This class initializes the boogie preprocessor.
 * 
 * @author hoenicke
 * @author dietsch@informatik.uni-freiburg.de (for backtranslation)
 * 
 */
public class BoogiePreprocessor implements IAnalysis {

	private IUltimateServiceProvider mServices;

	public String getPluginName() {
		return Activator.PLUGIN_NAME;
	}

	public String getPluginID() {
		return Activator.PLUGIN_ID;
	}

	public void init() {
	}

	/**
	 * I give you every model.
	 */
	public QueryKeyword getQueryKeyword() {
		return QueryKeyword.LAST;
	}

	/**
	 * I don't need a special tool
	 */
	public List<String> getDesiredToolID() {
		return null;
	}

	public GraphType getOutputDefinition() {
		/* use old graph type definition */
		return null;
	}

	public void setInputDefinition(GraphType graphType) {
		// not required.
	}

	// @Override
	public List<IObserver> getObservers() {
		BoogiePreprocessorBacktranslator backTranslator = new BoogiePreprocessorBacktranslator(mServices);
		mServices.getBacktranslationService().addTranslator(backTranslator);

		Logger logger = mServices.getLoggingService().getLogger(Activator.PLUGIN_ID);

		BoogieSymbolTableConstructor symb = new BoogieSymbolTableConstructor(logger);
		backTranslator.setSymbolTable(symb.getSymbolTable());

		ArrayList<IObserver> observers = new ArrayList<IObserver>();
		// observers.add(new DebugObserver(logger));
		observers.add(new TypeChecker(mServices));
		observers.add(new ConstExpander(backTranslator));
		observers.add(new StructExpander(backTranslator, logger));
		observers.add(new UnstructureCode(backTranslator));
		observers.add(new FunctionInliner());
		observers.add(symb);
		return observers;
	}

	@Override
	public boolean isGuiRequired() {
		return false;
	}

	@Override
	public UltimatePreferenceInitializer getPreferences() {
		return new PreferenceInitializer();
	}

	@Override
	public void setToolchainStorage(IToolchainStorage storage) {
		// storage is not used by this plugin
	}

	@Override
	public void setServices(IUltimateServiceProvider services) {
		mServices = services;

	}

	@Override
	public void finish() {
		// TODO Auto-generated method stub

	}
}
