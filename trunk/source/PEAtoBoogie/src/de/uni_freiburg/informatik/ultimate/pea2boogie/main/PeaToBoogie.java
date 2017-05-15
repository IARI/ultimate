/*
 * Copyright (C) 2013-2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2013-2015 Jochen Hoenicke (hoenicke@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 *
 * This file is part of the ULTIMATE PEAtoBoogie plug-in.
 *
 * The ULTIMATE PEAtoBoogie plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE PEAtoBoogie plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE PEAtoBoogie plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE PEAtoBoogie plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE PEAtoBoogie plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.pea2boogie.main;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.core.model.ISource;
import de.uni_freiburg.informatik.ultimate.core.model.models.IElement;
import de.uni_freiburg.informatik.ultimate.core.model.models.ModelType;
import de.uni_freiburg.informatik.ultimate.core.model.preferences.IPreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IToolchainStorage;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.lib.srparse.srParsePattern;
import de.uni_freiburg.informatik.ultimate.pea2boogie.Activator;
import de.uni_freiburg.informatik.ultimate.pea2boogie.req2pea.ReqToPEA;
import de.uni_freiburg.informatik.ultimate.pea2boogie.translator.Translator;

public class PeaToBoogie implements ISource {
	protected ILogger mLogger;
	List<String> mFileNames = new ArrayList<>();

	@Override
	public void init() {
		// not necessary
	}

	@Override
	public String getPluginName() {
		return "PEA to Boogie";
	}

	@Override
	public String getPluginID() {
		return Activator.s_PLUGIN_ID;
	}

	@Override
	public File[] parseable(final File[] files) {
		final List<File> rtrList = Arrays.stream(files).filter(this::parseable).collect(Collectors.toList());
		return rtrList.toArray(new File[rtrList.size()]);
	}

	public boolean parseable(final File file) {
		return file.getName().endsWith(".req");
	}

	@Override
	public IElement parseAST(final File[] files) throws Exception {
		if (files.length == 1) {
			return parseAST(files[0]);
		}
		throw new UnsupportedOperationException("Cannot parse more than one file");
	}

	private IElement parseAST(final File file) throws Exception {
		final Translator translator = new Translator(mLogger);
		final String inputPath = file.getAbsolutePath();
		mFileNames = new ArrayList<>();
		mFileNames.add(inputPath);
		mLogger.info("Parsing: '" + inputPath + "'");
		final srParsePattern[] patterns = new ReqToPEA(mLogger).genPatterns(inputPath);
		// TODO: Add options to this cruel program
		final BitSet vacuityChecks = new BitSet(patterns.length);
		vacuityChecks.set(0, patterns.length);

		final int combinationNum = Math.min(patterns.length, 2); // TODO preference
		translator.setVacuityChecks(vacuityChecks);
		translator.setCombinationNum(combinationNum);
		translator.setInputFilePath(inputPath);
		return translator.genBoogie(patterns);
	}

	@Override
	public String[] getFileTypes() {
		return new String[] { ".req" };
	}

	@Override
	public ModelType getOutputDefinition() {
		try {
			return new ModelType(getPluginID(), ModelType.Type.AST, mFileNames);
		} catch (final Exception ex) {
			mLogger.fatal("syntax error: " + ex.getMessage());
			return null;
		}
	}

	@Override
	public IPreferenceInitializer getPreferences() {
		return null;
	}

	@Override
	public void setToolchainStorage(final IToolchainStorage storage) {
		// not necessary
	}

	@Override
	public void setServices(final IUltimateServiceProvider services) {
		mLogger = services.getLoggingService().getLogger(Activator.s_PLUGIN_ID);
	}

	@Override
	public void finish() {
		// not necessary
	}
}
