package org.springframework.graphql.execution;

import graphql.GraphQLException;
import graphql.schema.idl.errors.SchemaProblem;


/**
 * Indicates that the Resource in {@link GraphQlSource.Builder} is invalid.
 *
 * @author GenKui Du
 * @since 1.0.0
 */
public class InvalidSchemaResourceException extends GraphQLException {

    private final String resourceDescription;

    private final SchemaProblem schemaProblem;

    public InvalidSchemaResourceException(String resourceDescription, SchemaProblem schemaProblem) {
        super(
                String.format("invalid schema, resource = %s, errors = %s", resourceDescription, schemaProblem.getErrors()),
                schemaProblem
        );
        this.resourceDescription = resourceDescription;
        this.schemaProblem = schemaProblem;
    }

    public String getResourceDescription() {
        return resourceDescription;
    }

    public SchemaProblem getSchemaProblem() {
        return schemaProblem;
    }

    @Override
    public String toString() {
        return "InvalidSchemaResourceException{" +
                "resourceDescription='" + resourceDescription + '\'' +
                ", schemaProblem=" + schemaProblem +
                '}';
    }

}
