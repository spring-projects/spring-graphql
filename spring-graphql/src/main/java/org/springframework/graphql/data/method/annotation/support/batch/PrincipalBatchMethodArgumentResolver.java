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
import org.springframework.graphql.data.method.annotation.support.AnnotatedControllerConfigurer;
import org.springframework.graphql.data.method.annotation.support.PrincipalMethodArgumentResolver;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ClassUtils;

import java.security.Principal;
import java.util.Collection;
import java.util.Map;


/**
 * Resolver to obtain {@link Principal} from Spring Security context via
 * {@link SecurityContext#getAuthentication()}.
 *
 * <p>The resolver checks both ThreadLocal context via {@link SecurityContextHolder}
 * for Spring MVC applications, and {@link ReactiveSecurityContextHolder} for
 * Spring WebFlux applications. It returns .
 *
 * @author Genkui Du
 * @since 1.0.0
 */
public class PrincipalBatchMethodArgumentResolver implements BatchHandlerMethodArgumentResolver {

    private final static boolean springSecurityPresent = ClassUtils.isPresent(
            "org.springframework.security.core.context.SecurityContext",
            AnnotatedControllerConfigurer.class.getClassLoader());

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return springSecurityPresent && Principal.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public <K> Object resolveArgument(MethodParameter parameter,
                                      Collection<K> keys,
                                      Map<K, ? extends DataFetchingEnvironment> keyContexts,
                                      BatchLoaderEnvironment environment) throws Exception {
        return PrincipalMethodArgumentResolver.doResolve();
    }
}
