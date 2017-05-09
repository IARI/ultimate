/*
 * Copyright (C) 2017 Julian Loeffler (loefflju@informatik.uni-freiburg.de)
 * Copyright (C) 2017 University of Freiburg
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
package de.uni_freiburg.informatik.ultimate.plugins.spaceex.icfg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.Check;
import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.Check.Spec;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.BasicIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgInternalTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.TransFormulaBuilder;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula.Infeasibility;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.automata.hybridsystem.HybridAutomaton;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.automata.hybridsystem.Location;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.automata.hybridsystem.Transition;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.icfg.HybridTermBuilder.BuildScenario;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.parser.preferences.SpaceExPreferenceGroup;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.parser.preferences.SpaceExPreferenceManager;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.util.HybridTranslatorConstants;

/**
 * Class that handles conversion of Hybrid Models/Systems/Automata to an ICFG
 *
 * @author Julian Loeffler (loefflju@informatik.uni-freiburg.de)
 *
 */
public class HybridIcfgGenerator {
	
	private final ILogger mLogger;
	
	private final UnmodifiableTransFormula mTrivialTransformula;
	private final SpaceExPreferenceManager mSpaceExPreferenceManager;
	private final CfgSmtToolkit mSmtToolkit;
	private final HybridVariableManager mVariableManager;
	private final HybridIcfgGeneratorHelper mHelper;
	private final IcfgLocation mErrorLocation;
	private final IcfgLocation mRootLocation;
	// Map of CFG Components which will be connected to an ICFG
	private final Map<String, HybridCfgComponent> mCfgComponents;
	// The connection list holds ICFGLocations which have to be connected to the initial location of a group
	private final List<IcfgLocation> mConnectionList;
	private final Scenario mScenario;
	private int mCurrentGroupID;
	
	// Scenario that determines if Preferencegroups are used or not.
	private enum Scenario {
		PREF_GROUPS, NO_GROUPS
	}
	
	public HybridIcfgGenerator(final ILogger logger, final SpaceExPreferenceManager preferenceManager,
			final CfgSmtToolkit smtToolkit, final HybridVariableManager variableManager) {
		mLogger = logger;
		mSpaceExPreferenceManager = preferenceManager;
		mSmtToolkit = smtToolkit;
		mVariableManager = variableManager;
		mHelper = new HybridIcfgGeneratorHelper(variableManager, preferenceManager, logger);
		mCfgComponents = new HashMap<>();
		mConnectionList = new ArrayList<>();
		mScenario = preferenceManager.hasPreferenceGroups() ? Scenario.PREF_GROUPS : Scenario.NO_GROUPS;
		mTrivialTransformula = TransFormulaBuilder.getTrivialTransFormula(smtToolkit.getManagedScript());
		// create a root and error location
		mErrorLocation = new IcfgLocation("error", HybridTranslatorConstants.PROC_NAME);
		// DD: You need the check annotation s.t. result reporting knows what you are checking
		new Check(Spec.ASSERT).annotate(mErrorLocation);
		// you need a location at the error loc for legacy reasons (we will find a way ;) ) -- the location is also
		// responsible for the line number in the result
		new DummyLocation("").annotate(mErrorLocation);
		mRootLocation = new IcfgLocation("root", HybridTranslatorConstants.PROC_NAME);
	}
	
