package org.springframework.graphql.boot;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.graphql.execution.GraphQlSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Default implementation of {@link  SchemaResourceResolver} that uses a {@link  ResourcePatternResolver} to find schema.
 *
 * @author Brian Clozel
 * @author Josh Long
 * @since 1.0.0
 */
public class DefaultSchemaResourceResolver implements SchemaResourceResolver {

    private static final Log logger = LogFactory.getLog(DefaultSchemaResourceResolver.class);

    private final ResourcePatternResolver resourcePatternResolver;
    private final String[] schemaLocations;
    private final String[] fileExtensions;

    DefaultSchemaResourceResolver(ResourcePatternResolver resourcePatternResolver, String[] schemaLocations, String[] fileExtensions) {
        this.resourcePatternResolver = resourcePatternResolver;
        this.schemaLocations = schemaLocations;
        this.fileExtensions = fileExtensions;
    }

    @Override
    public List<Resource> resolveSchemaResources() {
        List<Resource> schemaResources = new ArrayList<>();
        for (String location : this.schemaLocations) {
            for (String extension : this.fileExtensions) {
                String resourcePattern = location + "*" + extension;
                try {
                    schemaResources.addAll(Arrays.asList(this.resourcePatternResolver.getResources(resourcePattern)));
                } catch (IOException ex) {
                    logger.debug("Could not resolve schema location: '" + resourcePattern + "'", ex);
                }
            }
        }
        return schemaResources;
    }
}
