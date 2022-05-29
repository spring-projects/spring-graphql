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

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class DelegatingSubscriptionExceptionResolverTest {
    @Test
    void resolveException() {
        List<GraphQLError> expectedErrors = Collections.singletonList(GraphqlErrorBuilder.newError()
                .message("CustomError")
                .errorType(ErrorType.NOT_FOUND)
                .build());

        List<SubscriptionExceptionResolver> resolvers = new ArrayList<>();
        resolvers.add(new SubscriptionSingleExceptionResolverAdapter() {});
        resolvers.add(new SubscriptionSingleExceptionResolverAdapter() {
            @Override
            protected GraphQLError resolveToSingleError(Throwable exception) {
                return GraphqlErrorBuilder.newError()
                        .message("CustomError")
                        .errorType(ErrorType.NOT_FOUND)
                        .build();
            }
        });

        DelegatingSubscriptionExceptionResolver resolver = new DelegatingSubscriptionExceptionResolver(resolvers);
        List<GraphQLError> actual = resolver.resolveException(new RuntimeException()).block();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expectedErrors);
    }

    @Test
    void resolveExceptionAsEmptyList() {
        List<SubscriptionExceptionResolver> resolvers = new ArrayList<>();
        resolvers.add(new SubscriptionSingleExceptionResolverAdapter() {});
        resolvers.add(exception -> Mono.just(Collections.emptyList()));

        DelegatingSubscriptionExceptionResolver resolver = new DelegatingSubscriptionExceptionResolver(resolvers);
        List<GraphQLError> actual = resolver.resolveException(new RuntimeException()).block();
        assertThat(actual).usingRecursiveComparison().isEqualTo(Collections.emptyList());
    }

    @Test
    void resolveExceptionWithDefaultError() {
        List<GraphQLError> expectedErrors = Collections.singletonList(GraphqlErrorBuilder.newError()
                .message("DefaultError")
                .errorType(ErrorType.INTERNAL_ERROR)
                .build());

        DelegatingSubscriptionExceptionResolver resolver = new DelegatingSubscriptionExceptionResolver(
                Collections.emptyList(), Collections.singletonList(GraphqlErrorBuilder.newError()
                .message("DefaultError")
                .errorType(ErrorType.INTERNAL_ERROR)
                .build()));

        List<GraphQLError> actual = resolver.resolveException(new RuntimeException()).block();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expectedErrors);
    }

    @Test
    void resolveExceptionWithExceptionInResolver() {
        List<GraphQLError> expectedErrors = Collections.singletonList(GraphqlErrorBuilder.newError()
                .message("DefaultError")
                .errorType(ErrorType.INTERNAL_ERROR)
                .build());

        DelegatingSubscriptionExceptionResolver resolver = new DelegatingSubscriptionExceptionResolver(
                Collections.singletonList(ex -> Mono.error(new RuntimeException())),
                Collections.singletonList(GraphqlErrorBuilder.newError()
                        .message("DefaultError")
                        .errorType(ErrorType.INTERNAL_ERROR)
                        .build()));

        List<GraphQLError> actual = resolver.resolveException(new RuntimeException()).block();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expectedErrors);
    }
}