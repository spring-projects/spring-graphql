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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@link ConfigurationProperties properties} for Spring GraphQL.
 *
 * @author Brian Clozel
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "spring.graphql")
public class GraphQlProperties {

	/**
	 * Path at which to expose a GraphQL request HTTP endpoint.
	 */
	private String path = "/graphql";

	private final Schema schema = new Schema();

	private final GraphiQL graphiql = new GraphiQL();

	private final Websocket websocket = new Websocket();

	public String getPath() {
		return this.path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public Schema getSchema() {
		return this.schema;
	}

	public GraphiQL getGraphiql() {
		return this.graphiql;
	}

	public Websocket getWebsocket() {
		return this.websocket;
	}

	public static class Schema {

		/**
		 * Locations of GraphQL '*.graphqls' schema files.
		 */
		private List<String> locations = new ArrayList<>(Collections.singletonList("classpath:graphql/"));

		private final Printer printer = new Printer();

		public List<String> getLocations() {
			return this.locations;
		}

		public void setLocations(List<String> locations) {
			this.locations = appendSlashIfNecessary(locations);
		}

		private List<String> appendSlashIfNecessary(List<String> locations) {
			return locations.stream()
					.map(location -> location.endsWith("/") ? location : location + "/")
					.collect(Collectors.toList());
		}

		public Printer getPrinter() {
			return this.printer;
		}

		public static class Printer {

			/**
			 * Whether the endpoint that prints the schema is enabled.
			 */
			private boolean enabled = false;

			public boolean isEnabled() {
				return this.enabled;
			}

			public void setEnabled(boolean enabled) {
				this.enabled = enabled;
			}

		}

	}

	public static class GraphiQL {

		/**
		 * Path to the GraphiQL UI endpoint.
		 */
		private String path = "/graphiql";

		/**
		 * Whether the default GraphiQL UI is enabled.
		 */
		private boolean enabled = true;

		public String getPath() {
			return this.path;
		}

		public void setPath(String path) {
			this.path = path;
		}

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}
	}

	public static class Websocket {

		/**
		 * Path of the GraphQL WebSocket subscription endpoint.
		 */
		private String path;

		/**
		 * Time within which the initial {@code CONNECTION_INIT} type message must be
		 * received.
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
