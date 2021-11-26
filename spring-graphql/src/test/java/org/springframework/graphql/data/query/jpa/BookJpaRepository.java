package org.springframework.graphql.data.query.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.graphql.data.GraphQlRepository;

@GraphQlRepository
public interface BookJpaRepository extends JpaRepository<Book, Long> {
}
