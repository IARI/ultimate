package de.uni_freiburg.informatik.ultimate.interactive;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.eclipse.equinox.app.IApplication;

import de.uni_freiburg.informatik.ultimate.core.lib.toolchain.RunDefinition;
import de.uni_freiburg.informatik.ultimate.core.model.IController;
import de.uni_freiburg.informatik.ultimate.core.model.ICore;
import de.uni_freiburg.informatik.ultimate.core.model.ISource;
import de.uni_freiburg.informatik.ultimate.core.model.ITool;
import de.uni_freiburg.informatik.ultimate.core.model.IToolchainData;
import de.uni_freiburg.informatik.ultimate.core.model.preferences.IPreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.core.model.results.IResult;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;

public class InteractiveController implements IController<RunDefinition> {
	
	private ILogger mLogger;
	private IToolchainData<RunDefinition> mToolchain;
	
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
		return null;
	}
	
	@Override
	public int init(final ICore<RunDefinition> core) {
		if (core == null) {
			return -1;
		}
		
		mLogger = core.getCoreLoggingService().getControllerLogger();
		
		if (mLogger.isDebugEnabled()) {
			mLogger.debug("Initializing InteractiveController...");
		}
		
		// TODO: Start server here.
		
		// TODO: Fill mToolchain.
		
		// TODO: Look in CLI how the model and toolchain etc. is selected.
		
		if (true)
			throw new UnsupportedOperationException("NOT IMPLEMENTED!");
		
		return IApplication.EXIT_OK;
	}
	
	@Override
	public ISource selectParser(final Collection<ISource> parser) {
		throw new UnsupportedOperationException(
				"Interactively selecting the parser is not possible in InteractiveController.");
	}
	
	@Override
	public IToolchainData<RunDefinition> selectTools(final List<ITool> tools) {
		throw new UnsupportedOperationException("Todo: Return correct toolchain");
	}
	
	@Override
	public List<String> selectModel(final List<String> modelNames) {
		throw new UnsupportedOperationException("Interactively choosing the model is unsupported in interactive mode.");
	}
	
	@Override
	public void displayToolchainResults(final IToolchainData<RunDefinition> toolchain,
			final Map<String, List<IResult>> results) {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void displayException(final IToolchainData<RunDefinition> toolchain, final String description,
			final Throwable ex) {
		mLogger.fatal("RESULT: An exception occurred during the execution of Ultimate: " + description, ex);
	}
}
