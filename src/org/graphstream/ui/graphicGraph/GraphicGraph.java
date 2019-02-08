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
 * @since 2009-07-05
 *
 * @author Guilhelm Savin <guilhelm.savin@graphstream-project.org>
 * @author Antoine Dutot <antoine.dutot@graphstream-project.org>
 * @author Yoann Pigné <yoann.pigne@graphstream-project.org>
 * @author Stefan Balev <stefan.balev@graphstream-project.org>
 * @author Richard O. Legendi <richard.legendi@gmail.com>
 * @author Alex Bowen <bowen.a@gmail.com>
 * @author Ben Wiederhake <BenWiederhake.GitHub@gmx>
 * @author kitskub <kitskub@gmail.com>
 * @author Hicham Brahimi <hicham.brahimi@graphstream-project.org>
 */
package org.graphstream.ui.graphicGraph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.graphstream.graph.Edge;
import org.graphstream.graph.EdgeFactory;
import org.graphstream.graph.Element;
import org.graphstream.graph.ElementNotFoundException;
import org.graphstream.graph.Graph;
import org.graphstream.graph.IdAlreadyInUseException;
import org.graphstream.graph.Node;
import org.graphstream.graph.NodeFactory;
import org.graphstream.graph.implementations.AbstractElement;
import org.graphstream.stream.AttributeSink;
import org.graphstream.stream.ElementSink;
import org.graphstream.stream.Sink;
import org.graphstream.stream.SourceBase.ElementType;
import org.graphstream.stream.file.FileSink;
import org.graphstream.stream.file.FileSource;
import org.graphstream.ui.geom.Point3;
import org.graphstream.ui.graphicGraph.stylesheet.Style;
import org.graphstream.ui.graphicGraph.stylesheet.StyleConstants;
import org.graphstream.ui.graphicGraph.stylesheet.StyleConstants.Units;
import org.graphstream.ui.graphicGraph.stylesheet.StyleSheet;
import org.graphstream.ui.graphicGraph.stylesheet.Value;
import org.graphstream.ui.graphicGraph.stylesheet.Values;
import org.graphstream.ui.view.Viewer;
import org.graphstream.util.GraphListeners;

/**
 * Graph representation used in display classes.
 * <p>
 * <p>
 * Warning: This class is NOT a general graph class, and it should NOT be used
 * as it. This class is particularly dedicated to fast drawing of the graph and
 * is internally arranged to be fast for this task only. It implements graph
 * solely to be easily susceptible to be used as a sink and source for graph
 * events. Some of the common methods of the Graph interface are not functional
 * and will throw an exception if used (as documented in their respective
 * JavaDoc).
 * </p>
 * <p>
 * <p>
 * The purpose of the graphic graph is to represent a graph with some often used
 * graphic attributes (like position, label, etc.) stored as fields in the nodes
 * and edges and most of the style stored in styles pertaining to a style sheet
 * that tries to imitate the way CSS works. For example, the GraphicNode class
 * defines a label, a position (x,y,z) and a style that is taken from the style
 * sheet.
 * </p>
 * <p>
 * <p>
 * The style sheet is uploaded on the graph using an attribute correspondingly
 * named "stylesheet" or "ui.stylesheet" (the second one is better). It can be a
 * string that contains the whole style sheet, or an URL of the form :
 * </p>
 * <p>
 *
 * <pre>
 * url(name)
 * </pre>
 * <p>
 * <p>
 * The graphic graph does not completely duplicate a graph, it only store things
 * that are useful for drawing it. Although it implements "Graph", some methods
 * are not implemented and will throw a runtime exception. These methods are
 * mostly utility methods like write(), read(), and naturally display().
 * </p>
 * <p>
 * <p>
 * The graphic graph has the ability to store attributes like any other graph
 * element, however the attributes stored by the graphic graph are restricted.
 * There is a filter on the attribute adding methods that let pass only:
 * <ul>
 * <li>All attributes starting with "ui.".</li>
 * <li>The "x", "y", "z", "xy" and "xyz" attributes.</li>
 * <li>The "stylesheet" attribute (although "ui.stylesheet" is preferred).</li>
 * <li>The "label" attribute.</li>
 * </ul>
 * All other attributes are filtered and not stored. The result is that if the
 * graphic graph is used as an input (a source of graph events) some attributes
 * will not pass through the filter.
 * </p>
 * <p>
 * <p>
 * The implementation of this graph relies on the StyleGroupSet class and this
 * is indeed its way to store its elements (grouped by style and Z level).
 * </p>
 * <p>
 * <p>
 * In addition to this, it provides, as all graphs do, the relational
 * information for edges.
 * </p>
 * <p>
 * TODO : this graph cannot handle modification inside event listener methods !!
 */
public class GraphicGraph extends AbstractElement implements Graph, StyleGroupListener {

	/**
	 * class level logger
	 */
	private static final Logger logger = Logger.getLogger(GraphicGraph.class.getSimpleName());

	/**
	 * Set of styles.
	 */
	protected StyleSheet styleSheet;

	/**
	 * Associate graphic elements with styles.
	 */
	protected StyleGroupSet styleGroups;

