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

package org.springframework.graphql.execution;

import java.util.Collection;

import io.micrometer.context.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;

/**
 * Helper to adapt a result Object to {@link Mono} or {@link Flux} through
 * {@link ReactiveAdapterRegistry}.
 *
 * @author Rossen Stoyanchev
 * @since 1.3.1
 */
@SuppressWarnings({"ReactiveStreamsUnusedPublisher", "unchecked"})
public abstract class ReactiveAdapterRegistryHelper {

	private static final ReactiveAdapterRegistry registry = ReactiveAdapterRegistry.getSharedInstance();


	/**
	 * Return a {@link Mono} for the given Object by delegating to
	 * {@link #toMonoIfReactive}, and then applying {@link Mono#justOrEmpty}
	 * if necessary.
	 * @param result the result Object to adapt
	 * @param <T> the type of element in the Mono to cast to
	 * @return a {@code Mono} that represents the result
	 */
	public static <T> Mono<T> toMono(@Nullable Object result) {
		result = toMonoIfReactive(result);
		return (Mono<T>) ((result instanceof Mono<?> mono) ? mono : Mono.justOrEmpty(result));
	}

	/**
	 * Return a {@link Mono} for the given result Object if it can be adapted
	 * to a {@link Publisher} via {@link ReactiveAdapterRegistry}. Multivalued
	 * publishers are collected to a List.
	 * @param result the result Object to adapt
	 * @return the same instance or a {@code Mono} if the object is known to
	 * {@code ReactiveAdapterRegistry}
	 */
	@Nullable
	public static Object toMonoIfReactive(@Nullable Object result) {
		ReactiveAdapter adapter = ((result != null) ? registry.getAdapter(result.getClass()) : null);
		if (adapter == null) {
			return result;
		}
		Publisher<?> publisher = adapter.toPublisher(result);
		return (adapter.isMultiValue() ? Flux.from(publisher).collectList() : Mono.from(publisher));
	}

	/**
	 * Adapt the given result Object to {@link Mono} or {@link Flux} if it can
	 * be adapted to a single or multi-value {@link Publisher} respectively
	 * via {@link ReactiveAdapterRegistry}.
	 * @param result the result Object to adapt
	 * @return the same instance, a {@code Mono}, or a {@code Flux}
	 */
	@Nullable
	public static Object toMonoOrFluxIfReactive(@Nullable Object result) {
		ReactiveAdapter adapter = ((result != null) ? registry.getAdapter(result.getClass()) : null);
		if (adapter == null) {
			return result;
		}
		Publisher<Object> publisher = adapter.toPublisher(result);
		return (adapter.isMultiValue() ? Flux.from(publisher) : Mono.from(publisher));
	}

	/**
	 * Return a {@link Flux} for the given result Object, adapting to a
	 * {@link Publisher} via {@link ReactiveAdapterRegistry} or wrapping it as
	 * {@code Flux} if necessary.
	 * @param result the result Object to adapt
	 * @return a {@link Flux}, possibly empty if the result is {@code null}
	 */
	public static Flux<?> toSubscriptionFlux(@Nullable Object result) {
		if (result == null) {
			return Flux.empty();
		}
		if (result instanceof Publisher<?> publisher) {
			return Flux.from(publisher);
		}
		ReactiveAdapter adapter = registry.getAdapter(result.getClass());
		return ((adapter != null) ? Flux.from(adapter.toPublisher(result)) : Flux.just(result));
	}

	/**
	 * Return a {@link Flux} for the given result Object that represents a
	 * logical collection of values. The Object must be a {@link Collection}
	 * or a publisher of a {@code Collection}, which is flattened with
	 * {@link Flux#fromIterable(Iterable)}, or a multi-value publisher.
	 * @param result the result Object to adapt
	 * @param <T> the type of element in the collection to cast to
	 * @return a {@code Flux} that represents the collection
	 */
	public static <T> Flux<T> toFluxFromCollection(@Nullable Object result) {
		if (result instanceof Collection) {
			return Flux.fromIterable((Collection<T>) result);
		}
		ReactiveAdapter adapter = ((result != null) ? registry.getAdapter(result.getClass()) : null);
		if (adapter == null) {
			return Flux.error(new IllegalStateException("Unexpected return value: " + result));
		}
		Publisher<?> publisher = adapter.toPublisher(result);
		if (adapter.isMultiValue()) {
			return (Flux<T>) Flux.from(publisher);
		}
		else {
			return Mono.from(publisher).flatMapMany((c) -> Flux.fromIterable((Collection<T>) c));
		}
	}

}
