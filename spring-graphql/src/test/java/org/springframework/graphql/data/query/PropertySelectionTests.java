/*
 * Copyright 2002-present the original author or authors.
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

package org.springframework.graphql.data.query;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingFieldSelectionSet;
import org.junit.jupiter.api.Test;

import org.springframework.data.util.TypeInformation;
import org.springframework.graphql.BookSource;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.execution.ConnectionTypeDefinitionConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link PropertySelection}.
 *
 * @author Rossen Stoyanchev
 */
class PropertySelectionTests {

	@Test
	void propertySelectionWithConnection() {

		AtomicReference<DataFetchingFieldSelectionSet> ref = new AtomicReference<>();
		DataFetcher<?> dataFetcher = environment -> {
			ref.set(environment.getSelectionSet());
			return null;
		};

		GraphQlSetup.schemaResource(BookSource.paginationSchema)
				.typeDefinitionConfigurer(new ConnectionTypeDefinitionConfigurer())
				.dataFetcher("Query", "books", dataFetcher)
				.toGraphQlService()
				.execute(BookSource.booksConnectionQuery(""))
				.block();

		TypeInformation<Book> typeInfo = TypeInformation.of(Book.class);

		List<String> list = PropertySelection.create(typeInfo, ref.get()).toList();
		assertThat(list).containsExactly("id", "name");
	}

}