	/**
	 * Connectivity. The way nodes are connected one with another via edges. The map
	 * is sorted by node. For each node an array of edges lists the connectivity.
	 */
	protected final Map<GraphicNode, List<GraphicEdge>> connectivity;

	/**
	 * The style of this graph. This is a shortcut to avoid searching it in the
	 * style sheet.
	 */
	public StyleGroup style;

	/**
	 * Memorize the step events.
	 */
	public double step = 0;

	/**
	 * Set to true each time the graph was modified internally and a redraw is
	 * needed.
	 */
	public boolean graphChanged;

	/**
	 * Set to true each time a sprite or node moved.
	 */
	protected boolean boundsChanged = true;

	/**
	 * Maximum position of a node or sprite in the graphic graph. Computed by
	 * {@link #computeBounds()}.
	 */
	protected Point3 hi = new Point3();

	/**
	 * Minimum position of a node or sprite in the graphic graph. Computed by
	 * {@link #computeBounds()}.
	 */
	protected Point3 lo = new Point3();

	/**
	 * Set of listeners of this graph.
	 */
	protected GraphListeners listeners;

	/**
	 * Time of other known sources.
	 */
	// protected SinkTime sinkTime = new SinkTime();

	/**
	 * Report back the XYZ events on nodes and sprites? If enabled, each change in
	 * the position of nodes and sprites will be sent to potential listeners of the
	 * graph. By default this is disabled as long there are no listeners.
	 */
	protected boolean feedbackXYZ = true;

	private Lock lock = new ReentrantLock(true);

	/**
	 * New empty graphic graph.
	 * <p>
	 * A default style sheet is created, it then can be "cascaded" with other style
	 * sheets.
	 */
	public GraphicGraph(final String id) {
		super(id);

		this.listeners = new GraphListeners(this);
		this.styleSheet = new StyleSheet();
		this.styleGroups = new StyleGroupSet(this.styleSheet);
		this.connectivity = new HashMap<GraphicNode, List<GraphicEdge>>();

		this.styleGroups.addListener(this);
		this.styleGroups.addElement(this); // Add style to this graph.

		this.style = this.styleGroups.getStyleFor(this);
	}

	// Access

	/**
	 * True if the graph was edited or changed in any way since the last reset of
	 * the "changed" flag.
	 *
	 * @return true if the graph was changed.
	 */
	public boolean graphChangedFlag() {
		this.lock.lock();
		try {
			return this.graphChanged;
		} finally {
			this.lock.unlock();
		}
	}

	/**
	 * Reset the "changed" flag.
	 *
	 * @see #graphChangedFlag()
	 */
	public void resetGraphChangedFlag() {
		this.lock.lock();
		try {
			this.graphChanged = false;
		} finally {
			this.lock.unlock();
		}
	}

	/**
	 * The style sheet. This style sheet is the result of the "cascade" or
	 * accumulation of styles added via attributes of the graph.
	 *
	 * @return A style sheet.
	 */
	public StyleSheet getStyleSheet() {
		return this.styleSheet;
	}

	/**
	 * The graph style group.
	 *
	 * @return A style group.
	 */
	public StyleGroup getStyle() {
		return this.style;
	}

	/**
	 * The complete set of style groups.
	 *
	 * @return The style groups.
	 */
	public StyleGroupSet getStyleGroups() {
		return this.styleGroups;
	}

	@Override
	public String toString() {
		return String.format("[%s %d nodes %d edges]", this.getId(), this.getNodeCount(), this.getEdgeCount());
	}

	@Override
	public double getStep() {
		return this.step;
	}

	/**
	 * The maximum position of a node or sprite. Notice that this is updated only
	 * each time the {@link #computeBounds()} method is called.
	 *
	 * @return The maximum node or sprite position.
	 */
	public Point3 getMaxPos() {
		return this.hi;
	}

	/**
	 * The minimum position of a node or sprite. Notice that this is updated only
	 * each time the {@link #computeBounds()} method is called.
	 *
	 * @return The minimum node or sprite position.
	 */
	public Point3 getMinPos() {
		return this.lo;
	}

	/**
	 * Does the graphic graph publish via attribute changes the XYZ changes on nodes
	 * and sprites when changed ?. This is disabled by default, and enabled as soon
	 * as there is at least one listener.
	 */
	public boolean feedbackXYZ() {
		return this.feedbackXYZ;
	}

	// Command

	/**
	 * Should the graphic graph publish via attribute changes the XYZ changes on
	 * nodes and sprites when changed ?.
	 */
	public void feedbackXYZ(final boolean on) {
		this.feedbackXYZ = on;
	}

