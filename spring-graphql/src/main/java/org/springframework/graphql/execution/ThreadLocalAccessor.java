/*
 * Copyright 2002-2021 the original author or authors.
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

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;

/**
 * Interface to be implemented by a framework or an application in order to assist with
 * extracting ThreadLocal values at the web layer, which can then be re-established for
 * DataFetcher's that are potentially executing on a different thread.
 *
 * <p>
 * Implementations may be declared as beans in Spring configuration and ordered as defined
 * in {@link ObjectProvider#orderedStream()}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface ThreadLocalAccessor {

	/**
	 * Extract ThreadLocal values and add them to the given Map which is then passed to
	 * {@link #restoreValues(Map)} and {@link #resetValues(Map)} before and after the
	 * execution of a {@link graphql.schema.DataFetcher}.
	 * @param container container for ThreadLocal values
	 */
	void extractValues(Map<String, Object> container);

	/**
	 * Re-establish ThreadLocal context by looking up values, previously extracted via
	 * {@link #extractValues(Map)}.
	 * @param values the saved ThreadLocal values
	 */
	void restoreValues(Map<String, Object> values);

	/**
	 * Reset ThreadLocal context for the given values, previously extracted via
	 * {@link #extractValues(Map)}.
	 * @param values the saved ThreadLocal values
	 */
	void resetValues(Map<String, Object> values);

	/**
	 * Create a composite accessor that delegates to all of the given accessors.
	 * @param accessors the accessors to aggregate
	 * @return the composite accessor
	 */
	static ThreadLocalAccessor composite(List<ThreadLocalAccessor> accessors) {
		return new CompositeThreadLocalAccessor(accessors);
	}

}
