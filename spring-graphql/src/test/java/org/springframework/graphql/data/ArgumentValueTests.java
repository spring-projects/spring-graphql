/*
 * Copyright 2020-2025 the original author or authors.
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

package org.springframework.graphql.data;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ArgumentValue}.
 * @author Brian Clozel
 */
class ArgumentValueTests {

	@Test
	void existingValueShouldBePresent() {
		ArgumentValue<String> message = ArgumentValue.ofNullable("hello");
		assertThat(message.isOmitted()).isFalse();
		assertThat(message.isPresent()).isTrue();
		assertThat(message.isEmpty()).isFalse();
	}

	@Test
	void nullValueShouldBePresent() {
		ArgumentValue<String> message = ArgumentValue.ofNullable(null);
		assertThat(message.isOmitted()).isFalse();
		assertThat(message.isPresent()).isFalse();
		assertThat(message.isEmpty()).isTrue();
	}

	@Test
	void noValueShouldBeOmitted() {
		ArgumentValue<String> message = ArgumentValue.omitted();
		assertThat(message.isOmitted()).isTrue();
		assertThat(message.isPresent()).isFalse();
		assertThat(message.isEmpty()).isFalse();
	}

	@Test
	void asOptionalShouldMapOmitted() {
		assertThat(ArgumentValue.omitted().asOptional()).isEmpty();
		assertThat(ArgumentValue.ofNullable(null).asOptional()).isEmpty();
		assertThat(ArgumentValue.ofNullable("hello").asOptional()).isPresent();
	}

	@Test
	void ifPresentShouldExecuteWhenValue() {
		AtomicBoolean called = new AtomicBoolean();
		ArgumentValue.ofNullable("hello").ifPresent(value -> called.set(true));
		assertThat(called.get()).isTrue();
	}

	@Test
	void ifPresentShouldSkipWhenNull() {
		AtomicBoolean called = new AtomicBoolean();
		ArgumentValue.ofNullable(null).ifPresent(value -> called.set(true));
		assertThat(called.get()).isFalse();
	}

	@Test
	void ifPresentShouldSkipWhenOmitted() {
		AtomicBoolean called = new AtomicBoolean();
		ArgumentValue.omitted().ifPresent(value -> called.set(true));
		assertThat(called.get()).isFalse();
	}

	@Test
	void toStringValue() {
		assertThat(ArgumentValue.omitted()).hasToString("ArgumentValue[omitted]");
		assertThat(ArgumentValue.ofNullable(null)).hasToString("ArgumentValue[empty]");
		assertThat(ArgumentValue.ofNullable("hello")).hasToString("ArgumentValue[hello]");
	}
}
