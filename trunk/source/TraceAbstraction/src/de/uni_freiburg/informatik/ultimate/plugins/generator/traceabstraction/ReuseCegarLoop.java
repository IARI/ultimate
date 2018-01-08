/*
 * Copyright (C) 2017 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2017 University of Freiburg
 *
 * This file is part of the ULTIMATE TraceAbstraction plug-in.
 *
 * The ULTIMATE TraceAbstraction plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE TraceAbstraction plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE TraceAbstraction plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE TraceAbstraction plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE TraceAbstraction plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryException;
import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INwaOutgoingLetterAndTransitionProvider;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.VpAlphabet;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.Difference;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.IsIncluded;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.PowersetDeterminizer;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.oldapi.IOpWithDelayedDeadEndRemoval;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.IOutgoingTransitionlet;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.OutgoingCallTransition;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.OutgoingInternalTransition;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.OutgoingReturnTransition;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IToolchainStorage;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.logic.SMTLIBException;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.hoaretriple.IHoareTripleChecker;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.hoaretriple.IncrementalHoareTripleChecker;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.SimplificationTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.XnfConversionTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicateUnifier;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.interpolantautomata.transitionappender.AbstractInterpolantAutomaton;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.PredicateFactory;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TAPreferences;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TAPreferences.InterpolantAutomatonEnhancement;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.InterpolationTechnique;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singletracecheck.PredicateUnifier;
import de.uni_freiburg.informatik.ultimate.smtsolver.external.TermParseUtils;

/**
 * Subclass of {@link BasicCegarLoop} in which we initially subtract from the
 * abstraction a set of given Floyd-Hoare automata.
 * 
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 *
 */
public class ReuseCegarLoop<LETTER extends IIcfgTransition<?>> extends BasicCegarLoop<LETTER> {

	protected final boolean ENHANCE = true; //whether reused automata should be extended to "new" letters

	protected final List<AbstractInterpolantAutomaton<LETTER>> mFloydHoareAutomataFromOtherErrorLocations;
	protected final List<NestedWordAutomaton<String, String>> mRawFloydHoareAutomataFromFile;
	protected List<AbstractInterpolantAutomaton<LETTER>> mFloydHoareAutomataFromFile;
	
	public ReuseCegarLoop(final String name, final IIcfg<?> rootNode, final CfgSmtToolkit csToolkit,
			final PredicateFactory predicateFactory, final TAPreferences taPrefs,
			final Collection<? extends IcfgLocation> errorLocs, final InterpolationTechnique interpolation,
			final boolean computeHoareAnnotation, final IUltimateServiceProvider services,
			final IToolchainStorage storage,
			final List<AbstractInterpolantAutomaton<LETTER>> floydHoareAutomataFromOtherLocations,
			final List<NestedWordAutomaton<String, String>> rawFloydHoareAutomataFromFile) {
		super(name, rootNode, csToolkit, predicateFactory, taPrefs, errorLocs, interpolation, computeHoareAnnotation,
				services, storage);
		mFloydHoareAutomataFromOtherErrorLocations = floydHoareAutomataFromOtherLocations;
		mRawFloydHoareAutomataFromFile = rawFloydHoareAutomataFromFile;
		mFloydHoareAutomataFromFile = new ArrayList<>();
	}

