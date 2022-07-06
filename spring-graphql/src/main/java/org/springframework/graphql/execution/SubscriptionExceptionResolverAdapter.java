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

import java.util.Collections;
import java.util.List;

/**
 * Abstract class for {@link SubscriptionExceptionResolver} implementations.
 * This class provide an easy way to map an exception as GraphQL error synchronously.
 * <br/>
 * To use this class, you need to override either {@link SubscriptionExceptionResolverAdapter#resolveToSingleError(Throwable)}
 * or {@link SubscriptionExceptionResolverAdapter#resolveToMultipleErrors(Throwable)}.
 *
 * @author Mykyta Ivchenko
 * @see SubscriptionExceptionResolver
 */
public abstract class SubscriptionExceptionResolverAdapter implements SubscriptionExceptionResolver {
    @Override
    public Mono<List<GraphQLError>> resolveException(Throwable exception) {
        return Mono.just(resolveToMultipleErrors(exception));
    }

    protected List<GraphQLError> resolveToMultipleErrors(Throwable exception) {
        return Collections.singletonList(resolveToSingleError(exception));
    }

    protected GraphQLError resolveToSingleError(Throwable exception) {
        return null;
    }
}
