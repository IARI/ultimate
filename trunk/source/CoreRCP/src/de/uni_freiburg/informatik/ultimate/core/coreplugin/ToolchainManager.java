/*
 * Copyright (C) 2014-2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 *
 * This file is part of the ULTIMATE Core.
 *
 * The ULTIMATE Core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE Core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Core. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Core, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE Core grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.core.coreplugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.core.runtime.preferences.InstanceScope;

import de.uni_freiburg.informatik.ultimate.core.coreplugin.ToolchainWalker.CompleteToolchainData;
import de.uni_freiburg.informatik.ultimate.core.coreplugin.exceptions.StoreObjectException;
import de.uni_freiburg.informatik.ultimate.core.coreplugin.preferences.CorePreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.core.coreplugin.services.GenericServiceProvider;
import de.uni_freiburg.informatik.ultimate.core.coreplugin.services.ProgressMonitorService;
import de.uni_freiburg.informatik.ultimate.core.lib.results.BenchmarkResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.GenericResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.ResultUtil;
import de.uni_freiburg.informatik.ultimate.core.lib.toolchain.PluginType;
import de.uni_freiburg.informatik.ultimate.core.lib.toolchain.RunDefinition;
import de.uni_freiburg.informatik.ultimate.core.lib.toolchain.SubchainType;
import de.uni_freiburg.informatik.ultimate.core.model.IController;
import de.uni_freiburg.informatik.ultimate.core.model.ISource;
import de.uni_freiburg.informatik.ultimate.core.model.ITool;
import de.uni_freiburg.informatik.ultimate.core.model.IToolchain;
import de.uni_freiburg.informatik.ultimate.core.model.IToolchainData;
import de.uni_freiburg.informatik.ultimate.core.model.IToolchainProgressMonitor;
import de.uni_freiburg.informatik.ultimate.core.model.models.IElement;
import de.uni_freiburg.informatik.ultimate.core.model.models.ModelType;
import de.uni_freiburg.informatik.ultimate.core.model.results.IResultWithSeverity.Severity;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILoggingService;
import de.uni_freiburg.informatik.ultimate.core.model.services.IResultService;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.core.preferences.RcpPreferenceProvider;
import de.uni_freiburg.informatik.ultimate.util.VMUtils;
import de.uni_freiburg.informatik.ultimate.util.statistics.Benchmark;

/**
 *
 * The {@link ToolchainManager} controls the livecycle of all toolchains and the associated plugins.
 *
 * @author dietsch
 *
 */
public class ToolchainManager {

	private final ILogger mLogger;
	private final PluginFactory mPluginFactory;
	private final IController<RunDefinition> mCurrentController;
	private final AtomicLong mCurrentId;
	private final ConcurrentHashMap<Long, Toolchain> mActiveToolchains;
	private final ILoggingService mLoggingService;

	public ToolchainManager(final ILoggingService loggingService, final PluginFactory factory,
			final IController<RunDefinition> controller) {
		mLoggingService = loggingService;
		mLogger = mLoggingService.getLogger(Activator.PLUGIN_ID);
		mPluginFactory = factory;
		mCurrentController = controller;
		mCurrentId = new AtomicLong();
		mActiveToolchains = new ConcurrentHashMap<>();
	}

	public void releaseToolchain(final IToolchain<RunDefinition> toolchain) {
		if (toolchain == null) {
			throw new IllegalArgumentException("toolchain");
		}
		if (!mActiveToolchains.remove(toolchain.getId(), toolchain)) {
			mLogger.warn("An concurrency error occured: Toolchain ID has changed during livecycle");
		}
		if (toolchain.getCurrentToolchainData() != null && toolchain.getCurrentToolchainData().getStorage() != null) {
			toolchain.getCurrentToolchainData().getStorage().clear();
			mLogger.debug("Toolchain " + toolchain.getId() + " released");
		}
	}

	public IToolchain<RunDefinition> requestToolchain() {
		final Toolchain tc = new Toolchain(mCurrentId.incrementAndGet(), createModelManager());
		mActiveToolchains.put(tc.getId(), tc);
		return tc;
	}

