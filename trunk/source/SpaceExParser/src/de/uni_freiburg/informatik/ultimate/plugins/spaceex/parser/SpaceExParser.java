/*
 * Copyright (C) 2015 Marius Greitschus (greitsch@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 *
 * This file is part of the ULTIMATE SpaceExParser plug-in.
 *
 * The ULTIMATE SpaceExParser plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE SpaceExParser plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE SpaceExParser plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE SpaceExParser plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE SpaceExParser plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.spaceex.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import de.uni_freiburg.informatik.ultimate.core.lib.translation.DefaultTranslator;
import de.uni_freiburg.informatik.ultimate.core.model.ISource;
import de.uni_freiburg.informatik.ultimate.core.model.models.IElement;
import de.uni_freiburg.informatik.ultimate.core.model.models.ModelType;
import de.uni_freiburg.informatik.ultimate.core.model.preferences.IPreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IToolchainStorage;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.core.model.translation.ITranslator;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.DefaultIcfgSymbolTable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.ModifiableGlobalsTable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramNonOldVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SolverBuilder;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SolverBuilder.Settings;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SolverBuilder.SolverMode;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.automata.HybridModel;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.automata.hybridsystem.HybridAutomaton;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.automata.hybridsystem.HybridSystem;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.icfg.HybridIcfgGenerator;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.icfg.HybridIcfgSymbolTable;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.icfg.HybridVariableManager;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.parser.generated.ObjectFactory;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.parser.generated.Sspaceex;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.parser.preferences.SpaceExParserPreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.parser.preferences.SpaceExPreferenceManager;
import de.uni_freiburg.informatik.ultimate.smtsolver.external.ScriptorWithGetInterpolants.ExternalInterpolator;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.HashRelation;

/**
 * @author Marius Greitschus
 *
 */
public class SpaceExParser implements ISource {
	
	private final String[] mFileTypes;
	private final List<String> mFileNames;
	private IUltimateServiceProvider mServices;
	private ILogger mLogger;
	private IToolchainStorage mToolchainStorage;
	private SpaceExPreferenceManager mPreferenceManager;
	private HybridVariableManager mVariableManager;
	private ITranslator<IcfgEdge, IcfgEdge, Term, Term, String, String> mBacktranslator;
	
	/**
	 * Constructor of the SpaceEx Parser plugin.
	 */
	public SpaceExParser() {
		mFileTypes = new String[] { "xml", };
		mFileNames = new ArrayList<>();
	}
	
	@Override
	public void setToolchainStorage(final IToolchainStorage storage) {
		// TODO Auto-generated method stub
		mToolchainStorage = storage;
	}
	
	@Override
	public void setServices(final IUltimateServiceProvider services) {
		mServices = services;
		mLogger = mServices.getLoggingService().getLogger(Activator.PLUGIN_ID);
		mBacktranslator = new DefaultTranslator<>(IcfgEdge.class, IcfgEdge.class, Term.class, Term.class);
		mServices.getBacktranslationService().addTranslator(mBacktranslator);
	}
	
	@Override
	public void init() {
		// Auto-generated method stub
	}
	
