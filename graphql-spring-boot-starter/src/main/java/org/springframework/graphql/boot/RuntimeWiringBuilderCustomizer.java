/*
 * Copyright 2020-2020 the original author or authors.
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

import graphql.schema.idl.RuntimeWiring;

/**
 * Callback interface that can be implemented by beans wishing to customize the
 * {@link RuntimeWiring} via a {@link RuntimeWiring.Builder} whilst retaining default
 * auto-configuration.
 *
 * @author Brian Clozel
 * @since 1.0.0
 */
@FunctionalInterface
public interface RuntimeWiringBuilderCustomizer {

	/**
	 * Customize the {@link RuntimeWiring.Builder} instance.
	 * @param builder builder the builder to customize
	 */
	void customize(RuntimeWiring.Builder builder);

}
