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

package org.springframework.graphql.execution;

import graphql.schema.DataFetcher;

import org.springframework.core.ResolvableType;

/**
 * Specialized {@link DataFetcher} that exposes additional details such as
 * return type information.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
public interface SelfDescribingDataFetcher<T> extends DataFetcher<T> {

	/**
	 * The return type of this {@link DataFetcher}.
	 * <p>This could be derived from the method signature of an annotated
	 * {@code @Controller} method, the domain type of a {@link DataFetcher}
	 * backed by a Spring Data repository, or other.
	 */
	ResolvableType getReturnType();

}
