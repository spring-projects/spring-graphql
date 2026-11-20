/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
		context.stream().forEach((entry) -> {
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
