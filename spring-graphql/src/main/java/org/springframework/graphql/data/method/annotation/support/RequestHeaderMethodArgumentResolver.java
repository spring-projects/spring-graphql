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
package org.springframework.graphql.data.method.annotation.support;

import graphql.schema.DataFetchingEnvironment;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Iterator;

/**
 * Resolves method arguments annotated with {@link RequestHeader @RequestHeader}.
 * And obtains the header value from {@link HttpServletRequest} object.
 * <p>
 * This resolver is similar to {@link org.springframework.web.method.annotation.RequestHeaderMethodArgumentResolver}
 * but it is used for GraphQL requests.
 *
 * @author Hakan Kargın
 * @since 1.2.3
 */
public class RequestHeaderMethodArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(RequestHeader.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) throws Exception {
        RequestHeader annotation = parameter.getParameterAnnotation(RequestHeader.class);

        if (annotation == null) {
            throw new IllegalStateException("No @RequestHeader annotation found on parameter " + parameter);
        }

        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();

        for (Iterator<String> it = request.getHeaderNames().asIterator(); it.hasNext(); ) {
            String headerName = it.next();
            if (annotation.value().equalsIgnoreCase(headerName)) {
                return request.getHeader(headerName);
            }
        }



        boolean isDefault = annotation.defaultValue().equals(ValueConstants.DEFAULT_NONE);
        boolean isRequired = annotation.required();

        if (isDefault && isRequired) {
            throw new IllegalStateException(
                    "Missing header '" + annotation.value() + "' for method parameter of type " +
                            parameter.getNestedParameterType().getSimpleName());
        }

        return isDefault ? null : annotation.defaultValue();
    }

}
