/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.method.annotation.support;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.convert.ConversionService;
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
class AnnotatedControllerConfigurerTests {

	private final AnnotatedControllerConfigurer configurer = new AnnotatedControllerConfigurer();

	@Test
	void customArgumentResolvers() {
		HandlerMethodArgumentResolver customResolver1 = mock(HandlerMethodArgumentResolver.class);
		HandlerMethodArgumentResolver customResolver2 = mock(HandlerMethodArgumentResolver.class);

		configurer.addCustomArgumentResolver(customResolver1);
		configurer.addCustomArgumentResolver(customResolver2);
		configurer.setApplicationContext(new StaticApplicationContext());
		configurer.afterPropertiesSet();

		List<HandlerMethodArgumentResolver> resolvers = configurer.getArgumentResolvers().getResolvers();
		int size = resolvers.size();
		assertThat(resolvers).element(size - 1).isInstanceOf(SourceMethodArgumentResolver.class);
		assertThat(resolvers).element(size - 2).isSameAs(customResolver2);
		assertThat(resolvers).element(size - 3).isSameAs(customResolver1);
	}

	@Test
	void sortArgumentResolver() {
		SortStrategy sortStrategy = mock(SortStrategy.class);

		StaticApplicationContext context = new StaticApplicationContext();
		context.registerBean(SortStrategy.class, () -> sortStrategy);

		configurer.setApplicationContext(context);
		configurer.afterPropertiesSet();

		List<HandlerMethodArgumentResolver> resolvers = configurer.getArgumentResolvers().getResolvers();
		assertThat(resolvers.stream().filter(r -> r instanceof SortMethodArgumentResolver).findFirst()).isPresent();
	}

	@Test
	void sortArgumentResolverStrategyNotPresent() {
		configurer.setApplicationContext(new StaticApplicationContext());
		configurer.afterPropertiesSet();

		List<HandlerMethodArgumentResolver> resolvers = configurer.getArgumentResolvers().getResolvers();
		assertThat(resolvers.stream().filter(r -> r instanceof SortMethodArgumentResolver).findFirst()).isNotPresent();
	}

	@Test
	void customizeArgumentBinder() {
		ConversionService conversionService = mock(ConversionService.class);
		configurer.configureBinder(binder -> binder.conversionService(conversionService));
		assertThat(configurer.getBinderOptions().conversionService()).isEqualTo(conversionService);
	}

}
