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

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.BatchLoaderEnvironment;
import org.springframework.core.MethodParameter;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Resolver for {@link ContextValue @ContextValue} annotated method parameters.
 *
 * <p>For access to a value from the GraphQLContext of BatchLoaderEnvironment,
 * which is the same context as the one from the DataFetchingEnvironment.
 *
 * @author Genkui Du
 * @since 1.0.0
 */
public class ContextValueBatchMethodArgumentResolver extends BatchHandlerMethodArgumentResolverSupport {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(ContextValue.class);
    }

    @Override
    public <K> Object resolveArgument(MethodParameter parameter,
                                      Collection<K> keys,
                                      Map<K, ? extends DataFetchingEnvironment> keyContexts,
                                      BatchLoaderEnvironment environment) throws Exception {
        if(keyContexts.isEmpty()){
            return null;
        }

        return resolveContextValue(parameter, null, environment.getContext());
    }

    @Nullable
    static Object resolveContextValue(
            MethodParameter parameter, @Nullable Object localContext, GraphQLContext graphQlContext) {

        ContextValue annotation = parameter.getParameterAnnotation(ContextValue.class);
        Assert.state(annotation != null, "Expected @ContextValue annotation");
        String name = getValueName(parameter, annotation);

        Class<?> parameterType = parameter.getParameterType();
        Object value = null;

        if (localContext instanceof GraphQLContext) {
            value = ((GraphQLContext) localContext).get(name);
        }

        if (value != null) {
            return wrapAsOptionalIfNecessary(value, parameterType);
        }

        value = graphQlContext.get(name);
        if (value == null && annotation.required() && !parameterType.equals(Optional.class)) {
            throw new IllegalStateException("Missing required context value for " + parameter);
        }

        return wrapAsOptionalIfNecessary(value, parameterType);
    }

    private static String getValueName(MethodParameter parameter, ContextValue annotation) {
        if (StringUtils.hasText(annotation.name())) {
            return annotation.name();
        }
        String parameterName = parameter.getParameterName();
        if (parameterName != null) {
            return parameterName;
        }
        throw new IllegalArgumentException("Name for @ContextValue argument " +
                "of type [" + parameter.getNestedParameterType().getName() + "] not specified, " +
                "and parameter name information not found in class file either.");
    }

    @Nullable
    private static Object wrapAsOptionalIfNecessary(@Nullable Object value, Class<?> type) {
        return (type.equals(Optional.class) ? Optional.ofNullable(value) : value);
    }
}
