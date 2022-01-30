/*
 * Copyright 2002-2022 the original author or authors.
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

import java.util.List;

import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.WiringFactory;

/**
 * Callbacks that allow applying changes to the {@link RuntimeWiring.Builder}
 * in {@link GraphQlSource.Builder}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface RuntimeWiringConfigurer {

	/**
	 * Apply changes to the {@link RuntimeWiring.Builder} such as registering
	 * {@link graphql.schema.DataFetcher}s, custom scalar types, and more.
	 * @param builder the builder to configure
	 */
	void configure(RuntimeWiring.Builder builder);

	/**
	 * Variant of {@link #configure(RuntimeWiring.Builder)} that also collects
	 * {@link WiringFactory} instances that are then combined as one via
	 * {@link graphql.schema.idl.CombinedWiringFactory}.
	 * @param builder the builder to configure
	 * @param container the list of configured factories to add or insert into
	 */
	default void configure(RuntimeWiring.Builder builder, List<WiringFactory> container) {
		// no-op
	}

}
