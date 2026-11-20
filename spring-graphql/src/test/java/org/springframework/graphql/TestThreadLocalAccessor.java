/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql;

import io.micrometer.context.ThreadLocalAccessor;
import org.jspecify.annotations.Nullable;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ThreadLocalAccessor} that operates on the ThreadLocal it is given.
 */
public class TestThreadLocalAccessor<T> implements ThreadLocalAccessor<T> {

	private final ThreadLocal<T> threadLocal;

	@Nullable
	private Long threadId;


	public TestThreadLocalAccessor(ThreadLocal<T> threadLocal) {
		this.threadLocal = threadLocal;
	}


	@Override
	public Object key() {
		return getClass().getName();
	}

	@Override
	public T getValue() {
		T value = this.threadLocal.get();

		// Only save thread id on initial call (restore looks up previous value) and if there is a value.
		if (value != null && this.threadId == null) {
			this.threadId = Thread.currentThread().getId();
		}

		return value;
	}

	@Override
	public void setValue(T value) {
		if (this.threadId != null) {
			assertThat(Thread.currentThread().getId() != this.threadId)
					.as("ThreadLocal restored on the same thread. Propagation not tested effectively.")
					.isTrue();
		}
		this.threadLocal.set(value);
	}

	@Override
	public void reset() {
		this.threadLocal.remove();
	}

}
