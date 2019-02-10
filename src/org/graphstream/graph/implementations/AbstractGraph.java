/*
 * This file is part of GraphStream <http://graphstream-project.org>.
 *
 * GraphStream is a library whose purpose is to handle static or dynamic
 * graph, create them from scratch, file or any source and display them.
 *
 * This program is free software distributed under the terms of two licenses, the
 * CeCILL-C license that fits European law, and the GNU Lesser General Public
 * License. You can  use, modify and/ or redistribute the software under the terms
 * of the CeCILL-C license as circulated by CEA, CNRS and INRIA at the following
 * URL <http://www.cecill.info> or under the terms of the GNU LGPL as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL-C and LGPL licenses and that you accept their terms.
 */

/**
 * @author Stefan Balev <stefan.balev@graphstream-project.org>
 * @author Richard O. Legendi <richard.legendi@gmail.com>
 * @author Guilhelm Savin <guilhelm.savin@graphstream-project.org>
 * @author Yoann Pigné <yoann.pigne@graphstream-project.org>
 * @author Antoine Dutot <antoine.dutot@graphstream-project.org>
 * @author Alex Bowen <bowen.a@gmail.com>
 * @author Hicham Brahimi <hicham.brahimi@graphstream-project.org>
 * @since 2011-07-22
 */
package org.graphstream.graph.implementations;

import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Collectors;

import org.graphstream.graph.Edge;
import org.graphstream.graph.EdgeFactory;
import org.graphstream.graph.EdgeRejectedException;
import org.graphstream.graph.ElementNotFoundException;
import org.graphstream.graph.Graph;
import org.graphstream.graph.IdAlreadyInUseException;
import org.graphstream.graph.Node;
import org.graphstream.graph.NodeFactory;
import org.graphstream.stream.AttributeSink;
import org.graphstream.stream.ElementSink;
import org.graphstream.stream.Replayable;
import org.graphstream.stream.Sink;
import org.graphstream.stream.SourceBase;
import org.graphstream.ui.view.Viewer;
import org.graphstream.util.Display;
import org.graphstream.util.GraphListeners;
import org.graphstream.util.MissingDisplayException;

/**
 * <p>
 * This class provides a basic implementation of
 * {@link org.graphstream.graph.Graph} interface, to minimize the effort
 * required to implement this interface. It provides event management
 * implementing all the methods of {@link org.graphstream.stream.Pipe}. It also
 * manages strict checking and auto-creation policies, as well as other services
 * as displaying, reading and writing.
 * </p>
 * <p>
 * <p>
 * Subclasses have to maintain data structures allowing to efficiently access
 * graph elements by their id or index and iterating on them. They also have to
 * maintain coherent indices of the graph elements. When AbstractGraph decides
 * to add or remove elements, it calls one of the "callbacks"
 * {@link #addNodeCallback(AbstractNode)},
 * {@link #addEdgeCallback(AbstractEdge)},
 * {@link #removeNodeCallback(AbstractNode)},
 * {@link #removeEdgeCallback(AbstractEdge)}, {@link #clearCallback()}. The role
 * of these callbacks is to update the data structures and to re-index elements
 * if necessary.
 * </p>
 */
public abstract class AbstractGraph extends AbstractElement implements Graph, Replayable {
	// *** Fields ***

	GraphListeners listeners;
	private boolean strictChecking;
	private boolean autoCreate;
	private NodeFactory<? extends AbstractNode> nodeFactory;
	private EdgeFactory<? extends AbstractEdge> edgeFactory;

	private double step = 0;

	private long replayId = 0;

	// *** Constructors ***

	/**
	 * The same as {@code AbstractGraph(id, true, false)}
	 *
	 * @param id
	 * 		Identifier of the graph
	 * @see #AbstractGraph(String, boolean, boolean)
	 */
	public AbstractGraph(final String id) {
		this(id, true, false);
	}

	/**
	 * Creates a new graph. Subclasses must create their node and edge factories and
	 * initialize their data structures in their constructors.
	 *
	 * @param id
	 * @param strictChecking
	 * @param autoCreate
	 */
	public AbstractGraph(final String id, final boolean strictChecking, final boolean autoCreate) {
		super(id);

		this.strictChecking = strictChecking;
		this.autoCreate = autoCreate;
		this.listeners = new GraphListeners(this);
	}

	// *** Inherited from abstract element

