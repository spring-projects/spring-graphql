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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Predicate;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import graphql.GraphQLError;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.FileCopyUtils;

/**
 * Base class support for implementations of
 * {@link GraphQlTester.Builder} and {@link WebGraphQlTester.Builder}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
class GraphQlTesterBuilderSupport {

	private static final boolean jackson2Present;

	static {
		ClassLoader classLoader = GraphQlTesterBuilderSupport.class.getClassLoader();
		jackson2Present = ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", classLoader)
				&& ClassUtils.isPresent("com.fasterxml.jackson.core.JsonGenerator", classLoader);
	}

	private static final Duration DEFAULT_RESPONSE_DURATION = Duration.ofSeconds(5);


	@Nullable
	private Predicate<GraphQLError> errorFilter;

	@Nullable
	private Configuration jsonPathConfig;

	@Nullable
	private Duration responseTimeout;

	private final Function<String, String> operationNameResolver = new OperationNameResolver();


	protected void addErrorFilter(Predicate<GraphQLError> predicate) {
		this.errorFilter = (this.errorFilter != null ? errorFilter.and(predicate) : predicate);
	}

	@Nullable
	protected Predicate<GraphQLError> getErrorFilter() {
		return errorFilter;
	}

	protected void setJsonPathConfig(Configuration config) {
		this.jsonPathConfig = config;
	}

	protected void setResponseTimeout(Duration timeout) {
		Assert.notNull(timeout, "'timeout' is required");
		this.responseTimeout = timeout;
	}

	@Nullable
	protected Duration getResponseTimeout() {
		return this.responseTimeout;
	}

	protected Function<String, String> getOperationNameResolver() {
		return this.operationNameResolver;
	}

	protected Configuration initJsonPathConfig() {
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

	protected Duration initResponseTimeout() {
		return (this.responseTimeout != null ? this.responseTimeout : DEFAULT_RESPONSE_DURATION);
	}


	private static class OperationNameResolver implements Function<String, String> {

		private static final ClassPathResource LOCATION = new ClassPathResource("graphql/");

		private static final String[] EXTENSIONS = new String[] {".graphql", ".gql"};

		@Override
		public String apply(String operationName) {
			Resource operationResource = getOperationResource(operationName);
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			try {
				FileCopyUtils.copy(operationResource.getInputStream(), outputStream);
			}
			catch (IOException ex) {
				throw new IllegalArgumentException("Failed to read operation from: " + LOCATION.getPath());
			}
			return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
		}

		private Resource getOperationResource(String operationName) {
			for (String extension : EXTENSIONS) {
				Resource resource = LOCATION.createRelative(operationName + extension);
				if (resource.exists()) {
					return resource;
				}
			}
			throw new IllegalArgumentException(
					"Could not find file '" + operationName + "' with extensions " + Arrays.toString(EXTENSIONS) +
							" under " + LOCATION.getDescription());
		}
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
