package de.uni_freiburg.informatik.ultimate.ultimatetest.suites.treeautomizer;

import java.util.Collection;

import de.uni_freiburg.informatik.ultimate.test.UltimateTestCase;
import de.uni_freiburg.informatik.ultimate.ultimatetest.suites.AbstractEvalTestSuite;
import de.uni_freiburg.informatik.ultimate.ultimatetest.summaries.ColumnDefinition;

public class TreeAutomatonOperationsTest extends AbstractEvalTestSuite {


	private static final String[] ULTIMATE_EXAMPLES = {
		"examples/Automata/TreeAutomaton/regression/",
	};

	/**
	 * List of path to setting files.
	 * Ultimate will run on each program with each setting that is defined here.
	 * The path are defined relative to the folder "trunk/examples/settings/",
	 * because we assume that all settings files are in this folder.
	 *
	 */
	private static final String[] SETTINGS = {
		"EmptySettings.epf",
	};


	private static final String[] ATS_TOOLCHAINS = {
		"TreeAutomizer.xml",
	};


	@Override
	protected ColumnDefinition[] getColumnDefinitions() {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	protected long getTimeout() {
		return 10 * 1000;
	}

	@Override
	public Collection<UltimateTestCase> createTestCases() {
		for (final String setting : SETTINGS) {
			for (final String toolchain : ATS_TOOLCHAINS) {
				addTestCase(toolchain, setting, ULTIMATE_EXAMPLES, new String[] { ".ats" });
			}
		}
		return super.createTestCases();
	}
}
