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
 * @since 2009-07-04
 *
 * @author Yoann Pigné <yoann.pigne@graphstream-project.org>
 * @author Antoine Dutot <antoine.dutot@graphstream-project.org>
 * @author Guilhelm Savin <guilhelm.savin@graphstream-project.org>
 * @author Stefan Balev <stefan.balev@graphstream-project.org>
 * @author Alex Bowen <bowen.a@gmail.com>
 * @author Hicham Brahimi <hicham.brahimi@graphstream-project.org>
 */
package org.graphstream.stream.thread;

import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import org.graphstream.graph.Graph;
import org.graphstream.stream.ProxyPipe;
import org.graphstream.stream.Replayable;
import org.graphstream.stream.Replayable.Controller;
import org.graphstream.stream.Sink;
import org.graphstream.stream.Source;
import org.graphstream.stream.SourceBase;

/**
 * Filter that allows to pass graph events between two threads without explicit
 * synchronization.
 *
 * <p>
 * This filter allows to register it as an output for some source of events in a
 * source thread (hereafter called the input thread) and to register listening
 * outputs in a destination thread (hereafter called the sink thread).
 * </p>
 *
 * <pre>
 *                       |
 *   Source ---> ThreadProxyFilter ----> Sink
 *  Thread 1             |              Thread 2
 *                       |
 * </pre>
 *
 * <p>
 * In other words, this class allows to listen in a sink thread graph events
 * that are produced in another source thread without any explicit
 * synchronization on the source of events.
 * </p>
 *
 * <p>
 * The only restriction is that the sink thread must regularly call the
 * {@link #pump()} method to dispatch events coming from the source to all sinks
 * registered (see the explanation in {@link org.graphstream.stream.ProxyPipe}).
 * </p>
 *
 * <p>
 * You can register any kind of input as source of event, but if the input is a
 * graph, then you can choose to "replay" all the content of the graph so that
 * at the other end of the filter, all outputs receive the complete content of
 * the graph. This is the default behavior if this filter is constructed with a
 * graph as input.
 * </p>
 */
public class ThreadProxyPipe extends SourceBase implements ProxyPipe {

	/**
	 * class level logger
	 */
	private static final Logger logger = Logger.getLogger(ThreadProxyPipe.class.getSimpleName());

	/**
	 * Proxy id.
	 */
	protected String id;

	/**
	 * The event sender name, usually the graph name.
	 */
	protected String from;

	/**
	 * The message box used to exchange messages between the two threads.
	 */
	protected LinkedList<GraphEvents> events;
	protected LinkedList<Object[]> eventsData;

	protected ReentrantLock lock;
	protected Condition notEmpty;

	/**
	 * Used only to remove the listener. We ensure this is done in the source
	 * thread.
	 */
	protected Source input;

	/**
	 * Signals that this proxy must be removed from the source input.
	 */
	protected boolean unregisterWhenPossible = false;

	public ThreadProxyPipe() {
		this.events = new LinkedList<GraphEvents>();
		this.eventsData = new LinkedList<Object[]>();
		this.lock = new ReentrantLock();
		this.notEmpty = this.lock.newCondition();
		this.from = "<in>";
		this.input = null;
	}

	/**
	 *
	 * @param input
	 *            The source of events we listen at.
	 *
	 * @deprecated Use the default constructor and then call the
	 *             {@link #init(Source)} method.
	 */
	@Deprecated
	public ThreadProxyPipe(final Source input) {
		this(input, null, input instanceof Replayable);
	}

	/**
	 *
	 * @param input
	 * @param replay
	 *
	 * @deprecated Use the default constructor and then call the
	 *             {@link #init(Source)} method.
	 */
	@Deprecated
	public ThreadProxyPipe(final Source input, final boolean replay) {
		this(input, null, replay);
	}

	/**
	 *
	 * @param input
	 * @param initialListener
	 * @param replay
	 *
	 * @deprecated Use the default constructor and then call the
	 *             {@link #init(Source)} method.
	 */
	@Deprecated
	public ThreadProxyPipe(final Source input, final Sink initialListener, final boolean replay) {
		this();

		if (initialListener != null) {
			this.addSink(initialListener);
		}

		this.init(input, replay);
	}

