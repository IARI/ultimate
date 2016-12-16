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

package de.uni_freiburg.informatik.ultimate.rcfg.serializer.preferences;

import de.uni_freiburg.informatik.ultimate.rcfg.serializer.Activator;
import de.uni_freiburg.informatik.ultimate.core.lib.preferences.UltimatePreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.core.model.preferences.BaseUltimatePreferenceItem.PreferenceType;
import de.uni_freiburg.informatik.ultimate.core.model.preferences.UltimatePreferenceItem;

public class PreferenceInitializer extends UltimatePreferenceInitializer {

	public static final String TEMPLATE_FILE_LABEL = "Template:";
	private static final String TEMPLATE_FILE_DEFAULT = "data/template.gexf";
	public static final String ICFG_OUTPUT_FILE_NAME_LABEL = "ICFG Output file name:";
	private static final String ICFG_OUTPUT_FILE_NAME_DEFAULT = "icfg.gexf";
	public static final String USE_SOURCE_FILE_NAME_LABEL = "Use the sources file name?";
	private static final boolean USE_SOURCE_FILE_NAME_DEFAULT = false;
	public static final String DUMP_PATH_LABEL = "Dump path:";
	private static final String DUMP_PATH_DEFAULT = System.getProperty("java.io.tmpdir");

	public PreferenceInitializer() {
		super(Activator.PLUGIN_ID, Activator.PLUGIN_NAME);
	}

	@Override
	protected UltimatePreferenceItem<?>[] initDefaultPreferences() {
		return new UltimatePreferenceItem<?>[] {
				new UltimatePreferenceItem<String>(TEMPLATE_FILE_LABEL, TEMPLATE_FILE_DEFAULT, PreferenceType.File),
				new UltimatePreferenceItem<String>(DUMP_PATH_LABEL, DUMP_PATH_DEFAULT, PreferenceType.Directory),
				new UltimatePreferenceItem<String>(ICFG_OUTPUT_FILE_NAME_LABEL, ICFG_OUTPUT_FILE_NAME_DEFAULT,
						PreferenceType.String), 
				new UltimatePreferenceItem<Boolean>(USE_SOURCE_FILE_NAME_LABEL, USE_SOURCE_FILE_NAME_DEFAULT,
						PreferenceType.Boolean), 
		};
	}
}
