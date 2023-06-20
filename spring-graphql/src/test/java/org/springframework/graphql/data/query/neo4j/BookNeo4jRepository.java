package org.springframework.graphql.data.query.neo4j;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.graphql.data.GraphQlRepository;

/**
 * @author Gerrit Meier
 */
@GraphQlRepository
public interface BookNeo4jRepository extends Neo4jRepository<Book, String> {
}
