package io.spring.sample.graphql.repository;

import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.graphql.data.GraphQlRepository;

@GraphQlRepository
public interface ArtifactRepositories extends
		CrudRepository<ArtifactRepository, String>, QuerydslPredicateExecutor<ArtifactRepository> {

}
