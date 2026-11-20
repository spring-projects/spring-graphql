/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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

	@Test
	void omittedAndExplicitNullShouldNotBeEqual() {
		ArgumentValue<String> omitted = ArgumentValue.omitted();
		ArgumentValue<String> explicitNull = ArgumentValue.ofNullable(null);
		assertThat(omitted).isNotEqualTo(explicitNull);
		assertThat(omitted.hashCode()).isNotEqualTo(explicitNull.hashCode());
	}

	@Test
	void sameValuesShouldBeEqual() {
		assertThat(ArgumentValue.omitted()).isEqualTo(ArgumentValue.omitted());
		assertThat(ArgumentValue.ofNullable(null)).isEqualTo(ArgumentValue.ofNullable(null));
		assertThat(ArgumentValue.ofNullable("hello")).isEqualTo(ArgumentValue.ofNullable("hello"));
	}
}
