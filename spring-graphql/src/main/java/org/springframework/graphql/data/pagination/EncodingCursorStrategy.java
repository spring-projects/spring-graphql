/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.pagination;

import org.springframework.util.Assert;

/**
 * Decorator for a {@link CursorStrategy} that applies a {@link CursorEncoder}
 * to the cursor String to make it opaque for external use.
 *
 * <p>To create an instance, use
 * {@link CursorStrategy#withEncoder(CursorStrategy, CursorEncoder)}.
 *
 * @param <T> the type of position
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
public final class EncodingCursorStrategy<T> implements CursorStrategy<T> {

	private final CursorStrategy<T> delegate;

	private final CursorEncoder encoder;


	EncodingCursorStrategy(CursorStrategy<T> strategy, CursorEncoder encoder) {
		Assert.notNull(strategy, "CursorStrategy is required");
		Assert.notNull(encoder, "CursorEncoder is required");
		Assert.isTrue(!(strategy instanceof EncodingCursorStrategy<?>), "CursorStrategy already has encoding");
		this.delegate = strategy;
		this.encoder = encoder;
	}


	/**
	 * Return the decorated {@link CursorStrategy}.
	 */
	public CursorStrategy<T> getDelegate() {
		return this.delegate;
	}

	/**
	 * Return the configured {@link CursorEncoder}.
	 */
	public CursorEncoder getEncoder() {
		return this.encoder;
	}


	@Override
	public boolean supports(Class<?> targetType) {
		return this.delegate.supports(targetType);
	}

	@Override
	public String toCursor(T position) {
		String cursor = this.delegate.toCursor(position);
		return this.encoder.encode(cursor);
	}

	@Override
	public T fromCursor(String cursor) {
		String decodedCursor = this.encoder.decode(cursor);
		return this.delegate.fromCursor(decodedCursor);
	}

}
