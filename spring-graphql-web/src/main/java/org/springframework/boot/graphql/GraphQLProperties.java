package org.springframework.boot.graphql;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.graphql")
public class GraphQLProperties {

	private String schema = "classpath:schema.graphqls";

	public String getSchema() {
		return schema;
	}

	public void setSchema(String schema) {
		this.schema = schema;
	}
}
