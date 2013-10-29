package de.uni_freiburg.informatik.ultimate.gui.preferencepages;

import java.util.StringTokenizer;

import de.uni_freiburg.informatik.ultimate.core.api.UltimateServices;
import de.uni_freiburg.informatik.ultimate.core.coreplugin.preferences.constants.PreferenceConstants;
import de.uni_freiburg.informatik.ultimate.ep.interfaces.ITool;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.widgets.List;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

public class LoggingDetailsPreferencePage extends AbstractDetailsPreferencePage {

	private ScopedPreferenceStore mPreferenceStore;

	public LoggingDetailsPreferencePage() {
		mPreferenceStore = new ScopedPreferenceStore(InstanceScope.INSTANCE,
				PreferenceConstants.PLUGINID);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uni_freiburg.informatik.ultimate.core.coreplugin.preferences.
	 * AbstractDetailsPreferencePage #getCorrectPreferenceStore()
	 */
	@Override
	protected IPreferenceStore getCorrectPreferenceStore() {
		return mPreferenceStore;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uni_freiburg.informatik.ultimate.core.coreplugin.preferences.
	 * AbstractDetailsPreferencePage #getDefaults()
	 */
	@Override
	protected String[] getDefaults() {
		return convert(mPreferenceStore
				.getDefaultString(PreferenceConstants.PREFID_DETAILS));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uni_freiburg.informatik.ultimate.core.coreplugin.preferences.
	 * AbstractDetailsPreferencePage #getInvalidEntryMessage()
	 */
	@Override
	protected String getInvalidEntryMessage() {
		return PreferenceConstants.INVALID_ENTRY;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uni_freiburg.informatik.ultimate.core.coreplugin.preferences.
	 * AbstractDetailsPreferencePage #getPreferenceAsStringArray()
	 */
	@Override
	protected String[] getPreferenceAsStringArray() {
		return convert(mPreferenceStore
				.getString(PreferenceConstants.PREFID_DETAILS));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uni_freiburg.informatik.ultimate.core.coreplugin.preferences.
	 * AbstractDetailsPreferencePage #setThePreference(java.lang.String[])
	 */
	@Override
	protected void setThePreference(String[] items) {
		StringBuffer buffer = new StringBuffer();
		for (int i = 0; i < items.length; i++) {
			buffer.append(items[i]);
			buffer.append(PreferenceConstants.VALUE_DELIMITER_LOGGING_PREF);
		}
		mPreferenceStore.setValue(PreferenceConstants.PREFID_DETAILS, buffer
				.toString());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uni_freiburg.informatik.ultimate.core.coreplugin.preferences.
	 * AbstractDetailsPreferencePage #getInfoContent()
	 */
	@Override
	protected String getInfoContent(List detailList) {
		String response = PreferenceConstants.ALL_PLUGINS_PRESENT;
		StringBuffer invalidPluginIds = new StringBuffer();
		invalidPluginIds.append(PreferenceConstants.PLUGINS_NOT_PRESENT);
		boolean error = false;
		for (String line : detailList.getItems()) {
			String pluginId = line.substring(0, line.lastIndexOf("="));
			if (!isActivePluginId(pluginId)) {
				error = true;
				invalidPluginIds.append(pluginId + "\n");
			}
		}
		if (error) {
			return invalidPluginIds.toString();
		}
		return response;
	}

	private boolean isActivePluginId(String pluginId) {
		java.util.List<ITool> tools = UltimateServices.getInstance()
				.getActiveTools();
		boolean retVal = false;
		for (ITool iTool : tools) {
			if (iTool.getPluginID().equals(pluginId)) {
				retVal = true;
			}
		}
		return retVal;
	}

	@Override
	protected String[] getComboSupply() {
		java.util.List<ITool> plugins = UltimateServices.getInstance()
				.getActiveTools();
		String[] return_list = new String[plugins.size()];
		for (int i = 0; i < plugins.size(); i++) {
			return_list[i] = plugins.get(i).getPluginID() + "=<DEBUG LEVEL>";
		}
		return return_list;
	}

	private static String[] convert(String preferenceValue) {
		StringTokenizer tokenizer = new StringTokenizer(preferenceValue,
				PreferenceConstants.VALUE_DELIMITER_LOGGING_PREF);
		int tokenCount = tokenizer.countTokens();
		String[] elements = new String[tokenCount];
		for (int i = 0; i < tokenCount; i++) {
			elements[i] = tokenizer.nextToken();
		}

		return elements;
	}
}
