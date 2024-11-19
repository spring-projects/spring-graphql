package org.springframework.graphql.data.method.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a class as representing a certain GraphQL type.
 *
 * <p>If a class has this annotation, then the default class name extractor in
 * {@link org.springframework.graphql.execution.ClassNameTypeResolver ClassNameTypeResolver} will use the {@link #value}
 * from the annotation, rather than the class name.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface GraphQlType {

    /**
     * The GraphQL type name.
     */
    String value();

}
