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

import java.util.Map;

import org.springframework.graphql.execution.ThreadLocalAccessor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ThreadLocalAccessor} that operates on the ThreadLocal it is given.
 */
public class TestThreadLocalAccessor<T> implements ThreadLocalAccessor {

	private final ThreadLocal<T> threadLocal;

	@Nullable
	private Long threadId;

	private final boolean suppressThreadIdCheck;

	public TestThreadLocalAccessor(ThreadLocal<T> threadLocal) {
		this(threadLocal, false);
	}

	public TestThreadLocalAccessor(ThreadLocal<T> threadLocal, boolean suppressThreadIdCheck) {
		this.threadLocal = threadLocal;
		this.suppressThreadIdCheck = suppressThreadIdCheck;
	}

	@Override
	public void extractValues(Map<String, Object> container) {
		saveThreadId();
		T name = this.threadLocal.get();
		Assert.notNull(name, "No ThreadLocal value");
		container.put("name", name);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void restoreValues(Map<String, Object> values) {
		checkThreadId();
		T name = (T) values.get("name");
		Assert.notNull(name, "No value to set");
		this.threadLocal.set(name);
	}

	@Override
	public void resetValues(Map<String, Object> values) {
		this.threadLocal.remove();
	}

	private void saveThreadId() {
		if (this.suppressThreadIdCheck) {
			return;
		}
		this.threadId = Thread.currentThread().getId();
	}

	private void checkThreadId() {
		if (this.suppressThreadIdCheck) {
			return;
		}
		assertThat(this.threadId).as("No threadId to check. Was extractValues not called?").isNotNull();
		assertThat(Thread.currentThread().getId() != this.threadId)
				.as("ThreadLocal value extracted and restored on the same thread. Propagation not tested effectively.")
				.isTrue();
	}

}
