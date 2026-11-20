/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
