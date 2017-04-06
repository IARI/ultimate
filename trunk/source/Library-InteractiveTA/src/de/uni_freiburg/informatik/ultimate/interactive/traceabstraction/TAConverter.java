package de.uni_freiburg.informatik.ultimate.interactive.traceabstraction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.protobuf.GeneratedMessageV3;

import de.uni_freiburg.informatik.ultimate.automata.IRun;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.DoubleDecker;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.IOutgoingTransitionlet;
import de.uni_freiburg.informatik.ultimate.interactive.conversion.Converter;
import de.uni_freiburg.informatik.ultimate.interactive.conversion.ConverterRegistry;
import de.uni_freiburg.informatik.ultimate.interactive.traceabstraction.protobuf.TraceAbstractionProtos;
import de.uni_freiburg.informatik.ultimate.interactive.traceabstraction.protobuf.TraceAbstractionProtos.InteractiveIterationInfo;
import de.uni_freiburg.informatik.ultimate.interactive.traceabstraction.protobuf.TraceAbstractionProtos.PredicateDoubleDecker;
import de.uni_freiburg.informatik.ultimate.interactive.traceabstraction.protobuf.TraceAbstractionProtos.Result;
import de.uni_freiburg.informatik.ultimate.interactive.traceabstraction.protobuf.TraceAbstractionProtos.Strategy;
import de.uni_freiburg.informatik.ultimate.interactive.traceabstraction.protobuf.TraceAbstractionProtos.TAPreferences.Artifact;
import de.uni_freiburg.informatik.ultimate.interactive.traceabstraction.protobuf.TraceAbstractionProtos.TAPreferences.Concurrency;
import de.uni_freiburg.informatik.ultimate.interactive.traceabstraction.protobuf.TraceAbstractionProtos.TAPreferences.Format;
import de.uni_freiburg.informatik.ultimate.interactive.traceabstraction.protobuf.TraceAbstractionProtos.TAPreferences.HoareAnnotationPositions;
import de.uni_freiburg.informatik.ultimate.interactive.traceabstraction.protobuf.TraceAbstractionProtos.TAPreferences.HoareTripleChecks;
import de.uni_freiburg.informatik.ultimate.interactive.traceabstraction.protobuf.TraceAbstractionProtos.TAPreferences.InterpolantAutomaton;
import de.uni_freiburg.informatik.ultimate.interactive.traceabstraction.protobuf.TraceAbstractionProtos.TAPreferences.InterpolantAutomatonEnhancement;
import de.uni_freiburg.informatik.ultimate.interactive.traceabstraction.protobuf.TraceAbstractionProtos.TAPreferences.InterpolationTechnique;
import de.uni_freiburg.informatik.ultimate.interactive.traceabstraction.protobuf.TraceAbstractionProtos.TAPreferences.Minimization;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.CodeBlock;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.AbstractCegarLoop;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.CegarLoopStatisticsDefinitions;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.CegarLoopStatisticsGenerator;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.interactive.PredicateQueuePair;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.interactive.PredicateQueueResult;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.IMLPredicate;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.ISLPredicate;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TAPreferences;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.RefinementStrategy;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.tracehandling.MultiTrackTraceAbstractionRefinementStrategy;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.tracehandling.MultiTrackTraceAbstractionRefinementStrategy.Track;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.tracehandling.interactive.ParrotInteractiveIterationInfo;

public class TAConverter extends Converter<GeneratedMessageV3, Object> {
	public TAConverter() {
		super(Object.class);
	}