	@Override
	protected void getInitialAbstraction() throws AutomataLibraryException {
		super.getInitialAbstraction();

		//final List<IPredicateUnifier> predicateUnifiersForAutomata = new ArrayList<>();
		//for (int i = 0; i < mRawFloydHoareAutomataFromFile.size(); i++) {
		//	predicateUnifiersForAutomata.add(new PredicateUnifier(mServices, mCsToolkit.getManagedScript(),
		//			mPredicateFactory, mCsToolkit.getSymbolTable(), SimplificationTechnique.SIMPLIFY_DDA,
		//			XnfConversionTechnique.BOTTOM_UP_WITH_LOCAL_SIMPLIFICATION));
		//}
		PredicateUnifier pu = new PredicateUnifier(mServices, mCsToolkit.getManagedScript(),
				mPredicateFactory, mCsToolkit.getSymbolTable(), SimplificationTechnique.SIMPLIFY_DDA,
				XnfConversionTechnique.BOTTOM_UP_WITH_LOCAL_SIMPLIFICATION);
		final List<NestedWordAutomaton<LETTER, IPredicate>> floydHoareAutomataFromFile = interpretAutomata(
				mRawFloydHoareAutomataFromFile, (INestedWordAutomaton<LETTER, IPredicate>) mAbstraction,
				mPredicateFactoryInterpolantAutomata, mServices, mPredicateFactory, mLogger, mCsToolkit, pu);
		mLogger.info("Reusing " + mFloydHoareAutomataFromOtherErrorLocations.size() + " Floyd-Hoare automata from previous error locations.");
		mLogger.info("Reusing " + floydHoareAutomataFromFile.size() + " Floyd-Hoare automata from ats files.");
		
		for (NestedWordAutomaton<LETTER, IPredicate> automaton : floydHoareAutomataFromFile) {
			//Add capability for on-demand extension to automata from file.
			IHoareTripleChecker htc = new IncrementalHoareTripleChecker(super.mCsToolkit); //TODO super is needed??
			mFloydHoareAutomataFromFile.add(constructInterpolantAutomatonForOnDemandEnhancement(
					automaton, pu, htc, InterpolantAutomatonEnhancement.PREDICATE_ABSTRACTION));
		}
	}
	
	private static final <LETTER extends IIcfgTransition<?>> List<NestedWordAutomaton<LETTER, IPredicate>> interpretAutomata(
			final List<NestedWordAutomaton<String, String>> rawFloydHoareAutomataFromFile,
			final INestedWordAutomaton<LETTER, IPredicate> abstraction,
			final PredicateFactoryForInterpolantAutomata predicateFactoryInterpolantAutomata,
			final IUltimateServiceProvider services, final PredicateFactory predicateFactory, final ILogger logger,
			final CfgSmtToolkit csToolkit, final PredicateUnifier predicateUnifier) {
	
		final Boolean debugOn = true;
		final List<NestedWordAutomaton<LETTER, IPredicate>> res = new ArrayList<NestedWordAutomaton<LETTER, IPredicate>>();

		for (final NestedWordAutomaton<String, String> rawAutomatonFromFile : rawFloydHoareAutomataFromFile) {
			
			// Create map from strings to all equivalent "new" letters (abstraction letters)
			final HashMap<String, Set<LETTER>> mapStringToLetter = new HashMap<String, Set<LETTER>>();
			final VpAlphabet<LETTER> abstractionAlphabet = abstraction.getVpAlphabet();
			addLettersToStringMap(mapStringToLetter, abstractionAlphabet.getCallAlphabet());
			addLettersToStringMap(mapStringToLetter, abstractionAlphabet.getInternalAlphabet());
			addLettersToStringMap(mapStringToLetter, abstractionAlphabet.getReturnAlphabet());
			//Print debug information for letters
			if (debugOn) {
				countReusedAndRemovedLetters(rawAutomatonFromFile.getVpAlphabet(),
						mapStringToLetter, logger);
			}
			// Create empty automaton with same alphabet
			final NestedWordAutomaton<LETTER, IPredicate> resAutomaton = new NestedWordAutomaton<>(
					new AutomataLibraryServices(services), abstractionAlphabet, predicateFactoryInterpolantAutomata);
			// Add states
			final Set<String> statesOfRawAutomaton = rawAutomatonFromFile.getStates();
			final HashMap<String, IPredicate> mapStringToState = new HashMap<>();
			final HashMap<IPredicate, String> mapStateToString = new HashMap<>();
			int reusedStates = 0;
			int removedStates = 0;
			for (final String stringState : statesOfRawAutomaton) {
				AtomicBoolean parsingResult = new AtomicBoolean(false);
				final IPredicate predicateState = getPredicateFromString(predicateFactory, stringState, csToolkit, 
						services, parsingResult, logger, predicateUnifier);
				if (parsingResult.get()) {
					reusedStates++;
				} else {
					removedStates++;
				}
				mapStringToState.put(stringState, predicateState);
				mapStateToString.put(predicateState, stringState);
				final boolean isInitial = rawAutomatonFromFile.isInitial(stringState);
				final boolean isFinal = rawAutomatonFromFile.isFinal(stringState);
				resAutomaton.addState(isInitial, isFinal, predicateState);
			}
			int totalStates = removedStates + reusedStates;
			assert(totalStates==resAutomaton.size());
			logger.info(
					"Reusing " + reusedStates + "/" + totalStates + " states when constructing automaton from file.");
			// Add transitions
			addTransitionsFromRawAutomaton(resAutomaton, rawAutomatonFromFile, mapStringToLetter, mapStringToState, 
					mapStateToString, debugOn, logger);
			// Add new automaton to list
			res.add(resAutomaton);
		}

		return res;
	}

