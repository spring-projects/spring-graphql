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
package org.springframework.graphql.client;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

import org.springframework.graphql.support.DocumentSource;
import org.springframework.graphql.support.ResourceDocumentSource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Default implementation of {@link GraphQlClient.Builder}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
class DefaultGraphQlClientBuilder implements GraphQlClient.Builder {

	private static final boolean jackson2Present;

	static {
		ClassLoader classLoader = DefaultGraphQlClientBuilder.class.getClassLoader();
		jackson2Present = ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", classLoader)
				&& ClassUtils.isPresent("com.fasterxml.jackson.core.JsonGenerator", classLoader);
	}


	private final GraphQlTransport transport;

	@Nullable
	private Configuration jsonPathConfig;

	@Nullable
	private DocumentSource documentSource;


	DefaultGraphQlClientBuilder(GraphQlTransport transport) {
		Assert.notNull(transport, "GraphQlTransport is required");
		this.transport = transport;
	}


	@Override
	public GraphQlClient.Builder jsonPathConfig(@Nullable Configuration config) {
		this.jsonPathConfig = config;
		return this;
	}

	@Override
	public GraphQlClient.Builder documentSource(@Nullable DocumentSource contentLoader) {
		this.documentSource = contentLoader;
		return this;
	}

	@Override
	public GraphQlClient build() {
		return new DefaultGraphQlClient(this.transport, initJsonPathConfig(), initRequestNameResolver());
	}

	private Configuration initJsonPathConfig() {
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

	private DocumentSource initRequestNameResolver() {
		return (this.documentSource == null ?
				new ResourceDocumentSource() : this.documentSource);
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
