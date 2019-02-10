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
 * @since 2013-09-20
 *
 * @author Guilhelm Savin <guilhelm.savin@graphstream-project.org>
 * @author Hicham Brahimi <hicham.brahimi@graphstream-project.org>
 */
package org.graphstream.util;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.AbstractElement.AttributeChangeEvent;
import org.graphstream.stream.Pipe;
import org.graphstream.stream.SourceBase;
import org.graphstream.stream.sync.SinkTime;

/**
 * Helper object to handle events producted by a graph.
 *
 */
public class GraphListeners extends SourceBase implements Pipe {

	SinkTime sinkTime;
	boolean passYourWay, passYourWayAE;
	String dnSourceId;
	long dnTimeId;

	Graph g;

	public GraphListeners(final Graph g) {
		super(g.getId());

		this.sinkTime = new SinkTime();
		this.sourceTime.setSinkTime(this.sinkTime);
		this.passYourWay = false;
		this.passYourWayAE = false;
		this.dnSourceId = null;
		this.dnTimeId = Long.MIN_VALUE;
		this.g = g;
	}

	public long newEvent() {
		return this.sourceTime.newEvent();
	}

	public void sendAttributeChangedEvent(final String eltId, final ElementType eltType, final String attribute, final AttributeChangeEvent event, final Object oldValue, final Object newValue) {
		//
		// Attributes with name beginnig with a dot are hidden.
		//
		if (this.passYourWay || attribute.charAt(0) == '.') {
			return;
		}

		this.sendAttributeChangedEvent(this.sourceId, this.newEvent(), eltId, eltType, attribute, event, oldValue, newValue);
	}

	public void sendNodeAdded(final String nodeId) {
		// if (passYourWay)
		// return;

		this.sendNodeAdded(this.sourceId, this.newEvent(), nodeId);
	}

	public void sendNodeRemoved(final String nodeId) {
		if (this.dnSourceId != null) {
			this.sendNodeRemoved(this.dnSourceId, this.dnTimeId, nodeId);
		} else {
			this.sendNodeRemoved(this.sourceId, this.newEvent(), nodeId);
		}
	}

	public void sendEdgeAdded(final String edgeId, final String source, final String target, final boolean directed) {
		if (this.passYourWayAE) {
			return;
		}

		this.sendEdgeAdded(this.sourceId, this.newEvent(), edgeId, source, target, directed);
	}

	public void sendEdgeRemoved(final String edgeId) {
		if (this.passYourWay) {
			return;
		}

		this.sendEdgeRemoved(this.sourceId, this.newEvent(), edgeId);
	}

	public void sendGraphCleared() {
		if (this.passYourWay) {
			return;
		}

		this.sendGraphCleared(this.sourceId, this.newEvent());
	}

