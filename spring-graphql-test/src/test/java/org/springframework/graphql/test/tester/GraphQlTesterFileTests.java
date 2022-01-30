package org.springframework.graphql.test.tester;

import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.graphql.GraphQlService;
import org.springframework.graphql.execution.ExecutionGraphQlService;
import org.springframework.graphql.execution.GraphQlSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link GraphQlTester#operationName(String)}.
 *
 * <p>
 * These tests use the files in
 * {@code spring-graphql-test/src/test/resources/graphql/*.{graphql,gql}}.
 */
public class GraphQlTesterFileTests {

	private final String ourTestSchema = "type Query { x: String, y: String! } type Mutation { x: String!, y: String }";

	private final RuntimeWiring testRuntimeWiring = RuntimeWiring.newRuntimeWiring()
			.type("Query", builder -> builder.dataFetcher("x", env -> null).dataFetcher("y", env -> "y"))
			.type("Mutation", builder -> builder.dataFetcher("x", env -> "x").dataFetcher("y", env -> null)).build();

	private final GraphQlService service = new ExecutionGraphQlService(GraphQlSource.builder()
			.schemaResources(new ByteArrayResource(ourTestSchema.getBytes(StandardCharsets.UTF_8)))
			.schemaFactory((tdr, rw) -> new SchemaGenerator().makeExecutableSchema(tdr, testRuntimeWiring)).build());

	private final GraphQlTester graphQlTester = GraphQlTester.create(this.service);

	@Test
	public void invalidOperationFileIsError() {
		assertThatThrownBy(() -> this.graphQlTester.operationName("unknownGqlOrGraphqlFile").execute())
				.isExactlyInstanceOf(IllegalArgumentException.class)
				.hasMessage("Could not find file 'unknownGqlOrGraphqlFile' with extensions [.graphql, .gql] "
						+ "under class path resource [graphql/]");
	}

	@Test
	public void testQuery1Gql() {
		GraphQlTester.ResponseSpec spec = this.graphQlTester.operationName("testQuery1").execute();
		spec.errors().verify();
		spec.path("x").valueDoesNotExist();
		spec.path("y").valueExists().entity(String.class).isEqualTo("y");
	}

	@Test
	public void testQuery2Graphql() {
		GraphQlTester.ResponseSpec spec = this.graphQlTester.operationName("testQuery2").execute();
		spec.errors().verify();
		spec.path("x").valueIsEmpty(); // TODO Should check for explicit null
		spec.path("y").valueDoesNotExist();
	}

	@Test
	public void testMutation1Gql() {
		GraphQlTester.ResponseSpec spec = this.graphQlTester.operationName("testMutation1").execute();
		spec.errors().verify();
		spec.path("x").valueExists().entity(String.class).isEqualTo("x");
		spec.path("y").valueIsEmpty(); // TODO Should check for explicit null
	}

	@Test
	public void testMutation2Graphql() {
		GraphQlTester.ResponseSpec spec = this.graphQlTester.operationName("testMutation2").execute();
		spec.errors().verify();
		spec.path("x").valueExists().entity(String.class).isEqualTo("x");
		spec.path("y").valueDoesNotExist();
	}

}
