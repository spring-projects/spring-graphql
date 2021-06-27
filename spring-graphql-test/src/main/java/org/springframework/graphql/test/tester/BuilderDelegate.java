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

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Assist with collecting the input for {@link GraphQlTester.Builder},
 * essentially to avoid challenges with generics in the builder hierarchy.
 *
 * @author Rossen Stoyanchev
 */
final class BuilderDelegate {

	private static final boolean jackson2Present;

	static {
		ClassLoader classLoader = DefaultGraphQlTester.class.getClassLoader();
		jackson2Present = ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", classLoader)
				&& ClassUtils.isPresent("com.fasterxml.jackson.core.JsonGenerator", classLoader);
	}


	@Nullable
	private Configuration jsonPathConfig;

	private Duration responseTimeout = Duration.ofSeconds(5);

	public void jsonPathConfig(@Nullable Configuration config) {
		this.jsonPathConfig = config;
	}

	public void responseTimeout(Duration timeout) {
		Assert.notNull(timeout, "'timeout' is required");
		this.responseTimeout = timeout;
	}

	public Configuration initJsonPathConfig() {
		if (this.jsonPathConfig != null) {
			return this.jsonPathConfig;
		}
		else if (jackson2Present) {
			return Jackson2Configuration.create();
		}
		else {
			return Configuration.builder().build();
		}
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
