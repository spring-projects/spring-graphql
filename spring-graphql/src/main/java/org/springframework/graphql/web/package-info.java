/*
 * Copyright 2020-2021 the original author or authors.
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

/**
 * Support for executing GraphQL requests over the Web, including handlers for HTTP and
 * WebSocket. Handlers are provided for use in ether
 * {@link org.springframework.graphql.web.webmvc Spring WebMvc} or
 * {@link org.springframework.graphql.web.webflux Spring WebFlux} with a common
 * {@link org.springframework.graphql.web.WebInterceptor interception} model that allows
 * applications to customize request input and output.
 */
@NonNullApi
@NonNullFields
package org.springframework.graphql.web;

import org.springframework.lang.NonNullApi;
import org.springframework.lang.NonNullFields;
