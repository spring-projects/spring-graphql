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
import org.springframework.graphql.data.method.BatchHandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.support.ContextValueMethodArgumentResolver;

import java.util.Collection;
import java.util.Map;


/**
 * Resolver for {@link ContextValue @ContextValue} annotated method parameters.
 *
 * <p>For access to a value from the GraphQLContext of BatchLoaderEnvironment,
 * which is the same context as the one from the DataFetchingEnvironment.
 *
 * @author Genkui Du
 * @since 1.0.0
 */
public class ContextValueBatchMethodArgumentResolver implements BatchHandlerMethodArgumentResolver {

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

        return ContextValueMethodArgumentResolver.resolveContextValue(parameter, null, environment.getContext());
    }
}
