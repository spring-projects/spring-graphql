/*
 * Copyright 2020-2024 the original author or authors.
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

package org.springframework.graphql.server.webmvc;


import java.util.Collections;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GraphQlRequestPredicates}.
 *
 * @author Brian Clozel
 */
class GraphQlRequestPredicatesTests {

    @Nested
    class HttpPredicatesTests {

        RequestPredicate httpPredicate = GraphQlRequestPredicates.graphQlHttp("/graphql");

        @Test
        void shouldAcceptGraphQlHttpRequest() {
            MockHttpServletRequest request = createMatchingHttpRequest();
            ServerRequest serverRequest = ServerRequest.create(request, Collections.emptyList());
            assertThat(httpPredicate.test(serverRequest)).isTrue();
        }

        @Test
        void shouldAcceptCorsRequest() {
            MockHttpServletRequest request = createMatchingHttpRequest();
            request.setMethod("OPTIONS");
            request.addHeader("Origin", "https://example.com");
            request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
            ServerRequest serverRequest = ServerRequest.create(request, Collections.emptyList());
            assertThat(httpPredicate.test(serverRequest)).isTrue();
        }

        @Test
        void shouldRejectRequestWithGetMethod() {
            MockHttpServletRequest request = createMatchingHttpRequest();
            request.setMethod("GET");
            ServerRequest serverRequest = ServerRequest.create(request, Collections.emptyList());
            assertThat(httpPredicate.test(serverRequest)).isFalse();
        }

        @Test
        void shouldRejectRequestWithDifferentPath() {
            MockHttpServletRequest request = createMatchingHttpRequest();
            request.setRequestURI("/invalid");
            ServerRequest serverRequest = ServerRequest.create(request, Collections.emptyList());
            assertThat(httpPredicate.test(serverRequest)).isFalse();
        }

        @Test
        void shouldRejectRequestWithDifferentContentType() {
            MockHttpServletRequest request = createMatchingHttpRequest();
            request.setContentType("text/xml");
            ServerRequest serverRequest = ServerRequest.create(request, Collections.emptyList());
            assertThat(httpPredicate.test(serverRequest)).isFalse();
        }

        @Test
        void shouldRejectRequestWithIncompatibleAccept() {
            MockHttpServletRequest request = createMatchingHttpRequest();
            request.removeHeader("Accept");
            request.addHeader("Accept", "text/xml");
            ServerRequest serverRequest = ServerRequest.create(request, Collections.emptyList());
            assertThat(httpPredicate.test(serverRequest)).isFalse();
        }

        private MockHttpServletRequest createMatchingHttpRequest() {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/graphql");
            request.setContentType("application/json");
            request.addHeader("Accept", "application/graphql-response+json");
            return request;
        }

    }

    @Nested
    class SsePredicatesTests {

        RequestPredicate ssePredicate = GraphQlRequestPredicates.graphQlSse("/graphql");

        @Test
        void shouldAcceptGraphQlSseRequest() {
            MockHttpServletRequest request = createMatchingSseRequest();
            ServerRequest serverRequest = ServerRequest.create(request, Collections.emptyList());
            assertThat(ssePredicate.test(serverRequest)).isTrue();
        }

        @Test
        void shouldAcceptCorsRequest() {
            MockHttpServletRequest request = createMatchingSseRequest();
            request.setMethod("OPTIONS");
            request.addHeader("Origin", "https://example.com");
            request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
            ServerRequest serverRequest = ServerRequest.create(request, Collections.emptyList());
            assertThat(ssePredicate.test(serverRequest)).isTrue();
        }

        @Test
        void shouldRejectRequestWithGetMethod() {
            MockHttpServletRequest request = createMatchingSseRequest();
            request.setMethod("GET");
            ServerRequest serverRequest = ServerRequest.create(request, Collections.emptyList());
            assertThat(ssePredicate.test(serverRequest)).isFalse();
        }

        @Test
        void shouldRejectRequestWithDifferentPath() {
            MockHttpServletRequest request = createMatchingSseRequest();
            request.setRequestURI("/invalid");
            ServerRequest serverRequest = ServerRequest.create(request, Collections.emptyList());
            assertThat(ssePredicate.test(serverRequest)).isFalse();
        }

        @Test
        void shouldRejectRequestWithDifferentContentType() {
            MockHttpServletRequest request = createMatchingSseRequest();
            request.setContentType("text/xml");
            ServerRequest serverRequest = ServerRequest.create(request, Collections.emptyList());
            assertThat(ssePredicate.test(serverRequest)).isFalse();
        }

        @Test
        void shouldRejectRequestWithIncompatibleAccept() {
            MockHttpServletRequest request = createMatchingSseRequest();
            request.removeHeader("Accept");
            request.addHeader("Accept", "text/xml");
            ServerRequest serverRequest = ServerRequest.create(request, Collections.emptyList());
            assertThat(ssePredicate.test(serverRequest)).isFalse();
        }

        private MockHttpServletRequest createMatchingSseRequest() {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/graphql");
            request.addHeader("Content-Type", "application/json");
            request.addHeader("Accept", "text/event-stream");
            return request;
        }
    }

}
