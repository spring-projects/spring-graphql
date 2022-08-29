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

package org.springframework.graphql;

import io.micrometer.context.ThreadLocalAccessor;

import org.springframework.lang.Nullable;

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