	private static final IPredicate getPredicateFromString(final PredicateFactory predicateFactory, final String str,
			final CfgSmtToolkit csToolkit, final IUltimateServiceProvider services, AtomicBoolean parsingSuccesful,
			ILogger logger, final PredicateUnifier pu) {
		final PredicateParsingWrapperScript ppws = new PredicateParsingWrapperScript(csToolkit);
		IPredicate res = null;
		try {
			res = parsePredicate(ppws, pu, str, logger);
			parsingSuccesful.set(true);
		} catch (final UnsupportedOperationException ex) {
			res = predicateFactory.newDebugPredicate(str);
			parsingSuccesful.set(false);
		}
		return res;
	}

	private static final <LETTER> void addLettersToStringMap(HashMap<String, Set<LETTER>> map,
			final Set<LETTER> letters) {
		for (LETTER letter : letters) {
			if (!map.containsKey(letter.toString())) {
				Set<LETTER> equivalentLetters = new HashSet<LETTER>();
				equivalentLetters.add(letter);
				map.put(letter.toString(), equivalentLetters);
			} else {
				Set<LETTER> equivalentLetters = map.get(letter.toString());
				equivalentLetters.add(letter);
				map.put(letter.toString(), equivalentLetters); // needed? Will through exception?
			}
		}
	}

	/*
	 * Counts the number of letters of the original alphabet (of type String) that were matched to objects of type 
	 * LETTER in the new alphabet (reused letters), and those that were not matched to any object (removed letters).
	 * These two numbers are printed to the provided log.
	 * This function should only be used for debugging purposes.
	 */
	private static final <LETTER> void countReusedAndRemovedLetters(final VpAlphabet<String> orgAlphabet,
			final HashMap<String, Set<LETTER>> map, final ILogger logger) {
		int removedLetters = 0;
		int reusedLetters = 0;
		Set<String> letters = new HashSet<String>();
		letters.addAll(orgAlphabet.getInternalAlphabet());
		letters.addAll(orgAlphabet.getReturnAlphabet());
		letters.addAll(orgAlphabet.getCallAlphabet());
		for (String strLetter : letters) {
			if (!map.containsKey(strLetter)) {
				removedLetters++;
			} else {
				reusedLetters++;
			}
		}
		int totalLetters = removedLetters + reusedLetters;
		logger.info(
				"Reusing " + reusedLetters + "/" + totalLetters + " letters when constructing automaton from file.");
	}

