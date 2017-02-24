package de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.minimization.maxsat.collections;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This data structure can be fed with equality information and returns all fresh transitive implications. Additionally,
 * the data structure supports scoping and making scopes persistent.
 * <p>
 * TODO Get rid of the stack class, it has only one element to encapsulate (do not forget to make methods private!).
 * 
 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
 * @param <T>
 *            type of the content wrapper in pairs (controlled by subclass)
 * @param <C>
 *            content type
 */
public abstract class ScopedTransitivityGenerator<T, C> {
	protected final Map<C, NormalNode<C>> mContent2node;

	private final ScopeStack<C> mStack;

	private final TemporaryRootPredicate mTemporaryRootPredicate;
	private final PersistentRootPredicate mPersistentRootPredicate;

	private final boolean mCompressPaths;

	/**
	 * Constructor.
	 * 
	 * @param compressPaths
	 *            true iff paths should be compressed
	 */
	public ScopedTransitivityGenerator(final boolean compressPaths) {
		mContent2node = new HashMap<>();
		mStack = new ScopeStack<>();
		mTemporaryRootPredicate = new TemporaryRootPredicate();
		mPersistentRootPredicate = new PersistentRootPredicate();
		mCompressPaths = compressPaths;
	}

	/**
	 * Adds a new content if not already present.
	 * <p>
	 * NOTE: A content must be added before its first occurrence in any assertion.<br>
	 * It is, however, possible to add it after other unrelated assertions have been made.
	 * 
	 * @param content
	 *            new content
	 */
	public void addContentIfNotPresent(final C content) {
		mContent2node.put(content, new NormalNode<>(content));
	}

	/**
	 * Adds a new content.
	 * <p>
	 * NOTE: A content must be added before its first occurrence in any assertion.<br>
	 * It is, however, possible to add it after other unrelated assertions have been made.
	 * 
	 * @param content
	 *            new content
	 */
	public void addContent(final C content) {
		final NormalNode<C> oldValue = mContent2node.put(content, new NormalNode<>(content));
		assert oldValue == null : "Never add a content twice.";
	}

	/**
	 * @param pair
	 *            Content/pair.
	 * @return true iff content is known
	 */
	public abstract boolean hasContent(final T pair);

	/**
	 * Makes the given contents equal and reports all inferred transitivity information in order to be consistent.
	 * 
	 * @param pair
	 *            the two contents which are equal
	 * @return all transitive pairs of contents which also need to be equal
	 */
	@SuppressWarnings("squid:S1698")
	public Iterable<T> assertEquality(final T pair) {
		final NormalNode<C> root1 = find(mContent2node.get(getFirst(pair)));
		final NormalNode<C> root2 = find(mContent2node.get(getSecond(pair)));
		// equality is intended here
		if (root1 == root2) {
			// equality is already known, do nothing and report empty information
			return Collections.emptyList();
		}

		/*
		 * Set equality by adding a bridge between the roots of the trees containing the elements.
		 * <p>
		 * Arbitrarily make the first root the new root of both trees.
		 * <p>
		 * TODO optimize: attach smaller to bigger tree (maybe not worth the extra effort)
		 */
		final BridgeNode<C> newBridgeNode = mStack.bridgeTrees(root1, root2);

		return getTransitiveInformation(root1, root2, newBridgeNode, pair);
	}

	/**
	 * Makes all current changes (i.e., of all scopes) persistent.
	 */
	public void makeAllScopesPersistent() {
		mStack.makeAllScopesPersistent();
	}

	/**
	 * Reverts the last scope of changes.
	 */
	public void revertOneScope() {
		mStack.revertOneScope();
	}

	/**
	 * Opens a new (temporary) scope.
	 * <p>
	 * The scope lasts until either {@link #makeAllScopesPersistent()} is called from any scope or
	 * {@link #revertOneScope()} is called while this scope is the current scope.
	 */
	public void addScope() {
		mStack.addScope();
	}

	/**
	 * @param content1
	 *            Content 1.
	 * @param content2
	 *            content 2
	 * @return parametric pair of contents
	 */
	protected abstract T createPair(C content1, C content2);

