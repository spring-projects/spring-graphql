/*
 * Copyright 2002-2021 the original author or authors.
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
package org.springframework.graphql.test.tester;

import java.time.Duration;
import java.util.function.Predicate;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import graphql.GraphQLError;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Holds the input required for {@link GraphQlTester.Builder}, providing a
 * convenient way to pass it together, while also helping to avoid challenges
 * with builder hierarchy generics.
 *
 * @author Rossen Stoyanchev
 */
final class GraphQlTesterBuilderConfig {

	private static final boolean jackson2Present;

	static {
		ClassLoader classLoader = DefaultGraphQlTester.class.getClassLoader();
		jackson2Present = ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", classLoader)
				&& ClassUtils.isPresent("com.fasterxml.jackson.core.JsonGenerator", classLoader);
	}


	@Nullable
	private Predicate<GraphQLError> errorFilter;

	@Nullable
	private Configuration jsonPathConfig;

	private Duration responseTimeout = Duration.ofSeconds(5);

	public void errorFilter(Predicate<GraphQLError> predicate) {
		this.errorFilter = (this.errorFilter != null ? errorFilter.and(predicate) : predicate);
	}

	public void jsonPathConfig(@Nullable Configuration config) {
		this.jsonPathConfig = config;
	}

	public void responseTimeout(Duration timeout) {
		Assert.notNull(timeout, "'timeout' is required");
		this.responseTimeout = timeout;
	}

	@Nullable
	public Predicate<GraphQLError> getErrorFilter() {
		return this.errorFilter;
	}

	public Configuration getJsonPathConfig() {
		if (this.jsonPathConfig == null) {
			this.jsonPathConfig = (jackson2Present ?
					Jackson2Configuration.create() : Configuration.builder().build());
		}
		return this.jsonPathConfig;
	}

	public Duration getResponseTimeout() {
		return this.responseTimeout;
	}


	private static class Jackson2Configuration {

		static Configuration create() {
			return Configuration.builder()
					.jsonProvider(new JacksonJsonProvider())
					.mappingProvider(new JacksonMappingProvider())
					.build();
		}

	}

}