	@Override
	protected void attributeChanged(final AttributeChangeEvent event, final String attribute, final Object oldValue, final Object newValue) {
		this.listeners.sendAttributeChangedEvent(this.id, SourceBase.ElementType.GRAPH, attribute, event, oldValue, newValue);
	}

	// *** Inherited from graph ***

	/**
	 * This implementation returns an iterator over nodes.
	 *
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<Node> iterator() {
		return this.nodes().iterator();
	}

	// Factories

	@Override
	public NodeFactory<? extends Node> nodeFactory() {
		return this.nodeFactory;
	}

	@Override
	public EdgeFactory<? extends Edge> edgeFactory() {
		return this.edgeFactory;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void setNodeFactory(final NodeFactory<? extends Node> nf) {
		this.nodeFactory = (NodeFactory<? extends AbstractNode>) nf;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void setEdgeFactory(final EdgeFactory<? extends Edge> ef) {
		this.edgeFactory = (EdgeFactory<? extends AbstractEdge>) ef;
	}

	// strict checking, autocreation, etc

	@Override
	public boolean isStrict() {
		return this.strictChecking;
	}

	@Override
	public void setStrict(final boolean on) {
		this.strictChecking = on;
	}

	@Override
	public boolean isAutoCreationEnabled() {
		return this.autoCreate;
	}

	@Override
	public double getStep() {
		return this.step;
	}

	@Override
	public void setAutoCreate(final boolean on) {
		this.autoCreate = on;
	}

	@Override
	public void stepBegins(final double time) {
		this.listeners.sendStepBegins(time);
		this.step = time;
	}

	// display, read, write

	@Override
	public Viewer display() {
		return this.display(true);
	}

	@Override
	public Viewer display(final boolean autoLayout) {
		try {
			Display display = Display.getDefault();
			return display.display(this, autoLayout);
		} catch (MissingDisplayException e) {
			throw new RuntimeException("Cannot launch viewer.", e);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.graph.Graph#clear()
	 */
	@Override
	public void clear() {
		this.listeners.sendGraphCleared();

		this.nodes().forEach(n -> ((AbstractNode) n).clearCallback());

		this.clearCallback();
		this.clearAttributesWithNoEvent();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.graph.Graph#addNode(java.lang.String)
	 */
	@Override
	public Node addNode(final String id) {
		AbstractNode node = (AbstractNode) this.getNode(id);

		if (node != null) {
			if (this.strictChecking) {
				throw new IdAlreadyInUseException("id \"" + id + "\" already in use. Cannot create a node.");
			}
			return node;
		}

		node = this.nodeFactory.newInstance(id, this);
		this.addNodeCallback(node);

		this.listeners.sendNodeAdded(id);

		return node;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.graph.Graph#addEdge(java.lang.String,
	 * org.graphstream.graph.Node, org.graphstream.graph.Node, boolean)
	 */
	@Override
	public Edge addEdge(final String id, final Node from, final Node to, final boolean directed) {
		return this.addEdge(id, (AbstractNode) from, from.getId(), (AbstractNode) to, to.getId(), directed);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.graph.Graph#removeNode(org.graphstream.graph.Node)
	 */
	@Override
	public Node removeNode(final Node node) {
		if (node == null) {
			return null;
		}

		this.removeNode((AbstractNode) node, true);
		return node;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.graph.Graph#removeEdge(org.graphstream.graph.Edge)
	 */
	@Override
	public Edge removeEdge(final Edge edge) {
		if (edge == null) {
			return null;
		}

		this.removeEdge((AbstractEdge) edge, true, true, true);
		return edge;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.graph.Graph#removeEdge(org.graphstream.graph.Node,
	 * org.graphstream.graph.Node)
	 */
	@Override
	public Edge removeEdge(final Node node1, final Node node2) {
		Edge edge = node1.getEdgeToward(node2);

		if (edge == null) {
			if (this.strictChecking) {
				throw new ElementNotFoundException("There is no edge from \"%s\" to \"%s\". Cannot remove it.", node1.getId(), node2.getId());
			}
			return null;
		}

		return this.removeEdge(edge);
	}

	// *** Sinks, sources etc. ***

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.graph.Graph#attributeSinks()
	 */
	@Override
	public Iterable<AttributeSink> attributeSinks() {
		return this.listeners.attributeSinks();
	}

	/*
	 * *(non-Javadoc)
	 *
	 * @see org.graphstream.graph.Graph#elementSinks()
	 */
	@Override
	public Iterable<ElementSink> elementSinks() {
		return this.listeners.elementSinks();
	}

	/*
	 * *(non-Javadoc)
	 *
	 * @see org.graphstream.stream.Source#addAttributeSink(org.graphstream.stream
	 * .AttributeSink)
	 */
	@Override
	public void addAttributeSink(final AttributeSink sink) {
		this.listeners.addAttributeSink(sink);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.Source#addElementSink(org.graphstream.stream.
	 * ElementSink)
	 */
	@Override
	public void addElementSink(final ElementSink sink) {
		this.listeners.addElementSink(sink);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.Source#addSink(org.graphstream.stream.Sink)
	 */
	@Override
	public void addSink(final Sink sink) {
		this.listeners.addSink(sink);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.Source#clearAttributeSinks()
	 */
	@Override
	public void clearAttributeSinks() {
		this.listeners.clearAttributeSinks();
	}

	/*
	 * *(non-Javadoc)
	 *
	 * @see org.graphstream.stream.Source#clearElementSinks()
	 */
	@Override
	public void clearElementSinks() {
		this.listeners.clearElementSinks();
	}

	/*
	 * *(non-Javadoc)
	 *
	 * @see org.graphstream.stream.Source#clearSinks()
	 */
	@Override
	public void clearSinks() {
		this.listeners.clearSinks();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.Source#removeAttributeSink(org.graphstream.stream
	 * .AttributeSink)
	 */
	@Override
	public void removeAttributeSink(final AttributeSink sink) {
		this.listeners.removeAttributeSink(sink);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.Source#removeElementSink(org.graphstream.stream
	 * .ElementSink)
	 */
	@Override
	public void removeElementSink(final ElementSink sink) {
		this.listeners.removeElementSink(sink);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.Source#removeSink(org.graphstream.stream.Sink)
	 */
	@Override
	public void removeSink(final Sink sink) {
		this.listeners.removeSink(sink);
	}

	@Override
	public void edgeAttributeAdded(final String sourceId, final long timeId, final String edgeId, final String attribute, final Object value) {
		this.listeners.edgeAttributeAdded(sourceId, timeId, edgeId, attribute, value);
	}

	@Override
	public void edgeAttributeChanged(final String sourceId, final long timeId, final String edgeId, final String attribute, final Object oldValue, final Object newValue) {
		this.listeners.edgeAttributeChanged(sourceId, timeId, edgeId, attribute, oldValue, newValue);
	}

	@Override
	public void edgeAttributeRemoved(final String sourceId, final long timeId, final String edgeId, final String attribute) {
		this.listeners.edgeAttributeRemoved(sourceId, timeId, edgeId, attribute);
	}

	@Override
	public void graphAttributeAdded(final String sourceId, final long timeId, final String attribute, final Object value) {
		this.listeners.graphAttributeAdded(sourceId, timeId, attribute, value);
	}

	@Override
	public void graphAttributeChanged(final String sourceId, final long timeId, final String attribute, final Object oldValue, final Object newValue) {
		this.listeners.graphAttributeChanged(sourceId, timeId, attribute, oldValue, newValue);
	}

	@Override
	public void graphAttributeRemoved(final String sourceId, final long timeId, final String attribute) {
		this.listeners.graphAttributeRemoved(sourceId, timeId, attribute);
	}

	@Override
	public void nodeAttributeAdded(final String sourceId, final long timeId, final String nodeId, final String attribute, final Object value) {
		this.listeners.nodeAttributeAdded(sourceId, timeId, nodeId, attribute, value);
	}

	@Override
	public void nodeAttributeChanged(final String sourceId, final long timeId, final String nodeId, final String attribute, final Object oldValue, final Object newValue) {
		this.listeners.nodeAttributeChanged(sourceId, timeId, nodeId, attribute, oldValue, newValue);
	}

	@Override
	public void nodeAttributeRemoved(final String sourceId, final long timeId, final String nodeId, final String attribute) {
		this.listeners.nodeAttributeRemoved(sourceId, timeId, nodeId, attribute);
	}

	@Override
	public void edgeAdded(final String sourceId, final long timeId, final String edgeId, final String fromNodeId, final String toNodeId, final boolean directed) {
		this.listeners.edgeAdded(sourceId, timeId, edgeId, fromNodeId, toNodeId, directed);
	}

	@Override
	public void edgeRemoved(final String sourceId, final long timeId, final String edgeId) {
		this.listeners.edgeRemoved(sourceId, timeId, edgeId);
	}

	@Override
	public void graphCleared(final String sourceId, final long timeId) {
		this.listeners.graphCleared(sourceId, timeId);
	}

	@Override
	public void nodeAdded(final String sourceId, final long timeId, final String nodeId) {
		this.listeners.nodeAdded(sourceId, timeId, nodeId);
	}

	@Override
	public void nodeRemoved(final String sourceId, final long timeId, final String nodeId) {
		this.listeners.nodeRemoved(sourceId, timeId, nodeId);
	}

	@Override
	public void stepBegins(final String sourceId, final long timeId, final double step) {
		this.listeners.stepBegins(sourceId, timeId, step);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.Replayable#getReplayController()
	 */
	@Override
	public Replayable.Controller getReplayController() {
		return new GraphReplayController();
	}

	// *** callbacks maintaining user's data structure

	/**
	 * This method is automatically called when a new node is created. Subclasses
	 * must add the new node to their data structure and to set its index correctly.
	 *
	 * @param node
	 * 		the node to be added
	 */
	protected abstract void addNodeCallback(AbstractNode node);

	/**
	 * This method is automatically called when a new edge is created. Subclasses
	 * must add the new edge to their data structure and to set its index correctly.
	 *
	 * @param edge
	 * 		the edge to be added
	 */
	protected abstract void addEdgeCallback(AbstractEdge edge);

	/**
	 * This method is automatically called when a node is removed. Subclasses must
	 * remove the node from their data structures and to re-index other node(s) so
	 * that node indices remain coherent.
	 *
	 * @param node
	 * 		the node to be removed
	 */
	protected abstract void removeNodeCallback(AbstractNode node);

	/**
	 * This method is automatically called when an edge is removed. Subclasses must
	 * remove the edge from their data structures and re-index other edge(s) so that
	 * edge indices remain coherent.
	 *
	 * @param edge
	 * 		the edge to be removed
	 */
	protected abstract void removeEdgeCallback(AbstractEdge edge);

	/**
	 * This method is automatically called when the graph is cleared. Subclasses
	 * must remove all the nodes and all the edges from their data structures.
	 */
	protected abstract void clearCallback();

	// *** _ methods ***

	// Why do we pass both the ids and the references of the endpoints here?
	// When the caller knows the references it's stupid to call getNode(id)
	// here. If the node does not exist the reference will be null.
	// And if autoCreate is on, we need also the id. Sad but true!
	protected Edge addEdge(final String edgeId, AbstractNode src, final String srcId, AbstractNode dst, final String dstId, final boolean directed) {
		AbstractEdge edge = (AbstractEdge) this.getEdge(edgeId);

		if (edge != null) {
			if (this.strictChecking) {
				throw new IdAlreadyInUseException("id \"" + edgeId + "\" already in use. Cannot create an edge.");
			}
			if ((edge.getSourceNode() == src && edge.getTargetNode() == dst) || (!directed && edge.getTargetNode() == src && edge.getSourceNode() == dst)) {
				return edge;
			}
			return null;
		}

		if (src == null || dst == null) {
			if (this.strictChecking) {
				throw new ElementNotFoundException(String.format("Cannot create edge %s[%s-%s%s]. Node '%s' does not exist.", edgeId, srcId, directed ? ">" : "-", dstId, src == null ? srcId : dstId));
			}
			if (!this.autoCreate) {
				return null;
			}

			if (src == null) {
				src = (AbstractNode) this.addNode(srcId);
			}
			if (dst == null) {
				dst = (AbstractNode) this.addNode(dstId);
			}
		}
		// at this point edgeId is not in use and both src and dst are not null
		edge = this.edgeFactory.newInstance(edgeId, src, dst, directed);
		// see if the endpoints accept the edge
		if (!src.addEdgeCallback(edge)) {
			if (this.strictChecking) {
				throw new EdgeRejectedException("Edge " + edge + " was rejected by node " + src);
			}
			return null;
		}
		// note that for loop edges the callback is called only once
		if (src != dst && !dst.addEdgeCallback(edge)) {
			// the edge is accepted by src but rejected by dst
			// so we have to remove it from src
			src.removeEdgeCallback(edge);
			if (this.strictChecking) {
				throw new EdgeRejectedException("Edge " + edge + " was rejected by node " + dst);
			}
			return null;
		}

		// now we can finally add it
		this.addEdgeCallback(edge);

		this.listeners.sendEdgeAdded(edgeId, srcId, dstId, directed);

		return edge;
	}

	// helper for removeNode_
	private void removeAllEdges(final AbstractNode node) {
		Collection<Edge> toRemove = node.edges().collect(Collectors.toList());
		toRemove.forEach(this::removeEdge);
	}

	// *** Methods for iterators ***

	/**
	 * This method is similar to {@link #removeNode(Node)} but allows to control if
	 * {@link #removeNodeCallback(AbstractNode)} is called or not. It is useful for
	 * iterators supporting {@link java.util.Iterator#remove()} who want to update
	 * the data structures by their owns.
	 *
	 * @param node
	 * 		the node to be removed
	 * @param graphCallback
	 * 		if {@code false}, {@code removeNodeCallback(node)} is not called
	 */
	protected void removeNode(final AbstractNode node, final boolean graphCallback) {
		if (node == null) {
			return;
		}

		this.removeAllEdges(node);
		this.listeners.sendNodeRemoved(node.getId());

		if (graphCallback) {
			this.removeNodeCallback(node);
		}
	}

	/**
	 * This method is similar to {@link #removeEdge(Edge)} but allows to control if
	 * different callbacks are called or not. It is useful for iterators supporting
	 * {@link java.util.Iterator#remove()} who want to update the data structures by
	 * their owns.
	 *
	 * @param edge
	 * 		the edge to be removed
	 * @param graphCallback
	 * 		if {@code false}, {@link #removeEdgeCallback(AbstractEdge)} of the
	 * 		graph is not called
	 * @param sourceCallback
	 * 		if {@code false},
	 * 		{@link AbstractNode#removeEdgeCallback(AbstractEdge)} is not
	 * 		called for the source node of the edge
	 * @param targetCallback
	 * 		if {@code false},
	 * 		{@link AbstractNode#removeEdgeCallback(AbstractEdge)} is not
	 * 		called for the target node of the edge
	 */
	protected void removeEdge(final AbstractEdge edge, final boolean graphCallback, final boolean sourceCallback, final boolean targetCallback) {
		if (edge == null) {
			return;
		}

		AbstractNode src = (AbstractNode) edge.getSourceNode();
		AbstractNode dst = (AbstractNode) edge.getTargetNode();

		this.listeners.sendEdgeRemoved(edge.getId());

		if (sourceCallback) {
			src.removeEdgeCallback(edge);
		}

		if (src != dst && targetCallback) {
			dst.removeEdgeCallback(edge);
		}

		if (graphCallback) {
			this.removeEdgeCallback(edge);
		}
	}

	class GraphReplayController extends SourceBase implements Replayable.Controller {
		GraphReplayController() {
			super(AbstractGraph.this.id + "replay");
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see org.graphstream.stream.Replayable.Controller#replay()
		 */
		@Override
		public void replay() {
			String sourceId = String.format("%s-replay-%x", AbstractGraph.this.id, AbstractGraph.this.replayId++);
			this.replay(sourceId);
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see org.graphstream.stream.Replayable.Controller#replay(java.lang.String)
		 */
		@Override
		public void replay(final String sourceId) {
			AbstractGraph.this.attributeKeys().forEach(key -> this.sendGraphAttributeAdded(sourceId, key, AbstractGraph.this.getAttribute(key)));

			for (int i = 0; i < AbstractGraph.this.getNodeCount(); i++) {
				Node node = AbstractGraph.this.getNode(i);
				String nodeId = node.getId();

				this.sendNodeAdded(sourceId, nodeId);

				node.attributeKeys().forEach(key -> this.sendNodeAttributeAdded(sourceId, nodeId, key, node.getAttribute(key)));
			}

			for (int i = 0; i < AbstractGraph.this.getEdgeCount(); i++) {
				Edge edge = AbstractGraph.this.getEdge(i);
				String edgeId = edge.getId();

				this.sendEdgeAdded(sourceId, edgeId, edge.getNode0().getId(), edge.getNode1().getId(), edge.isDirected());

				edge.attributeKeys().forEach(key -> this.sendEdgeAttributeAdded(sourceId, edgeId, key, edge.getAttribute(key)));
			}
		}
	}
}