	/**
	 * Compute the overall bounds of the graphic graph according to the nodes and
	 * sprites positions. We can only compute the graph bounds from the nodes and
	 * sprites centres since the node and graph bounds may in certain circumstances
	 * be computed according to the graph bounds. The bounds are stored in the graph
	 * metrics.
	 * <p>
	 * This operation will process each node and sprite and is therefore costly.
	 * However it does this computation again only when a node or sprite moved.
	 * Therefore it can be called several times, if nothing moved in the graph, the
	 * computation will not be redone.
	 *
	 * @see #getMaxPos()
	 * @see #getMinPos()
	 */
	public void computeBounds() {
		if (this.boundsChanged) {
			final AtomicBoolean effectiveChange = new AtomicBoolean(false);

			this.lo.x = this.lo.y = this.lo.z = Double.MAX_VALUE;
			this.hi.x = this.hi.y = this.hi.z = -Double.MAX_VALUE;

			this.nodes().forEach(n -> {
				GraphicNode node = (GraphicNode) n;

				if (!node.hidden && node.positionned) {
					effectiveChange.set(true);

					if (node.x < this.lo.x) {
						this.lo.x = node.x;
					}
					if (node.x > this.hi.x) {
						this.hi.x = node.x;
					}
					if (node.y < this.lo.y) {
						this.lo.y = node.y;
					}
					if (node.y > this.hi.y) {
						this.hi.y = node.y;
					}
					if (node.z < this.lo.z) {
						this.lo.z = node.z;
					}
					if (node.z > this.hi.z) {
						this.hi.z = node.z;
					}
				}
			});

			this.sprites().forEach(sprite -> {
				if (!sprite.isAttached() && sprite.getUnits() == StyleConstants.Units.GU) {
					double x = sprite.getX();
					double y = sprite.getY();
					double z = sprite.getZ();

					if (!sprite.hidden) {
						effectiveChange.set(true);

						if (x < this.lo.x) {
							this.lo.x = x;
						}
						if (x > this.hi.x) {
							this.hi.x = x;
						}
						if (y < this.lo.y) {
							this.lo.y = y;
						}
						if (y > this.hi.y) {
							this.hi.y = y;
						}
						if (z < this.lo.z) {
							this.lo.z = z;
						}
						if (z > this.hi.z) {
							this.hi.z = z;
						}
					}
				}
			});

			if (this.hi.x - this.lo.x < 0.000001) {
				this.hi.x = this.hi.x + 1;
				this.lo.x = this.lo.x - 1;
			}
			if (this.hi.y - this.lo.y < 0.000001) {
				this.hi.y = this.hi.y + 1;
				this.lo.y = this.lo.y - 1;
			}
			if (this.hi.z - this.lo.z < 0.000001) {
				this.hi.z = this.hi.z + 1;
				this.lo.z = this.lo.z - 1;
			}

			//
			// Prevent infinities that can be produced by Double.MAX_VALUE.
			//
			if (effectiveChange.get()) {
				this.boundsChanged = false;
			} else {
				this.lo.x = this.lo.y = this.lo.z = -1;
				this.hi.x = this.hi.y = this.hi.z = 1;
			}
		}
	}

	protected void moveNode(final String id, final double x, final double y, final double z) {
		this.lock.lock();
		try {
			GraphicNode node = (GraphicNode) this.styleGroups.getNode(id);

			if (node != null) {
				node.x = x;
				node.y = y;
				node.z = z;
				node.setAttribute("x", x);
				node.setAttribute("y", y);
				node.setAttribute("z", z);

				this.graphChanged = true;
			}
		} finally {
			this.lock.unlock();
		}
	}

	@Override
	public Node getNode(final String id) {
		this.lock.lock();
		try {
			return this.styleGroups.getNode(id);
		} finally {
			this.lock.unlock();
		}
	}

	@Override
	public Edge getEdge(final String id) {
		this.lock.lock();
		try {
			return this.styleGroups.getEdge(id);
		} finally {
			this.lock.unlock();
		}
	}

	public GraphicSprite getSprite(final String id) {
		this.lock.lock();
		try {
			return this.styleGroups.getSprite(id);
		} finally {
			this.lock.unlock();
		}
	}

	@Override
	protected void attributeChanged(final AttributeChangeEvent event, final String attribute, final Object oldValue, final Object newValue) {

		// One of the most important method. Most of the communication comes
		// from attributes.
		this.lock.lock();
		try {
			if (attribute.equals("ui.repaint")) {
				this.graphChanged = true;
			} else if (attribute.equals("ui.stylesheet") || attribute.equals("stylesheet")) {
				if (event == AttributeChangeEvent.ADD || event == AttributeChangeEvent.CHANGE) {
					if (newValue instanceof String) {
						try {
							this.styleSheet.load((String) newValue);
							this.graphChanged = true;
						} catch (Exception e) {
							logger.log(Level.WARNING, String.format("Error while parsing style sheet for graph '%s'.", this.getId()), e);
						}
					} else {
						logger.warning(String.format("Error with stylesheet specification what to do with '%s'.", newValue));
					}
				} else // Remove the style.
				{
					this.styleSheet.clear();
					this.graphChanged = true;
				}
			} else if (attribute.startsWith("ui.sprite.")) {
				// Defers the sprite handling to the sprite API.
				this.spriteAttribute(event, null, attribute, newValue);
				this.graphChanged = true;
			}

		} finally {
			this.lock.unlock();
		}
		this.listeners.sendAttributeChangedEvent(this.getId(), ElementType.GRAPH, attribute, event, oldValue, newValue);
	}

