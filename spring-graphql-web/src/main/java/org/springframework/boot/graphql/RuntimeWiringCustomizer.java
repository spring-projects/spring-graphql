package org.springframework.boot.graphql;

import graphql.schema.idl.RuntimeWiring;

@FunctionalInterface
public interface RuntimeWiringCustomizer {

	void customize(RuntimeWiring.Builder runtimeWiringBuilder);

}
