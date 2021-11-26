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
package org.springframework.graphql.data.query;

import java.lang.reflect.Type;

import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.graphql.data.GraphQlRepository;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Utility methods to get information for Spring Data repositories.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
class RepositoryUtils {

	@SuppressWarnings("unchecked")
	public static <T> Class<T> getDomainType(Object executor) {
		return (Class<T>) getRepositoryMetadata(executor).getDomainType();
	}

	public static RepositoryMetadata getRepositoryMetadata(Object executor) {
		Assert.isInstanceOf(Repository.class, executor);

		Type[] genericInterfaces = executor.getClass().getGenericInterfaces();
		for (Type genericInterface : genericInterfaces) {
			Class<?> rawClass = ResolvableType.forType(genericInterface).getRawClass();
			if (rawClass == null || MergedAnnotations.from(rawClass).isPresent(NoRepositoryBean.class)) {
				continue;
			}
			if (Repository.class.isAssignableFrom(rawClass)) {
				return new DefaultRepositoryMetadata(rawClass);
			}
		}

		throw new IllegalArgumentException(
				String.format("Cannot resolve repository interface from %s", executor));
	}

	@Nullable
	public static String getGraphQlTypeName(Object repository) {
		GraphQlRepository annotation =
				AnnotatedElementUtils.findMergedAnnotation(repository.getClass(), GraphQlRepository.class);

		if (annotation == null) {
			return null;
		}

		return (StringUtils.hasText(annotation.typeName()) ?
				annotation.typeName() : RepositoryUtils.getDomainType(repository).getSimpleName());
	}

}
