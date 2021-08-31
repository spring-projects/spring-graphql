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
package org.springframework.graphql.data.method.annotation.support;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;

import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.graphql.data.method.InvocableHandlerMethod;

/**
 * {@link DataFetcher} that wrap and invokes a {@link HandlerMethod}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class AnnotatedDataFetcher implements DataFetcher<Object> {

	private final FieldCoordinates coordinates;

	private final HandlerMethod handlerMethod;

	private final HandlerMethodArgumentResolverComposite argumentResolvers;


	public AnnotatedDataFetcher(FieldCoordinates coordinates, HandlerMethod handlerMethod,
			HandlerMethodArgumentResolverComposite resolvers) {

		this.coordinates = coordinates;
		this.handlerMethod = handlerMethod;
		this.argumentResolvers = resolvers;
	}


	/**
	 * Return the {@link FieldCoordinates} the HandlerMethod is mapped to.
	 */
	public FieldCoordinates getCoordinates() {
		return this.coordinates;
	}

	/**
	 * Return the {@link HandlerMethod} used to fetch data.
	 */
	public HandlerMethod getHandlerMethod() {
		return this.handlerMethod;
	}


	@Override
	@SuppressWarnings("ConstantConditions")
	public Object get(DataFetchingEnvironment environment) throws Exception {

		InvocableHandlerMethod invocable =
				new InvocableHandlerMethod(this.handlerMethod.createWithResolvedBean(), this.argumentResolvers);

		return invocable.invoke(environment);
	}

}
