/*
 * Copyright (C) 2016 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2016 University of Freiburg
 *
 * This file is part of the ULTIMATE CLI plug-in.
 *
 * The ULTIMATE CLI plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE CLI plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE CLI plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE CLI plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE CLI plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.server;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplication;

import de.uni_freiburg.informatik.ultimate.core.coreplugin.toolchain.BasicToolchainJob;
import de.uni_freiburg.informatik.ultimate.core.coreplugin.toolchain.DefaultToolchainJob;
import de.uni_freiburg.informatik.ultimate.core.lib.results.ResultSummarizer;
import de.uni_freiburg.informatik.ultimate.core.lib.toolchain.RunDefinition;
import de.uni_freiburg.informatik.ultimate.core.model.IController;
import de.uni_freiburg.informatik.ultimate.core.model.ICore;
import de.uni_freiburg.informatik.ultimate.core.model.ISource;
import de.uni_freiburg.informatik.ultimate.core.model.ITool;
import de.uni_freiburg.informatik.ultimate.core.model.IToolchainData;
import de.uni_freiburg.informatik.ultimate.core.model.preferences.IPreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.core.model.results.IResult;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IProgressMonitorService;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.server.exceptions.InvalidFileArgumentException;
import de.uni_freiburg.informatik.ultimate.server.util.RcpUtils;
import de.uni_freiburg.informatik.ultimate.util.Utils;

/**
 * The {@link ServerController} class provides a user interface for Clients that can connect via
 * TCP.
 *
 * @author Julian Jarecki
 */
public class ServerController implements IController<RunDefinition> {

	private ILogger mLogger;
	private IToolchainData<RunDefinition> mToolchain;

	@Override
	public int init(final ICore<RunDefinition> core) {
		if (core == null) {
			return -1;
		}

		mLogger = core.getCoreLoggingService().getControllerLogger();

		if (mLogger.isDebugEnabled()) {
			mLogger.debug("Initializing ServerController...");
			mLogger.debug("Data directory is " + Platform.getLocation());
			mLogger.debug("Working directory is " + Platform.getInstallLocation().getURL());
			mLogger.debug("user.dir is " + System.getProperty("user.dir"));
			mLogger.debug("ServerController version is " + RcpUtils.getVersion(Activator.PLUGIN_ID));
		}

		final String[] args = Platform.getCommandLineArgs();

		// first, parse to see if toolchain is specified.
		// the preparser has to accept all options, but may not require any
		final CommandLineParser onlyCliHelpParser = CommandLineParser.createCliOnlyParser(core);
		final CommandLineParser toolchainStageParser = CommandLineParser.createCompleteNoReqsParser(core);
		ParsedParameter toolchainStageParams;
		try {
			toolchainStageParams = toolchainStageParser.parse(args);

		} catch (final ParseException pex) {
			printParseException(args, pex);
			onlyCliHelpParser.printHelp();
			return -1;
		}

		if (toolchainStageParams.isVersionRequested()) {
			mLogger.info("Version is " + RcpUtils.getVersion(Activator.PLUGIN_ID));
			mLogger.info(
					"Maximal heap size is " + Utils.humanReadableByteCount(Runtime.getRuntime().maxMemory(), true));
			return IApplication.EXIT_OK;
		}

		if (!toolchainStageParams.hasToolchain()) {
			if (toolchainStageParams.isHelpRequested()) {
				printHelp(onlyCliHelpParser, toolchainStageParams);
				return IApplication.EXIT_OK;
			}
			mLogger.info("Missing required option: " + CommandLineOptions.OPTION_NAME_TOOLCHAIN);
			printHelp(onlyCliHelpParser, toolchainStageParams);
			printAvailableToolchains(core);
			return IApplication.EXIT_OK;
		}

		final Predicate<String> pluginNameFilter;
		try {
			pluginNameFilter = getPluginFilter(core, toolchainStageParams.getToolchainFile());
		} catch (final ParseException pex) {
			mLogger.error("Could not find the provided toolchain file: " + pex.getMessage());
			return -1;
		} catch (final InvalidFileArgumentException e) {
			mLogger.error(e.getMessage());
			printArgs(args);
			return -1;
		}

		// second, perform real parsing
		final CommandLineParser fullParser = CommandLineParser.createCompleteParser(core, pluginNameFilter);
		final ParsedParameter fullParams;

		try {
			fullParams = fullParser.parse(args);
			if (fullParams.isHelpRequested()) {
				printHelp(fullParser, fullParams);
				return IApplication.EXIT_OK;
			}

			if (!fullParams.hasInputFiles()) {
				printParseException(args,
						new ParseException("Missing required option: " + CommandLineOptions.OPTION_NAME_INPUTFILES));
				printHelp(fullParser, fullParams);
				return -1;
			}

			final IToolchainData<RunDefinition> currentToolchain = prepareToolchain(core, fullParams);
			assert currentToolchain == mToolchain;
			// from now on, use the shutdown hook that disables the toolchain if the user presses CTRL+C (hopefully)
			Runtime.getRuntime().addShutdownHook(new Thread(new SigIntTrap(currentToolchain, mLogger), "SigIntTrap"));
			startExecutingToolchain(core, fullParams, mLogger, currentToolchain);

		} catch (final ParseException pex) {
			printParseException(args, pex);
			fullParser.printHelp();
			return -1;
		} catch (final InvalidFileArgumentException e) {
			mLogger.error(e.getMessage());
			printArgs(args);
			return -1;
		} catch (@SuppressWarnings("squid:S2142") final InterruptedException e) {
			mLogger.fatal("Exception during execution of toolchain", e);
			return -1;
		}
		return IApplication.EXIT_OK;
	}