	public void close() {
		// we should drop everything

		if (mActiveToolchains.size() > 0) {
			mLogger.info("There are still " + mActiveToolchains.size() + " active toolchains alive");
			final List<Toolchain> openChains = new ArrayList<>(mActiveToolchains.values());
			for (final Toolchain tc : openChains) {
				if (tc != null && tc.getCurrentToolchainData() != null
						&& tc.getCurrentToolchainData().getStorage() != null) {
					tc.getCurrentToolchainData().getStorage().clear();
				}
			}
			mActiveToolchains.clear();
		}
	}

	private IModelManager createModelManager() {
		return new PersistenceAwareModelManager(
				new RcpPreferenceProvider(Activator.PLUGIN_ID).getString(CorePreferenceInitializer.LABEL_TMP_DIRECTORY),
				mLogger);
	}

	/*************************** ToolchainContainer Implementation ****************************/
	private final class Toolchain implements IToolchain<RunDefinition> {

		private final long mId;
		private final IModelManager mModelManager;
		private final Benchmark mBenchmark;

		private IToolchainData<RunDefinition> mToolchainData;
		private final Map<File, ISource> mParsers;
		private File[] mInputFiles;
		private ToolchainWalker mToolchainWalker;

		Toolchain(final long id, final IModelManager modelManager) {
			mId = id;
			mModelManager = modelManager;
			mBenchmark = new Benchmark();
			mParsers = new LinkedHashMap<>();
		}

		/*************************** IToolchain<RunDefinition> Implementation ****************************/

		@Override
		public void init(final IToolchainProgressMonitor monitor) {
			if (mToolchainData == null) {
				return;
			}
			mToolchainWalker = new ToolchainWalker(mBenchmark, mModelManager, mPluginFactory, mLogger);

			mToolchainData.getStorage().clear();

			// install logging services into toolchain storage
			mLoggingService.setCurrentControllerID(mCurrentController.getPluginID());
			mLoggingService.store(mToolchainData.getStorage());

			// install service provider service into toolchain storage
			mToolchainData.getStorage().putStorable(GenericServiceProvider.getServiceKey(),
					new GenericServiceProvider(mPluginFactory));

			// install new ProgressMonitorService
			final ProgressMonitorService monitorService =
					new ProgressMonitorService(monitor, mLogger, mToolchainWalker);
			mToolchainData.getStorage().putStorable(ProgressMonitorService.getServiceKey(), monitorService);

		}

		@Override
		public void setInputFiles(final File[] files) {
			mInputFiles = files;
		}

		@Override
		public IToolchainData<RunDefinition> makeToolSelection(final IToolchainProgressMonitor monitor) {
			final List<ITool> tools = mPluginFactory.getAllAvailableTools();

			if (tools.isEmpty()) {
				mLogger.warn(getLogPrefix() + ": There are no plugins present, returning null tools.");
				return null;
			}

			// present selection dialog
			final IToolchainData<RunDefinition> rtr = mCurrentController.selectTools(tools);
			return setToolSelection(monitor, rtr);
		}

		@Override
		public IToolchainData<RunDefinition> setToolSelection(final IToolchainProgressMonitor monitor,
				final IToolchainData<RunDefinition> data) {
			if (data == null) {
				/* dialog was aborted */
				mLogger.warn(getLogPrefix() + ": Dialog was aborted, returning null tools.");
				return null;
			}
			if (!checkToolchain(data.getRootElement().getToolchain().getPluginOrSubchain())) {
				mLogger.warn(getLogPrefix() + ": Invalid toolchain selection, returning null tools.");
				return null;
			}
			mToolchainData = data;
			init(monitor);
			mLogger.info(getLogPrefix() + ": Toolchain data selected.");
			return data;
		}

		@Override
		public boolean initializeParsers() {
			if (mInputFiles == null || mInputFiles.length == 0) {
				mLogger.fatal(getLogPrefix() + ": No input files specified");
				return false;
			}

			for (final File inputFile : mInputFiles) {
				final ISource parser = selectParser(inputFile);

				if (parser == null) {
					mLogger.warn(getLogPrefix() + ": No parsers available for " + inputFile.getAbsolutePath());
					return false;
				}

				// TODO: remove preludes from parser interface
				parser.setPreludeFile(null);
				mParsers.put(inputFile, parser);
			}
			mLogger.info(getLogPrefix() + ": Parser(s) successfully initiated...");
			return true;
		}

