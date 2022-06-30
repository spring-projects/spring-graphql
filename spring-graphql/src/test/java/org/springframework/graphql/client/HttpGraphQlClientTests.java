package org.springframework.graphql.client;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.server.webflux.GraphQlHttpHandler;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.test.web.reactive.server.HttpHandlerConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

public class HttpGraphQlClientTests {

    private static final String DOCUMENT = "{ Mutation }";

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    void shouldSendOneFile() {
        MultipartHttpBuilderSetup clientSetup = new MultipartHttpBuilderSetup();

        // Original header value
        HttpGraphQlClient.Builder<?> builder = clientSetup.initBuilder();

        HttpGraphQlClient client = builder.build();
        client.document(DOCUMENT)
                .variable("existingVar", "itsValue")
                .fileVariable("fileInput", new ClassPathResource("/foo.txt"))
                .executeFileUpload().block(TIMEOUT);
        assertThat(clientSetup.getActualRequest().getVariables().get("existingVar")).isEqualTo("itsValue");
        assertThat(clientSetup.getActualRequest().getVariables().get("fileInput")).isNotNull();
        assertThat(((FilePart)clientSetup.getActualRequest().getVariables().get("fileInput")).filename()).isEqualTo("foo.txt");
    }

    @Test
    void shouldSendOneCollectionOfFiles() {
        MultipartHttpBuilderSetup clientSetup = new MultipartHttpBuilderSetup();

        // Original header value
        HttpGraphQlClient.Builder<?> builder = clientSetup.initBuilder();

        HttpGraphQlClient client = builder.build();
        List<ClassPathResource> resources = new ArrayList<>();
        resources.add(new ClassPathResource("/foo.txt"));
        resources.add(new ClassPathResource("/bar.txt"));

        client.document(DOCUMENT)
                .variable("existingVar", "itsValue")
                .fileVariable("filesInput", resources)
                .executeFileUpload().block(TIMEOUT);
        assertThat(clientSetup.getActualRequest().getVariables().get("existingVar")).isEqualTo("itsValue");
        assertThat(clientSetup.getActualRequest().getVariables().get("filesInput")).isNotNull();
        assertThat(((Collection<FilePart>)clientSetup.getActualRequest().getVariables().get("filesInput")).size()).isEqualTo(2);
        assertThat(((Collection<FilePart>)clientSetup.getActualRequest().getVariables().get("filesInput")).stream().map(filePart -> filePart.filename()).collect(Collectors.toSet())).contains("foo.txt", "bar.txt");
    }

    private static class MultipartHttpBuilderSetup extends WebGraphQlClientBuilderTests.AbstractBuilderSetup {

        @Override
        public HttpGraphQlClient.Builder<?> initBuilder() {
            GraphQlHttpHandler handler = new GraphQlHttpHandler(webGraphQlHandler());
            RouterFunction<ServerResponse> routerFunction = route().POST("/**", handler::handleMultipartRequest).build();
            HttpHandler httpHandler = RouterFunctions.toHttpHandler(routerFunction, HandlerStrategies.withDefaults());
            HttpHandlerConnector connector = new HttpHandlerConnector(httpHandler);
            return HttpGraphQlClient.builder(WebClient.builder().clientConnector(connector));
        }

    }
}
