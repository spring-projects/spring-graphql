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
import java.util.function.Function;

import graphql.GraphQLError;
import reactor.core.publisher.Mono;

/**
 * Contract for a component that is invoked when a GraphQL subscription
 * {@link org.reactivestreams.Publisher} ends with an error.
 *
 * <p>Resolver implementations can extend the convenience base class
 * {@link SubscriptionExceptionResolverAdapter} and override one of its methods
 * {@link SubscriptionExceptionResolverAdapter#resolveToSingleError resolveToSingleError} or
 * {@link SubscriptionExceptionResolverAdapter#resolveToMultipleErrors resolveToMultipleErrors}
 * that resolve the exception synchronously.
 *
 * <p>Resolved errors are wrapped in a {@link SubscriptionPublisherException}
 * and propagated further to the underlying transport which access the errors
 * and prepare a final error message to send to the client.
 *
 * @author Mykyta Ivchenko
 * @author Rossen Stoyanchev
 * @since 1.0.1
 * @see SubscriptionExceptionResolverAdapter
 */
@FunctionalInterface
public interface SubscriptionExceptionResolver {

    /**
     * Resolve the given exception to a list of {@link GraphQLError}'s to be
     * sent in an error message to the client.
     * @param exception the exception from the Publisher
     * @return a {@code Mono} with the GraphQL errors to send to the client;
     * if the {@code Mono} completes with an empty List, the exception is resolved
     * without any errors to send; if the {@code Mono} completes empty, without
     * emitting a List, the exception remains unresolved, and that allows other
     * resolvers to resolve it.
     */
    Mono<List<GraphQLError>> resolveException(Throwable exception);


    /**
     * Factory method to create a {@link SubscriptionExceptionResolver} to
     * resolve an exception to a single GraphQL error. Effectively, a shortcut
     * for creating {@link SubscriptionExceptionResolverAdapter} and overriding
     * its {@code resolveToSingleError} method.
     * @param resolver the resolver function to map the exception
     * @return the created instance
     */
    static SubscriptionExceptionResolverAdapter forSingleError(Function<Throwable, GraphQLError> resolver) {
        return new SubscriptionExceptionResolverAdapter() {

            @Override
            protected GraphQLError resolveToSingleError(Throwable ex) {
                return resolver.apply(ex);
            }
        };
    }

}