	@Override
	public void finish() {
		// Auto-generated method stub
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
	public IPreferenceInitializer getPreferences() {
		return new SpaceExParserPreferenceInitializer();
	}
	
	@Override
	public boolean parseable(final File[] files) {
		for (final File f : files) {
			if (!parseable(f)) {
				return false;
			}
		}
		return true;
	}
	
	@Override
	public boolean parseable(final File file) {
		
		boolean knownExtension = false;
		
		for (final String s : getFileTypes()) {
			if (file.getName().endsWith(s)) {
				knownExtension = true;
				break;
			}
		}
		
		if (!knownExtension) {
			return false;
		}
		
		try {
			final FileReader fr = new FileReader(file);
			final BufferedReader br = new BufferedReader(fr);
			try {
				if (!br.readLine().contains("<?xml")) {
					mLogger.debug("The input file does not contain an opening xml tag.");
					return false;
				}
				
				if (!br.readLine().contains("<sspaceex")) {
					mLogger.debug("The input file does not contain a spaceex tag.");
					return false;
				}
			} finally {
				br.close();
				fr.close();
			}
		} catch (final IOException ioe) {
			return false;
		}
		
		return true;
	}
	
	@Override
	public IElement parseAST(final File[] files) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public IElement parseAST(final File file) throws Exception {
		// Parse the SpaceEx model
		mFileNames.add(file.getName());
		final FileInputStream fis = new FileInputStream(file);
		final JAXBContext jaxContext = JAXBContext.newInstance(ObjectFactory.class);
		final Unmarshaller unmarshaller = jaxContext.createUnmarshaller();
		final Sspaceex spaceEx = (Sspaceex) unmarshaller.unmarshal(fis);
		fis.close();
		// Initialize the preference manager + parse the config file right away.
		mPreferenceManager = new SpaceExPreferenceManager(mServices, mLogger, file);
		// Create the model
		mLogger.info("Starting creation of hybrid model...");
		long startTime = System.nanoTime();
		final HybridModel model = new HybridModel(spaceEx, mLogger, mPreferenceManager);
		long estimatedTime = System.nanoTime() - startTime;
		mLogger.info("Creation of hybrid model done in " + estimatedTime / (float) 1000000 + " milliseconds");
		// get the System specified in the config.
		final HybridSystem system = mPreferenceManager.getRegardedSystem(model);
		// calculate the parallel Compositions of the different preferencegroups.
		final Map<Integer, HybridAutomaton> parallelCompositions;
		// if the preferencemanager has preferencegroups, calculate the parallel compositions for those groups.
		if (mPreferenceManager.hasPreferenceGroups()) {
			mLogger.info("Starting Computation of parallel compositions...");
			startTime = System.nanoTime();
			parallelCompositions = model.calculateParallelCompositionsForGroups(system);
			mPreferenceManager.setGroupIdToParallelComposition(parallelCompositions);
			estimatedTime = System.nanoTime() - startTime;
			mLogger.info("Computation of parallel compositions done in " + estimatedTime / (float) 1000000
					+ " milliseconds");
		} else {
			parallelCompositions = new HashMap<>();
		}
		// set some automaton for the toolkit generation, anyone will do.
		HybridAutomaton automaton;
		if (!parallelCompositions.isEmpty()) {
			automaton = parallelCompositions.get(1);
		} else {
			if (!system.getAutomata().isEmpty()) {
				automaton = model.mergeAutomata(system, null);
			} else {
				throw new IllegalStateException("system does not have any automata");
			}
		}
		final CfgSmtToolkit smtToolkit = generateToolkit(automaton);
		final HybridIcfgGenerator gen =
				new HybridIcfgGenerator(mLogger, mPreferenceManager, smtToolkit, mVariableManager);
		// return gen.getSimpleIcfg();
		return gen.createIfcgFromComponents(automaton);
		/*
		 * final Marshaller marshaller = jaxContext.createMarshaller();
		 * marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE); final StringWriter streamWriter = new
		 * StringWriter(); final SpaceExWriter spaceexWriter = new SpaceExWriter(mLogger); Map<String, HybridAutomaton>
		 * mergedAutomata = system.getMergedAutomata(); Sspaceex root =
		 * spaceexWriter.HybridAutomatonToSpaceEx(mergedAutomata.get("ofOnn||controller||clock")); String targetfile =
		 * "" ; // some path/filename you want spaceexWriter.writeXmlToDisk(root,targetfile);
		 */
		// return new SpaceExModelBuilder(system, mLogger).getModel();
	}
	
	private CfgSmtToolkit generateToolkit(final HybridAutomaton automaton) {
		IPredicate axioms = null;
		final Set<String> procedures = new HashSet<>();
		procedures.add("MAIN");
		final Script script =
				SolverBuilder.buildAndInitializeSolver(mServices, mToolchainStorage,
						SolverMode.Internal_SMTInterpol, new Settings(true, false, "root", 2500,
								ExternalInterpolator.SMTINTERPOL, false, "/tmp", "dump_script"),
						false, false, "", "SMTINTERPOL");
		final ManagedScript managedScript = new ManagedScript(mServices, script);
		mVariableManager = new HybridVariableManager(managedScript);
		final HybridIcfgSymbolTable symbolTable =
				new HybridIcfgSymbolTable(managedScript, automaton, "MAIN", mVariableManager);
		final DefaultIcfgSymbolTable defaultTable = new DefaultIcfgSymbolTable(symbolTable, procedures);
		defaultTable.finishConstruction();
		final HashRelation<String, IProgramNonOldVar> proc2globals = new HashRelation<>();
		final ModifiableGlobalsTable modifiableGlobalsTable = new ModifiableGlobalsTable(proc2globals);
		axioms = new IPredicate() {
			
			@Override
			public Set<IProgramVar> getVars() {
				return Collections.emptySet();
			}
			
			@Override
			public String[] getProcedures() {
				return procedures.toArray(new String[procedures.size()]);
			}
			
			@Override
			public Term getFormula() {
				return script.term("true");
			}
			
			@Override
			public Term getClosedFormula() {
				return script.term("true");
			}
		};
		return new CfgSmtToolkit(modifiableGlobalsTable, managedScript, defaultTable, axioms, procedures);
	}
	
	@Override
	public String[] getFileTypes() {
		return mFileTypes;
	}
	
	@Override
	public ModelType getOutputDefinition() {
		try {
			return new ModelType(Activator.PLUGIN_ID, ModelType.Type.AST, mFileNames);
		} catch (final Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	@Override
	public void setPreludeFile(final File prelude) {
		// TODO Auto-generated method stub
		
	}
}
