/*
 * Copyright 2020-present the original author or authors.
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

import com.querydsl.core.types.Path;
import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.PathInits;
import com.querydsl.core.types.dsl.StringPath;

import static com.querydsl.core.types.PathMetadataFactory.forVariable;

/**
 * QBook is a Querydsl query type for Book
 */
public class QBook extends EntityPathBase<Book> {
	private static final long serialVersionUID = 1773522017L;
	private static final PathInits INITS = PathInits.DIRECT2;
	public static final QBook book = new QBook("book");
	public final org.springframework.graphql.QAuthor author;
	public final NumberPath<Long> id = createNumber("id", Long.class);
	public final StringPath name = createString("name");

	public QBook(String variable) {
		this(Book.class, forVariable(variable), INITS);
	}

	public QBook(Path<? extends Book> path) {
		this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
	}

	public QBook(PathMetadata metadata) {
		this(metadata, PathInits.getFor(metadata, INITS));
	}

	public QBook(PathMetadata metadata, PathInits inits) {
		this(Book.class, metadata, inits);
	}

	public QBook(Class<? extends Book> type, PathMetadata metadata, PathInits inits) {
		super(type, metadata, inits);
		this.author = inits.isInitialized("author") ? new org.springframework.graphql.QAuthor(forProperty("author")) : null;
	}

}
