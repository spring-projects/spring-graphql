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

package org.springframework.graphql;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link FieldValue}.
 * @author Brian Clozel
 */
class FieldValueTests {

	@Test
	void existingValueShouldBePresent() {
		FieldValue<String> message = FieldValue.ofNullable("hello");
		assertThat(message.isOmitted()).isFalse();
		assertThat(message.isPresent()).isTrue();
		assertThat(message.isEmpty()).isFalse();
	}

	@Test
	void nullValueShouldBePresent() {
		FieldValue<String> message = FieldValue.ofNullable(null);
		assertThat(message.isOmitted()).isFalse();
		assertThat(message.isPresent()).isFalse();
		assertThat(message.isEmpty()).isTrue();
	}

	@Test
	void noValueShouldBeOmitted() {
		FieldValue<String> message = FieldValue.omitted();
		assertThat(message.isOmitted()).isTrue();
		assertThat(message.isPresent()).isFalse();
		assertThat(message.isEmpty()).isFalse();
	}

	@Test
	void asOptionalShouldMapOmitted() {
		assertThat(FieldValue.omitted().asOptional()).isEmpty();
		assertThat(FieldValue.ofNullable(null).asOptional()).isEmpty();
		assertThat(FieldValue.ofNullable("hello").asOptional()).isPresent();
	}

	@Test
	void ifPresentShouldExecuteWhenValue() {
		AtomicBoolean called = new AtomicBoolean();
		FieldValue.ofNullable("hello").ifPresent(value -> called.set(true));
		assertThat(called.get()).isTrue();
	}

	@Test
	void ifPresentShouldSkipWhenNull() {
		AtomicBoolean called = new AtomicBoolean();
		FieldValue.ofNullable(null).ifPresent(value -> called.set(true));
		assertThat(called.get()).isFalse();
	}

	@Test
	void ifPresentShouldSkipWhenOmitted() {
		AtomicBoolean called = new AtomicBoolean();
		FieldValue.omitted().ifPresent(value -> called.set(true));
		assertThat(called.get()).isFalse();
	}

}
