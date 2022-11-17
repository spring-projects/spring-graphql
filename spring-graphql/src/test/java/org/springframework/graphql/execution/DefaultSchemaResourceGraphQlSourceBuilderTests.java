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
import java.util.concurrent.atomic.AtomicInteger;

import graphql.Scalars;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitor;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.idl.FieldWiringEnvironment;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.WiringFactory;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import org.junit.jupiter.api.Test;

import org.springframework.graphql.BookSource;
import org.springframework.graphql.GraphQlSetup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link DefaultSchemaResourceGraphQlSourceBuilder}.
 *
 * @author Rossen Stoyanchev
 */
public class DefaultSchemaResourceGraphQlSourceBuilderTests {

	@Test // gh-230
	void duplicateResourcesAreIgnored() {
		// This should not fail with schema errors
		GraphQlSetup.schemaResource(BookSource.schema, BookSource.schema).toGraphQlSource();
	}

	@Test
	void typeVisitors() {

		AtomicInteger counter = new AtomicInteger();

		GraphQLTypeVisitor visitor = new GraphQLTypeVisitorStub() {

			@Override
			public TraversalControl visitGraphQLObjectType(
					GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {

				counter.incrementAndGet();
				return TraversalControl.CONTINUE;
			}
		};

		GraphQlSetup.schemaContent("type Query {  myQuery: String}").typeVisitor(visitor).toGraphQlSource();

		assertThat(counter.get()).isPositive();
	}

	@Test
	void typeVisitorToTransformSchema() {

		String schemaContent = "" +
				"type Query {" +
				"  person: Person" +
				"} " +
				"type Person {" +
				"  firstName: String" +
				"}";

		GraphQLTypeVisitor visitor = new GraphQLTypeVisitorStub() {

			@Override
			public TraversalControl visitGraphQLObjectType(
					GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {

				if (node.getName().equals("Person")) {
					node = node.transform(builder -> builder.field(
							GraphQLFieldDefinition.newFieldDefinition()
									.name("lastName")
									.type(Scalars.GraphQLString)
									.build()));
					changeNode(context, node);
				}

				return TraversalControl.CONTINUE;
			}
		};

		GraphQLSchema schema = GraphQlSetup.schemaContent(schemaContent)
				.typeVisitorToTransformSchema(visitor)
				.toGraphQlSource()
				.schema();

		assertThat(schema.getObjectType("Person").getFieldDefinition("lastName")).isNotNull();
	}

	@Test
	void wiringFactoryList() {

		String schemaContent = "type Query {" +
				"  q1: String" +
				"  q2: String" +
				"}";

		DataFetcher<?> dataFetcher1 = mock(DataFetcher.class);
		DataFetcher<?> dataFetcher2 = mock(DataFetcher.class);

		RuntimeWiringConfigurer configurer = new RuntimeWiringConfigurer() {

			@Override
			public void configure(RuntimeWiring.Builder builder) {
			}

			@Override
			public void configure(RuntimeWiring.Builder builder, List<WiringFactory> container) {
				container.add(new DataFetcherWiringFactory("q1", dataFetcher1));
				container.add(new DataFetcherWiringFactory("q2", dataFetcher2));
			}
		};

		GraphQLSchema schema = GraphQlSetup.schemaContent(schemaContent)
				.runtimeWiring(configurer)
				.toGraphQlSource()
				.schema();

		assertThat(getDataFetcherForQuery(schema, "q1")).isSameAs(dataFetcher1);
		assertThat(getDataFetcherForQuery(schema, "q2")).isSameAs(dataFetcher2);
	}

	@Test
	void wiringFactoryListAndBuilderWiringFactory() {

		String schemaContent = "type Query {" +
				"  q1: String" +
				"  q2: String" +
				"}";

		DataFetcher<?> dataFetcher1 = mock(DataFetcher.class);
		DataFetcher<?> dataFetcher2 = mock(DataFetcher.class);

		RuntimeWiringConfigurer configurer = new RuntimeWiringConfigurer() {

			@Override
			public void configure(RuntimeWiring.Builder builder) {
				builder.wiringFactory(new DataFetcherWiringFactory("q1", dataFetcher1));
			}

			@Override
			public void configure(RuntimeWiring.Builder builder, List<WiringFactory> container) {
				container.add(new DataFetcherWiringFactory("q2", dataFetcher2));
			}
		};

		GraphQLSchema schema = GraphQlSetup.schemaContent(schemaContent)
				.runtimeWiring(configurer)
				.toGraphQlSource()
				.schema();

		assertThat(getDataFetcherForQuery(schema, "q1")).isSameAs(dataFetcher1);
		assertThat(getDataFetcherForQuery(schema, "q2")).isSameAs(dataFetcher2);
	}

	private DataFetcher<?> getDataFetcherForQuery(GraphQLSchema schema, String query) {
		FieldCoordinates coordinates = FieldCoordinates.coordinates("Query", query);
		GraphQLFieldDefinition fieldDefinition = schema.getFieldDefinition(coordinates);
		return schema.getCodeRegistry().getDataFetcher(coordinates, fieldDefinition);
	}


	private static class DataFetcherWiringFactory implements WiringFactory {

		private final String queryName;

		private final DataFetcher<?> dataFetcher;

		DataFetcherWiringFactory(String queryName, DataFetcher<?> dataFetcher) {
			this.queryName = queryName;
			this.dataFetcher = dataFetcher;
		}

		@Override
		public boolean providesDataFetcher(FieldWiringEnvironment environment) {
			return (environment.getParentType().getName().equals("Query") &&
					environment.getFieldDefinition().getName().equals(this.queryName));
		}

		@Override
		public DataFetcher<?> getDataFetcher(FieldWiringEnvironment environment) {
			return this.dataFetcher;
		}

	}

}