	public void init() {
		this.init(null, false);
	}

	/**
	 * Init the proxy. If there are previous events, they will be cleared.
	 *
	 * @param source
	 *            source of the events
	 */
	public void init(final Source source) {
		this.init(source, source instanceof Replayable);
	}

	/**
	 * Init the proxy. If there are previous events, they will be cleared.
	 *
	 * @param source
	 *            source of the events
	 * @param replay
	 *            true if the source should be replayed. You need a
	 *            {@link org.graphstream.stream.Replayable} source to enable replay,
	 *            else nothing happens.
	 */
	public void init(final Source source, final boolean replay) {
		this.lock.lock();

		try {
			if (this.input != null) {
				this.input.removeSink(this);
			}

			this.input = source;

			this.events.clear();
			this.eventsData.clear();
		} finally {
			this.lock.unlock();
		}

		if (source != null) {
			if (source instanceof Graph) {
				this.from = ((Graph) source).getId();
			}

			this.input.addSink(this);

			if (replay && source instanceof Replayable) {
				Replayable r = (Replayable) source;
				Controller rc = r.getReplayController();

				rc.addSink(this);
				rc.replay();
			}
		}
	}

	@Override
	public String toString() {
		String dest = "nil";

		if (this.attrSinks.size() > 0) {
			dest = this.attrSinks.get(0).toString();
		}

		return String.format("thread-proxy(from %s to %s)", this.from, dest);
	}

	/**
	 * Ask the proxy to unregister from the event input source (stop receive events)
	 * as soon as possible (when the next event will occur in the graph).
	 */
	public void unregisterFromSource() {
		this.unregisterWhenPossible = true;
	}

