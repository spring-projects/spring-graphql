package org.springframework.boot.graphql.reactive;

import graphql.ExecutionInput;
import graphql.ExecutionResultImpl;
import graphql.GraphQL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class IntegrationTest {

    @Autowired
    WebTestClient webClient;

    @Autowired
    GraphQL graphql;

    @Test
    public void endpointIsAvailable() {
        String query = "{foo}";

        ExecutionResultImpl executionResult = ExecutionResultImpl.newExecutionResult()
                .data("bar")
                .build();
        CompletableFuture cf = CompletableFuture.completedFuture(executionResult);
        ArgumentCaptor<ExecutionInput> captor = ArgumentCaptor.forClass(ExecutionInput.class);
        Mockito.when(graphql.executeAsync(captor.capture())).thenReturn(cf);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("query", query);

        Map<String, Object> expectedResult = new LinkedHashMap<>();
        expectedResult.put("data", "bar");

        this.webClient.post().uri("/graphql").body(BodyInserters.fromValue(body)).exchange().expectStatus().isOk()
                .expectBody(Map.class).isEqualTo(expectedResult);

        Assertions.assertThat(captor.getValue().getQuery()).isEqualTo(query);
    }

}