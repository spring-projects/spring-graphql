/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.pagination;

import org.springframework.util.Assert;

/**
 * Convenient base class for implementations of
 * {@link org.springframework.graphql.data.pagination.ConnectionAdapter}.
 *
 * @param <P> the position type
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
public class ConnectionAdapterSupport<P> {

	private final CursorStrategy<P> cursorStrategy;


	/**
	 * Constructor with a {@link CursorStrategy} to use.
	 * @param cursorStrategy the cursor strategy to use
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
