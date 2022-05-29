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

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

/**
 * An implementation of {@link SubscriptionExceptionResolver} that is trying to map exception to GraphQL error
 * using provided implementation of {@link SubscriptionExceptionResolver}.
 * <br/>
 * If none of provided implementations resolve exception to error or if any of implementation throw an exception,
 * this {@link SubscriptionExceptionResolver} will return a default error.
 *
 * @author Mykyta Ivchenko
 * @see SubscriptionExceptionResolver
 */
public class DelegatingSubscriptionExceptionResolver implements SubscriptionExceptionResolver {
    private static final Log logger = LogFactory.getLog(DelegatingSubscriptionExceptionResolver.class);
    private final List<SubscriptionExceptionResolver> resolvers;
    private final List<GraphQLError> defaultErrors;

    public DelegatingSubscriptionExceptionResolver(
            List<SubscriptionExceptionResolver> resolvers, List<GraphQLError> defaultErrors) {
        this.resolvers = resolvers;
        this.defaultErrors = defaultErrors;
    }

    public DelegatingSubscriptionExceptionResolver(List<SubscriptionExceptionResolver> resolvers) {
        this(resolvers, Collections.singletonList(GraphqlErrorBuilder.newError()
                .message("Unknown error")
                .errorType(ErrorType.DataFetchingException)
                .build()));
    }

    @Override
    public Mono<List<GraphQLError>> resolveException(Throwable exception) {
        return Flux.fromIterable(resolvers)
                .flatMap(resolver -> resolver.resolveException(exception))
                .next()
                .onErrorResume(error -> Mono.just(handleMappingException(error, exception)))
                .switchIfEmpty(Mono.just(defaultErrors));
    }

    private List<GraphQLError> handleMappingException(Throwable resolverException, Throwable originalException) {
        if (logger.isWarnEnabled()) {
            logger.warn("Failure while resolving " + originalException.getClass().getName(), resolverException);
        }
        return defaultErrors;
    }
}
