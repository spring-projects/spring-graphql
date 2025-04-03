/*
 * Copyright 2002-2025 the original author or authors.
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


import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * Simple container for the value from binding a GraphQL argument to a higher
 * level Object, along with a flag to indicate whether the input argument was
 * omitted altogether, as opposed to provided but set to the {@literal "null"}
 * literal.
 *
 * <p>Supported in one of the following places:
 * <ul>
 * <li>On a controller method parameter, either instead of
 * {@link org.springframework.graphql.data.method.annotation.Argument @Argument}
 * in which case the argument name is determined from the method parameter name,
 * or together with {@code @Argument} to specify the argument name.
 * <li>As a field within the object structure of an {@code @Argument} method
 * parameter, either initialized via a constructor argument or a setter,
 * including as a field of an object nested at any level below the top level
 * object.
 * </ul>
 *
 * @param <T> the type of value contained
 * @author Rossen Stoyanchev
 * @since 1.1.0
 * @see <a href="http://spec.graphql.org/October2021/#sec-Non-Null.Nullable-vs-Optional">Nullable vs Optional</a>
 */
public final class ArgumentValue<T> {

	private static final ArgumentValue<?> OMITTED = new ArgumentValue<>(null, true);


	@Nullable
	private final T value;

	private final boolean omitted;


	private ArgumentValue(@Nullable T value, boolean omitted) {
		this.value = value;
		this.omitted = omitted;
	}


	/**
	 * Return {@code true} if a non-null value is present, and {@code false} otherwise.
	 */
	public boolean isPresent() {
		return (this.value != null);
	}

	/**
	 * Return {@code true} if the input value was present in the input but the value was {@code null},
	 * and {@code false} otherwise.
	 * @since 1.4.0
	 */
	public boolean isEmpty() {
		return !this.omitted && this.value == null;
	}

	/**
	 * Return {@code true} if the input value was omitted altogether from the
	 * input, and {@code false} if it was provided, but possibly set to the
	 * {@literal "null"} literal.
	 */
	public boolean isOmitted() {
		return this.omitted;
	}

	/**
	 * Return the contained value, or {@code null}.
	 */
	@Nullable
	public T value() {
		return this.value;
	}

	/**
	 * Return the contained value as a nullable {@link Optional}.
	 */
	public Optional<T> asOptional() {
		return Optional.ofNullable(this.value);
	}

	/**
	 * If a value is present, performs the given action with the value, otherwise does nothing.
	 * @param action the action to be performed, if a value is present
	 * @since 1.4.0
	 */
	public void ifPresent(Consumer<? super T> action) {
		Assert.notNull(action, "Action is required");
		if (this.value != null) {
			action.accept(this.value);
		}
	}

	@Override
	public boolean equals(Object other) {
		// This covers OMITTED constant
		if (this == other) {
			return true;
		}
		if (!(other instanceof ArgumentValue<?> otherValue)) {
			return false;
		}
		return ObjectUtils.nullSafeEquals(this.value, otherValue.value);
	}

	@Override
	public int hashCode() {
		int result = ObjectUtils.nullSafeHashCode(this.value);
		result = 31 * result + Boolean.hashCode(this.omitted);
		return result;
	}


	/**
	 * Static factory method for an argument value that was provided, even if
	 * it was set to {@literal "null}.
	 * @param <T> the type of value
	 * @param value the value to hold in the instance
	 */
	public static <T> ArgumentValue<T> ofNullable(@Nullable T value) {
		return new ArgumentValue<>(value, false);
	}

	/**
	 * Static factory method for an argument value that was omitted.
	 * @param <T> the type of value
	 */
	@SuppressWarnings("unchecked")
	public static <T> ArgumentValue<T> omitted() {
		return (ArgumentValue<T>) OMITTED;
	}

}
