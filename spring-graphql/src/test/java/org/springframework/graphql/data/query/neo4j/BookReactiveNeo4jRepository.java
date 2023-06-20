package org.springframework.graphql.data.query.neo4j;

import org.springframework.data.neo4j.repository.ReactiveNeo4jRepository;
import org.springframework.graphql.data.GraphQlRepository;

/**
 * @author Gerrit Meier
 */
@GraphQlRepository
public interface BookReactiveNeo4jRepository extends ReactiveNeo4jRepository<Book, String> {
}
