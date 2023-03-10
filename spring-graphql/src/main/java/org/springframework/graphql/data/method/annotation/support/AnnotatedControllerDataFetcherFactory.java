/*
 * Copyright 2002-2023 the original author or authors.
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
package org.springframework.graphql.data.method.annotation.support;

import graphql.com.google.common.base.Preconditions;
import graphql.com.google.common.collect.Maps;
import graphql.schema.*;

import java.util.Map;

/**
 * A {@link DataFetcherFactory} that delegates to a {@link AnnotatedControllerConfigurer} to
 * create a {@link DataFetcher} for a given {@link graphql.schema.DataFetcherFactoryEnvironment}.
 *
 * @author Tomas Rehak
 * @since 1.2.0
 */
public class AnnotatedControllerDataFetcherFactory implements DataFetcherFactory<Object> {

    private final Map<FieldCoordinates, Map<Class<?>, DataFetcher<Object>>> fetchers = Maps.newConcurrentMap();

    private final AnnotatedControllerConfigurer annotatedControllerConfigurer;

    public AnnotatedControllerDataFetcherFactory(AnnotatedControllerConfigurer annotatedControllerConfigurer) {
        Preconditions.checkNotNull(annotatedControllerConfigurer, "AnnotatedControllerConfigurer must not be null");
        this.annotatedControllerConfigurer = annotatedControllerConfigurer;
    }

    @Override
    public DataFetcher<Object> get(DataFetcherFactoryEnvironment dataFetcherFactoryEnvironment) {
        return environment -> {
            final FieldCoordinates coordinates = AnnotatedControllerConfigurer.getCoordinates(environment);
            final Object src = environment.getSource();
            if (src == null) {
                return null;
            } else {
                final DataFetcher<Object> dataFetcher = fetchers.computeIfAbsent(coordinates, c -> Maps.newConcurrentMap())
                        .computeIfAbsent(src.getClass(), (aClass) -> {
                            final DataFetcher<Object> df = annotatedControllerConfigurer.createDataFetcher(environment);
                            return df != null ? df : new PropertyDataFetcher<>(coordinates.getFieldName());
                        });
                return dataFetcher.get(environment);
            }
        };
    }
}