	/**
	 * Display the node/edge relations.
	 */
	public void printConnectivity() {
		Iterator<GraphicNode> keys = this.connectivity.keySet().iterator();

		System.err.printf("Graphic graph connectivity:%n");

		while (keys.hasNext()) {
			GraphicNode node = keys.next();
			System.err.printf("    [%s] -> ", node.getId());
			Iterable<GraphicEdge> edges = this.connectivity.get(node);
			for (GraphicEdge edge : edges) {
				System.err.printf(" (%s %d)", edge.getId(), edge.getMultiIndex());
			}
			System.err.printf("%n");
		}
	}

	// Style group listener interface

	@Override
	public void elementStyleChanged(final Element element, final StyleGroup oldStyle, final StyleGroup style) {
		this.lock.lock();
		try {
			if (element instanceof GraphicElement) {
				GraphicElement ge = (GraphicElement) element;
				ge.style = style;
				this.graphChanged = true;
			} else if (element instanceof GraphicGraph) {
				GraphicGraph gg = (GraphicGraph) element;
				gg.style = style;
				this.graphChanged = true;
			} else {
				throw new RuntimeException("WTF ?");
			}
		} finally {
			this.lock.unlock();
		}
	}

	public void styleChanged(final StyleGroup style) {

	}

	// Graph interface

	@Override
	public Stream<Node> nodes() {
		return this.styleGroups.nodes();
	}

	@Override
	public Stream<Edge> edges() {
		return this.styleGroups.edges();
	}

	public Stream<GraphicSprite> sprites() {
		return this.styleGroups.sprites();
	}

