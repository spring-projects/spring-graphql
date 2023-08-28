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
package org.springframework.graphql.data.method.annotation.support;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RequestHeaderMethodArgumentResolverTests}.
 *
 * @author Brian Clozel
 */
class RequestHeaderMethodArgumentResolverTests extends ArgumentResolverTestSupport {

    private final RequestHeaderMethodArgumentResolver resolver = new RequestHeaderMethodArgumentResolver();

    @Test
    void checkAuthHeader() throws Exception {
        String authHeader = "Random auth header";

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth");
        request.addHeader("Authorization", authHeader);

        GraphQLContext context = new GraphQLContext.Builder()
                .of(HttpServletRequest.class, request)
                .build();
        DataFetchingEnvironment environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                .graphQLContext(context)
                .build();

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Object result = this.resolver.resolveArgument(
                methodParam(AuthController.class, "checkAuth", String.class),
                environment);
        assertThat(result).isEqualTo(authHeader);
    }

    @Test
    void checkToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth");

        GraphQLContext context = new GraphQLContext.Builder()
                .of(HttpServletRequest.class, request)
                .build();
        DataFetchingEnvironment environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                .graphQLContext(context)
                .build();

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Object result = this.resolver.resolveArgument(
                methodParam(AuthController.class, "checkToken", String.class),
                environment);

        assertThat(result).isEqualTo("No token");
    }

    @Test
    void checkToken2() throws Exception {
        String token = "Random token";

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth");
        request.addHeader("Token", token);

        GraphQLContext context = new GraphQLContext.Builder()
                .of(HttpServletRequest.class, request)
                .build();
        DataFetchingEnvironment environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                .graphQLContext(context)
                .build();

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Object result = this.resolver.resolveArgument(
                methodParam(AuthController.class, "checkToken", String.class),
                environment);

        assertThat(result).isEqualTo(token);
    }

    @Test
    void checkUserAgent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth");

        GraphQLContext context = new GraphQLContext.Builder()
                .of(HttpServletRequest.class, request)
                .build();
        DataFetchingEnvironment environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                .graphQLContext(context)
                .build();

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Object result = this.resolver.resolveArgument(
                methodParam(AuthController.class, "checkUserAgent", String.class),
                environment);

        assertThat(result).isEqualTo(null);
    }


    @SuppressWarnings({"ConstantConditions", "unused"})
    @Controller("/auth")
    static class AuthController {

        @QueryMapping
        public String checkAuth(@RequestHeader("Authorization") String auth) {
            return auth;
        }

        @QueryMapping
        public String checkToken(@RequestHeader(value = "Token", defaultValue = "No token") String token) {
            return token;
        }

        @QueryMapping
        public String checkUserAgent(@RequestHeader(value = "User-Agent", required = false) String userAgent) {
            return userAgent;
        }

    }

}