	/**
	 * @param pair
	 *            Parametric pair.
	 * @return first entry
	 */
	protected abstract C getFirst(T pair);

	/**
	 * @param pair
	 *            Parametric pair.
	 * @return second entry
	 */
	protected abstract C getSecond(T pair);

	@SuppressWarnings("squid:S1698")
	private NormalNode<C> find(final NormalNode<C> source) {
		if (mCompressPaths) {
			final NormalNode<C> persistentParent = findNextRoot(source, mPersistentRootPredicate);
			if (source != persistentParent) {
				// if the source is not the transitive persistent parent of its subtree, compress the path to this node
				final INode<C> sourceDirectParent = source.getParent();
				assert sourceDirectParent.getClass() == NormalNode.class : "";
				// remove source from its direct parent's children
				((NormalNode<C>) sourceDirectParent).removeNormalChild(source);
				// set source's new parent to transitive parent
				source.setParent(persistentParent);
				// add source to transitive parent's children
				persistentParent.addNormalChild(source);
			}
			return findNextRoot(persistentParent, mTemporaryRootPredicate);
		}
		return findNextRoot(source, mTemporaryRootPredicate);
	}

	private NormalNode<C> findNextRoot(final NormalNode<C> source, final INodePredicate predicate) {
		INode<C> node = source;
		while (!predicate.check(node)) {
			node = node.getParent();
		}
		assert node.getClass() == NormalNode.class : "Invalid tree root.";
		return (NormalNode<C>) node;
	}

	/**
	 * Creates all pairs of contents which are also equal as a consequence of melding two trees.
	 * 
	 * @param root1
	 *            root of first tree
	 * @param root2
	 *            root of second tree
	 * @param newBridgeNode
	 *            bridge node connecting the two roots
	 * @param inputPair
	 *            information which should not occur in the result
	 * @return all transitive information
	 */
	private Iterable<T> getTransitiveInformation(final NormalNode<C> root1, final NormalNode<C> root2,
			final BridgeNode<C> newBridgeNode, final T inputPair) {
		// at the moment we only have an array-based implemenation
		return getTransitiveInformationArrays(root1, root2, newBridgeNode, inputPair);
	}

	/**
	 * This implementation first transforms the trees to arrays and then just iterates over the two arrays to create all
	 * pairs.<br>
	 * While this needs more time and space initially, iteration afterward should be very fast.
	 * <p>
	 * TODO this is a proof-of-concept implementation, replace with more efficient one after everything else works
	 * 
	 * @param root1
	 *            root of first tree
	 * @param root2
	 *            root of second tree
	 * @param newBridgeNode
	 *            bridge node connecting the two roots
	 * @param inputPair
	 *            information which should not occur in the result
	 * @return all transitive information
	 */
	private Iterable<T> getTransitiveInformationArrays(final NormalNode<C> root1, final NormalNode<C> root2,
			final BridgeNode<C> newBridgeNode, final T inputPair) {
		// transform trees to arrays
		final ArrayList<C> asList1 = toArrayList(root1, newBridgeNode);
		final ArrayList<C> asList2 = toArrayList(root2, null);

		// insert all pairs to a list
		final List<T> result = new ArrayList<>(asList1.size() * asList2.size() - 1);
		for (final C content1 : asList1) {
			for (final C content2 : asList2) {
				final T pair = createPair(content1, content2);
				if (!pair.equals(inputPair)) {
					result.add(pair);
				}
			}
		}

		assert result.size() == (asList1.size() * asList2.size() - 1);
		return result;
	}

	@SuppressWarnings("squid:S1698")
	private ArrayList<C> toArrayList(final NormalNode<C> root, final BridgeNode<C> newBridgeNode) {
		final ArrayList<C> result = new ArrayList<>();
		final ArrayDeque<INode<C>> stack = new ArrayDeque<>();
		stack.push(root);

		while (!stack.isEmpty()) {
			final INode<C> node = stack.pop();
			if (node.getClass() == NormalNode.class) {
				result.add(((NormalNode<C>) node).getContent());
			}
			for (final INode<C> child : node.getChildren()) {
				// equality is intended here
				if (child != newBridgeNode) {
					stack.push(child);
				}
			}
		}
		return result;
	}

