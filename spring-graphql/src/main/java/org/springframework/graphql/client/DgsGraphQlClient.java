/*
 * Copyright 2002-present the original author or authors.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLMultiQueryRequest;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import graphql.schema.Coercing;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.util.Assert;

/**
 * Simple wrapper around a {@link GraphQlClient} that prepares the request
 * from classes generated with the
 * <a href="https://github.com/Netflix/dgs-codegen">DGS Code Generation</a> library.
 *
 * <pre class="code">
 * GraphQlClient client = ... ;
 * DgsGraphQlClient dgsClient = DgsGraphQlClient.create(client);
 *
 * List&lt;Book&gt; books = dgsClient.request(new BooksGraphQLQuery())
 * 		.projection(new BooksProjectionRoot&lt;&gt;().id().name())
 * 		.retrieveSync()
 * 		.toEntityList(Book.class);
 * </pre>
 *
 * @author Rossen Stoyanchev
 * @since 1.3.0
 */
public final class DgsGraphQlClient {

	private final GraphQlClient graphQlClient;


	private DgsGraphQlClient(GraphQlClient graphQlClient) {
		this.graphQlClient = graphQlClient;
	}


	/**
	 * Return the wrapped {@link GraphQlClient} to delegate to.
	 */
	public GraphQlClient getGraphQlClient() {
		return this.graphQlClient;
	}

	/**
	 * Start defining a GraphQL request for the given {@link GraphQLQuery}.
	 * @param query the GraphQL query
	 */
	public RequestSpec request(GraphQLQuery query) {
		return new RequestSpec(query);
	}


	/**
	 * Create instance that wraps the given {@link GraphQlClient}.
	 * @param client the client to delegate to
	 */
	public static DgsGraphQlClient create(GraphQlClient client) {
		return new DgsGraphQlClient(client);
	}


	/**
	 * Declare options to gather input for a GraphQL request and execute it.
	 */
	public final class RequestSpec {

		private final GraphQLQuery query;

		private final List<GraphQLQueryRequest> additionalRequests;

		private @Nullable BaseProjectionNode projectionNode;

		private @Nullable Map<Class<?>, Coercing<?, ?>> coercingMap;

		private @Nullable Map<String, Object> attributes;

		private RequestSpec(GraphQLQuery query) {
			this(query, Collections.emptyList(), null);
		}

		private RequestSpec(GraphQLQuery query, List<GraphQLQueryRequest> additionalRequests,
							@Nullable Map<String, Object> existingAttributes) {
			Assert.notNull(query, "Expected GraphQLQuery");
			Assert.notNull(additionalRequests, "Expected additionalRequests");
			this.query = query;
			this.additionalRequests = additionalRequests;
			if (existingAttributes != null) {
				attributes((attr) -> attr.putAll(existingAttributes));
			}
		}

		/**
		 * Configure an alias for the current query.
		 * @param queryAlias the alias for this query
		 * @return the same builder instance
		 */
		public RequestSpec queryAlias(String queryAlias) {
			this.query.setQueryAlias(queryAlias);
			return this;
		}

		/**
		 * Provide a {@link BaseProjectionNode} that defines the response selection set.
		 * @param projectionNode the response selection set
		 * @return the same builder instance
		 */
		public RequestSpec projection(BaseProjectionNode projectionNode) {
			this.projectionNode = projectionNode;
			return this;
		}

		/**
		 * Configure {@link Coercing} for serialization of scalar types.
		 * @param scalarType the scalar type
		 * @param coercing the coercing function for this scalar
		 * @return the same builder instance
		 */
		public RequestSpec coercing(Class<?> scalarType, Coercing<?, ?> coercing) {
			this.coercingMap = (this.coercingMap != null) ? this.coercingMap : new LinkedHashMap<>();
			this.coercingMap.put(scalarType, coercing);
			return this;
		}

		/**
		 * Configure {@link Coercing} for serialization of scalar types.
		 * @param coercingMap the map of coercing function
		 * @return the same builder instance
		 */
		public RequestSpec coercing(Map<Class<?>, Coercing<?, ?>> coercingMap) {
			this.coercingMap = (this.coercingMap != null) ? this.coercingMap : new LinkedHashMap<>();
			this.coercingMap.putAll(coercingMap);
			return this;
		}

		/**
		 * Set a client request attribute.
		 * <p>This is purely for client side request processing, i.e. available
		 * throughout the {@link GraphQlClientInterceptor} chain but not sent.
		 * @param name the attribute name
		 * @param value the attribute value
		 * @return the same builder instance
		 */
		public RequestSpec attribute(String name, Object value) {
			this.attributes = (this.attributes != null) ? this.attributes : new HashMap<>();
			this.attributes.put(name, value);
			return this;
		}

		/**
		 * Manipulate the client request attributes. The map provided to the consumer
		 * is "live", so the consumer can inspect and modify attributes accordingly.
		 * @param attributesConsumer the consumer that will manipulate request attributes
		 * @return the same builder instance
		 */
		public RequestSpec attributes(Consumer<Map<String, Object>> attributesConsumer) {
			this.attributes = (this.attributes != null) ? this.attributes : new HashMap<>();
			attributesConsumer.accept(this.attributes);
			return this;
		}

