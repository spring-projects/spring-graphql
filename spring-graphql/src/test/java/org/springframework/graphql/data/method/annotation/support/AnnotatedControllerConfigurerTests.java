/*
 * Copyright 2002-2023 the original author or authors.
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

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.context.support.StaticApplicationContext;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.query.SortStrategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link AnnotatedControllerConfigurer}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
public class AnnotatedControllerConfigurerTests {

	@Test
	void customArgumentResolvers() {
		HandlerMethodArgumentResolver customResolver1 = mock(HandlerMethodArgumentResolver.class);
		HandlerMethodArgumentResolver customResolver2 = mock(HandlerMethodArgumentResolver.class);

		AnnotatedControllerConfigurer configurer = new AnnotatedControllerConfigurer();
		configurer.addCustomArgumentResolver(customResolver1);
		configurer.addCustomArgumentResolver(customResolver2);
		configurer.setApplicationContext(new StaticApplicationContext());
		configurer.afterPropertiesSet();

		List<HandlerMethodArgumentResolver> resolvers = configurer.getArgumentResolvers().getResolvers();
		int size = resolvers.size();
		assertThat(resolvers).element(size -1).isInstanceOf(SourceMethodArgumentResolver.class);
		assertThat(resolvers).element(size -2).isSameAs(customResolver2);
		assertThat(resolvers).element(size -3).isSameAs(customResolver1);
	}

	@Test
	void sortArgumentResolver() {
		SortStrategy sortStrategy = mock(SortStrategy.class);

		StaticApplicationContext context = new StaticApplicationContext();
		context.registerBean(SortStrategy.class, () -> sortStrategy);

		AnnotatedControllerConfigurer configurer = new AnnotatedControllerConfigurer();
		configurer.setApplicationContext(context);
		configurer.afterPropertiesSet();

		List<HandlerMethodArgumentResolver> resolvers = configurer.getArgumentResolvers().getResolvers();
		assertThat(resolvers.stream().filter(SortMethodArgumentResolver.class::isInstance).findFirst()).isPresent();
	}

	@Test
	void sortArgumentResolverStrategyNotPresent() {
		AnnotatedControllerConfigurer configurer = new AnnotatedControllerConfigurer();
		configurer.setApplicationContext(new StaticApplicationContext());
		configurer.afterPropertiesSet();

		List<HandlerMethodArgumentResolver> resolvers = configurer.getArgumentResolvers().getResolvers();
		assertThat(resolvers.stream().filter(SortMethodArgumentResolver.class::isInstance).findFirst()).isNotPresent();
	}

}
