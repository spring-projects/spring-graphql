/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.execution.timeout;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.server.TimeoutWebGraphQlInterceptor;

@Configuration(proxyBeanMethods = false)
public class HttpTimeoutConfiguration {

	@Bean
	public TimeoutWebGraphQlInterceptor timeoutWebGraphQlInterceptor() {
		return new TimeoutWebGraphQlInterceptor(Duration.ofSeconds(5));
	}

}