		/**
		 * Define an additional GraphQL request for the given {@link GraphQLQuery},
		 * resulting in a {@link GraphQLMultiQueryRequest} being sent.
		 * @param query the GraphQL query
		 * @see #queryAlias(String)
		 */
		public RequestSpec request(GraphQLQuery query) {
			List<GraphQLQueryRequest> otherRequests = new ArrayList<>(this.additionalRequests);
			otherRequests.add(createRequest());
			return new RequestSpec(query, otherRequests, this.attributes);
		}

		/**
		 * Create {@link GraphQLQueryRequest}, serialize it to a String document
		 * to send, and delegate to the wrapped {@code GraphQlClient}.
		 * <p>See Javadoc of delegate method
		 * {@link GraphQlClient.RequestSpec#retrieveSync(String)} for details.
		 * The path used is the operationName.
		 */
		public GraphQlClient.RetrieveSyncSpec retrieveSync() {
			return initRequestSpec().retrieveSync(getDefaultPath());
		}

		/**
		 * Variant of {@link #executeSync()} with explicit path relative to the "data" key.
		 * @param path the JSON path relative to the "data" key
		 */
		public GraphQlClient.RetrieveSyncSpec retrieveSync(String path) {
			return initRequestSpec().retrieveSync(path);
		}

		/**
		 * Create {@link GraphQLQueryRequest}, serialize it to a String document
		 * to send, and delegate to the wrapped {@code GraphQlClient}.
		 * <p>See Javadoc of delegate method
		 * {@link GraphQlClient.RequestSpec#retrieve(String)} for details.
		 * The path used is the operationName.
		 */
		public GraphQlClient.RetrieveSpec retrieve() {
			return initRequestSpec().retrieve(getDefaultPath());
		}

		/**
		 * Variant of {@link #retrieve()} with explicit path relative to the "data" key.
		 * @param path the JSON path relative to the "data" key
		 */
		public GraphQlClient.RetrieveSpec retrieve(String path) {
			return initRequestSpec().retrieve(path);
		}

		/**
		 * Create {@link GraphQLQueryRequest}, serialize it to a String document
		 * to send, and delegate to the wrapped {@code GraphQlClient}.
		 * <p>See Javadoc of delegate method
		 * {@link GraphQlClient.RequestSpec#retrieveSubscription(String)} for details.
		 * The path used is the operationName.
		 */
		public GraphQlClient.RetrieveSubscriptionSpec retrieveSubscription() {
			return initRequestSpec().retrieveSubscription(getDefaultPath());
		}

		/**
		 * Create {@link GraphQLQueryRequest}, serialize it to a String document
		 * to send, and delegate to the wrapped {@code GraphQlClient}.
		 * <p>See Javadoc of delegate method
		 * {@link GraphQlClient.RequestSpec#executeSync()} for details.
		 */
		public ClientGraphQlResponse executeSync() {
			return initRequestSpec().executeSync();
		}

		/**
		 * Create {@link GraphQLQueryRequest}, serialize it to a String document
		 * to send, and delegate to the wrapped {@code GraphQlClient}.
		 * <p>See Javadoc of delegate method
		 * {@link GraphQlClient.RequestSpec#execute()} for details.
		 */
		public Mono<ClientGraphQlResponse> execute() {
			return initRequestSpec().execute();
		}

		/**
		 * Create {@link GraphQLQueryRequest}, serialize it to a String document
		 * to send, and delegate to the wrapped {@code GraphQlClient}.
		 * <p>See Javadoc of delegate method
		 * {@link GraphQlClient.RequestSpec#executeSubscription()} for details.
		 */
		public Flux<ClientGraphQlResponse> executeSubscription() {
			return initRequestSpec().executeSubscription();
		}

		private GraphQLQueryRequest createRequest() {
			Assert.state(this.projectionNode != null || this.coercingMap == null,
					"Coercing map provided without projection");

			GraphQLQueryRequest request;
			if (this.coercingMap != null && this.projectionNode != null) {
				request = new GraphQLQueryRequest(this.query, this.projectionNode, this.coercingMap);
			}
			else if (this.projectionNode != null) {
				request = new GraphQLQueryRequest(this.query, this.projectionNode);
			}
			else {
				request = new GraphQLQueryRequest(this.query);
			}
			return request;
		}

		@SuppressWarnings("NullAway")
		private GraphQlClient.RequestSpec initRequestSpec() {
			String document;
			String operationName;
			if (!this.additionalRequests.isEmpty()) {
				this.additionalRequests.add(createRequest());
				document = new GraphQLMultiQueryRequest(this.additionalRequests).serialize();
				operationName = null;
			}
			else {
				document = createRequest().serialize();
				operationName = (this.query.getName() != null) ? this.query.getName() : null;
			}

			return DgsGraphQlClient.this.graphQlClient.document(document)
					.operationName(operationName)
					.attributes((map) -> {
						if (this.attributes != null) {
							map.putAll(this.attributes);
						}
					});
		}

		private String getDefaultPath() {
			return this.query.getOperationName();
		}
	}

}
