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

import java.util.Collections;
import java.util.List;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.util.Assert;

/**
 * Implementation of {@link SubscriptionExceptionResolver} that is given a list
 * of other {@link SubscriptionExceptionResolver}'s to invoke in turn until one
 * returns a list of {@link GraphQLError}'s.
 *
 * <p>If the exception remains unresolved, it is mapped to a default error of
 * type {@link ErrorType#INTERNAL_ERROR} with a generic message.
 *
 * @author Mykyta Ivchenko
 * @author Rossen Stoyanchev
 * @since 1.0.1
 */
class CompositeSubscriptionExceptionResolver implements SubscriptionExceptionResolver {

    private static final Log logger = LogFactory.getLog(CompositeSubscriptionExceptionResolver.class);

    private final List<SubscriptionExceptionResolver> resolvers;


    CompositeSubscriptionExceptionResolver(List<SubscriptionExceptionResolver> resolvers) {
        Assert.notNull(resolvers, "'resolvers' is required");
        this.resolvers = resolvers;
    }


    @Override
    public Mono<List<GraphQLError>> resolveException(Throwable exception) {
        return Flux.fromIterable(this.resolvers)
                .flatMap(resolver -> resolver.resolveException(exception))
                .next()
                .onErrorResume(error -> Mono.just(handleResolverException(error, exception)))
                .defaultIfEmpty(createDefaultError());
    }

    private List<GraphQLError> handleResolverException(
            Throwable resolverException, Throwable originalException) {

        if (logger.isWarnEnabled()) {
            logger.warn("Failure while resolving " + originalException.getClass().getName(), resolverException);
        }
        return createDefaultError();
    }

    private List<GraphQLError> createDefaultError() {
        return Collections.singletonList(GraphqlErrorBuilder.newError()
                .message("Subscription error")
                .errorType(ErrorType.INTERNAL_ERROR)
                .build());
    }

}
