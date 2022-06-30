package org.springframework.graphql.test.tester;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.server.webflux.GraphQlHttpHandler;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

public class HttpGraphQlTesterTests {

    private static final String DOCUMENT = "{ Mutation }";

    @Test
    void shouldSendOneFile() {
        MultipartHttpBuilderSetup testerSetup = new MultipartHttpBuilderSetup();

        HttpGraphQlTester.Builder<?> builder = testerSetup.initBuilder();
        HttpGraphQlTester tester = builder.build();
        tester.document(DOCUMENT)
                .variable("existingVar", "itsValue")
                .fileVariable("fileInput", new ClassPathResource("/foo.txt"))
                .executeFileUpload();
        assertThat(testerSetup.getWebGraphQlRequest().getVariables().get("existingVar")).isEqualTo("itsValue");
        assertThat(testerSetup.getWebGraphQlRequest().getVariables().get("fileInput")).isNotNull();
        assertThat(((FilePart)testerSetup.getWebGraphQlRequest().getVariables().get("fileInput")).filename()).isEqualTo("foo.txt");
    }

    @Test
    void shouldSendOneCollectionOfFiles() {
        MultipartHttpBuilderSetup testerSetup = new MultipartHttpBuilderSetup();

        HttpGraphQlTester.Builder<?> builder = testerSetup.initBuilder();
        HttpGraphQlTester tester = builder.build();
        List<ClassPathResource> resources = new ArrayList<>();
        resources.add(new ClassPathResource("/foo.txt"));
        resources.add(new ClassPathResource("/bar.txt"));
        tester.document(DOCUMENT)
                .variable("existingVar", "itsValue")
                .fileVariable("filesInput", resources)
                .executeFileUpload();
        assertThat(testerSetup.getWebGraphQlRequest().getVariables().get("existingVar")).isEqualTo("itsValue");
        assertThat(testerSetup.getWebGraphQlRequest().getVariables().get("filesInput")).isNotNull();
        assertThat(((Collection<FilePart>)testerSetup.getWebGraphQlRequest().getVariables().get("filesInput")).size()).isEqualTo(2);
        assertThat(((Collection<FilePart>)testerSetup.getWebGraphQlRequest().getVariables().get("filesInput")).stream().map(filePart -> filePart.filename()).collect(Collectors.toSet())).contains("foo.txt", "bar.txt");
    }

    private static class MultipartHttpBuilderSetup extends WebGraphQlTesterBuilderTests.WebBuilderSetup {

        @Override
        public HttpGraphQlTester.Builder<?> initBuilder() {
            GraphQlHttpHandler handler = new GraphQlHttpHandler(webGraphQlHandler());
            RouterFunction<ServerResponse> routerFunction = route().POST("/**", handler::handleMultipartRequest).build();
            return HttpGraphQlTester.builder(WebTestClient.bindToRouterFunction(routerFunction).configureClient());
        }

    }
}
