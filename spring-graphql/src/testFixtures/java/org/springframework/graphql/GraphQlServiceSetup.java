/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql;

import org.springframework.graphql.execution.DataLoaderRegistrar;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlSetup;

/**
 * Workflow that results in the creation of a {@link ExecutionGraphQlService} or a
 * {@link WebGraphQlHandler}.
 *
 * @author Rossen Stoyanchev
 */
public interface GraphQlServiceSetup extends WebGraphQlSetup {

	GraphQlServiceSetup dataLoaders(DataLoaderRegistrar... registrars);

	TestExecutionGraphQlService toGraphQlService();

}