	@Override
	@SuppressWarnings("unchecked")
	public Iterator<Node> iterator() {
		return (Iterator<Node>) this.styleGroups.getNodeIterator();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.Source#addSink(org.graphstream.stream.Sink)
	 */
	@Override
	public void addSink(final Sink listener) {
		this.listeners.addSink(listener);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.Source#removeSink(org.graphstream.stream.Sink)
	 */
	@Override
	public void removeSink(final Sink listener) {
		this.listeners.removeSink(listener);
	}

	/*
	 * *(non-Javadoc)
	 *
	 * @see org.graphstream.stream.Source#addAttributeSink(org.graphstream.stream
	 * .AttributeSink)
	 */
	@Override
	public void addAttributeSink(final AttributeSink listener) {
		this.listeners.addAttributeSink(listener);
	}

	/*
	 * *(non-Javadoc)
	 *
	 * @see org.graphstream.stream.Source#removeAttributeSink(org.graphstream.stream
	 * .AttributeSink)
	 */
	@Override
	public void removeAttributeSink(final AttributeSink listener) {
		this.listeners.removeAttributeSink(listener);
	}

	/*
	 * *(non-Javadoc)
	 *
	 * @see org.graphstream.stream.Source#addElementSink(org.graphstream.stream.
	 * ElementSink)
	 */
	@Override
	public void addElementSink(final ElementSink listener) {
		this.listeners.addElementSink(listener);
	}

	/*
	 * *(non-Javadoc)
	 *
	 * @see org.graphstream.stream.Source#removeElementSink(org.graphstream.stream
	 * .ElementSink)
	 */
	@Override
	public void removeElementSink(final ElementSink listener) {
		this.listeners.removeElementSink(listener);
	}

	/*
	 * *(non-Javadoc)
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
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.graph.Graph#addEdge(java.lang.String, java.lang.String,
	 * java.lang.String, boolean)
	 */
	@Override
	public Edge addEdge(final String id, final String from, final String to, final boolean directed) throws IdAlreadyInUseException, ElementNotFoundException {
		boolean sendEvent = false;

		this.lock.lock();
		try {
			GraphicEdge edge = (GraphicEdge) this.styleGroups.getEdge(id);

			if (edge == null) {
				GraphicNode n1 = (GraphicNode) this.styleGroups.getNode(from);
				GraphicNode n2 = (GraphicNode) this.styleGroups.getNode(to);

				if (n1 == null) {
					throw new ElementNotFoundException("node \"%s\"", from);
				}

				if (n2 == null) {
					throw new ElementNotFoundException("node \"%s\"", to);
				}

				edge = new GraphicEdge(id, n1, n2, directed, null);// , attributes);

				this.styleGroups.addElement(edge);

				List<GraphicEdge> l1 = this.connectivity.get(n1);
				List<GraphicEdge> l2 = this.connectivity.get(n2);

				if (l1 == null) {
					l1 = new ArrayList<GraphicEdge>();
					this.connectivity.put(n1, l1);
				}

				if (l2 == null) {
					l2 = new ArrayList<GraphicEdge>();
					this.connectivity.put(n2, l2);
				}

				l1.add(edge);
				l2.add(edge);
				edge.countSameEdges(l1);

				this.graphChanged = true;

			}

			return edge;
		} finally {
			this.lock.unlock();
			if (sendEvent) {
				this.listeners.sendEdgeAdded(id, from, to, directed);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.graph.Graph#addNode(java.lang.String)
	 */
	@Override
	public Node addNode(final String id) throws IdAlreadyInUseException {
		boolean sendEvent = false;
		this.lock.lock();
		try {
			GraphicNode node = (GraphicNode) this.styleGroups.getNode(id);

			if (node == null) {
				node = new GraphicNode(this, id, null);// , attributes);

				this.styleGroups.addElement(node);

				this.graphChanged = true;

			}

			return node;
		} finally {
			this.lock.unlock();
			if (sendEvent) {
				this.listeners.sendNodeAdded(id);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.graph.Graph#clear()
	 */
	@Override
	public void clear() {
		this.lock.lock();
		try {
			this.listeners.sendGraphCleared();

			this.clearAttributesWithNoEvent();

			this.connectivity.clear();
			this.styleGroups.clear();
			this.styleSheet.clear();

			this.step = 0;
			this.graphChanged = true;

			this.styleGroups.addElement(this);
			this.style = this.styleGroups.getStyleFor(this);
		} finally {
			this.lock.unlock();
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.graph.Graph#removeEdge(java.lang.String)
	 */
	@Override
	public Edge removeEdge(final String id) throws ElementNotFoundException {
		boolean sendEvent = false;
		this.lock.lock();
		try {
			GraphicEdge edge = (GraphicEdge) this.styleGroups.getEdge(id);

			if (edge != null) {

				if (this.connectivity.get(edge.from) != null) {
					this.connectivity.get(edge.from).remove(edge);
				}
				if (this.connectivity.get(edge.to) != null) {
					this.connectivity.get(edge.to).remove(edge);
				}

				this.styleGroups.removeElement(edge);
				edge.removed();

				this.graphChanged = true;
			}

			return edge;
		} finally {
			this.lock.unlock();
			if (sendEvent) {
				this.listeners.sendEdgeRemoved(id);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.graph.Graph#removeEdge(java.lang.String,
	 * java.lang.String)
	 */
	@Override
	public Edge removeEdge(final String from, final String to) throws ElementNotFoundException {
		this.lock.lock();
		try {
			GraphicNode node0 = (GraphicNode) this.styleGroups.getNode(from);
			GraphicNode node1 = (GraphicNode) this.styleGroups.getNode(to);

			if (node0 != null && node1 != null) {
				Collection<GraphicEdge> edges0 = this.connectivity.get(node0);
				Collection<GraphicEdge> edges1 = this.connectivity.get(node1);

				for (GraphicEdge edge0 : edges0) {
					for (GraphicEdge edge1 : edges1) {
						if (edge0 == edge1) {
							this.removeEdge(edge0.getId());
							return edge0;
						}
					}
				}
			}
		} finally {
			this.lock.unlock();
		}

		return null;
	}

	/*
	 * *(non-Javadoc)
	 *
	 * @see org.graphstream.graph.Graph#removeNode(java.lang.String)
	 */
	@Override
	public Node removeNode(final String id) throws ElementNotFoundException {
		boolean sendEvent = false;
		this.lock.lock();
		try {
			GraphicNode node = (GraphicNode) this.styleGroups.getNode(id);

			if (node != null) {

				if (this.connectivity.get(node) != null) {
					// We must do a copy of the connectivity set for the node
					// since we will be modifying the connectivity as we process
					// edges.
					List<GraphicEdge> l = new ArrayList<GraphicEdge>(this.connectivity.get(node));

					for (GraphicEdge edge : l) {
						this.removeEdge(edge.getId());
					}

					this.connectivity.remove(node);
				}

				this.styleGroups.removeElement(node);
				node.removed();

				this.graphChanged = true;
			}

			return node;
		} finally {
			this.lock.unlock();
			if (sendEvent) {
				this.listeners.sendNodeRemoved(id);
			}
		}
	}

	@Override
	public Viewer display() {
		throw new RuntimeException("GraphicGraph is used by display() and cannot recursively define display()");
	}

	@Override
	public Viewer display(final boolean autoLayout) {
		throw new RuntimeException("GraphicGraph is used by display() and cannot recursively define display()");
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.graph.Graph#stepBegins(double)
	 */
	@Override
	public void stepBegins(final double step) {
		this.listeners.sendStepBegins(step);
		this.step = step;
	}

	@Override
	public EdgeFactory<? extends Edge> edgeFactory() {
		throw new RuntimeException("GraphicGraph does not support EdgeFactory");
	}

	@Override
	public int getEdgeCount() {
		this.lock.lock();
		try {
			return this.styleGroups.getEdgeCount();
		} finally {
			this.lock.unlock();
		}
	}

	@Override
	public int getNodeCount() {
		this.lock.lock();
		try {
			return this.styleGroups.getNodeCount();
		} finally {
			this.lock.unlock();
		}
	}

	public int getSpriteCount() {
		return this.styleGroups.getSpriteCount();
	}

	@Override
	public boolean isAutoCreationEnabled() {
		return false;
	}

	@Override
	public NodeFactory<? extends Node> nodeFactory() {
		throw new RuntimeException("GraphicGraph does not support NodeFactory");
	}

	@Override
	public void setAutoCreate(final boolean on) {
		throw new RuntimeException("GraphicGraph does not support auto-creation");
	}

	@Override
	public boolean isStrict() {
		return false;
	}

	@Override
	public void setStrict(final boolean on) {
		throw new RuntimeException("GraphicGraph does not support strict checking");
	}

	@Override
	public void setEdgeFactory(final EdgeFactory<? extends Edge> ef) {
		throw new RuntimeException("you cannot change the edge factory for graphic graphs !");
	}

	@Override
	public void setNodeFactory(final NodeFactory<? extends Node> nf) {
		throw new RuntimeException("you cannot change the node factory for graphic graphs !");
	}

	@Override
	public void read(final String filename) throws IOException {
		throw new RuntimeException("GraphicGraph does not support I/O");
	}

	@Override
	public void read(final FileSource input, final String filename) throws IOException {
		throw new RuntimeException("GraphicGraph does not support I/O");
	}

	@Override
	public void write(final FileSink output, final String filename) throws IOException {
		throw new RuntimeException("GraphicGraph does not support I/O");
	}

	@Override
	public void write(final String filename) throws IOException {
		throw new RuntimeException("GraphicGraph does not support I/O");
	}

	// Output interface

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.graphstream.stream.AttributeSink#edgeAttributeAdded(java.lang.String,
	 * long, java.lang.String, java.lang.String, java.lang.Object)
	 */
	@Override
	public void edgeAttributeAdded(final String sourceId, final long timeId, final String edgeId, final String attribute, final Object value) {
		this.listeners.edgeAttributeAdded(sourceId, timeId, edgeId, attribute, value);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.graphstream.stream.AttributeSink#edgeAttributeChanged(java.lang.String ,
	 * long, java.lang.String, java.lang.String, java.lang.Object, java.lang.Object)
	 */
	@Override
	public void edgeAttributeChanged(final String sourceId, final long timeId, final String edgeId, final String attribute, final Object oldValue, final Object newValue) {
		this.listeners.edgeAttributeChanged(sourceId, timeId, edgeId, attribute, oldValue, newValue);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.graphstream.stream.AttributeSink#edgeAttributeRemoved(java.lang.String ,
	 * long, java.lang.String, java.lang.String)
	 */
	@Override
	public void edgeAttributeRemoved(final String sourceId, final long timeId, final String edgeId, final String attribute) {
		this.listeners.edgeAttributeRemoved(sourceId, timeId, edgeId, attribute);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.graphstream.stream.AttributeSink#graphAttributeAdded(java.lang.String ,
	 * long, java.lang.String, java.lang.Object)
	 */
	@Override
	public void graphAttributeAdded(final String sourceId, final long timeId, final String attribute, final Object value) {
		this.listeners.graphAttributeAdded(sourceId, timeId, attribute, value);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.AttributeSink#graphAttributeChanged(java.lang.
	 * String, long, java.lang.String, java.lang.Object, java.lang.Object)
	 */
	@Override
	public void graphAttributeChanged(final String sourceId, final long timeId, final String attribute, final Object oldValue, final Object newValue) {
		this.listeners.graphAttributeChanged(sourceId, timeId, attribute, oldValue, newValue);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.AttributeSink#graphAttributeRemoved(java.lang.
	 * String, long, java.lang.String)
	 */
	@Override
	public void graphAttributeRemoved(final String sourceId, final long timeId, final String attribute) {
		this.listeners.graphAttributeRemoved(sourceId, timeId, attribute);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.graphstream.stream.AttributeSink#nodeAttributeAdded(java.lang.String,
	 * long, java.lang.String, java.lang.String, java.lang.Object)
	 */
	@Override
	public void nodeAttributeAdded(final String sourceId, final long timeId, final String nodeId, final String attribute, final Object value) {
		this.listeners.nodeAttributeAdded(sourceId, timeId, nodeId, attribute, value);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.graphstream.stream.AttributeSink#nodeAttributeChanged(java.lang.String ,
	 * long, java.lang.String, java.lang.String, java.lang.Object, java.lang.Object)
	 */
	@Override
	public void nodeAttributeChanged(final String sourceId, final long timeId, final String nodeId, final String attribute, final Object oldValue, final Object newValue) {
		this.listeners.nodeAttributeChanged(sourceId, timeId, nodeId, attribute, oldValue, newValue);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.graphstream.stream.AttributeSink#nodeAttributeRemoved(java.lang.String ,
	 * long, java.lang.String, java.lang.String)
	 */
	@Override
	public void nodeAttributeRemoved(final String sourceId, final long timeId, final String nodeId, final String attribute) {
		this.listeners.nodeAttributeRemoved(sourceId, timeId, nodeId, attribute);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.ElementSink#edgeAdded(java.lang.String, long,
	 * java.lang.String, java.lang.String, java.lang.String, boolean)
	 */
	@Override
	public void edgeAdded(final String sourceId, final long timeId, final String edgeId, final String fromNodeId, final String toNodeId, final boolean directed) {
		this.listeners.edgeAdded(sourceId, timeId, edgeId, fromNodeId, toNodeId, directed);
	}

	/*
	 * *(non-Javadoc)
	 *
	 * @see org.graphstream.stream.ElementSink#edgeRemoved(java.lang.String, long,
	 * java.lang.String)
	 */
	@Override
	public void edgeRemoved(final String sourceId, final long timeId, final String edgeId) {
		this.listeners.edgeRemoved(sourceId, timeId, edgeId);
	}

	/*
	 * *(non-Javadoc)
	 *
	 * @see org.graphstream.stream.ElementSink#graphCleared(java.lang.String, long)
	 */
	@Override
	public void graphCleared(final String sourceId, final long timeId) {
		this.listeners.graphCleared(sourceId, timeId);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.ElementSink#nodeAdded(java.lang.String, long,
	 * java.lang.String)
	 */
	@Override
	public void nodeAdded(final String sourceId, final long timeId, final String nodeId) {
		this.listeners.nodeAdded(sourceId, timeId, nodeId);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.ElementSink#nodeRemoved(java.lang.String, long,
	 * java.lang.String)
	 */
	@Override
	public void nodeRemoved(final String sourceId, final long timeId, final String nodeId) {
		this.listeners.nodeRemoved(sourceId, timeId, nodeId);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.ElementSink#stepBegins(java.lang.String, long,
	 * double)
	 */
	@Override
	public void stepBegins(final String sourceId, final long timeId, final double time) {
		this.listeners.sendStepBegins(sourceId, timeId, time);
		this.stepBegins(time);
	}

	// Sprite interface

	protected void spriteAttribute(final AttributeChangeEvent event, final Element element, final String attribute, final Object value) {
		this.lock.lock();
		try {
			String spriteId = attribute.substring(10); // Remove the "ui.sprite."
			// prefix.
			int pos = spriteId.indexOf('.'); // Look if there is something after the
			// sprite id.
			String attr = null;

			if (pos > 0) {
				attr = spriteId.substring(pos + 1); // Cut the sprite id.
				spriteId = spriteId.substring(0, pos); // Cut the sprite attribute
				// name.
			}

			if (attr == null) {
				this.addOrChangeSprite(event, element, spriteId, value);
			} else {
				if (event == AttributeChangeEvent.ADD) {
					GraphicSprite sprite = this.styleGroups.getSprite(spriteId);

					// We add the sprite, in case of a replay, some attributes of
					// the sprite can be
					// changed before the sprite is declared.
					if (sprite == null) {
						this.addOrChangeSprite(AttributeChangeEvent.ADD, element, spriteId, null);
						sprite = this.styleGroups.getSprite(spriteId);
					}

					sprite.setAttribute(attr, value);
				} else if (event == AttributeChangeEvent.CHANGE) {
					GraphicSprite sprite = this.styleGroups.getSprite(spriteId);

					if (sprite == null) {
						this.addOrChangeSprite(AttributeChangeEvent.ADD, element, spriteId, null);
						sprite = this.styleGroups.getSprite(spriteId);
					}

					sprite.setAttribute(attr, value);
				} else if (event == AttributeChangeEvent.REMOVE) {
					GraphicSprite sprite = this.styleGroups.getSprite(spriteId);

					if (sprite != null) {
						sprite.removeAttribute(attr);
					}
				}
			}
		} finally {
			this.lock.unlock();
		}
	}

	protected void addOrChangeSprite(final AttributeChangeEvent event, final Element element, final String spriteId, final Object value) {
		this.lock.lock();
		try {
			if (event == AttributeChangeEvent.ADD || event == AttributeChangeEvent.CHANGE) {
				GraphicSprite sprite = this.styleGroups.getSprite(spriteId);

				if (sprite == null) {
					sprite = this.addSprite_(spriteId);
				}

				if (element != null) {
					if (element instanceof GraphicNode) {
						sprite.attachToNode((GraphicNode) element);
					} else if (element instanceof GraphicEdge) {
						sprite.attachToEdge((GraphicEdge) element);
					}
				}

				if (value != null && (!(value instanceof Boolean))) {
					this.positionSprite(sprite, value);
				}
			} else if (event == AttributeChangeEvent.REMOVE) {
				if (element == null) {
					if (this.styleGroups.getSprite(spriteId) != null) {
						this.removeSprite_(spriteId);
					}
				} else {
					GraphicSprite sprite = this.styleGroups.getSprite(spriteId);

					if (sprite != null) {
						sprite.detach();
					}
				}
			}
		} finally {
			this.lock.unlock();
		}
	}

	public GraphicSprite addSprite(final String id) {
		this.lock.lock();
		try {
			String prefix = String.format("ui.sprite.%s", id);
			logger.info(String.format("Added sprite %s.", id));
			this.setAttribute(prefix, 0, 0, 0);
			GraphicSprite s = this.styleGroups.getSprite(id);
			assert (s != null);
			return s;
		} finally {
			this.lock.unlock();
		}
	}

	protected GraphicSprite addSprite_(final String id) {
		this.lock.lock();
		try {
			GraphicSprite s = new GraphicSprite(id, this);
			this.styleGroups.addElement(s);
			this.graphChanged = true;
			return s;
		} finally {
			this.lock.unlock();
		}
	}

	public void removeSprite(final String id) {
		this.lock.lock();
		try {
			String prefix = String.format("ui.sprite.%s", id);
			this.removeAttribute(prefix);
		} finally {
			this.lock.unlock();
		}
	}

	protected GraphicSprite removeSprite_(final String id) {
		this.lock.lock();
		try {
			GraphicSprite sprite = this.styleGroups.getSprite(id);

			if (sprite != null) {
				sprite.detach();
				this.styleGroups.removeElement(sprite);
				sprite.removed();

				this.graphChanged = true;
			}

			return sprite;
		} finally {
			this.lock.unlock();
		}
	}

	protected void positionSprite(final GraphicSprite sprite, final Object value) {
		this.lock.lock();
		try {
			if (value instanceof Object[]) {
				Object[] values = (Object[]) value;

				if (values.length == 4) {
					if (values[0] instanceof Number && values[1] instanceof Number && values[2] instanceof Number && values[3] instanceof Style.Units) {
						sprite.setPosition(((Number) values[0]).doubleValue(), ((Number) values[1]).doubleValue(), ((Number) values[2]).doubleValue(), (Style.Units) values[3]);
					} else {
						logger.warning("Cannot parse values[4] for sprite position.");
					}
				} else if (values.length == 3) {
					if (values[0] instanceof Number && values[1] instanceof Number && values[2] instanceof Number) {
						sprite.setPosition(((Number) values[0]).doubleValue(), ((Number) values[1]).doubleValue(), ((Number) values[2]).doubleValue(), Units.GU);
					} else {
						logger.warning("Cannot parse values[3] for sprite position.");
					}
				} else if (values.length == 1) {
					if (values[0] instanceof Number) {
						sprite.setPosition(((Number) values[0]).doubleValue());
					} else {
						logger.warning("Sprite position percent is not a number.");
					}
				} else {
					logger.warning(String.format("Cannot transform value '%s' (length=%d) into a position%n", Arrays.toString(values), values.length));
				}
			} else if (value instanceof Number) {
				sprite.setPosition(((Number) value).doubleValue());
			} else if (value instanceof Value) {
				sprite.setPosition(((Value) value).value);
			} else if (value instanceof Values) {
				sprite.setPosition((Values) value);
			} else if (value == null) {
				throw new RuntimeException("What do you expect with a null value ?");
			} else {
				logger.warning(String.format("Cannot place sprite with posiiton '%s' (instance of %s)%n", value, value.getClass().getName()));
			}
		} finally {
			this.lock.unlock();
		}
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
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.Source#clearSinks()
	 */
	@Override
	public void clearSinks() {
		this.listeners.clearSinks();
	}

	// stubs for the new methods

	@Override
	public Edge addEdge(final String id, final int index1, final int index2) {
		throw new RuntimeException("not implemented !");
	}

	@Override
	public Edge addEdge(final String id, final int fromIndex, final int toIndex, final boolean directed) {
		throw new RuntimeException("not implemented !");
	}

	@Override
	public Edge addEdge(final String id, final Node node1, final Node node2) {
		throw new RuntimeException("not implemented !");
	}

	@Override
	public Edge addEdge(final String id, final Node from, final Node to, final boolean directed) {
		throw new RuntimeException("not implemented !");
	}

	@Override
	public Edge getEdge(final int index) throws IndexOutOfBoundsException {
		throw new RuntimeException("not implemented !");
	}

	@Override
	public Node getNode(final int index) throws IndexOutOfBoundsException {
		throw new RuntimeException("not implemented !");
	}

	@Override
	public Edge removeEdge(final int index) {
		throw new RuntimeException("not implemented !");
	}

	@Override
	public Edge removeEdge(final int fromIndex, final int toIndex) {
		throw new RuntimeException("not implemented !");
	}

	@Override
	public Edge removeEdge(final Node node1, final Node node2) {
		throw new RuntimeException("not implemented !");
	}

	@Override
	public Edge removeEdge(final Edge edge) {
		throw new RuntimeException("not implemented !");
	}

	@Override
	public Node removeNode(final int index) {
		throw new RuntimeException("not implemented !");
	}

	@Override
	public Node removeNode(final Node node) {
		throw new RuntimeException("not implemented !");
	}

	/**
	 * Replay all the elements of the graph and all attributes as new events to all
	 * connected sinks.
	 * <p>
	 * Be very careful with this method, it introduces new events in the event
	 * stream and some sinks may therefore receive them twice !! Graph replay is
	 * always dangerous !
	 */
	public void replay() {
		// Replay all graph attributes.

		this.attributeKeys().forEach(key -> {
			this.listeners.sendGraphAttributeAdded(this.id, key, this.getAttribute(key));
		});

		// Replay all nodes and their attributes.

		this.nodes().forEach(node -> {
			this.listeners.sendNodeAdded(this.id, node.getId());

			node.attributeKeys().forEach(key -> {
				this.listeners.sendNodeAttributeAdded(this.id, node.getId(), key, node.getAttribute(key));
			});
		});

		// Replay all edges and their attributes.

		this.edges().forEach(edge -> {
			this.listeners.sendEdgeAdded(this.id, edge.getId(), edge.getSourceNode().getId(), edge.getTargetNode().getId(), edge.isDirected());

			edge.attributeKeys().forEach(key -> {
				this.listeners.sendEdgeAttributeAdded(this.id, edge.getId(), key, edge.getAttribute(key));
			});
		});
	}
}