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
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Contract to resolve exceptions, that are thrown by subscription publisher.
 * Implementations are typically declared as beans in Spring configuration and
 * are invoked sequentially until one emits a List of {@link GraphQLError}s.
 * <br/>
 * Usually, it is enough to implement this interface by extending {@link SubscriptionSingleExceptionResolverAdapter}
 * and overriding one of its {@link SubscriptionSingleExceptionResolverAdapter#resolveToSingleError(Throwable)}
 * or {@link SubscriptionSingleExceptionResolverAdapter#resolveToSingleErrorMono(Throwable)}
 *
 * @author Mykyta Ivchenko
 * @see SubscriptionSingleExceptionResolverAdapter
 * @see DelegatingSubscriptionExceptionResolver
 * @see org.springframework.graphql.server.webflux.GraphQlWebSocketHandler
 */
@FunctionalInterface
public interface SubscriptionExceptionResolver {
    /**
     * Resolve given exception as list of {@link GraphQLError}s and send them as WebSocket message.
     * @param exception the exception to resolve
     * @return a {@code Mono} with errors to send in a WebSocket message;
     * if the {@code Mono} completes with an empty List, the exception is resolved
     * without any errors added to the response; if the {@code Mono} completes
     * empty, without emitting a List, the exception remains unresolved and gives
     * other resolvers a chance.
     */
    Mono<List<GraphQLError>> resolveException(Throwable exception);
}
