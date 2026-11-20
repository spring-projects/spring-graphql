/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.testing.graphqltester.builder;

import java.time.Duration;
import java.util.function.Predicate;

import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.client.GraphQlTransport;
import org.springframework.graphql.support.DocumentSource;
import org.springframework.graphql.support.ResourceDocumentSource;
import org.springframework.graphql.test.tester.GraphQlTester;

public class GraphQlTesterBuilder {

	void inlineDocument() {
		// tag::inlineDocument[]
		GraphQlTransport transport = /**/ null;
		Predicate<ResponseError> errorFilter = /**/ null;
		ClassPathResource resource = new ClassPathResource("custom-folder/");
		DocumentSource documentSource = new ResourceDocumentSource(resource);

		GraphQlTester tester = GraphQlTester.builder(transport)
				.documentSource(documentSource)
				.errorFilter(errorFilter)
				.responseTimeout(Duration.ofSeconds(5))
				.build();
		// end::inlineDocument[]
	}

}
