package de.uni_freiburg.informatik.ultimate.plugins.analysis.reachingdefinitions.trace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import de.uni_freiburg.informatik.ultimate.boogie.symboltable.BoogieSymbolTable;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.AssumeStatement;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.Statement;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.reachingdefinitions.annotations.IAnnotationProvider;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.reachingdefinitions.annotations.ReachDefEdgeAnnotation;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.reachingdefinitions.annotations.ReachDefStatementAnnotation;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.reachingdefinitions.boogie.ScopedBoogieVar;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.reachingdefinitions.boogie.ScopedBoogieVarBuilder;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.reachingdefinitions.dataflowdag.DataflowDAG;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.CodeBlock;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.StatementSequence;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.util.RCFGEdgeVisitor;

public class ReachDefTrace {

	private final Logger mLogger;
	private final IAnnotationProvider<ReachDefStatementAnnotation> mStatementProvider;
	private final IAnnotationProvider<ReachDefEdgeAnnotation> mEdgeProvider;
	private final BoogieSymbolTable mSymbolTable;

	public ReachDefTrace(IAnnotationProvider<ReachDefEdgeAnnotation> edgeProvider,
			IAnnotationProvider<ReachDefStatementAnnotation> stmtProvider, Logger logger, BoogieSymbolTable symboltable) {
		mLogger = logger;
		mStatementProvider = stmtProvider;
		mEdgeProvider = edgeProvider;
		mSymbolTable = symboltable;
	}

	public List<DataflowDAG<CodeBlock>> process(List<CodeBlock> trace) throws Throwable {
		annotateReachingDefinitions(trace);
		List<BlockAndAssumes> assumes = findAssumes(trace);
		List<DataflowDAG<CodeBlock>> rtr = buildDAG(trace, assumes);

		if (mLogger.isDebugEnabled()) {
			mLogger.debug("#" + rtr.size() + " dataflow DAGs constructed");
			printDebugForest(rtr);
		}

		return rtr;

	}

	private List<DataflowDAG<CodeBlock>> buildDAG(List<CodeBlock> trace, List<BlockAndAssumes> assumeContainers) {
		List<DataflowDAG<CodeBlock>> rtr = new ArrayList<>();
		for (BlockAndAssumes assumeContainer : assumeContainers) {
			for (AssumeStatement stmt : assumeContainer.getAssumes()) {
				rtr.add(buildDAG(trace, assumeContainer, stmt));
			}
		}
		return rtr;
	}

	private DataflowDAG<CodeBlock> buildDAG(List<CodeBlock> trace, BlockAndAssumes assumeContainer,
			AssumeStatement assume) {
		LinkedList<DataflowDAG<CodeBlock>> store = new LinkedList<>();

		DataflowDAG<CodeBlock> current = new DataflowDAG<CodeBlock>(assumeContainer.getBlock());
		DataflowDAG<CodeBlock> root = current;
		store.add(current);

		while (!store.isEmpty()) {
			current = store.removeFirst();
			Set<Entry<ScopedBoogieVar, HashSet<Statement>>> uses = getUse(current);
			for (Entry<ScopedBoogieVar, HashSet<Statement>> use : uses) {
				for (Statement stmt : use.getValue()) {
					CodeBlock nextBlock = getBlockContainingStatement(trace, stmt);
					assert nextBlock != null;
					DataflowDAG<CodeBlock> next = new DataflowDAG<CodeBlock>(nextBlock);
					current.connectOutgoing(next, use.getKey());
					store.addFirst(next); // use last for BFS
				}
			}
		}
		return root;
	}

	private CodeBlock getBlockContainingStatement(List<CodeBlock> trace, final Statement stmt) {
		StatementFinder finder = new StatementFinder();
		ISearchPredicate<Statement> predicate = new ISearchPredicate<Statement>() {
			@Override
			public boolean is(Statement object) {
				return object.equals(stmt);
			}
		};
		for (int i = trace.size() - 1; i >= 0; --i) {
			CodeBlock current = trace.get(i);
			List<Statement> lil = finder.start(current, predicate);
			if (!lil.isEmpty()) {
				return current;
			}
		}
		return null;
	}

	private Set<Entry<ScopedBoogieVar, HashSet<Statement>>> getUse(DataflowDAG<CodeBlock> current) {
		ReachDefEdgeAnnotation annot = mEdgeProvider.getAnnotation(current.getNodeLabel());
		assert annot != null;
		HashMap<ScopedBoogieVar, HashSet<Statement>> use = annot.getUse();
		assert use != null;
		return use.entrySet();
	}

	private void annotateReachingDefinitions(List<CodeBlock> trace) {
		ScopedBoogieVarBuilder builder = new ScopedBoogieVarBuilder(mSymbolTable);
		for (int i = 0; i < trace.size(); i++) {
			CodeBlock current = null;
			if (i != 0) {
				current = trace.get(i - 1);
			}

			new ReachDefTraceVisitor(mStatementProvider, mEdgeProvider, current, mLogger, builder)
					.process(trace.get(i));
		}
	}

	private List<BlockAndAssumes> findAssumes(List<CodeBlock> trace) {
		List<BlockAndAssumes> rtr = new ArrayList<>();
		StatementFinder visitor = new StatementFinder();
		ISearchPredicate<Statement> predicate = new ISearchPredicate<Statement>() {
			@Override
			public boolean is(Statement object) {
				return object instanceof AssumeStatement;
			}
		};

		int i = 0;
		for (CodeBlock block : trace) {
			List<AssumeStatement> assumes = new ArrayList<>();
			for (Statement stmt : visitor.start(block, predicate)) {
				assumes.add((AssumeStatement) stmt);
			}

			if (!assumes.isEmpty()) {
				rtr.add(new BlockAndAssumes(assumes, i, block));
			}
			++i;
		}
		return rtr;
	}

	private void printDebugForest(List<DataflowDAG<CodeBlock>> forest) {
		if (forest == null) {
			return;
		}

		for (DataflowDAG<CodeBlock> dag : forest) {
			dag.printGraphDebug(mLogger);
		}
	}

	private class BlockAndAssumes {

		private final List<AssumeStatement> mAssumes;
		private final int mIndex;
		private final CodeBlock mBlock;

		public BlockAndAssumes(List<AssumeStatement> assumes, int index, CodeBlock block) {
			mAssumes = assumes;
			mIndex = index;
			mBlock = block;
		}

		public List<AssumeStatement> getAssumes() {
			return mAssumes;
		}

		public int getIndex() {
			return mIndex;
		}

		public CodeBlock getBlock() {
			return mBlock;
		}

	}

	private class StatementFinder extends RCFGEdgeVisitor {
		private List<Statement> mStatements;
		private ISearchPredicate<Statement> mPredicate;

		@Override
		protected void visit(StatementSequence c) {
			for (Statement stmt : c.getStatements()) {
				if (mPredicate.is(stmt)) {
					mStatements.add(stmt);
				}
			}
			super.visit(c);
		}

		public List<Statement> start(CodeBlock block, ISearchPredicate<Statement> predicate) {
			mStatements = new ArrayList<>();
			mPredicate = predicate;
			visit(block);
			return mStatements;
		}
	}

	public interface ISearchPredicate<T> {
		boolean is(T object);
	}

}