	/**
	 * Fucntion that converts a HybridAutomaton into an ICFG
	 *
	 * @param automaton
	 * @return BasicIcfg<IcfgLocation>
	 */
	public BasicIcfg<IcfgLocation> createIfcgFromComponents(final HybridAutomaton automaton) {
		if (mLogger.isDebugEnabled()) {
			mLogger.debug("AUTOMATON: " + automaton);
		}
		
		// If scenario is that we have preferencegroups, get the parallel compositions of the groups.
		// else just build the ICFG for one automaton without initial assignments.
		if (mScenario == Scenario.PREF_GROUPS) {
			final Map<Integer, HybridAutomaton> parallelCompositions =
					mSpaceExPreferenceManager.getGroupIdToParallelComposition();
			parallelCompositions.forEach((groupid, aut) -> {
				mLogger.info(aut);
				mCurrentGroupID = groupid;
				modelToIcfg(aut);
			});
		} else {
			modelToIcfg(automaton);
		}
		
		final BasicIcfg<IcfgLocation> icfg = new BasicIcfg<>("icfg", mSmtToolkit, IcfgLocation.class);
		
		// root location of the ICFG, to this root location each sub-icfg will be connected.
		icfg.addLocation(mRootLocation, true, false, true, false, false);
		
		// error location
		icfg.addLocation(mErrorLocation, false, true, false, true, false);
		
		// push the remaining locations into the icfg
		mCfgComponents.forEach((id, comp) -> {
			// start is proc_entry + end is proc_exit
			icfg.addOrdinaryLocation(comp.getStart());
			icfg.addOrdinaryLocation(comp.getEnd());
			for (final IcfgLocation loc : comp.getLocations()) {
				icfg.addOrdinaryLocation(loc);
			}
		});
		
		// debug stuff
		if (mLogger.isDebugEnabled()) {
			mLogger.debug("################# COMPONENTS ###################");
			mCfgComponents.forEach((key, value) -> mLogger.debug("ID:" + key + ", Component:" + value.toString()));
			mLogger.debug("#################### ICFG ######################");
			mLogger.debug(icfg.getProgramPoints().toString());
			mLogger.debug(icfg.getSymboltable().getLocals(HybridTranslatorConstants.PROC_NAME).toString());
		}
		return icfg;
	}
	
	public void modelToIcfg(final HybridAutomaton aut) {
		/*
		 * in order to convert the hybrid model to an ICFG, we have to convert the parallelComposition of the regarded
		 * system.
		 */
		if (aut == null) {
			throw new IllegalStateException("HybridAutomaton aut has not been assigned and is null");
		}
		// convert automaton to cfg components
		automatonToIcfg(aut);
	}
	
	/**
	 * Function that converts a given hybrid automaton into ICFG components.
	 *
	 * @param automaton
	 * @param groupid
	 */
	private void automatonToIcfg(final HybridAutomaton automaton) {
		final Location initialLocation = automaton.getInitialLocation();
		final Map<Integer, Location> locations = automaton.getLocations();
		final List<Transition> transitions = automaton.getTransitions();
		// we can merge all variables into one set.
		final Set<String> variables = automaton.getGlobalParameters();
		variables.addAll(automaton.getGlobalConstants());
		variables.addAll(automaton.getLocalConstants());
		variables.addAll(automaton.getLocalParameters());
		mVariableManager.addVarsToConstants(automaton.getGlobalConstants());
		mVariableManager.addVarsToConstants(automaton.getLocalConstants());
		// ICFG locations + edges for variables
		variablesToIcfg(variables);
		// for locations
		locationsToIcfg(locations);
		// for transitions
		transitionsToIcfg(transitions, initialLocation);
	}
	
	/**
	 * Function that Creates the Variable assignment ICFG component.
	 *
	 * @param variables
	 * @param groupid
	 */
	private void variablesToIcfg(final Set<String> variables) {
		// if the group id exists, get the group,
		// else just set it null. Groups start from ID 1
		SpaceExPreferenceGroup group;
		if (mSpaceExPreferenceManager.getPreferenceGroups().containsKey(mCurrentGroupID)) {
			group = mSpaceExPreferenceManager.getPreferenceGroups().get(mCurrentGroupID);
		} else {
			group = null;
		}
		
		// create a transformula consisting of the initial variable assingments of the group
		final TransFormulaBuilder tfb = new TransFormulaBuilder(Collections.emptyMap(), Collections.emptyMap(), true,
				Collections.emptySet(), true, Collections.emptyList(), true);
		// first add all variables.
		for (final String var : variables) {
			final HybridProgramVar progVar = mVariableManager.getVar2ProgramVar().get(var);
			final TermVariable inVar = mVariableManager.getVar2InVarTermVariable().get(var);
			final TermVariable outVar = mVariableManager.getVar2OutVarTermVariable().get(var);
			tfb.addInVar(progVar, inVar);
			tfb.addOutVar(progVar, outVar);
		}
		
		// then evaluate the infix string of the variable assigment specified in the config.
		UnmodifiableTransFormula transformula;
		final String infix = group == null ? "" : group.getVariableInfix();
		
		// process infix to transformula
		if (infix.isEmpty()) {
			transformula = mTrivialTransformula;
		} else {
			transformula = buildTransformula(infix, BuildScenario.INITIALLY);
		}
		
		// create variable component of the form start ----variable assignment----> end
		final List<IcfgLocation> locations = new ArrayList<>();
		final List<IcfgInternalTransition> transitions = new ArrayList<>();
		final String id = "varAssignment_" + (group == null ? "" : group.getId());
		final IcfgLocation start = new IcfgLocation(id + "_start", HybridTranslatorConstants.PROC_NAME);
		final IcfgLocation end = new IcfgLocation(id + "_end", HybridTranslatorConstants.PROC_NAME);
		transitions.add(mHelper.createIcfgTransition(start, end, transformula));
		
		// create a list for the start node which contains the target.
		// new cfgComponent, has to be connected to the root node.
		mCfgComponents.put(id, new HybridCfgComponent(id, start, end, locations, transitions, ""));
		
		/*
		 * Transition from Root to varAssignment
		 */
		// the target of the transition is the the start of the target CFG component
		final IcfgLocation target = mCfgComponents.get(id).getStart();
		mHelper.createIcfgTransition(mRootLocation, target, mTrivialTransformula);
		// add connection that has to be made from Variable assignment to initial location.
		mConnectionList.add(end);
	}
	
