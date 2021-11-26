package org.springframework.graphql.data.query.mongo;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.graphql.data.GraphQlRepository;

@GraphQlRepository
public interface BookReactiveMongoRepository extends ReactiveMongoRepository<Book, String> {
}
