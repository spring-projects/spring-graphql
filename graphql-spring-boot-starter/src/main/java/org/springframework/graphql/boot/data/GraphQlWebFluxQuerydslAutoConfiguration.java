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
package org.springframework.graphql.boot.data;

import com.querydsl.core.types.dsl.EntityPathBase;
import graphql.GraphQL;
import graphql.schema.GraphQLTypeVisitor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.querydsl.ReactiveQuerydslPredicateExecutor;
import org.springframework.data.querydsl.binding.QuerydslBinderCustomizer;
import org.springframework.graphql.boot.GraphQlAutoConfiguration;
import org.springframework.graphql.boot.GraphQlSourceBuilderCustomizer;
import org.springframework.graphql.data.QuerydslDataFetcher;
import org.springframework.graphql.execution.GraphQlSource;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link EnableAutoConfiguration Auto-configuration} that creates a
 * {@link GraphQlSourceBuilderCustomizer}s to detect Spring Data repositories
 * with Querydsl support and register them as {@code DataFetcher}s for any
 * queries with a matching return type.
 *
 * @author Rossen Stoyanchev
 * @see QuerydslDataFetcher#registrationTypeVisitor(List, List, List)
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass({GraphQL.class, QuerydslPredicateExecutor.class })
@ConditionalOnBean(GraphQlSource.class)
@AutoConfigureAfter(GraphQlAutoConfiguration.class)
public class GraphQlWebFluxQuerydslAutoConfiguration {

	@Bean
	public GraphQlSourceBuilderCustomizer reactiveQuerydslRegistrar(
			ObjectProvider<ReactiveQuerydslPredicateExecutor<?>> executorsProvider,
			ObjectProvider<QuerydslBinderCustomizer<? extends EntityPathBase<?>>> customizer) {

		return builder -> {
			List<ReactiveQuerydslPredicateExecutor<?>> executors =
					executorsProvider.stream().collect(Collectors.toList());

			List<QuerydslBinderCustomizer<? extends EntityPathBase<?>>> customizers = customizer.stream().collect(Collectors.toList());


			if (!executors.isEmpty()) {
				GraphQLTypeVisitor visitor = QuerydslDataFetcher.registrationTypeVisitor(Collections.emptyList(), executors, customizers);
				builder.typeVisitors(Collections.singletonList(visitor));
			}
		};
	}

}