	/**
	 * Stack of scopes objects which controls the different scopes of reversible information.
	 * <p>
	 * One scope is a list of {@link T}.
	 * 
	 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
	 * @param <T>
	 *            stack content type
	 */
	private static final class ScopeStack<T> {
		private final Deque<List<BridgeNode<T>>> mStack;

		private final IBridgeAction<T> mRevertBridgeAction;
		private final IBridgeAction<T> mPersistentBridgeAction;

		/**
		 * Constructor.
		 */
		public ScopeStack() {
			mStack = new ArrayDeque<>();
			/*
			 * TODO (INIT_SCOPE) We could ignore the first scope here. This way we could detect that the current scope
			 * is the lowermost stack level and hence ignore temporary stuff in this situation as this is never
			 * reverted!
			 */
			addScope();

			mRevertBridgeAction = new TemporaryBridgeAction<>();
			mPersistentBridgeAction = new PersistentBridgeAction<>();
		}

		/**
		 * Opens a new (temporary) scope.
		 */
		public void addScope() {
			mStack.push(new ArrayList<>());
		}

		/**
		 * Removes the current scope and returns the stack content.<br>
		 * If the stack is empty afterward, we automatically add a fresh scope.
		 * <p>
		 * TODO see (INIT_SCOPE)
		 * 
		 * @return stack content of the old top of the stack
		 */
		private List<BridgeNode<T>> removeScope() {
			final List<BridgeNode<T>> oldStackContent = mStack.pop();

			if (mStack.isEmpty()) {
				/*
				 * TODO see (INIT_SCOPE)
				 */
				addScope();
			}

			return oldStackContent;
		}

		/**
		 * Makes all current changes (i.e., of all scopes) persistent.
		 */
		public void makeAllScopesPersistent() {
			for (int i = mStack.size() - 1; i >= 0; --i) {
				final List<BridgeNode<T>> oldBridges = removeScope();

				// make equalities persistent
				processBridgeNodes(mPersistentBridgeAction, oldBridges);
			}

		}

		/**
		 * Reverts the last scope of changes.
		 */
		public void revertOneScope() {
			final List<BridgeNode<T>> oldBridges = removeScope();

			// revert equalities
			processBridgeNodes(mRevertBridgeAction, oldBridges);
		}

		/**
		 * Creates a temporary bridge between the roots such that the first root becomes the parent and the second root
		 * becomes the child of the bridge.
		 * 
		 * @param newRoot
		 *            first root, becomes parent of the bridge
		 * @param newChild
		 *            second root, becomes child of the bridge
		 * @return the temporary bridge node
		 */
		public BridgeNode<T> bridgeTrees(final NormalNode<T> newRoot, final NormalNode<T> newChild) {
			final BridgeNode<T> bridgeNode = new BridgeNode<>(newRoot, newChild);
			newChild.setParent(bridgeNode);
			newRoot.addBridgeChild(bridgeNode);
			mStack.peek().add(bridgeNode);
			return bridgeNode;
		}

		private void processBridgeNodes(final IBridgeAction<T> action, final List<BridgeNode<T>> bridges) {
			for (int i = bridges.size() - 1; i >= 0; --i) {
				assert i == (bridges.size() - 1);
				final BridgeNode<T> bridgeNode = bridges.remove(i);
				action.execute(bridgeNode);
			}
		}

		@Override
		public String toString() {
			return mStack.toString();
		}

		/**
		 * Action executed on a {@link BridgeNode}.
		 * 
		 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
		 * @param <T>
		 *            node type
		 */
		@FunctionalInterface
		private interface IBridgeAction<T> {
			void execute(BridgeNode<T> bridgeNode);
		}

