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
 * Decorator for a {@link CursorStrategy} that applies a {@link CursorEncoder}
 * to the cursor String to make it opaque for external use.
 *
 * <p>To create an instance, use
 * {@link CursorStrategy#withEncoder(CursorStrategy, CursorEncoder)}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2
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