	@Override
	protected void init(ConverterRegistry<GeneratedMessageV3, Object> converterRegistry) {
		converterRegistry.registerBA(TraceAbstractionProtos.NestedRun.class, IRun.class, TAConverter::fromNestedRun);
		@SuppressWarnings("unchecked")
		Class<INestedWordAutomaton<CodeBlock, IPredicate>> cls =
				(Class<INestedWordAutomaton<CodeBlock, IPredicate>>) (Class) INestedWordAutomaton.class;
		converterRegistry.registerBA(TraceAbstractionProtos.NestedWordAutomaton.class, cls, TAConverter::fromAutomaton);
		converterRegistry.registerBA(TraceAbstractionProtos.TAPreferences.class, TAPreferences.class,
				TAConverter::fromTAPreferences);
		converterRegistry.registerBA(TraceAbstractionProtos.CegarResult.class, AbstractCegarLoop.Result.class,
				TAConverter::fromResult);

		converterRegistry.registerAB(TraceAbstractionProtos.Question.class, Boolean.class,
				TraceAbstractionProtos.Question::getAnswer);

		converterRegistry.registerAB(TraceAbstractionProtos.Tracks.class,
				MultiTrackTraceAbstractionRefinementStrategy.Track[].class, TAConverter::toTracks);
		converterRegistry.registerBA(TraceAbstractionProtos.Tracks.class,
				MultiTrackTraceAbstractionRefinementStrategy.Track[].class, TAConverter::fromTracks);
		converterRegistry.registerAB(InteractiveIterationInfo.class, ParrotInteractiveIterationInfo.class,
				TAConverter::toIterationInfo);
		converterRegistry.registerBA(InteractiveIterationInfo.class, ParrotInteractiveIterationInfo.class,
				TAConverter::fromIterationInfo);

		converterRegistry.registerBA(PredicateDoubleDecker.QueuePair.class, PredicateQueuePair.class,
				TAConverter::fromQueuePair);
		converterRegistry.registerRConv(PredicateDoubleDecker.QueueResponse.class, PredicateQueuePair.class,
				PredicateQueueResult.class, TAConverter::toDoubleDecker);

		// converterRegistry.registerBA(TraceAbstractionProtos.IterationInfo.class, IterationInfo.Iteration.class,
		// i -> TraceAbstractionProtos.IterationInfo.newBuilder().setIteration(i.mIteration).build());
		converterRegistry.registerBA(TraceAbstractionProtos.IterationInfo.class, CegarLoopStatisticsGenerator.class,
				TAConverter::fromCegarLoopStatisticsGenerator);
	}

	private static TraceAbstractionProtos.IterationInfo
			fromCegarLoopStatisticsGenerator(CegarLoopStatisticsGenerator benchmark) {
		final TraceAbstractionProtos.IterationInfo.Builder builder = TraceAbstractionProtos.IterationInfo.newBuilder();
		builder.setIteration((int) benchmark.getValue(CegarLoopStatisticsDefinitions.OverallIterations.toString()));
		return builder.build();
	}

	private static PredicateDoubleDecker.QueuePair fromQueuePair(PredicateQueuePair qPair) {
		PredicateDoubleDecker.QueuePair.Builder builder = PredicateDoubleDecker.QueuePair.newBuilder();
		qPair.mCallQueue.stream().map(TAConverter::fromDoubleDecker).forEach(builder::addCallQueue);
		qPair.mQueue.stream().map(TAConverter::fromDoubleDecker).forEach(builder::addQueue);
		return builder.build();
	}

	private static PredicateDoubleDecker fromDoubleDecker(DoubleDecker<IPredicate> pdd) {
		final TraceAbstractionProtos.Predicate up = fromPredicate(pdd.getUp());
		final TraceAbstractionProtos.Predicate down = fromPredicate(pdd.getUp());
		return PredicateDoubleDecker.newBuilder().setUp(up).setDown(down).build();
	}

	private static PredicateQueueResult toDoubleDecker(PredicateDoubleDecker.QueueResponse response,
			PredicateQueuePair data) {
		final Deque<DoubleDecker<IPredicate>> queue;
		switch (response.getQueueType()) {
		case CALL:
			queue = data.mCallQueue;
			break;
		default:
			queue = data.mQueue;
		}
		final DoubleDecker<IPredicate>[] arr = new DoubleDecker[queue.size()];
		DoubleDecker<IPredicate> result = queue.toArray(arr)[response.getIndex()];
		assert queue.remove(result);
		return new PredicateQueueResult(result);
	}

	private static InteractiveIterationInfo fromIterationInfo(ParrotInteractiveIterationInfo itInfo) {
		return InteractiveIterationInfo.newBuilder().setNextInteractiveIteration(itInfo.getNextInteractiveIteration())
				.setFallback(convertTo(Strategy.class, itInfo.getFallbackStrategy())).build();
	}

	private static ParrotInteractiveIterationInfo toIterationInfo(InteractiveIterationInfo itInfo) {
		return new ParrotInteractiveIterationInfo(convertTo(RefinementStrategy.class, itInfo.getFallback()),
				itInfo.getNextInteractiveIteration());
	}

	private static TraceAbstractionProtos.Tracks
			fromTracks(MultiTrackTraceAbstractionRefinementStrategy.Track[] tracks) {
		final TraceAbstractionProtos.Tracks.Builder builder = TraceAbstractionProtos.Tracks.newBuilder();
		Arrays.stream(tracks).map(convertToEnum(TraceAbstractionProtos.Track.class)).forEach(builder::addTrack);
		return builder.build();
	}