	/**
	 * Function that converts locations to ICFG components.
	 *
	 * @param autLocations
	 * @param groupid
	 */
	private void locationsToIcfg(final Map<Integer, Location> autLocations) {
		/*
		 * locations consist of Flow and the invariant. -> Startnode (1) -> invariant (2) -> preflow (3) ->
		 * postFlow(4)-> invariant(5) ->End(6)
		 *
		 */
		for (final Map.Entry<Integer, Location> entry : autLocations.entrySet()) {
			final Integer autid = entry.getKey();
			final Location loc = entry.getValue();
			final List<IcfgInternalTransition> transitions = new ArrayList<>();
			final List<IcfgLocation> locations = new ArrayList<>();
			/*
			 * Locations: Start, End, Flow, InvariantCheck
			 */
			final IcfgLocation start =
					new IcfgLocation(autid + "_" + mCurrentGroupID + "_start", HybridTranslatorConstants.PROC_NAME);
			final IcfgLocation end =
					new IcfgLocation(autid + "_" + mCurrentGroupID + "_end", HybridTranslatorConstants.PROC_NAME);
			final IcfgLocation preFlow =
					new IcfgLocation(autid + "_" + mCurrentGroupID + "_preFlow", HybridTranslatorConstants.PROC_NAME);
			final IcfgLocation postFlow =
					new IcfgLocation(autid + "_" + mCurrentGroupID + "_postFlow", HybridTranslatorConstants.PROC_NAME);
			locations.add(preFlow);
			locations.add(postFlow);
			/*
			 * Transitions from start to Flow if invariant true
			 */
			// invariant to term:
			final String invariant = mHelper.replaceConstantValues(loc.getInvariant(), mCurrentGroupID);
			final UnmodifiableTransFormula invariantTransformula =
					buildTransformula(invariant, BuildScenario.INVARIANT);
			transitions.add(mHelper.createIcfgTransition(start, preFlow, invariantTransformula));
			
			/*
			 * Transition preFLow to postFlow
			 */
			final String flowTerms = mHelper.buildFlowInfix(loc.getFlow());
			final UnmodifiableTransFormula tfFlow = buildTransformula(flowTerms, BuildScenario.FLOW);
			transitions.add(mHelper.createIcfgTransition(preFlow, postFlow, tfFlow));
			/*
			 * Transition postFlow to End
			 */
			transitions.add(mHelper.createIcfgTransition(postFlow, end, invariantTransformula));
			
			/*
			 * Forbidden check
			 */
			if (mSpaceExPreferenceManager.hasForbiddenGroup() && !loc.getForbiddenConstraint().isEmpty()
					|| loc.isForbidden()) {
				// if the current location is forbidden, it shall get a transition from start --> error.
				// the transformula depends on whether
				final UnmodifiableTransFormula forbiddenTransformula =
						buildTransformula(loc.getForbiddenConstraint(), BuildScenario.INVARIANT);
				mLogger.debug("FORBIDDEN " + loc.getForbiddenConstraint());
				transitions.add(mHelper.createIcfgTransition(preFlow, mErrorLocation, forbiddenTransformula));
				transitions.add(mHelper.createIcfgTransition(end, mErrorLocation, forbiddenTransformula));
			}
			// create new cfgComponent
			mCfgComponents.put(autid.toString(),
					new HybridCfgComponent(autid.toString(), start, end, locations, transitions, loc.getInvariant()));
		}
	}
	
