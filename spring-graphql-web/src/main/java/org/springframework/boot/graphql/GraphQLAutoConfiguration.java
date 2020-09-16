package org.springframework.boot.graphql;

import java.io.File;
import java.io.FileNotFoundException;

import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ResourceUtils;

@Configuration
@ConditionalOnClass(GraphQL.class)
@ConditionalOnMissingBean(GraphQL.class)
@EnableConfigurationProperties(GraphQLProperties.class)
public class GraphQLAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public RuntimeWiring runtimeWiring(ObjectProvider<RuntimeWiringCustomizer> customizers) {
		RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();
		customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
		return builder.build();
	}

	@Bean
	public GraphQL.Builder graphQLBuilder(GraphQLProperties properties, RuntimeWiring runtimeWiring) throws FileNotFoundException {
		File schemaFile = ResourceUtils.getFile(properties.getSchema());
		GraphQLSchema schema = buildSchema(schemaFile, runtimeWiring);
		return GraphQL.newGraphQL(schema);
	}

	private GraphQLSchema buildSchema(File schemaFile, RuntimeWiring runtimeWiring) {
		TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(schemaFile);
		SchemaGenerator schemaGenerator = new SchemaGenerator();
		return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
	}

}
