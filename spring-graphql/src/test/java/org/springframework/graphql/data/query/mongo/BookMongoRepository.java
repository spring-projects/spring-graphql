package org.springframework.graphql.data.query.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.graphql.data.GraphQlRepository;

@GraphQlRepository
public interface BookMongoRepository extends MongoRepository<Book, String> {
}