	/**
	 * Function that creates the necessary transitions between ICFG components.
	 *
	 * @param transitions
	 * @param initialLocation
	 */
	private void transitionsToIcfg(final List<Transition> transitions, final Location initialLocation) {
		// a transition in a hybrid automaton is simply an edge from one location to another.
		// guard and update can be merged with &&
		transitions.forEach(trans -> {
			// the source of the transition is the the end of the source CFG component
			final IcfgLocation source = mCfgComponents.get(Integer.toString(trans.getSourceId())).getEnd();
			// the target of the transition is the the start of the target CFG component
			final IcfgLocation target = mCfgComponents.get(Integer.toString(trans.getTargetId())).getStart();
			// invariant to term:
			final UnmodifiableTransFormula transFormula =
					buildTransitionTransformula(mHelper.replaceConstantValues(trans.getUpdate(), mCurrentGroupID),
							mHelper.replaceConstantValues(trans.getGuard(), mCurrentGroupID));
			mHelper.createIcfgTransition(source, target, transFormula);
		});
		
		// Transitions from Group var assignment to Initial Location
		mConnectionList.forEach((loc) -> {
			// the source of the transition is the the end of the source CFG component
			final IcfgLocation source = loc;
			// the target of the transition is the the start of the target CFG component
			final IcfgLocation target = mCfgComponents.get(Integer.toString(initialLocation.getId())).getStart();
			final UnmodifiableTransFormula transformula =
					TransFormulaBuilder.getTrivialTransFormula(mSmtToolkit.getManagedScript());
			mHelper.createIcfgTransition(source, target, transformula);
		});
		mConnectionList.clear();
	}
	
	/**
	 * Fucntion to build a transformula for a transition between Locations of automata.
	 *
	 * @param update
	 * @param guard
	 * @return
	 */
	private UnmodifiableTransFormula buildTransitionTransformula(final String update, final String guard) {
		final HybridTermBuilder tb = new HybridTermBuilder(mVariableManager, mSmtToolkit.getManagedScript(), mLogger);
		UnmodifiableTransFormula transformula;
		Term term = null;
		if (update.isEmpty() && guard.isEmpty()) {
			term = mSmtToolkit.getManagedScript().getScript().term("true");
		} else if (!update.isEmpty() && !guard.isEmpty()) {
			final Term guardTerm = tb.infixToTerm(guard, BuildScenario.GUARD);
			final Term updateTerm = tb.infixToTerm(update, BuildScenario.UPDATE);
			term = mSmtToolkit.getManagedScript().getScript().term("and", updateTerm, guardTerm);
		} else if (update.isEmpty() && !guard.isEmpty()) {
			term = tb.infixToTerm(guard, BuildScenario.GUARD);
		} else if (!update.isEmpty() && guard.isEmpty()) {
			term = tb.infixToTerm(update, BuildScenario.UPDATE);
		}
		// TFB
		final TransFormulaBuilder tfb = new TransFormulaBuilder(Collections.emptyMap(), Collections.emptyMap(), true,
				Collections.emptySet(), true, Collections.emptyList(), true);
		tb.getInVars().forEach(tfb::addInVar);
		tb.getOutVars().forEach(tfb::addOutVar);
		tfb.setFormula(term);
		tfb.setInfeasibility(Infeasibility.NOT_DETERMINED);
		// finish construction of the transformula.
		transformula = tfb.finishConstruction(mSmtToolkit.getManagedScript());
		// DEBUG STUFF
		if (mLogger.isDebugEnabled()) {
			mLogger.debug("######## CREATING NEW TRANSITION TRANSFORMULA ######### ");
			mLogger.debug("GUARD: " + guard);
			mLogger.debug("UPDATE: " + update);
			
			mLogger.debug("INVARS:  " + tb.getInVars());
			mLogger.debug("OUTVARS: " + tb.getOutVars());
			mLogger.debug("AUXVAR:  " + tb.getAuxVar());
			mLogger.debug("TERM:    " + term.toStringDirect());
			mLogger.debug("############################################ ");
		}
		return transformula;
	}
	