	/**
	 * This method must be called regularly in the output thread to check if the
	 * input source sent events. If some event occurred, the listeners will be
	 * called.
	 */
	@Override
	public void pump() {
		GraphEvents e = null;
		Object[] data = null;

		do {
			this.lock.lock();

			try {
				e = this.events.poll();
				data = this.eventsData.poll();
			} finally {
				this.lock.unlock();
			}

			if (e != null) {
				this.processMessage(e, data);
			}
		} while (e != null);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.graphstream.stream.ProxyPipe#blockingPump()
	 */
	@Override
	public void blockingPump() throws InterruptedException {
		this.blockingPump(0);
	}

	@Override
	public void blockingPump(final long timeout) throws InterruptedException {
		GraphEvents e;
		Object[] data;

		this.lock.lock();

		try {
			if (timeout > 0) {
				while (this.events.size() == 0) {
					this.notEmpty.await(timeout, TimeUnit.MILLISECONDS);
				}
			} else {
				while (this.events.size() == 0) {
					this.notEmpty.await();
				}
			}
		} finally {
			this.lock.unlock();
		}

		do {
			this.lock.lock();

			try {
				e = this.events.poll();
				data = this.eventsData.poll();
			} finally {
				this.lock.unlock();
			}

			if (e != null) {
				this.processMessage(e, data);
			}
		} while (e != null);
	}

	public boolean hasPostRemaining() {
		boolean r = true;
		this.lock.lock();

		try {
			r = this.events.size() > 0;
		} finally {
			this.lock.unlock();
		}

		return r;
	}

	/**
	 * Set of events sent via the message box.
	 */
	protected static enum GraphEvents {
		ADD_NODE, DEL_NODE, ADD_EDGE, DEL_EDGE, STEP, CLEARED, ADD_GRAPH_ATTR, CHG_GRAPH_ATTR, DEL_GRAPH_ATTR, ADD_NODE_ATTR, CHG_NODE_ATTR, DEL_NODE_ATTR, ADD_EDGE_ATTR, CHG_EDGE_ATTR, DEL_EDGE_ATTR
	};

	protected boolean maybeUnregister() {
		if (this.unregisterWhenPossible) {
			if (this.input != null) {
				this.input.removeSink(this);
			}
			return true;
		}

		return false;
	}

	protected void post(final GraphEvents e, final Object... data) {
		this.lock.lock();

		try {
			this.events.add(e);
			this.eventsData.add(data);

			this.notEmpty.signal();
		} finally {
			this.lock.unlock();
		}
	}

	@Override
	public void edgeAttributeAdded(final String graphId, final long timeId, final String edgeId, final String attribute, final Object value) {
		if (this.maybeUnregister()) {
			return;
		}

		this.post(GraphEvents.ADD_EDGE_ATTR, graphId, timeId, edgeId, attribute, value);
	}

	@Override
	public void edgeAttributeChanged(final String graphId, final long timeId, final String edgeId, final String attribute, final Object oldValue, final Object newValue) {
		if (this.maybeUnregister()) {
			return;
		}

		this.post(GraphEvents.CHG_EDGE_ATTR, graphId, timeId, edgeId, attribute, oldValue, newValue);
	}

	@Override
	public void edgeAttributeRemoved(final String graphId, final long timeId, final String edgeId, final String attribute) {
		if (this.maybeUnregister()) {
			return;
		}

		this.post(GraphEvents.DEL_EDGE_ATTR, graphId, timeId, edgeId, attribute);
	}

	@Override
	public void graphAttributeAdded(final String graphId, final long timeId, final String attribute, final Object value) {
		if (this.maybeUnregister()) {
			return;
		}

		this.post(GraphEvents.ADD_GRAPH_ATTR, graphId, timeId, attribute, value);
	}

	@Override
	public void graphAttributeChanged(final String graphId, final long timeId, final String attribute, final Object oldValue, final Object newValue) {
		if (this.maybeUnregister()) {
			return;
		}

		this.post(GraphEvents.CHG_GRAPH_ATTR, graphId, timeId, attribute, oldValue, newValue);
	}

	@Override
	public void graphAttributeRemoved(final String graphId, final long timeId, final String attribute) {
		if (this.maybeUnregister()) {
			return;
		}

		this.post(GraphEvents.DEL_GRAPH_ATTR, graphId, timeId, attribute);
	}

	@Override
	public void nodeAttributeAdded(final String graphId, final long timeId, final String nodeId, final String attribute, final Object value) {
		if (this.maybeUnregister()) {
			return;
		}

		this.post(GraphEvents.ADD_NODE_ATTR, graphId, timeId, nodeId, attribute, value);
	}

	@Override
	public void nodeAttributeChanged(final String graphId, final long timeId, final String nodeId, final String attribute, final Object oldValue, final Object newValue) {
		if (this.maybeUnregister()) {
			return;
		}

		this.post(GraphEvents.CHG_NODE_ATTR, graphId, timeId, nodeId, attribute, oldValue, newValue);
	}

	@Override
	public void nodeAttributeRemoved(final String graphId, final long timeId, final String nodeId, final String attribute) {
		if (this.maybeUnregister()) {
			return;
		}

		this.post(GraphEvents.DEL_NODE_ATTR, graphId, timeId, nodeId, attribute);
	}

	@Override
	public void edgeAdded(final String graphId, final long timeId, final String edgeId, final String fromNodeId, final String toNodeId, final boolean directed) {
		if (this.maybeUnregister()) {
			return;
		}

		this.post(GraphEvents.ADD_EDGE, graphId, timeId, edgeId, fromNodeId, toNodeId, directed);
	}

	@Override
	public void edgeRemoved(final String graphId, final long timeId, final String edgeId) {
		if (this.maybeUnregister()) {
			return;
		}

		this.post(GraphEvents.DEL_EDGE, graphId, timeId, edgeId);
	}

	@Override
	public void graphCleared(final String graphId, final long timeId) {
		if (this.maybeUnregister()) {
			return;
		}

		this.post(GraphEvents.CLEARED, graphId, timeId);
	}

	@Override
	public void nodeAdded(final String graphId, final long timeId, final String nodeId) {
		if (this.maybeUnregister()) {
			return;
		}

		this.post(GraphEvents.ADD_NODE, graphId, timeId, nodeId);
	}

	@Override
	public void nodeRemoved(final String graphId, final long timeId, final String nodeId) {
		if (this.maybeUnregister()) {
			return;
		}

		this.post(GraphEvents.DEL_NODE, graphId, timeId, nodeId);
	}

	@Override
	public void stepBegins(final String graphId, final long timeId, final double step) {
		if (this.maybeUnregister()) {
			return;
		}

		this.post(GraphEvents.STEP, graphId, timeId, step);
	}

	// MBoxListener

	protected synchronized void processMessage(final GraphEvents e, final Object[] data) {
		String graphId, elementId, attribute;
		Long timeId;
		Object newValue, oldValue;

		switch (e) {
		case ADD_NODE:
			graphId = (String) data[0];
			timeId = (Long) data[1];
			elementId = (String) data[2];

			this.sendNodeAdded(graphId, timeId, elementId);
			break;
		case DEL_NODE:
			graphId = (String) data[0];
			timeId = (Long) data[1];
			elementId = (String) data[2];

			this.sendNodeRemoved(graphId, timeId, elementId);
			break;
		case ADD_EDGE:
			graphId = (String) data[0];
			timeId = (Long) data[1];
			elementId = (String) data[2];

			String fromId = (String) data[3];
			String toId = (String) data[4];
			boolean directed = (Boolean) data[5];

			this.sendEdgeAdded(graphId, timeId, elementId, fromId, toId, directed);
			break;
		case DEL_EDGE:
			graphId = (String) data[0];
			timeId = (Long) data[1];
			elementId = (String) data[2];

			this.sendEdgeRemoved(graphId, timeId, elementId);
			break;
		case STEP:
			graphId = (String) data[0];
			timeId = (Long) data[1];

			double step = (Double) data[2];

			this.sendStepBegins(graphId, timeId, step);
			break;
		case ADD_GRAPH_ATTR:
			graphId = (String) data[0];
			timeId = (Long) data[1];
			attribute = (String) data[2];
			newValue = data[3];

			this.sendGraphAttributeAdded(graphId, timeId, attribute, newValue);
			break;
		case CHG_GRAPH_ATTR:
			graphId = (String) data[0];
			timeId = (Long) data[1];
			attribute = (String) data[2];
			oldValue = data[3];
			newValue = data[4];

			this.sendGraphAttributeChanged(graphId, timeId, attribute, oldValue, newValue);
			break;
		case DEL_GRAPH_ATTR:
			graphId = (String) data[0];
			timeId = (Long) data[1];
			attribute = (String) data[2];

			this.sendGraphAttributeRemoved(graphId, timeId, attribute);
			break;
		case ADD_EDGE_ATTR:
			graphId = (String) data[0];
			timeId = (Long) data[1];
			elementId = (String) data[2];
			attribute = (String) data[3];
			newValue = data[4];

			this.sendEdgeAttributeAdded(graphId, timeId, elementId, attribute, newValue);
			break;
		case CHG_EDGE_ATTR:
			graphId = (String) data[0];
			timeId = (Long) data[1];
			elementId = (String) data[2];
			attribute = (String) data[3];
			oldValue = data[4];
			newValue = data[5];

			this.sendEdgeAttributeChanged(graphId, timeId, elementId, attribute, oldValue, newValue);
			break;
		case DEL_EDGE_ATTR:
			graphId = (String) data[0];
			timeId = (Long) data[1];
			elementId = (String) data[2];
			attribute = (String) data[3];

			this.sendEdgeAttributeRemoved(graphId, timeId, elementId, attribute);
			break;
		case ADD_NODE_ATTR:
			graphId = (String) data[0];
			timeId = (Long) data[1];
			elementId = (String) data[2];
			attribute = (String) data[3];
			newValue = data[4];

			this.sendNodeAttributeAdded(graphId, timeId, elementId, attribute, newValue);
			break;
		case CHG_NODE_ATTR:
			graphId = (String) data[0];
			timeId = (Long) data[1];
			elementId = (String) data[2];
			attribute = (String) data[3];
			oldValue = data[4];
			newValue = data[5];

			this.sendNodeAttributeChanged(graphId, timeId, elementId, attribute, oldValue, newValue);
			break;
		case DEL_NODE_ATTR:
			graphId = (String) data[0];
			timeId = (Long) data[1];
			elementId = (String) data[2];
			attribute = (String) data[3];

			this.sendNodeAttributeRemoved(graphId, timeId, elementId, attribute);
			break;
		case CLEARED:
			graphId = (String) data[0];
			timeId = (Long) data[1];

			this.sendGraphCleared(graphId, timeId);
			break;
		default:
			logger.warning(String.format("Unknown message %s.", e));
			break;
		}
	}
}