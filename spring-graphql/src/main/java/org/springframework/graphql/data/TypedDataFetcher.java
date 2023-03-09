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

package org.springframework.graphql.data;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import org.springframework.core.ResolvableType;

/**
 * Specialized {@link DataFetcher} that can provide {@link ResolvableType type information}
 * about the {@link #get(DataFetchingEnvironment) instances returned}.
 * <p>Such {@code DataFetchers} are often backed by actual Java methods with declared return types.
 * Declared types might not reflect the concrete type of the returned instance.
 * @author Brian Clozel
 * @since 1.2.0
 */
public interface TypedDataFetcher<T> extends DataFetcher<T> {

	/**
	 * The type declared by this {@link DataFetcher}.
	 * <p>The concrete type of the returned instance might differ from the declared one.
	 * @return the declared type for the data to be fetched.
	 */
	ResolvableType getDeclaredType();
	
}
