package org.springframework.graphql.boot;

import org.springframework.core.io.Resource;

import java.util.List;

/**
 * Strategy interface that supports pluggable resolution of GraphQL schema files as Spring Framework {@link  Resource}s.
 * @author Josh Long
 */
public interface SchemaResourceResolver {

    List<Resource> resolveSchemaResources();
}
