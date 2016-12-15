/*
 * Copyright (C) 2015 Marius Greitschus (greitsch@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 * 
 * This file is part of the ULTIMATE ICFGSerializer plug-in.
 * 
 * The ULTIMATE ICFGSerializer plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * The ULTIMATE ICFGSerializer plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE ICFGSerializer plug-in. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE ICFGSerializer plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP), 
 * containing parts covered by the terms of the Eclipse Public License, the 
 * licensors of the ULTIMATE ICFGSerializer plug-in grant you additional permission 
 * to convey the resulting work.
 */

package de.uni_freiburg.informatik.ultimate.rcfg.serializer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import de.uni_freiburg.informatik.ultimate.core.model.models.IElement;
import de.uni_freiburg.informatik.ultimate.core.model.models.ModelType;
import de.uni_freiburg.informatik.ultimate.core.model.observers.IUnmanagedObserver;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.rcfg.serializer.preferences.PreferenceInitializer;

public class ICFGSerializerObserver implements IUnmanagedObserver {

	private final ILogger mLogger;
	private final IUltimateServiceProvider mServices;

	public ICFGSerializerObserver(final IUltimateServiceProvider services) {
		mServices = services;
		mLogger = services.getLoggingService().getLogger(Activator.PLUGIN_ID);
	}

	@Override
	public void init(ModelType modelType, int currentModelIndex, int numberOfModels) throws Throwable {
		// not required
	}

	@Override
	public void finish() throws Throwable {
		// not required
	}

	@Override
	public boolean performedChanges() {
		return false;
	}

	@Override
	public boolean process(IElement root) throws Throwable {
		if (root instanceof IIcfg) {

			final PrintWriter writer = openTempFile();
			if (writer != null) {
				final IIcfg<IcfgLocation> rootNode = (IIcfg<IcfgLocation>) root;
				final Document template = readTemplate();
				final ICFGOutput output = new ICFGOutput(writer, template, mLogger);
				output.printRCFG(rootNode);
				writer.close();
			}
			return false;
		}

		mLogger.debug("Root element type is: " + root.getClass().getSimpleName() + ", expected RootNode");

		return true;
	}

	private Document readTemplate() {
		final String filepath;
		final File file;
		final Document dom;
		final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		try {

			filepath = mServices.getPreferenceProvider(Activator.PLUGIN_ID)
					.getString(PreferenceInitializer.TEMPLATE_FILE_LABEL);
			file = new File(filepath);

			if (!(file.exists() && file.isFile() && file.canRead())) {
				mLogger.fatal("Cannot read file: " + file.getAbsolutePath());
				return null;
			}
			DocumentBuilder db = dbf.newDocumentBuilder();

			dom = db.parse(file);

			return dom;

		} catch (final IOException e) {
			mLogger.fatal("Cannot open file", e);
			return null;
		} catch (ParserConfigurationException e) {
			// e.printStackTrace();
			mLogger.fatal("Cannot create DocumentBuilder", e);
			return null;
		} catch (SAXException e) {
			// e.printStackTrace();
			mLogger.fatal("Cannot create DocumentBuilder", e);
			return null;
		}
	}

	private PrintWriter openTempFile() {

		String path;
		String filename;
		File file;

		path = mServices.getPreferenceProvider(Activator.PLUGIN_ID).getString(PreferenceInitializer.DUMP_PATH_LABEL);

		try {
			filename = mServices.getPreferenceProvider(Activator.PLUGIN_ID)
					.getString(PreferenceInitializer.ICFG_OUTPUT_FILE_NAME_LABEL);
			file = new File(path + File.separatorChar + filename);
			if ((!file.isFile() || !file.canWrite()) && file.exists()) {
				mLogger.warn("Cannot write to: " + file.getAbsolutePath());
				return null;
			}

			if (file.exists()) {
				mLogger.info("File already exists and will be overwritten: " + file.getAbsolutePath());
			}
			file.createNewFile();
			mLogger.info("Writing to file " + file.getAbsolutePath());
			return new PrintWriter(new FileWriter(file));

		} catch (final IOException e) {
			mLogger.fatal("Cannot open file", e);
			return null;
		}
	}

}
