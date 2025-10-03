/*
 * Copyright 2025-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
