/*
 * Copyright 2002-2021 the original author or authors.
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
package org.springframework.graphql.web;

import org.springframework.graphql.execution.ThreadLocalAccessor;

/**
 * Workflow that results in the creation of a {@link WebGraphQlHandler} or
 * an HTTP handler for WebMvc or WebFlux.
 *
 * @author Rossen Stoyanchev
 */
public interface WebGraphQlSetup {

	WebGraphQlSetup interceptor(WebGraphQlHandlerInterceptor... interceptors);

	WebGraphQlSetup threadLocalAccessor(ThreadLocalAccessor... accessors);

	WebGraphQlHandler toWebGraphQlHandler();

	org.springframework.graphql.web.webmvc.GraphQlHttpHandler toHttpHandler();

	org.springframework.graphql.web.webflux.GraphQlHttpHandler toHttpHandlerWebFlux();

}
