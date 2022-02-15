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
package org.springframework.graphql.data.method;

import graphql.schema.DataFetchingEnvironment;
import org.dataloader.BatchLoaderEnvironment;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves method parameters by delegating to a list of registered
 * {@link BatchHandlerMethodArgumentResolver BatchHandlerMethodArgumentResolvers}.
 * Previously resolved method parameters are cached for faster lookups.
 *
 * @author Genkui Du
 * @since 1.0.0
 */
public class BatchHandlerMethodArgumentResolverComposite implements BatchHandlerMethodArgumentResolver {

    private final List<BatchHandlerMethodArgumentResolver> argumentResolvers = new ArrayList<>();

    private final Map<MethodParameter, BatchHandlerMethodArgumentResolver> argumentResolverCache =
            new ConcurrentHashMap<>(256);


    /**
     * Return a read-only list with the contained resolvers, or an empty list.
     */
    public List<BatchHandlerMethodArgumentResolver> getArgumentResolvers() {
        return Collections.unmodifiableList(argumentResolvers);
    }

    /**
     * Add the given {@link BatchHandlerMethodArgumentResolver BatchHandlerMethodArgumentResolvers}.
     */
    public BatchHandlerMethodArgumentResolverComposite addResolvers(
            @Nullable List<? extends BatchHandlerMethodArgumentResolver> resolvers) {

        if (resolvers != null) {
            this.argumentResolvers.addAll(resolvers);
        }
        return this;
    }

    /**
     * Clear the list of configured resolvers and the resolver cache.
     */
    public void clear() {
        this.argumentResolvers.clear();
        this.argumentResolverCache.clear();
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return this.getArgumentResolver(parameter) != null;
    }

    @Override
    public <K> Object resolveArgument(MethodParameter parameter,
                                      Collection<K> keys,
                                      Map<K, ? extends DataFetchingEnvironment> keyContexts,
                                      BatchLoaderEnvironment environment) throws Exception {

        BatchHandlerMethodArgumentResolver argumentResolver = getArgumentResolver(parameter);
        if (argumentResolver == null) {
            throw new IllegalArgumentException("Unsupported parameter type [" +
                    parameter.getParameterType().getName() + "]. supportsParameter should be called first.");
        }
        return argumentResolver.resolveArgument(parameter, keys, keyContexts, environment);

    }

    /**
     * Find a registered {@link BatchHandlerMethodArgumentResolver} that supports
     * the given method parameter.
     */
    @Nullable
    private BatchHandlerMethodArgumentResolver getArgumentResolver(MethodParameter parameter) {
        BatchHandlerMethodArgumentResolver result = argumentResolverCache.get(parameter);
        if (result == null) {
            for (BatchHandlerMethodArgumentResolver argumentResolver : this.argumentResolvers) {
                if (argumentResolver.supportsParameter(parameter)) {
                    result = argumentResolver;
                    this.argumentResolverCache.put(parameter, argumentResolver);
                    break;
                }
            }
        }

        return result;
    }

}