		@Override
		public void runParsers() throws Exception {
			for (final Entry<File, ISource> entry : mParsers.entrySet()) {
				final ISource parser = entry.getValue();
				final File input = entry.getKey();

				// note that runParser has to happen before parser.getOutputDefinition() !
				@SuppressWarnings("squid:S1941")
				final IElement element = runParser(input, parser);
				final ModelType t = parser.getOutputDefinition();
				if (t == null) {
					final String errorMsg = parser.getPluginName() + " returned invalid output definition for file "
							+ input.getAbsolutePath();
					mLogger.fatal(getLogPrefix() + ": " + errorMsg + ", aborting...");
					throw new IllegalArgumentException(errorMsg);
				}
				addAST(element, t);
			}
		}

		@Override
		public ReturnCode processToolchain(final IToolchainProgressMonitor monitor) throws Throwable {
			mLogger.info("####################### " + getLogPrefix() + " #######################");
			final RcpPreferenceProvider ups = new RcpPreferenceProvider(Activator.PLUGIN_ID);
			final boolean useBenchmark = ups.getBoolean(CorePreferenceInitializer.LABEL_BENCHMARK);
			final IUltimateServiceProvider currentToolchainServices = mToolchainData.getServices();
			Benchmark bench = null;
			if (useBenchmark) {
				bench = new Benchmark();
				bench.start("Toolchain (without parser)");
			}
			try {
				if (mModelManager.size() < 1) {
					mLogger.error(getLogPrefix()
							+ ": There is no model present. Did you run a ISource or IGenerator plugin in your toolchain?");
					throw new IllegalStateException("There is no model present.");
				}

				final Collection<ISource> parsers = mParsers.values();
				final CompleteToolchainData data = new CompleteToolchainData(mToolchainData,
						parsers.toArray(new ISource[parsers.size()]), mCurrentController);
				data.getController().prerun(mToolchainData);
				return mToolchainWalker.walk(data, currentToolchainServices.getProgressMonitorService(), monitor);

			} finally {
				final IResultService resultService = currentToolchainServices.getResultService();
				if (VMUtils.areAssertionsEnabled()) {
					resultService.reportResult(Activator.PLUGIN_ID, new GenericResult(Activator.PLUGIN_ID,
							"Assertions are enabled", "Assertions are enabled", Severity.INFO));
				}

				if (useBenchmark) {
					bench.stopAll();
					bench.printResult(mLogger);
					mBenchmark.printResult(mLogger);

					// report benchmark results
					resultService.reportResult(Activator.PLUGIN_ID,
							new BenchmarkResult<>(Activator.PLUGIN_ID, "Toolchain Benchmarks", mBenchmark));

				}

				mLogger.info("#######################  End " + getLogPrefix() + " #######################");
				// TODO: Move all result logging to the different controllers
				final boolean appendCompleteLongDescription =
						CorePreferenceInitializer.getPreferenceProvider(currentToolchainServices)
								.getBoolean(CorePreferenceInitializer.LABEL_LONG_RESULT);
				final ILogger controllerLogger = currentToolchainServices.getLoggingService().getControllerLogger();
				ResultUtil.logResults(controllerLogger, resultService, appendCompleteLongDescription);
				mCurrentController.displayToolchainResults(mToolchainData, resultService.getResults());
				mModelManager.removeAll();
				mToolchainWalker.endToolchain();
			}
		}

		@Override
		public void addAST(final IElement root, final ModelType outputDefinition) {
			if (mModelManager.addItem(root, outputDefinition)) {
				mLogger.debug(getLogPrefix() + ": Successfully added AST to model manager");
			} else {
				mLogger.error(getLogPrefix() + ": Could not add AST to model manager!");
			}
		}

		@Override
		public long getId() {
			return mId;
		}

		@Override
		public boolean hasInputFiles() {
			return mInputFiles != null;
		}

		/*************************** End IToolchain<RunDefinition> Implementation ****************************/

		/**
		 * Checks whether all plugins in the toolchain are present.
		 *
		 * @param chain
		 *            Toolchain description to check.
		 * @return <code>true</code> if and only if every plugin in the chain exists.
		 */
		private boolean checkToolchain(final List<Object> chain) {
			return chain.stream().allMatch(this::checkToolchainElement);
		}