	public void sendStepBegins(final double step) {
		if (this.passYourWay) {
			return;
		}

		this.sendStepBegins(this.sourceId, this.newEvent(), step);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.AttributeSink#edgeAttributeAdded(java.lang
	 * .String, long, java.lang.String, java.lang.String, java.lang.Object)
	 */
	@Override
	public void edgeAttributeAdded(final String sourceId, final long timeId, final String edgeId, final String attribute, final Object value) {
		if (this.sinkTime.isNewEvent(sourceId, timeId)) {
			Edge edge = this.g.getEdge(edgeId);
			if (edge != null) {
				this.passYourWay = true;

				try {
					edge.setAttribute(attribute, value);
				} finally {
					this.passYourWay = false;
				}

				this.sendEdgeAttributeAdded(sourceId, timeId, edgeId, attribute, value);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.AttributeSink#edgeAttributeChanged(java.lang
	 * .String, long, java.lang.String, java.lang.String, java.lang.Object,
	 * java.lang.Object)
	 */
	@Override
	public void edgeAttributeChanged(final String sourceId, final long timeId, final String edgeId, final String attribute, Object oldValue, final Object newValue) {
		if (this.sinkTime.isNewEvent(sourceId, timeId)) {
			Edge edge = this.g.getEdge(edgeId);
			if (edge != null) {
				this.passYourWay = true;

				if (oldValue == null) {
					oldValue = edge.getAttribute(attribute);
				}

				try {
					edge.setAttribute(attribute, newValue);
				} finally {
					this.passYourWay = false;
				}

				this.sendEdgeAttributeChanged(sourceId, timeId, edgeId, attribute, oldValue, newValue);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.AttributeSink#edgeAttributeRemoved(java.lang
	 * .String, long, java.lang.String, java.lang.String)
	 */
	@Override
	public void edgeAttributeRemoved(final String sourceId, final long timeId, final String edgeId, final String attribute) {
		if (this.sinkTime.isNewEvent(sourceId, timeId)) {
			Edge edge = this.g.getEdge(edgeId);
			if (edge != null) {
				this.sendEdgeAttributeRemoved(sourceId, timeId, edgeId, attribute);
				this.passYourWay = true;

				try {
					edge.removeAttribute(attribute);
				} finally {
					this.passYourWay = false;
				}

			}
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.AttributeSink#graphAttributeAdded(java.lang
	 * .String, long, java.lang.String, java.lang.Object)
	 */
	@Override
	public void graphAttributeAdded(final String sourceId, final long timeId, final String attribute, final Object value) {
		if (this.sinkTime.isNewEvent(sourceId, timeId)) {
			this.passYourWay = true;

			try {
				this.g.setAttribute(attribute, value);
			} finally {
				this.passYourWay = false;
			}

			this.sendGraphAttributeAdded(sourceId, timeId, attribute, value);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.AttributeSink#graphAttributeChanged(java.lang
	 * .String, long, java.lang.String, java.lang.Object, java.lang.Object)
	 */
	@Override
	public void graphAttributeChanged(final String sourceId, final long timeId, final String attribute, Object oldValue, final Object newValue) {
		if (this.sinkTime.isNewEvent(sourceId, timeId)) {
			this.passYourWay = true;

			if (oldValue == null) {
				oldValue = this.g.getAttribute(attribute);
			}

			try {
				this.g.setAttribute(attribute, newValue);
			} finally {
				this.passYourWay = false;
			}

			this.sendGraphAttributeChanged(sourceId, timeId, attribute, oldValue, newValue);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.AttributeSink#graphAttributeRemoved(java.lang
	 * .String, long, java.lang.String)
	 */
	@Override
	public void graphAttributeRemoved(final String sourceId, final long timeId, final String attribute) {
		if (this.sinkTime.isNewEvent(sourceId, timeId)) {
			this.sendGraphAttributeRemoved(sourceId, timeId, attribute);
			this.passYourWay = true;

			try {
				this.g.removeAttribute(attribute);
			} finally {
				this.passYourWay = false;
			}
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.AttributeSink#nodeAttributeAdded(java.lang
	 * .String, long, java.lang.String, java.lang.String, java.lang.Object)
	 */
	@Override
	public void nodeAttributeAdded(final String sourceId, final long timeId, final String nodeId, final String attribute, final Object value) {
		if (this.sinkTime.isNewEvent(sourceId, timeId)) {
			Node node = this.g.getNode(nodeId);
			if (node != null) {
				this.passYourWay = true;

				try {
					node.setAttribute(attribute, value);
				} finally {
					this.passYourWay = false;
				}

				this.sendNodeAttributeAdded(sourceId, timeId, nodeId, attribute, value);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.AttributeSink#nodeAttributeChanged(java.lang
	 * .String, long, java.lang.String, java.lang.String, java.lang.Object,
	 * java.lang.Object)
	 */
	@Override
	public void nodeAttributeChanged(final String sourceId, final long timeId, final String nodeId, final String attribute, Object oldValue, final Object newValue) {
		if (this.sinkTime.isNewEvent(sourceId, timeId)) {
			Node node = this.g.getNode(nodeId);
			if (node != null) {
				this.passYourWay = true;

				if (oldValue == null) {
					oldValue = node.getAttribute(attribute);
				}

				try {
					node.setAttribute(attribute, newValue);
				} finally {
					this.passYourWay = false;
				}

				this.sendNodeAttributeChanged(sourceId, timeId, nodeId, attribute, oldValue, newValue);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.AttributeSink#nodeAttributeRemoved(java.lang
	 * .String, long, java.lang.String, java.lang.String)
	 */
	@Override
	public void nodeAttributeRemoved(final String sourceId, final long timeId, final String nodeId, final String attribute) {
		if (this.sinkTime.isNewEvent(sourceId, timeId)) {
			Node node = this.g.getNode(nodeId);
			if (node != null) {
				this.sendNodeAttributeRemoved(sourceId, timeId, nodeId, attribute);
				this.passYourWay = true;

				try {
					node.removeAttribute(attribute);
				} finally {
					this.passYourWay = false;
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.ElementSink#edgeAdded(java.lang.String, long,
	 * java.lang.String, java.lang.String, java.lang.String, boolean)
	 */
	@Override
	public void edgeAdded(final String sourceId, final long timeId, final String edgeId, final String fromNodeId, final String toNodeId, final boolean directed) {
		if (this.sinkTime.isNewEvent(sourceId, timeId)) {
			this.passYourWayAE = true;

			try {
				this.g.addEdge(edgeId, fromNodeId, toNodeId, directed);
			} finally {
				this.passYourWayAE = false;
			}

			this.sendEdgeAdded(sourceId, timeId, edgeId, fromNodeId, toNodeId, directed);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.ElementSink#edgeRemoved(java.lang.String, long,
	 * java.lang.String)
	 */
	@Override
	public void edgeRemoved(final String sourceId, final long timeId, final String edgeId) {
		if (this.sinkTime.isNewEvent(sourceId, timeId)) {
			this.sendEdgeRemoved(sourceId, timeId, edgeId);
			this.passYourWay = true;

			try {
				this.g.removeEdge(edgeId);
			} finally {
				this.passYourWay = false;
			}
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.ElementSink#graphCleared(java.lang.String, long)
	 */
	@Override
	public void graphCleared(final String sourceId, final long timeId) {
		if (this.sinkTime.isNewEvent(sourceId, timeId)) {
			this.sendGraphCleared(sourceId, timeId);
			this.passYourWay = true;

			try {
				this.g.clear();
			} finally {
				this.passYourWay = false;
			}
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.ElementSink#nodeAdded(java.lang.String, long,
	 * java.lang.String)
	 */
	@Override
	public void nodeAdded(final String sourceId, final long timeId, final String nodeId) {
		if (this.sinkTime.isNewEvent(sourceId, timeId)) {
			this.passYourWay = true;

			try {
				this.g.addNode(nodeId);
			} finally {
				this.passYourWay = false;
			}

			this.sendNodeAdded(sourceId, timeId, nodeId);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.ElementSink#nodeRemoved(java.lang.String, long,
	 * java.lang.String)
	 */
	@Override
	public void nodeRemoved(final String sourceId, final long timeId, final String nodeId) {
		if (this.sinkTime.isNewEvent(sourceId, timeId)) {
			// sendNodeRemoved(sourceId, timeId, nodeId);
			this.dnSourceId = sourceId;
			this.dnTimeId = timeId;

			try {
				this.g.removeNode(nodeId);
			} finally {
				this.dnSourceId = null;
				this.dnTimeId = Long.MIN_VALUE;
			}
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.ElementSink#stepBegins(java.lang.String, long,
	 * double)
	 */
	@Override
	public void stepBegins(final String sourceId, final long timeId, final double step) {
		if (this.sinkTime.isNewEvent(sourceId, timeId)) {
			this.passYourWay = true;

			try {
				this.g.stepBegins(step);
			} finally {
				this.passYourWay = false;
			}

			this.sendStepBegins(sourceId, timeId, step);
		}
	}

	@Override
	public String toString() {
		return String.format("GraphListeners of %s.%s", this.g.getClass().getSimpleName(), this.g.getId());
	}
}