	private static final <LETTER> void addTransitionsFromRawAutomaton(NestedWordAutomaton<LETTER, IPredicate> resAutomaton,
			final NestedWordAutomaton<String, String> rawAutomatonFromFile, 
			final HashMap<String, Set<LETTER>> mapStringToLetter,
			final HashMap<String, IPredicate> mapStringToState, final HashMap<IPredicate, String> mapStateToString,
			final Boolean debugOn, final ILogger logger) {
		int[] reusedAndRemoved = {0,0}; //Index 0 is for Reused, index 1 is for removed
		for (final IPredicate predicateState : resAutomaton.getStates()) {
			String stringState = mapStateToString.get(predicateState);
			addTransitionsFromState(rawAutomatonFromFile.callSuccessors(stringState), mapStringToLetter, mapStringToState, resAutomaton, predicateState, reusedAndRemoved);
			addTransitionsFromState(rawAutomatonFromFile.internalSuccessors(stringState), mapStringToLetter, mapStringToState, resAutomaton, predicateState, reusedAndRemoved);
			addTransitionsFromState(rawAutomatonFromFile.returnSuccessors(stringState), mapStringToLetter, mapStringToState, resAutomaton, predicateState, reusedAndRemoved);
		}
		int totalTransitions = reusedAndRemoved[0]+reusedAndRemoved[1];
		if (debugOn) {
			logger.info("Reusing " + reusedAndRemoved[0] + "/" + totalTransitions
					+ " transitions when constructing automaton from file.");
		}
	}
	
	private static final <LETTER, E extends IOutgoingTransitionlet<String, String>> void addTransitionsFromState(
			final Iterable<E> transitionsIterator, final HashMap<String, Set<LETTER>> mapStringToLetter,
			final HashMap<String, IPredicate> mapStringToFreshState, NestedWordAutomaton<LETTER, IPredicate> resAutomaton, 
			final IPredicate predicateState, int[] reusedAndRemovedTransitions) {
		for (E transition : transitionsIterator) {
			String transitionLetter = transition.getLetter();
			String transitionSuccString = transition.getSucc();
			String transitionHeirPredString = "";
			if (transition instanceof OutgoingReturnTransition<?,?>) {
				transitionHeirPredString = ((OutgoingReturnTransition<String,String>)transition).getHierPred();
			}
			if (mapStringToLetter.containsKey(transitionLetter)) {
				IPredicate succState = mapStringToFreshState.get(transitionSuccString);
				IPredicate heirPredState = null;
				if (transition instanceof OutgoingReturnTransition<?,?>) {
					heirPredState = mapStringToFreshState.get(transitionHeirPredString);
				}
				for (LETTER letter : mapStringToLetter.get(transitionLetter)) {
					if (transition instanceof OutgoingReturnTransition<?,?>) {
						resAutomaton.addReturnTransition(predicateState, heirPredState, letter, succState);
					} else if (transition instanceof OutgoingCallTransition<?,?>) {
						resAutomaton.addCallTransition(predicateState, letter, succState);
					} else if (transition instanceof OutgoingInternalTransition<?,?>) {
						resAutomaton.addInternalTransition(predicateState, letter, succState);
					}
				}
				reusedAndRemovedTransitions[0]++;
			} else {
				reusedAndRemovedTransitions[1]++;
			}
		}
	}

	private static IPredicate parsePredicate(final PredicateParsingWrapperScript ppws, final PredicateUnifier pu, 
			final String rawString, final ILogger logger) {
		final String termString = removeSerialNumber(rawString,logger);
		final Term term;
		try {
			term = TermParseUtils.parseTerm(ppws, termString);
		} catch (final SMTLIBException ex) {
			if (ex.getMessage().startsWith("Undeclared function symbol (")) {
				throw new UnsupportedOperationException("Automaton probably uses unknown variables. We should think how we can continue in this case.");
			}
			throw ex;
		}
		return pu.getOrConstructPredicate(term);
	}

	private static String removeSerialNumber(final String rawString, final ILogger logger) {
		String[] res = rawString.split("#",2);
		if (res.length == 1){
			logger.warn("String "+rawString+" doesn't have a # symbol in it. Kepping entire string.");
			return res[0];
		} else if (res.length == 2) { //res[0] is the serial number, res[1] is the string
			return res[1];
		} else {
			logger.warn("Unexpected result from String's split function. String parsing failed.");
			return null;
		}
	}
	

}
