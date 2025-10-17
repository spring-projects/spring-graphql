/*
 * Copyright 2020-present the original author or authors.
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

package org.springframework.graphql.docs.server.interception.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.server.support.HttpRequestHeaderInterceptor;
import org.springframework.stereotype.Controller;

@Configuration
class RequestHeaderInterceptorConfig {

	@Bean
	public HttpRequestHeaderInterceptor headerInterceptor() { // <1>
		return HttpRequestHeaderInterceptor.builder().mapHeader("myHeader").build();
	}
}

@Controller
class MyContextValueController { // <2>

	@QueryMapping
	Person person(@ContextValue String myHeader) {
		/**/ return new Person("spring", "graphql");
	}
}
