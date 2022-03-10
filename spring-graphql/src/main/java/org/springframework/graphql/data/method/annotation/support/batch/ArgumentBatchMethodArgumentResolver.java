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
package org.springframework.graphql.data.method.annotation.support.batch;

import graphql.schema.DataFetchingEnvironment;
import org.dataloader.BatchLoaderEnvironment;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.graphql.data.GraphQlArgumentInitializer;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Map;

/**
 * Resolver for {@link Argument @Argument} annotated method parameters, obtained
 * via {@link DataFetchingEnvironment#getArgument(String)} and converted to the
 * declared type of the method parameter.
 *
 * @author Genkui Du
 * @since 1.0.0
 */
public class ArgumentBatchMethodArgumentResolver extends BatchHandlerMethodArgumentResolverSupport {

    private final GraphQlArgumentInitializer argumentInitializer;

    public ArgumentBatchMethodArgumentResolver(GraphQlArgumentInitializer initializer) {
        Assert.notNull(initializer, "GraphQlArgumentInitializer is required");
        this.argumentInitializer = initializer;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterAnnotation(Argument.class) != null;
    }

    @Override
    public <K> Object resolveArgument(MethodParameter parameter,
                                      Collection<K> keys,
                                      Map<K, ? extends DataFetchingEnvironment> keyContexts,
                                      BatchLoaderEnvironment environment) throws Exception {
        String name = getArgumentName(parameter);
        ResolvableType resolvableType = ResolvableType.forMethodParameter(parameter);

        return this.argumentInitializer.initializeArgument(findFirstDataFetchingEnvironmentFromContexts(keyContexts), name, resolvableType);
    }

    static String getArgumentName(MethodParameter parameter) {
        Argument annotation = parameter.getParameterAnnotation(Argument.class);
        Assert.state(annotation != null, "Expected @Argument annotation");
        if (StringUtils.hasText(annotation.name())) {
            return annotation.name();
        }
        String parameterName = parameter.getParameterName();
        if (parameterName != null) {
            return parameterName;
        }
        throw new IllegalArgumentException(
                "Name for argument of type [" + parameter.getNestedParameterType().getName() +
                        "] not specified, and parameter name information not found in class file either.");
    }
}
