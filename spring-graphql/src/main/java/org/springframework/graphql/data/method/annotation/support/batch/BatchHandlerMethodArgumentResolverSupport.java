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
import org.springframework.graphql.data.method.BatchHandlerMethodArgumentResolver;

import java.util.Map;

/**
 * Base implementation of the {@link BatchHandlerMethodArgumentResolver} with some methods
 * which may be useful in subclasses.
 *
 * @author Genkui Du
 * @since 1.0.0
 */
public abstract class BatchHandlerMethodArgumentResolverSupport implements BatchHandlerMethodArgumentResolver {

    protected <K> DataFetchingEnvironment findFirstDataFetchingEnvironmentFromContexts(Map<K, ? extends DataFetchingEnvironment> keyContexts) {
        return keyContexts.values().iterator().next();
    }

}
