/*
 * Copyright 2020-2021 the original author or authors.
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

package org.springframework.graphql.boot;

import graphql.schema.GraphQLCodeRegistry;

/**
 * Callback interface that can be implemented by beans wishing to customize the
 * {@link GraphQLCodeRegistry} via a {@link GraphQLCodeRegistry.Builder} whilst retaining default
 * auto-configuration.
 *
 * @author Hantsy Bai <hantsy@gmail.com>
 * @since 1.0.0
 */
@FunctionalInterface
public interface CodeRegistryBuilderCustomizer {

	/**
	 * Customize the {@link GraphQLCodeRegistry.Builder} instance.
	 * @param builder builder the builder to customize
	 */
	void customize(GraphQLCodeRegistry.Builder builder);

}
