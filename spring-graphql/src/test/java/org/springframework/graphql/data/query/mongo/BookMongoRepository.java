/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.query.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.graphql.data.GraphQlRepository;

@GraphQlRepository
public interface BookMongoRepository extends MongoRepository<Book, String> {
}
