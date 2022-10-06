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
package org.springframework.graphql.execution;


import java.util.Map;
import java.util.function.Predicate;

import graphql.GraphQLContext;
import io.micrometer.context.ContextAccessor;

/**
 * {@code ContextAccessor} that enables support for reading and writing values
 * to and from a {@link GraphQLContext}. This accessor is automatically
 * registered via {@link java.util.ServiceLoader}.
 *
 * @author Rossen Stoyanchev
 * @since 1.1.0
 */
public class GraphQlContextAccessor implements ContextAccessor<GraphQLContext, GraphQLContext> {

	@Override
	public Class<? extends GraphQLContext> readableType() {
		return GraphQLContext.class;
	}

	@Override
	public void readValues(GraphQLContext context, Predicate<Object> keyPredicate, Map<Object, Object> readValues) {
		context.stream().forEach(entry -> {
			if (keyPredicate.test(entry.getKey())) {
				readValues.put(entry.getKey(), entry.getValue());
			}
		});
	}

	@Override
	public <T> T readValue(GraphQLContext context, Object key) {
		return context.get(key);
	}

	@Override
	public Class<? extends GraphQLContext> writeableType() {
		return GraphQLContext.class;
	}

	@Override
	public GraphQLContext writeValues(Map<Object, Object> valuesToWrite, GraphQLContext targetContext) {
		return targetContext.putAll(valuesToWrite);
	}

}