	/**
	 * Creates one or many {@link BasicToolchainJob}s, schedules them and waits for their termination. Upon normal
	 * termination of this method, the controller will terminate with success return code. During the execution of a
	 * toolchain, {@link ICore} may perform asynchronous callbacks to
	 * {@link #displayToolchainResults(IToolchainData, Map)} and/or
	 * {@link #displayException(IToolchainData, String, Throwable)} to signal results right before ending.
	 *
	 * @param core
	 *            The {@link ICore} instance managing all toolchains.
	 * @param cliParams
	 *            The settings picked up from the command line
	 * @param logger
	 *            The {@link ILogger} instance that should be used to communicate with the user.
	 * @param toolchain
	 *            The user-selected toolchain.
	 * @throws ParseException
	 *             If lazy parsing of command line options fails (i.e., when accessing the input files stored in
	 *             <code>cliParams</code>, this exception might be thrown.
	 * @throws InvalidFileArgumentException
	 *             If a file is not valid or does not exist, this exception might be thrown.
	 * @throws InterruptedException
	 *             If during toolchain execution the thread is interrupted, this exception might be thrown.
	 */
	protected void startExecutingToolchain(final ICore<RunDefinition> core, final ParsedParameter cliParams,
			final ILogger logger, final IToolchainData<RunDefinition> toolchain)
			throws ParseException, InvalidFileArgumentException, InterruptedException {
		final File[] inputFiles = cliParams.getInputFiles();
		executeToolchain(core, inputFiles, logger, toolchain);
	}

	protected void executeToolchain(final ICore<RunDefinition> core, final File[] inputFiles, final ILogger logger,
			final IToolchainData<RunDefinition> toolchain) throws InterruptedException {
		final BasicToolchainJob tcj = new DefaultToolchainJob("Processing Toolchain", core, this, logger, inputFiles);
		tcj.schedule();
		tcj.join();
	}

	private IToolchainData<RunDefinition> prepareToolchain(final ICore<RunDefinition> core,
			final ParsedParameter fullParams) throws ParseException, InvalidFileArgumentException {
		core.resetPreferences();
		if (fullParams.hasSettings()) {
			core.loadPreferences(fullParams.getSettingsFile());
		}
		mToolchain = fullParams.createToolchainData();
		fullParams.applyCliSettings(mToolchain.getServices());
		return mToolchain;
	}

	private static void printHelp(final CommandLineParser parser, final ParsedParameter params) {
		if (params.showExperimentals()) {
			parser.printHelpWithExperimentals();
		} else {
			parser.printHelp();
		}
	}

	private void printParseException(final String[] args, final ParseException pex) {
		mLogger.error(pex.getMessage());
		printArgs(args);
		mLogger.error("--");
	}

	private void printArgs(final String[] args) {
		mLogger.error("Arguments were \"" + String.join(" ", args) + "\"");
	}

