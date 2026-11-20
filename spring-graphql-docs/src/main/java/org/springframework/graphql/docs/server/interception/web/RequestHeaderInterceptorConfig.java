/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
