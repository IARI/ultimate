package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.INestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.NestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.core.services.model.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.TermVarsProc;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.CodeBlock;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.ISLPredicate;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.SmtManager;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singleTraceCheck.PredicateUnifier;

/**
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 *
 */
public class AbstractInterpretationAutomatonGenerator {

	private static final boolean CANNIBALIZE = false;

	private final IUltimateServiceProvider mServices;
	private final Logger mLogger;
	private final NestedWordAutomaton<CodeBlock, IPredicate> mResult;
	private final SmtManager mSmtManager;

	public AbstractInterpretationAutomatonGenerator(final IUltimateServiceProvider services,
			final INestedWordAutomaton<CodeBlock, IPredicate> oldAbstraction, final Map<IPredicate, Term> loc2Term,
			final PredicateUnifier predicateUnifier, final SmtManager smtManager) {
		mServices = services;
		mLogger = services.getLoggingService().getLogger(Activator.s_PLUGIN_ID);
		mSmtManager = smtManager;

		mResult = getTermAutomaton(oldAbstraction, loc2Term, predicateUnifier);
	}

	public NestedWordAutomaton<CodeBlock, IPredicate> getResult() {
		return mResult;
	}

	private NestedWordAutomaton<CodeBlock, IPredicate> getTermAutomaton(
			final INestedWordAutomaton<CodeBlock, IPredicate> oldAbstraction, final Map<IPredicate, Term> loc2Term,
			final PredicateUnifier predicateUnifier) {
		final NestedWordAutomaton<CodeBlock, IPredicate> result = new NestedWordAutomaton<CodeBlock, IPredicate>(
				new AutomataLibraryServices(mServices), oldAbstraction.getInternalAlphabet(),
				oldAbstraction.getCallAlphabet(), oldAbstraction.getReturnAlphabet(), oldAbstraction.getStateFactory());
		final Collection<IPredicate> nwaLocs = oldAbstraction.getStates();

		final Set<IPredicate> predicates = new HashSet<>();
		result.addState(true, false, predicateUnifier.getTruePredicate());
		predicates.add(predicateUnifier.getTruePredicate());

		final IPredicate falsePred = predicateUnifier.getFalsePredicate();
		for (final IPredicate pred : nwaLocs) {
			final ISLPredicate slPred = (ISLPredicate) pred;
			final Term term = loc2Term.get(pred);
			if (CANNIBALIZE) {
				addStateCannibalize(oldAbstraction, predicateUnifier, result, predicates, falsePred, slPred, term);
			} else {
				addState(oldAbstraction, predicateUnifier, result, predicates, falsePred, slPred, term);
			}
		}

		if (result.getFinalStates().isEmpty() || !predicates.contains(falsePred)) {
			result.addState(false, true, predicateUnifier.getFalsePredicate());
		}
		if (mLogger.isDebugEnabled()) {
			mLogger.info("Using " + predicates.size() + " predicates from AI: "
					+ String.join(",", predicates.stream().map(a -> a.toString()).collect(Collectors.toList())));
		} else {
			mLogger.info("Using " + predicates.size() + " predicates from AI.");
		}
		return result;
	}

	private void addState(final INestedWordAutomaton<CodeBlock, IPredicate> oldAbstraction,
			final PredicateUnifier predicateUnifier, final NestedWordAutomaton<CodeBlock, IPredicate> result,
			final Set<IPredicate> alreadyUsed, final IPredicate falsePred, final ISLPredicate slPred, final Term term) {
		if (term == null) {
			return;
		}

		final TermVarsProc tvp = TermVarsProc.computeTermVarsProc(term, mSmtManager.getBoogie2Smt());
		final IPredicate newPred = predicateUnifier.getOrConstructPredicate(tvp);
		if (!alreadyUsed.add(newPred)) {
			return;
		}
		if (newPred == falsePred) {
			result.addState(oldAbstraction.isInitial(slPred), true, newPred);
		} else {
			result.addState(oldAbstraction.isInitial(slPred), oldAbstraction.isFinal(slPred), newPred);
		}
	}

	private void addStateCannibalize(final INestedWordAutomaton<CodeBlock, IPredicate> oldAbstraction,
			final PredicateUnifier predicateUnifier, final NestedWordAutomaton<CodeBlock, IPredicate> result,
			final Set<IPredicate> alreadyUsed, final IPredicate falsePred, final ISLPredicate slPred, final Term term) {
		if (term == null) {
			return;
		}

		final Set<IPredicate> newPreds = predicateUnifier.cannibalize(false, term);

		for (final IPredicate newPred : newPreds) {
			if (!alreadyUsed.add(newPred)) {
				continue;
			}
			if (newPred == falsePred) {
				result.addState(oldAbstraction.isInitial(slPred), true, newPred);
			} else {
				result.addState(oldAbstraction.isInitial(slPred), oldAbstraction.isFinal(slPred), newPred);
			}
		}
	}
}