	@Override
	public ISource selectParser(final Collection<ISource> parser) {
		throw new UnsupportedOperationException("Interactively selecting parsers is not supported in CLI mode");
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
	public IToolchainData<RunDefinition> selectTools(final List<ITool> tools) {
		return mToolchain;
	}

	@Override
	public List<String> selectModel(final List<String> modelNames) {
		throw new UnsupportedOperationException("Interactively selecting models is not supported in CLI mode");
	}

	@Override
	public void displayToolchainResults(final IToolchainData<RunDefinition> toolchain,
			final Map<String, List<IResult>> results) {
		final ResultSummarizer summarizer = new ResultSummarizer(results);
		switch (summarizer.getResultSummary()) {
		case CORRECT:
			mLogger.info("RESULT: Ultimate proved your program to be correct!");
			break;
		case INCORRECT:
			mLogger.info("RESULT: Ultimate proved your program to be incorrect!");
			break;
		default:
			mLogger.info("RESULT: Ultimate could not prove your program: " + summarizer.getResultDescription());
			break;
		}
	}

	@Override
	public void displayException(final IToolchainData<RunDefinition> toolchain, final String description,
			final Throwable ex) {
		mLogger.fatal("RESULT: An exception occured during the execution of Ultimate: " + description, ex);
	}

	@Override
	public IPreferenceInitializer getPreferences() {
		return null;
	}

	private Predicate<String> getPluginFilter(final ICore<RunDefinition> core, final File toolchainFileOrDir) {
		final ToolchainLocator locator = new ToolchainLocator(toolchainFileOrDir, core, mLogger);

		final Set<String> allowedIds = new HashSet<>();
		// all plugins in the toolchain are allowed
		allowedIds.addAll(locator.createFilterForAvailableTools());
		// the the core is allowed
		allowedIds.add(de.uni_freiburg.informatik.ultimate.core.coreplugin.Activator.PLUGIN_ID);
		// the current controller (aka, we) are allowed
		allowedIds.add(getPluginID());
		// parser are allowed
		Arrays.stream(core.getRegisteredUltimatePlugins()).filter(a -> a instanceof ISource).map(a -> a.getPluginID())
				.forEach(allowedIds::add);

		// convert to lower case and build matching predicate
		final Set<String> lowerCaseIds = allowedIds.stream().map(a -> a.toLowerCase()).collect(Collectors.toSet());
		return a -> lowerCaseIds.contains(a.toLowerCase());
	}

	private void printAvailableToolchains(final ICore<RunDefinition> core) {
		final File workingDir = RcpUtils.getWorkingDirectory();
		final ToolchainLocator locator = new ToolchainLocator(workingDir, core, mLogger);

		final Map<File, IToolchainData<RunDefinition>> availableToolchains = locator.locateToolchains();
		if (availableToolchains.isEmpty()) {
			mLogger.warn("There are no toolchains in Ultimates working directory " + workingDir);
			return;
		}
		final String indent = "    ";

		mLogger.info("The following toolchains are available:");
		for (final Entry<File, IToolchainData<RunDefinition>> entry : availableToolchains.entrySet()) {
			mLogger.info(entry.getKey());
			mLogger.info(indent + entry.getValue().getRootElement().getName());
		}
	}

	private static final class SigIntTrap implements Runnable {

		private static final int SHUTDOWN_GRACE_PERIOD_SECONDS = 5;
		private final IToolchainData<RunDefinition> mCurrentToolchain;
		private final ILogger mLogger;

		public SigIntTrap(final IToolchainData<RunDefinition> currentToolchain, final ILogger logger) {
			mCurrentToolchain = currentToolchain;
			mLogger = logger;
		}

		@Override
		public void run() {
			mLogger.warn("Received shutdown request...");
			final IUltimateServiceProvider services = mCurrentToolchain.getServices();
			if (services == null) {
				return;
			}
			final IProgressMonitorService progressMonitor = services.getProgressMonitorService();
			if (progressMonitor == null) {
				return;
			}

			final CountDownLatch cdl = progressMonitor.cancelToolchain();
			try {
				if (cdl.await(SHUTDOWN_GRACE_PERIOD_SECONDS, TimeUnit.SECONDS)) {
					mLogger.info("Completed graceful shutdown");
				} else {
					mLogger.fatal("Cannot interrupt operation gracefully because timeout expired. Forcing shutdown");
				}
			} catch (@SuppressWarnings("squid:S2142") final InterruptedException e) {
				mLogger.fatal("Received interrupt while waiting for graceful shutdown: " + e.getMessage());
				return;
			}
		}
	}
}
