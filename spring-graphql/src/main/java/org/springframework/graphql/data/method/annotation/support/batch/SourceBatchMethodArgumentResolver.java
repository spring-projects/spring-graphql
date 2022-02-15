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
import org.springframework.core.CollectionFactory;
import org.springframework.core.MethodParameter;
import org.springframework.graphql.data.method.BatchHandlerMethodArgumentResolver;

import java.util.Collection;
import java.util.Map;


/**
 * Resolver for access source/parent objects.
 *
 * @author GenKui Du
 * @since 1.0.0
 */
public class SourceBatchMethodArgumentResolver implements BatchHandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        Class<?> parameterType = parameter.getParameterType();
        return Collection.class.isAssignableFrom(parameterType);
    }

    @Override
    public <K> Object resolveArgument(MethodParameter parameter,
                                      Collection<K> keys,
                                      Map<K, ? extends DataFetchingEnvironment> keyContexts,
                                      BatchLoaderEnvironment environment) throws Exception {
        Class<?> parameterType = parameter.getParameterType();
        if (parameterType.isInstance(keys)) {
            return keys;
        }

        Class<?> elementType = parameter.nested().getNestedParameterType();
        Collection<K> collection = CollectionFactory.createCollection(parameterType, elementType, keys.size());
        collection.addAll(keys);
        return collection;
    }

}
