/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.execution;

import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Factory for Reactor {@link Scheduler schedulers}.
 * @author Brian Clozel
 * @since 2.0.0
 */
public abstract class ReactorSchedulers {

	/**
	 * Create a scheduler backed by a single new thread, using {@link Schedulers#newSingle(String)} on Java &lt; 21
	 * and a custom Virtual Thread factory on Java &gte; 21.
	 * @param name component and thread name prefix
	 */
	public static Scheduler singleThread(String name) {
		return Schedulers.newSingle(Thread.ofVirtual().name(name).factory());
	}

}