		/**
		 * Disconnects a temporary bridge between two trees.
		 * 
		 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
		 * @param <T>
		 *            node type
		 */
		private static final class TemporaryBridgeAction<T> implements IBridgeAction<T> {
			@Override
			public void execute(final BridgeNode<T> bridgeNode) {
				bridgeNode.getChild().makeRoot();
				bridgeNode.getParent().removeBridgeChild(bridgeNode);
			}

			@Override
			public String toString() {
				return this.getClass().getSimpleName();
			}
		}

		/**
		 * Removes a temporary bridge between two trees and instead melds the trees persistently.
		 * 
		 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
		 * @param <T>
		 *            node type
		 */
		private static final class PersistentBridgeAction<T> implements IBridgeAction<T> {
			@Override
			public void execute(final BridgeNode<T> bridgeNode) {
				final NormalNode<T> parent = bridgeNode.getParent();
				final NormalNode<T> child = bridgeNode.getChild();
				child.setParent(parent);
				parent.addNormalChild(child);
				parent.removeBridgeChild(bridgeNode);
			}

			@Override
			public String toString() {
				return this.getClass().getSimpleName();
			}
		}
	}

	/**
	 * Predicate on an {@link INode}.
	 * 
	 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
	 */
	@FunctionalInterface
	private interface INodePredicate {
		boolean check(INode<?> node);
	}

	/**
	 * Returns true iff the node is a root.
	 * 
	 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
	 */
	private static final class TemporaryRootPredicate implements INodePredicate {
		@Override
		public boolean check(final INode<?> node) {
			return node.isRoot();
		}

		@Override
		public String toString() {
			return this.getClass().getSimpleName();
		}
	}

	/**
	 * Returns true iff the node is either a root or a bridge node.
	 * 
	 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
	 */
	private static final class PersistentRootPredicate implements INodePredicate {
		@Override
		public boolean check(final INode<?> node) {
			return node.isTemporaryRootOrBridge();
		}

		@Override
		public String toString() {
			return this.getClass().getSimpleName();
		}
	}

	/**
	 * Doubly-linked tree's node interface.
	 * 
	 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
	 * @param <T>
	 *            node type
	 */
	private interface INode<T> {
		INode<T> getParent();

		Iterable<INode<T>> getChildren();

		boolean isRoot();

		boolean isTemporaryRootOrBridge();
	}

	/**
	 * Normal tree node.
	 * 
	 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
	 * @param <T>
	 *            node type
	 */
	private static final class NormalNode<T> implements INode<T> {
		private INode<T> mParent;
		private final T mContent;
		private Set<INode<T>> mNormalChildren;
		private Set<INode<T>> mBridgeChildren;

		public NormalNode(final T content) {
			mParent = this;
			mContent = content;
			mNormalChildren = Collections.emptySet();
			mBridgeChildren = Collections.emptySet();
		}

		@Override
		public INode<T> getParent() {
			return mParent;
		}

		@SuppressWarnings("squid:S1698")
		@Override
		public boolean isRoot() {
			// equality is fine here
			return getParent() == this;
		}

		@Override
		public boolean isTemporaryRootOrBridge() {
			return isRoot() || (mParent.getClass() == BridgeNode.class);
		}

		public void setParent(final INode<T> parent) {
			mParent = parent;
		}

		public T getContent() {
			return mContent;
		}

		/**
		 * Makes the node a new root of its children.
		 */
		public void makeRoot() {
			setParent(this);
		}

		@Override
		public Iterable<INode<T>> getChildren() {
			return new ChildrenIterable<>(mNormalChildren, mBridgeChildren);
		}

		/**
		 * @param normalChild
		 *            A normal node to add as a child.
		 */
		public void addNormalChild(final NormalNode<T> normalChild) {
			if (mNormalChildren.isEmpty()) {
				mNormalChildren = new LinkedHashSet<>();
			}
			mNormalChildren.add(normalChild);
		}

		/**
		 * @param normalChild
		 *            A normal node to remove from the children.
		 */
		public void removeNormalChild(final NormalNode<T> normalChild) {
			final boolean wasPresent = mNormalChildren.remove(normalChild);
			assert wasPresent : "The bridge node was not present.";
			if (mNormalChildren.isEmpty()) {
				mNormalChildren = Collections.emptySet();
			}
		}

