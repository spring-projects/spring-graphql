/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.execution;

import io.micrometer.context.ContextSnapshotFactory;

/**
 * Helper to use a single {@link ContextSnapshotFactory} instance by saving and
 * obtaining it to and from Reactor and GraphQL contexts.
 *
 * @author Rossen Stoyanchev
 * @since 1.3.0
 * @deprecated since 1.3.5 in favor of {@link ContextPropagationHelper}.
 */
@Deprecated(since = "1.3.5", forRemoval = true)
public abstract class ContextSnapshotFactoryHelper extends ContextPropagationHelper {

}