	private static MultiTrackTraceAbstractionRefinementStrategy.Track[] toTracks(TraceAbstractionProtos.Tracks tracks) {
		return tracks.getTrackList().stream()
				.map(convertToEnum(MultiTrackTraceAbstractionRefinementStrategy.Track.class)).toArray(Track[]::new);
	}

	public static TraceAbstractionProtos.NestedRun fromNestedRun(IRun<CodeBlock, IPredicate, ?> run) {
		TraceAbstractionProtos.NestedRun.Builder builder = TraceAbstractionProtos.NestedRun.newBuilder();
		run.getWord().asList().stream().map(TAConverter::fromCodeblock).forEach(builder::addNestedWord);
		// builder.addAllNestedWord(values)
		return builder.build();
	}

	public static TraceAbstractionProtos.NestedWordAutomaton
			fromAutomaton(INestedWordAutomaton<CodeBlock, IPredicate> automaton) {
		final List<CodeBlock> callAlphabet = new ArrayList<>();
		automaton.getCallAlphabet().forEach(callAlphabet::add);
		final List<CodeBlock> internalAlphabet = new ArrayList<>();
		automaton.getInternalAlphabet().forEach(internalAlphabet::add);
		final List<CodeBlock> returnAlphabet = new ArrayList<>();
		automaton.getReturnAlphabet().forEach(returnAlphabet::add);
		final List<IPredicate> states = new ArrayList<>();
		automaton.getStates().forEach(states::add);
		final Function<Consumer<Integer>, Consumer<IPredicate>> addStateRef = addRef(states);

		final TraceAbstractionProtos.NestedWordAutomaton.Builder builder =
				TraceAbstractionProtos.NestedWordAutomaton.newBuilder();

		builder.setCall(fromAlphabet(callAlphabet)).setInternal(fromAlphabet(internalAlphabet))
				.setReturn(fromAlphabet(returnAlphabet));

		addStateRef.apply(builder::setEmptyStack).accept(automaton.getEmptyStackState());

		for (IPredicate state : states) {
			builder.addStates(fromPredicate(state));
			if (automaton.isInitial(state))
				addStateRef.apply(builder::addInitial).accept(state);
			if (automaton.isFinal(state))
				addStateRef.apply(builder::addFinal).accept(state);

			stream(automaton.internalSuccessors(state)).map(getTransition(state, internalAlphabet, states))
					.forEach(builder::addInternalEdges);
			stream(automaton.callSuccessors(state)).map(getTransition(state, callAlphabet, states))
					.forEach(builder::addCallEdges);
			stream(automaton.returnSuccessors(state)).map(getTransition(state, returnAlphabet, states))
					.forEach(builder::addReturnEdges);
		}
		// states.stream()
		// .map(Converter::fromPredicate)
		// .forEach(builder::addStates);

		// .setCallIn(fromTransition(automaton.callPredecessors(succ),
		// states, alphabet))
		//
		// automaton.getInitialStates().forEach(addStateRef.apply(builder::addInitial));
		// automaton.getFinalStates().forEach(addStateRef.apply(builder::addFinal));

		return builder.build();
	}

	private static <T> Stream<T> stream(Iterable<T> source) {
		return StreamSupport.stream(source.spliterator(), false);
	}

	private static
			Function<IOutgoingTransitionlet<CodeBlock, IPredicate>, TraceAbstractionProtos.NestedWordAutomaton.transition.Builder>
			getTransition(final IPredicate origin, List<CodeBlock> alphabet, List<IPredicate> states) {
		return transition -> {
			final TraceAbstractionProtos.NestedWordAutomaton.transition.Builder builder =
					TraceAbstractionProtos.NestedWordAutomaton.transition.newBuilder();
			addRef(states).apply(builder::setOriginState).accept(origin);
			addRef(states).apply(builder::setSuccessorState).accept(transition.getSucc());
			addRef(alphabet).apply(builder::setLetter).accept(transition.getLetter());
			return builder;
		};
	}

	private static <T> Function<Consumer<Integer>, Consumer<T>> addRef(final List<T> targets) {
		return action -> {
			return new Consumer<T>() {
				@Override
				public void accept(final T t) {
					action.accept(targets.indexOf(t));
				}
			};
		};
	}

	private static TraceAbstractionProtos.Alphabet fromAlphabet(Iterable<CodeBlock> alphabet) {
		final TraceAbstractionProtos.Alphabet.Builder builder = TraceAbstractionProtos.Alphabet.newBuilder();
		for (CodeBlock codeBlock : alphabet) {
			builder.addLetter(fromCodeblock(codeBlock));
		}
		return builder.build();
	}

