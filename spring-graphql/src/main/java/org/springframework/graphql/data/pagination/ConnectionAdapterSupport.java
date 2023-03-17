/*
 * Copyright 2020-2023 the original author or authors.
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

package org.springframework.graphql.data.pagination;

import org.springframework.util.Assert;

/**
 * Convenient base class for implementations of
 * {@link org.springframework.graphql.data.pagination.ConnectionAdapter}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2
 */
public class ConnectionAdapterSupport<P> {

	private final CursorStrategy<P> cursorStrategy;


	/**
	 * Constructor with a {@link CursorStrategy} to use.
	 */
	protected ConnectionAdapterSupport(CursorStrategy<P> cursorStrategy) {
		Assert.notNull(cursorStrategy, "CursorStrategy is required");
		this.cursorStrategy = cursorStrategy;
	}


	/**
	 * Return the configured {@link CursorStrategy}.
	 */
	public CursorStrategy<P> getCursorStrategy() {
		return this.cursorStrategy;
	}

}
