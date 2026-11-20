/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.test.tester;

import java.util.Collections;
import java.util.Map;

import graphql.ExecutionResultImpl;
import graphql.GraphqlErrorBuilder;
import graphql.execution.ExecutionId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.support.DocumentSource;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.util.MimeType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GraphQlTester} builder with a mock {@link ExecutionGraphQlService}.
 *
 * @author Rossen Stoyanchev
 */
class GraphQlTesterBuilderTests extends GraphQlTesterTestSupport {

	private static final String DOCUMENT = "{ Query }";


	@Test
	void defaultDocumentSource() {
		String document = "{ greeting }";
		getGraphQlService().setDataAsJson(document, "{}");
		graphQlTester().documentName("greeting").execute();
		assertThat(getActualRequestDocument()).isEqualTo(document);
	}

	@Test
	void mutateDocumentSource() {

		DocumentSource documentSource = name -> name.equals("name") ?
				Mono.just(DOCUMENT) : Mono.error(new IllegalArgumentException());

		getGraphQlService().setDataAsJson(DOCUMENT, "{}");

		// Original
		ExecutionGraphQlServiceTester tester = graphQlTesterBuilder()
				.documentSource(documentSource)
				.configureExecutionInput((executionInput, builder) -> builder.operationName("A").build())
				.build();
		tester.documentName("name").execute();

		ExecutionGraphQlRequest request = getGraphQlService().getGraphQlRequest();
		assertThat(request.toExecutionInput().getOperationName()).isEqualTo("A");
		assertThat(request.getDocument()).isEqualTo(DOCUMENT);

		// Mutate
		ExecutionId id = ExecutionId.from("123");
		tester = tester.mutate()
				.configureExecutionInput((executionInput, builder) -> builder.executionId(id).build())
				.build();
		tester.documentName("name").execute();

		request = getGraphQlService().getGraphQlRequest();
		assertThat(request.toExecutionInput().getOperationName()).isEqualTo("A");
		assertThat(request.toExecutionInput().getExecutionId()).isEqualTo(id);
		assertThat(request.getDocument()).isEqualTo(DOCUMENT);
	}

	@Test
	void codecConfigurerRegistersJsonPathMappingProvider() {

		TestJacksonJsonDecoder testDecoder = new TestJacksonJsonDecoder();

		ExecutionGraphQlServiceTester.Builder<?> builder = graphQlTesterBuilder()
				.encoder(new JacksonJsonEncoder())
				.decoder(testDecoder);

		String document = "{me {name}}";
		MovieCharacter character = MovieCharacter.create("Luke Skywalker");
		getGraphQlService().setResponse(document,
				ExecutionResultImpl.newExecutionResult()
						.data(Collections.singletonMap("me", character))
						.build());

		GraphQlTester client = builder.build();
		GraphQlTester.Response response = client.document(document).execute();

		testDecoder.resetLastValue();
		assertThat(testDecoder.getLastValue()).isNull();

		assertThat(response).isNotNull();
		response.path("me").entity(MovieCharacter.class).isEqualTo(character);
		response.path("me").matchesJson("{name:\"Luke Skywalker\"}");
		assertThat(testDecoder.getLastValue()).isEqualTo(character);
	}

	@Test
	void errorsFilteredGlobally() {

		String document = "{me {name, friends}}";

		getGraphQlService().setErrors(
				document,
				GraphqlErrorBuilder.newError().message("some error").build(),
				GraphqlErrorBuilder.newError().message("some other error").build());

		graphQlTesterBuilder()
				.errorFilter((error) -> error.getMessage().startsWith("some "))
				.build()
				.document(document)
				.execute()
				.errors().verify()
				.path("me").pathDoesNotExist();

		assertThat(getActualRequestDocument()).contains(document);
	}


	private static class TestJacksonJsonDecoder extends JacksonJsonDecoder {

		private @Nullable Object lastValue;


		@Nullable Object getLastValue() {
			return this.lastValue;
		}

		@Override
		public Object decode(DataBuffer dataBuffer, ResolvableType targetType,
				@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) throws DecodingException {

			this.lastValue = super.decode(dataBuffer, targetType, mimeType, hints);
			return this.lastValue;
		}

		void resetLastValue() {
			this.lastValue = null;
		}

	}

}
