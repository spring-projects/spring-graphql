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

import java.time.Duration;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import org.springframework.graphql.TestThreadLocalAccessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ContextManager}.
 * @author Rossen Stoyanchev
 */
public class ContextManagerTests {

	@Test
	void restoreThreadLocaValues() {
		ThreadLocal<String> threadLocal = new ThreadLocal<>();
		threadLocal.set("myValue");

		Context context = ContextManager.extractThreadLocalValues(
				new TestThreadLocalAccessor<>(threadLocal), Context.empty());
		try {
			Mono.delay(Duration.ofMillis(10))
					.doOnNext(aLong -> {
						assertThat(threadLocal.get()).isNull();
						ContextManager.restoreThreadLocalValues(context);
						assertThat(threadLocal.get()).isEqualTo("myValue");
						ContextManager.resetThreadLocalValues(context);
					})
					.block();
		}
		finally {
			threadLocal.remove();
		}
	}

	@Test
	void restoreThreadLocaValuesOnSameThreadIsNoOp() {
		ThreadLocal<String> threadLocal = new ThreadLocal<>();
		threadLocal.set("myValue");

		Context context = ContextManager.extractThreadLocalValues(
				new TestThreadLocalAccessor<>(threadLocal, true), Context.empty());

		threadLocal.remove();
		ContextManager.restoreThreadLocalValues(context);
		assertThat(threadLocal.get()).isNull();

		threadLocal.set("anotherValue");
		ContextManager.resetThreadLocalValues(context);
		assertThat(threadLocal.get()).isEqualTo("anotherValue");
	}

}
