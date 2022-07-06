package org.springframework.graphql.execution;

import graphql.GraphQLError;
import org.springframework.core.NestedRuntimeException;

import java.util.List;

@SuppressWarnings("serial")
public class SubscriptionStreamException extends NestedRuntimeException {
    private final List<GraphQLError> errors;

    public SubscriptionStreamException(List<GraphQLError> errors) {
        super("An exception happened in GraphQL subscription stream.");
        this.errors = errors;
    }

    public List<GraphQLError> getErrors() {
        return errors;
    }
}