	private static TraceAbstractionProtos.NestedWordAutomaton.transition fromTransition(
			Map<IPredicate, Map<CodeBlock, Set<IPredicate>>> transitions, List<IPredicate> states,
			List<CodeBlock> alphabet) {
		final TraceAbstractionProtos.NestedWordAutomaton.transition.Builder builder =
				TraceAbstractionProtos.NestedWordAutomaton.transition.newBuilder();
		for (Map.Entry<IPredicate, Map<CodeBlock, Set<IPredicate>>> transition : transitions.entrySet()) {
			final int firstIndex = states.indexOf(transition.getKey());
		}
		return builder.build();
	}

	public static TraceAbstractionProtos.CodeBlock fromCodeblock(CodeBlock codeblock) {
		return TraceAbstractionProtos.CodeBlock.newBuilder().setCode(codeblock.getPrettyPrintedStatements()).build();
	}

	public static TraceAbstractionProtos.IcfgLocation fromLocation(IcfgLocation location) {
		return TraceAbstractionProtos.IcfgLocation.newBuilder().setDebugIdentifier(location.getDebugIdentifier())
				.setProcedure(location.getProcedure()).build();
	}

	public static TraceAbstractionProtos.Predicate fromPredicate(IPredicate predicate) {
		final TraceAbstractionProtos.Predicate.Builder builder = TraceAbstractionProtos.Predicate.newBuilder();
		if (predicate instanceof ISLPredicate) {
			ISLPredicate islPred = (ISLPredicate) predicate;
			builder.addLocation(fromLocation(islPred.getProgramPoint()));
		} else if (predicate instanceof IMLPredicate) {
			IMLPredicate imlPred = (IMLPredicate) predicate;
			Arrays.stream(imlPred.getProgramPoints()).map(TAConverter::fromLocation).forEach(builder::addLocation);
		}
		builder.setFormulaString(predicate.getFormula().toString())
				.setFormulaHashCode(predicate.getFormula().hashCode());
		Arrays.stream(predicate.getProcedures()).forEach(builder::addProcedures);
		return builder.build();
	}

	public static TraceAbstractionProtos.TAPreferences fromTAPreferences(TAPreferences preferences) {
		final InterpolantAutomatonEnhancement enhancment;
		switch (preferences.interpolantAutomatonEnhancement()) {
		case NONE:
			enhancment = InterpolantAutomatonEnhancement.NO_ENHANCEMENT;
			break;
		default:
			enhancment =
					convertTo(InterpolantAutomatonEnhancement.class, preferences.interpolantAutomatonEnhancement());
		}

		final Minimization minimization;
		switch (preferences.getMinimization()) {
		case NONE:
			minimization = Minimization.NO_MINIMIZATION;
			break;

		default:
			minimization = convertTo(Minimization.class, preferences.getMinimization());
			break;
		}

		return TraceAbstractionProtos.TAPreferences.newBuilder().setMInterprocedural(preferences.interprocedural())
				.setMMaxIterations(preferences.maxIterations()).setMWatchIteration(preferences.watchIteration())
				.setMArtifact(convertTo(Artifact.class, preferences.artifact()))
				.setMInterpolation(convertTo(InterpolationTechnique.class, preferences.interpolation()))
				.setMInterpolantAutomaton(convertTo(InterpolantAutomaton.class, preferences.interpolantAutomaton()))
				.setMDumpAutomata(preferences.dumpAutomata())
				.setMAutomataFormat(convertTo(Format.class, preferences.getAutomataFormat()))
				.setMDumpPath(preferences.dumpPath()).setMDeterminiation(enhancment).setMMinimize(minimization)
				.setMHoare(preferences.computeHoareAnnotation())
				.setMConcurrency(convertTo(Concurrency.class, preferences.getConcurrency()))
				.setMHoareTripleChecks(convertTo(HoareTripleChecks.class, preferences.getHoareTripleChecks()))
				.setMHoareAnnotationPositions(
						convertTo(HoareAnnotationPositions.class, preferences.getHoareAnnotationPositions()))
				.build();
	}

	public static TraceAbstractionProtos.CegarResult fromResult(AbstractCegarLoop.Result result) {
		return TraceAbstractionProtos.CegarResult.newBuilder().setResult(convertTo(Result.class, result)).build();
	}

	public static <E extends Enum<E>> Function<Enum<?>, E> convertToEnum(Class<E> toCls) {
		return f -> convertTo(toCls, f);
	}

	public static <E extends Enum<E>> E convertTo(Class<E> toCls, Enum<?> from) {
		return Enum.valueOf(toCls, from.name());
	}
}