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
 * @since 2009-12-22
 *
 * @author Alex Bowen <bowen.a@gmail.com>
 * @author Guilhelm Savin <guilhelm.savin@graphstream-project.org>
 * @author Dave1704 <davnie@mail.uni-paderborn.de>
 * @author Hicham Brahimi <hicham.brahimi@graphstream-project.org>
 * @author Yoann Pigné <yoann.pigne@graphstream-project.org>
 */
package org.graphstream.ui.view;

import java.util.HashSet;

import org.graphstream.stream.ProxyPipe;
import org.graphstream.stream.SourceBase;

/**
 * Shell around a proxy pipe coming from the viewer allowing to put viewer
 * listeners on a viewer that runs in a distinct thread.
 *
 * <p>
 * This pipe is a probe that you can place in the event loop between the viewer
 * and the graph. It will transmit all events coming from the viewer to the
 * graph (or any sink you connect to it). But in addition it will monitor
 * standard attribute changes to redistribute them to specify "viewer
 * listeners".
 * </p>
 *
 * <p>
 * As any proxy pipe, a viewer pipe must be "pumped" to receive events coming
 * from other threads.
 * </p>
 */
public class ViewerPipe extends SourceBase implements ProxyPipe {
	// Attribute

	private String id;

	/**
	 * The incoming event stream.
	 */
	protected ProxyPipe pipeIn;

	/**
	 * Listeners on the viewer specific events.
	 */
	protected HashSet<ViewerListener> viewerListeners = new HashSet<ViewerListener>();

	// Construction

	/**
	 * A shell around a pipe coming from a viewer in another thread.
	 */
	public ViewerPipe(final String id, final ProxyPipe pipeIn) {
		this.id = id;
		this.pipeIn = pipeIn;
		pipeIn.addSink(this);

	}

	// Access

	public String getId() {
		return this.id;
	}

	// Commands

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.ProxyPipe#pump()
	 */
	@Override
	public void pump() {
		this.pipeIn.pump();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.ProxyPipe#blockingPump()
	 */
	@Override
	public void blockingPump() throws InterruptedException {
		this.pipeIn.blockingPump();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.ProxyPipe#blockingPump(long)
	 */
	@Override
	public void blockingPump(final long timeout) throws InterruptedException {
		this.pipeIn.blockingPump(timeout);
	}

	public void addViewerListener(final ViewerListener listener) {
		this.viewerListeners.add(listener);
	}

	public void removeViewerListener(final ViewerListener listener) {
		this.viewerListeners.remove(listener);
	}

	// Sink interface

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.graphstream.stream.AttributeSink#edgeAttributeAdded(java.lang.String,
	 * long, java.lang.String, java.lang.String, java.lang.Object)
	 */
	@Override
	public void edgeAttributeAdded(final String sourceId, final long timeId, final String edgeId, final String attribute, final Object value) {
		this.sendEdgeAttributeAdded(sourceId, timeId, edgeId, attribute, value);
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
		this.sendEdgeAttributeChanged(sourceId, timeId, edgeId, attribute, oldValue, newValue);
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
		this.sendEdgeAttributeRemoved(sourceId, timeId, edgeId, attribute);
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
		this.sendGraphAttributeAdded(sourceId, timeId, attribute, value);

		if (attribute.equals("ui.viewClosed") && value instanceof String) {
			for (ViewerListener listener : this.viewerListeners) {
				listener.viewClosed((String) value);
			}

			this.sendGraphAttributeRemoved(this.id, attribute);
		} else if (attribute.equals("ui.clicked") && value instanceof String) {
			for (ViewerListener listener : this.viewerListeners) {
				listener.buttonPushed((String) value);
			}

			this.sendGraphAttributeRemoved(this.id, attribute);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.AttributeSink#graphAttributeChanged(java.lang.
	 * String, long, java.lang.String, java.lang.Object, java.lang.Object)
	 */
	@Override
	public void graphAttributeChanged(final String sourceId, final long timeId, final String attribute, final Object oldValue, final Object newValue) {
		this.sendGraphAttributeChanged(sourceId, timeId, attribute, oldValue, newValue);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.AttributeSink#graphAttributeRemoved(java.lang.
	 * String, long, java.lang.String)
	 */
	@Override
	public void graphAttributeRemoved(final String sourceId, final long timeId, final String attribute) {
		this.sendGraphAttributeRemoved(sourceId, timeId, attribute);
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
		this.sendNodeAttributeAdded(sourceId, timeId, nodeId, attribute, value);

		if (attribute.equals("ui.clicked")) {
			for (ViewerListener listener : this.viewerListeners) {
				listener.buttonPushed(nodeId);
			}
		}

		if (attribute.equals("ui.mouseOver")) {
			for (ViewerListener listener : this.viewerListeners) {
				listener.mouseOver(nodeId);
			}
		}
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
		this.sendNodeAttributeChanged(sourceId, timeId, nodeId, attribute, oldValue, newValue);
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
		this.sendNodeAttributeRemoved(sourceId, timeId, nodeId, attribute);

		if (attribute.equals("ui.clicked")) {
			for (ViewerListener listener : this.viewerListeners) {
				listener.buttonReleased(nodeId);
			}
		}

		if (attribute.equals("ui.mouseOver")) {
			for (ViewerListener listener : this.viewerListeners) {
				listener.mouseLeft(nodeId);
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
		this.sendEdgeAdded(sourceId, timeId, edgeId, fromNodeId, toNodeId, directed);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.ElementSink#edgeRemoved(java.lang.String, long,
	 * java.lang.String)
	 */
	@Override
	public void edgeRemoved(final String sourceId, final long timeId, final String edgeId) {
		this.sendEdgeRemoved(sourceId, timeId, edgeId);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.ElementSink#graphCleared(java.lang.String, long)
	 */
	@Override
	public void graphCleared(final String sourceId, final long timeId) {
		this.sendGraphCleared(sourceId, timeId);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.ElementSink#nodeAdded(java.lang.String, long,
	 * java.lang.String)
	 */
	@Override
	public void nodeAdded(final String sourceId, final long timeId, final String nodeId) {
		this.sendNodeAdded(sourceId, timeId, nodeId);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.ElementSink#nodeRemoved(java.lang.String, long,
	 * java.lang.String)
	 */
	@Override
	public void nodeRemoved(final String sourceId, final long timeId, final String nodeId) {
		this.sendNodeRemoved(sourceId, timeId, nodeId);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.ElementSink#stepBegins(java.lang.String, long,
	 * double)
	 */
	@Override
	public void stepBegins(final String sourceId, final long timeId, final double step) {
		this.sendStepBegins(sourceId, timeId, step);
	}
}