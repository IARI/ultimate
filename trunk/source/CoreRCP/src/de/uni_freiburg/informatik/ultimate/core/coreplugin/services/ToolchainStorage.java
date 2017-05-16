/*
 * Copyright (C) 2014-2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
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

package de.uni_freiburg.informatik.ultimate.core.coreplugin.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.uni_freiburg.informatik.ultimate.core.coreplugin.Activator;
import de.uni_freiburg.informatik.ultimate.core.model.IServiceFactory;
import de.uni_freiburg.informatik.ultimate.core.model.preferences.IPreferenceProvider;
import de.uni_freiburg.informatik.ultimate.core.model.services.IBacktranslationService;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILoggingService;
import de.uni_freiburg.informatik.ultimate.core.model.services.IProgressMonitorService;
import de.uni_freiburg.informatik.ultimate.core.model.services.IResultService;
import de.uni_freiburg.informatik.ultimate.core.model.services.IService;
import de.uni_freiburg.informatik.ultimate.core.model.services.IStorable;
import de.uni_freiburg.informatik.ultimate.core.model.services.IToolchainStorage;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.core.preferences.RcpPreferenceProvider;

/**
 * Simple implementation of {@link IToolchainStorage} and {@link IUltimateServiceProvider}
 *
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 *
 */
public class ToolchainStorage implements IToolchainStorage, IUltimateServiceProvider {

	private final Map<String, IStorable> mToolchainStorage;
	private final Map<String, PreferenceLayer> mPreferenceLayers;

	public ToolchainStorage() {
		mToolchainStorage = new LinkedHashMap<>();
		mPreferenceLayers = new HashMap<>();
	}

	private ToolchainStorage(final Map<String, IStorable> storage, final Map<String, PreferenceLayer> layers) {
		mToolchainStorage = Objects.requireNonNull(storage);
		mPreferenceLayers = Objects.requireNonNull(layers);
	}

	@Override
	public IStorable getStorable(final String key) {
		return mToolchainStorage.get(key);
	}

	@Override
	public IStorable putStorable(final String key, final IStorable value) {
		if (value == null || key == null) {
			throw new IllegalArgumentException("Cannot store nothing");
		}
		return mToolchainStorage.put(key, value);
	}

	@Override
	public IStorable removeStorable(final String key) {
		return mToolchainStorage.remove(key);
	}

	@Override
	public void clear() {
		final List<IStorable> current = new ArrayList<>(mToolchainStorage.values());

		if (current.isEmpty()) {
			return;
		}

		// destroy storables in reverse order s.t., e.g., scripts are destroyed
		// before the solver is destroyed.
		// this is done because we assume that instances created later may
		// depend on instances created earlier.
		Collections.reverse(current);

		final ILogger coreLogger = getLoggingService().getLogger(Activator.PLUGIN_ID);
		coreLogger.info("Clearing " + current.size() + " storables from " + getClass().getSimpleName());
		for (final IStorable storable : current) {
			if (storable == null) {
				coreLogger.warn("Found NULL storable, ignoring");
				continue;
			}
			try {
				storable.destroy();
			} catch (final Throwable t) {
				if (coreLogger == null) {
					continue;
				}
				coreLogger.fatal("There was an exception during clearing of toolchain storage while destroying "
						+ storable.getClass().toString() + ": " + t.getMessage());
			}
		}
		mToolchainStorage.clear();
	}

	@Override
	public void destroyStorable(final String key) {
		final IStorable storable = mToolchainStorage.remove(key);
		if (storable != null) {
			storable.destroy();
		}
	}

	@Override
	public String toString() {
		return mToolchainStorage.toString();
	}

	@Override
	public IBacktranslationService getBacktranslationService() {
		return BacktranslationService.getService(this);
	}

	@Override
	public ILoggingService getLoggingService() {
		return Log4JLoggingService.getService(this);
		// return Log4J2LoggingService.getService(this);
	}

	@Override
	public IResultService getResultService() {
		return ResultService.getService(this);
	}

	@Override
	public IProgressMonitorService getProgressMonitorService() {
		return ProgressMonitorService.getService(this);
	}

	@Override
	public <T extends IService, K extends IServiceFactory<T>> T getServiceInstance(final Class<K> serviceType) {
		return GenericServiceProvider.getServiceInstance(this, serviceType);
	}

	@Override
	public IPreferenceProvider getPreferenceProvider(final String pluginId) {
		final PreferenceLayer layer = mPreferenceLayers.get(pluginId);
		if (layer != null) {
			return layer;
		}
		return new RcpPreferenceProvider(pluginId);
	}

	@Override
	public IUltimateServiceProvider registerPreferenceLayer(final Class<?> creator, final String... pluginIds) {
		final Map<String, PreferenceLayer> newLayers = new HashMap<>(mPreferenceLayers);
		if (pluginIds == null || pluginIds.length == 0) {
			return new ToolchainStorage(mToolchainStorage, newLayers);
		}
		for (final String pluginId : pluginIds) {
			final PreferenceLayer existingLayer = newLayers.get(pluginId);
			final PreferenceLayer newLayer;
			if (existingLayer != null) {
				newLayer = new PreferenceLayer(existingLayer, creator);
			} else {
				newLayer = new PreferenceLayer(getPreferenceProvider(pluginId), creator);
			}
			newLayers.put(pluginId, newLayer);
		}
		return new ToolchainStorage(mToolchainStorage, newLayers);
	}
}