	/**
	 * Function to Build a transformula according to a BuildScenario.
	 *
	 * @param infix
	 * @param scenario
	 * @param groupid
	 * @return
	 */
	private UnmodifiableTransFormula buildTransformula(final String infix, final BuildScenario scenario) {
		// Empty infix ---> true transformula.
		if (infix.isEmpty()) {
			return TransFormulaBuilder.getTrivialTransFormula(mSmtToolkit.getManagedScript());
		}
		final HybridTermBuilder tb = new HybridTermBuilder(mVariableManager, mSmtToolkit.getManagedScript(), mLogger);
		
		// Build a term according to the scenario.
		final Term term = tb.infixToTerm(
				scenario != BuildScenario.INITIALLY ? mHelper.replaceConstantValues(infix, mCurrentGroupID) : infix,
				scenario);
		final TransFormulaBuilder tfb = new TransFormulaBuilder(Collections.emptyMap(), Collections.emptyMap(), true,
				Collections.emptySet(), true, Collections.emptyList(), false);
		
		// Add in/Outvars
		tb.getInVars().forEach(tfb::addInVar);
		tb.getOutVars().forEach(tfb::addOutVar);
		tfb.addAuxVar(tb.getAuxVar());
		tfb.setFormula(term);
		tfb.setInfeasibility(Infeasibility.NOT_DETERMINED);
		
		// finish construction of the transformula.
		final UnmodifiableTransFormula transformula = tfb.finishConstruction(mSmtToolkit.getManagedScript());
		
		// DEBUG STUFF
		if (mLogger.isDebugEnabled()) {
			mLogger.debug("######## CREATING NEW TRANSFORMULA ######### ");
			mLogger.debug("INFIX: " + infix);
			mLogger.debug("SCENARIO: " + scenario.toString());
			mLogger.debug("INVARS:  " + tb.getInVars());
			mLogger.debug("OUTVARS: " + tb.getOutVars());
			mLogger.debug("AUXVAR:  " + tb.getAuxVar());
			mLogger.debug("TERM:    " + term.toStringDirect());
			mLogger.debug("############################################ ");
		}
		
		return transformula;
	}
	
	public BasicIcfg<IcfgLocation> getSimpleIcfg() {
		final BasicIcfg<IcfgLocation> icfg = new BasicIcfg<>("testicfg", mSmtToolkit, IcfgLocation.class);
		
		final IcfgLocation startLoc = new IcfgLocation("start", "MAIN");
		icfg.addLocation(startLoc, true, false, true, false, false);
		
		final IcfgLocation middleLoc = new IcfgLocation("middle", "MAIN");
		icfg.addLocation(middleLoc, false, false, false, false, false);
		
		final IcfgLocation endLoc = new IcfgLocation("error", "MAIN");
		icfg.addLocation(endLoc, false, true, false, true, false);
		
		// Every procedure must have a unique entry and a unique exit. It is not allowed to have more than one exit (or
		// entry).
		
		// QUESTION: Is procExit = true correct here?
		
		TransFormulaBuilder tfb = new TransFormulaBuilder(Collections.emptyMap(), Collections.emptyMap(), true,
				Collections.emptySet(), true, Collections.emptyList(), true);
		tfb.setFormula(mSmtToolkit.getManagedScript().getScript().term("true"));
		tfb.setInfeasibility(Infeasibility.NOT_DETERMINED);
		// QUESTION: Is not determined correct here?
		
		// QUESTION: Does BoogieASTNode influence TraceAbstraction? Currently, we pass the same BoogieASTNode every time
		// to the ICFG. Should we construct new BoogieASTNodes every time?
		
		// QUESTION: Payload currently contains only a dummy location. Every payload consists of the SAME dummy
		// location. Is this correct / feasible?
		
		// Transitions
		final IcfgInternalTransition startToMiddle = new IcfgInternalTransition(startLoc, middleLoc, null,
				tfb.finishConstruction(mSmtToolkit.getManagedScript()));
		
		tfb = new TransFormulaBuilder(Collections.emptyMap(), Collections.emptyMap(), true, Collections.emptySet(),
				true, Collections.emptyList(), true);
		tfb.setFormula(mSmtToolkit.getManagedScript().getScript().term("true"));
		tfb.setInfeasibility(Infeasibility.NOT_DETERMINED);
		
		// If (true, false): Assertion error in SdHoareTripleChecker
		// If (true, true): Cast error (to CodeBlock)
		// If (false, false) or (false, true): No Error
		
		final IcfgInternalTransition middleToEnd = new IcfgInternalTransition(middleLoc, endLoc, null,
				tfb.finishConstruction(mSmtToolkit.getManagedScript()));
		
		startLoc.addOutgoing(startToMiddle);
		middleLoc.addIncoming(startToMiddle);
		middleLoc.addOutgoing(middleToEnd);
		endLoc.addIncoming(middleToEnd);
		
		return icfg;
	}
	
}