		private boolean checkToolchainElement(final Object elem) {
			if (elem instanceof PluginType) {
				final PluginType plugin = (PluginType) elem;
				if (mPluginFactory.isPluginAvailable(plugin.getId())) {
					return true;
				}
				mLogger.error(getLogPrefix() + ": Did not find plugin with id \"" + plugin.getId()
						+ "\". The following plugins are currently available:");
				printAvailableTools();
				return false;
			} else if (elem instanceof SubchainType) {
				return checkToolchain(((SubchainType) elem).getPluginOrSubchain());
			} else {
				throw new IllegalArgumentException("Found unknown type in toolchain: " + elem.getClass());
			}
		}

		private void printAvailableTools() {
			if (!mLogger.isInfoEnabled()) {
				return;
			}
			for (final ITool t : mPluginFactory.getAllAvailableTools()) {
				mLogger.info(getLogPrefix() + ": " + t.getPluginID());
			}
		}

		private IElement runParser(final File file, final ISource parser) throws Exception {
			final boolean useBenchmark = new RcpPreferenceProvider(Activator.PLUGIN_ID)
					.getBoolean(CorePreferenceInitializer.LABEL_BENCHMARK);
			IElement root = null;

			PluginConnector.initializePlugin(mLogger, parser, mToolchainData.getServices(),
					mToolchainData.getStorage());

			// parse the files to Graph
			try {
				mLogger.info(getLogPrefix() + ": Parsing single file: " + file.getAbsolutePath());
				if (useBenchmark) {
					mBenchmark.start(parser.getPluginName());
				}
				root = parser.parseAST(file);
				if (useBenchmark) {
					mBenchmark.stop(parser.getPluginName());
				}

			} catch (final Exception e) {
				mLogger.fatal(getLogPrefix() + ": Exception during parsing: ", e);
				resetModelManager();
				throw e;
			} finally {
				parser.finish();
			}
			return root;
		}

		private void resetModelManager() {
			if (!mModelManager.isEmpty()) {
				mLogger.info(getLogPrefix() + ": Clearing model...");
				try {
					mModelManager.persistAll(false);
				} catch (final StoreObjectException e) {
					final Throwable cause = e.getCause();
					mLogger.error(getLogPrefix() + ": Failed to persist models: "
							+ (cause == null ? e.getMessage() : cause.getMessage()));
				}
			}
		}

		private ISource selectParser(final File file) {
			// how many parsers does mSourcePlugins provide?
			final List<ISource> usableParsers = new ArrayList<>();
			final List<String> parserIds = mPluginFactory.getPluginClassNames(ISource.class);

			if (mLogger.isDebugEnabled()) {
				mLogger.debug(getLogPrefix() + ": We have " + parserIds.size() + " parsers present.");
			}

			// how many of these parsers can be used on our input file?
			for (final String parserId : parserIds) {
				final ISource p = mPluginFactory.createTool(parserId);
				if (p == null) {
					if (mLogger.isDebugEnabled()) {
						mLogger.debug(getLogPrefix() + ": Parser with ID " + parserId
								+ " is registered but cannot be created");
					}
					continue;
				}

				if (!p.parseable(file)) {
					if (mLogger.isDebugEnabled()) {
						mLogger.debug(getLogPrefix() + ": Parser " + p.getPluginName() + " is not usable for "
								+ file.getAbsolutePath());
					}
					continue;
				}

				mLogger.info(
						getLogPrefix() + ": Parser " + p.getPluginName() + " is usable for " + file.getAbsolutePath());
				usableParsers.add(p);
			}

			final boolean showusableparser = InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID).getBoolean(
					CorePreferenceInitializer.LABEL_SHOWUSABLEPARSER,
					CorePreferenceInitializer.VALUE_SHOWUSABLEPARSER_DEFAULT);

			// if only parser can be used, choose it!
			if (usableParsers.size() == 1 && !showusableparser) {
				return usableParsers.get(0);
			} else if (usableParsers.isEmpty()) {
				return null;
			} else {
				// otherwise use parser choosing mechanism provided by
				// Controller
				return mCurrentController.selectParser(usableParsers);
			}
		}

		@Override
		public IToolchainData<RunDefinition> getCurrentToolchainData() {
			return mToolchainData;
		}

		private String getLogPrefix() {
			return "[Toolchain " + mId + "]";
		}
	}
	/*************************** End ToolchainContainer Implementation ****************************/
}
