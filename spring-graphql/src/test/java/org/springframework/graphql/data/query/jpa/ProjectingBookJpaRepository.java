/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.query.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.graphql.data.GraphQlRepository;
import org.springframework.graphql.data.query.QueryByExampleDataFetcher.Builder;
import org.springframework.graphql.data.query.QueryByExampleDataFetcher.QueryByExampleBuilderCustomizer;

@GraphQlRepository
public interface ProjectingBookJpaRepository extends JpaRepository<Book, Long>, QueryByExampleBuilderCustomizer<Book> {

	@Override
	default Builder<Book, ?> customize(Builder<Book, ?> builder) {
		return builder.projectAs(BookProjection.class);
	}
}
