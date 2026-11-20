/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.execution;

import graphql.ExecutionInput;
import graphql.GraphQLContext;
import org.dataloader.DataLoaderRegistry;

/**
 * Contract for access to the {@link DataLoaderRegistry} for each request for
 * the purpose of registering {@link org.dataloader.DataLoader} instances.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see ExecutionInput#getDataLoaderRegistry()
 */
public interface DataLoaderRegistrar {


	/**
	 * Whether the registrar has any {@code DataLoader} registrations to make.
	 * @since 1.2.8
	 */
	default boolean hasRegistrations() {
		DataLoaderRegistry registry = DataLoaderRegistry.newRegistry().build();
		registerDataLoaders(registry, GraphQLContext.newContext().build());
		return !registry.getDataLoaders().isEmpty();
	}

	/**
	 * Callback that provides access to the {@link DataLoaderRegistry} from the
	 * the {@link graphql.ExecutionInput}.
	 * @param registry the registry to make registrations against
	 * @param context the GraphQLContext from the ExecutionInput that registrars
	 * should set in the {@link org.dataloader.DataLoaderOptions} so that batch
	 * loaders can access it via {@link org.dataloader.BatchLoaderEnvironment}.
	 */
	void registerDataLoaders(DataLoaderRegistry registry, GraphQLContext context);

}
