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

import graphql.GraphQLError;

import org.springframework.core.NestedRuntimeException;

/**
 * An exception raised after a GraphQL subscription
 * {@link org.reactivestreams.Publisher} ends with an exception, and after that
 * exception has been resolved to GraphQL errors.
 *
 * <p>The underlying transport, e.g. WebSocket, can handle a
 * {@link SubscriptionPublisherException} and send a final error message to the
 * client with the list of GraphQL errors.
 *
 * @author Mykyta Ivchenko
 * @author Rossen Stoyanchev
 * @since 1.0.1
 */
@SuppressWarnings("serial")
public final class SubscriptionPublisherException extends NestedRuntimeException {

    private final List<GraphQLError> errors;


    /**
     * Constructor with the resolved GraphQL errors and the original exception
     * from the GraphQL subscription {@link org.reactivestreams.Publisher}.
     */
    public SubscriptionPublisherException(List<GraphQLError> errors, Throwable cause) {
        super("GraphQL subscription ended with error(s): " + errors, cause);
        this.errors = errors;
    }


    /**
     * Return the GraphQL errors the exception was resolved to by the configured
     * {@link SubscriptionExceptionResolver}'s. These errors can be included in
     * an error message to be sent to the client by the underlying transport.
     */
    public List<GraphQLError> getErrors() {
        return this.errors;
    }

}
