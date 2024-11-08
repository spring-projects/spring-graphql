/*
 * Copyright 2024 the original author or authors.
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

package org.springframework.graphql;

import com.querydsl.core.types.Path;
import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;

import static com.querydsl.core.types.PathMetadataFactory.forVariable;

/**
 * QAuthor is a Querydsl query type for Author
 */
public class QAuthor extends EntityPathBase<Author> {
	private static final long serialVersionUID = 1773522017L;
	public static final QAuthor author = new QAuthor("author");
	public final StringPath firstName = createString("firstName");
	public final NumberPath<Long> id = createNumber("id", Long.class);
	public final StringPath lastName = createString("lastName");

	public QAuthor(String variable) {
		super(Author.class, forVariable(variable));
	}

	public QAuthor(Path<? extends Author> path) {
		super(path.getType(), path.getMetadata());
	}

	public QAuthor(PathMetadata metadata) {
		super(Author.class, metadata);
	}

}
