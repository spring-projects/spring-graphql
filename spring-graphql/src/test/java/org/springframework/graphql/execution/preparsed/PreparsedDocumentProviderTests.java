package org.springframework.graphql.execution.preparsed;

import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.execution.GraphQlSource;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for
 * {@link GraphQlSource.Builder#preparsedDocumentProvider(PreparsedDocumentProvider)}.
 */
public class PreparsedDocumentProviderTests {

	private static final String recursiveTestSchema = "type Query { query: Query! }";

	private static class CountingPreparsedDocumentProvider implements PreparsedDocumentProvider {

		// <query, counter>
		private final ConcurrentMap<String, Integer> counter;

		private CountingPreparsedDocumentProvider() {
			this.counter = new ConcurrentHashMap<>();
		}

		@Override
		public PreparsedDocumentEntry getDocument(ExecutionInput executionInput,
				Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
			counter.compute(executionInput.getQuery(), (k, v) -> v == null ? 1 : v + 1);
			return parseAndValidateFunction.apply(executionInput);
		}

		public int getCount(String query) {
			return counter.getOrDefault(query, 0);
		}

	}

	@Test
	public void correctDocumentProviderIsSet() {
		PreparsedDocumentProvider sample = new CountingPreparsedDocumentProvider();
		GraphQL graphQL = GraphQlSetup.schemaContent(recursiveTestSchema).preparsedDocumentProvider(sample).toGraphQl();
		assertThat(graphQL.getPreparsedDocumentProvider()).isEqualTo(sample);
	}

	@Test
	public void noOpProviderIsSetByDefault() {
		GraphQL graphQL = GraphQlSetup.schemaContent(recursiveTestSchema).toGraphQl();
		assertThat(graphQL.getPreparsedDocumentProvider()).isInstanceOf(SpringNoOpPreparsedDocumentProvider.class);
	}

	@Test
	public void preparsedDocumentProviderWorks() {
		CountingPreparsedDocumentProvider sample = new CountingPreparsedDocumentProvider();
		GraphQL graphQL = GraphQlSetup.schemaContent(recursiveTestSchema).preparsedDocumentProvider(sample).toGraphQl();
		graphQL.execute("{query  }");
		graphQL.execute("{query }");
		graphQL.execute("{query}");
		graphQL.execute("{query }");
		graphQL.execute("{query  }");
		graphQL.execute("{query }");
		graphQL.execute("{query  }");
		graphQL.execute("{query}");
		graphQL.execute("{query  }");
		assertThat(sample.getCount("{query}")).isEqualTo(2);
		assertThat(sample.getCount("{query }")).isEqualTo(3);
		assertThat(sample.getCount("{query  }")).isEqualTo(4);
		assertThat(sample.getCount("{ query }")).isEqualTo(0);
	}

}
