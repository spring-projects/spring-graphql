/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.graphql;

import org.springframework.graphql.execution.DataLoaderRegistrar;
import org.springframework.graphql.web.WebGraphQlSetup;

/**
 * Workflow that results in the creation of a {@link ExecutionGraphQlService} or a
 * {@link org.springframework.graphql.web.WebGraphQlHandler}.
 *
 * @author Rossen Stoyanchev
 */
public interface GraphQlServiceSetup extends WebGraphQlSetup {

	GraphQlServiceSetup dataLoaders(DataLoaderRegistrar... registrars);

	ExecutionGraphQlService toGraphQlService();

}
