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

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.graphql")
public class GraphQLProperties {

	/**
	 * Location of the GraphQL schema file.
	 */
	private String schemaLocation = "classpath:schema.graphqls";

	/**
	 * Path of the GraphQL HTTP query endpoint.
	 */
	private String path = "/graphql";

	private WebSocket websocket = new WebSocket();

	public String getPath() {
		return this.path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getSchemaLocation() {
		return this.schemaLocation;
	}

	public void setSchemaLocation(String schemaLocation) {
		this.schemaLocation = schemaLocation;
	}

	public WebSocket getWebsocket() {
		return this.websocket;
	}

	static class WebSocket {

		/**
		 * Path of the GraphQL WebSocket subscription endpoint.
		 */
		private String path;

		/**
		 * Time within which the initial {@code CONNECTION_INIT} type message must be received.
		 */
		private Duration connectionInitTimeout = Duration.ofSeconds(60);

		public String getPath() {
			return this.path;
		}

		public void setPath(String path) {
			this.path = path;
		}

		public Duration getConnectionInitTimeout() {
			return this.connectionInitTimeout;
		}

		public void setConnectionInitTimeout(Duration connectionInitTimeout) {
			this.connectionInitTimeout = connectionInitTimeout;
		}
	}
}