		/**
		 * @param bridgeChild
		 *            A bridge node to add as a child.
		 */
		public void addBridgeChild(final BridgeNode<T> bridgeChild) {
			if (mBridgeChildren.isEmpty()) {
				mBridgeChildren = new LinkedHashSet<>();
			}
			mBridgeChildren.add(bridgeChild);
		}

		/**
		 * @param bridgeChild
		 *            A bridge node to remove from the children.
		 */
		public void removeBridgeChild(final BridgeNode<T> bridgeChild) {
			final boolean wasPresent = mBridgeChildren.remove(bridgeChild);
			assert wasPresent : "The bridge node was not present.";
			if (mBridgeChildren.isEmpty()) {
				mBridgeChildren = Collections.emptySet();
			}
		}

		@Override
		public String toString() {
			return "<NN: " + mContent + ", "
					+ (isTemporaryRootOrBridge() ? (isRoot() ? "Root, " : "TempRoot, ") : "no TempRoot nor Bridge, ")
					+ mNormalChildren.size() + " NC, " + mBridgeChildren.size() + " BC>";
		}

		/**
		 * Iterable over a node's children.
		 * 
		 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
		 * @param <T>
		 *            node type
		 */
		private static class ChildrenIterable<T> implements Iterable<INode<T>> {
			private final Collection<INode<T>> mNormalChildren;
			private final Collection<INode<T>> mBridgeChildren;

			public ChildrenIterable(final Collection<INode<T>> normalChildren,
					final Collection<INode<T>> bridgeChildren) {
				mNormalChildren = normalChildren;
				mBridgeChildren = bridgeChildren;
			}

			@Override
			public Iterator<INode<T>> iterator() {
				return new ChildrenIterator<>(mNormalChildren, mBridgeChildren);
			}
		}

		/**
		 * Iterator over a node's children.
		 * 
		 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
		 * @param <T>
		 *            node type
		 */
		private static class ChildrenIterator<T> implements Iterator<INode<T>> {
			private final Iterator<? extends INode<T>> mBridgeChildrenIterator;
			private Iterator<? extends INode<T>> mIterator;
			private boolean mIsAtBridgeNodes;

			public ChildrenIterator(final Collection<INode<T>> normalChildren,
					final Collection<INode<T>> bridgeChildren) {
				mBridgeChildrenIterator = bridgeChildren.iterator();
				mIterator = normalChildren.iterator();
				if (!mIterator.hasNext()) {
					mIterator = mBridgeChildrenIterator;
					mIsAtBridgeNodes = true;
				} else {
					mIsAtBridgeNodes = false;
				}
			}

			@Override
			public boolean hasNext() {
				return mIterator.hasNext();
			}

			@Override
			public INode<T> next() {
				assert mIterator.hasNext() : "Iterator not used properly.";
				final INode<T> next = mIterator.next();
				if ((!mIsAtBridgeNodes) && (!mIterator.hasNext())) {
					mIsAtBridgeNodes = true;
					mIterator = mBridgeChildrenIterator;
				}
				return next;
			}
		}
	}

	/**
	 * Node which acts as a temporary bridge between two trees.
	 * 
	 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
	 * @param <T>
	 *            node type
	 */
	private static final class BridgeNode<T> implements INode<T> {
		private final NormalNode<T> mParent;
		private final NormalNode<T> mChild;

		public BridgeNode(final NormalNode<T> parent, final NormalNode<T> child) {
			mParent = parent;
			mChild = child;
		}

		@Override
		public NormalNode<T> getParent() {
			return mParent;
		}

		@Override
		public boolean isRoot() {
			return false;
		}

		@Override
		public boolean isTemporaryRootOrBridge() {
			return true;
		}

		@Override
		public Iterable<INode<T>> getChildren() {
			return Collections.singleton(mChild);
		}

		public NormalNode<T> getChild() {
			return mChild;
		}

		@Override
		public String toString() {
			return "<BN: " + mParent + ", " + mChild + ">";
		}
	}
}
