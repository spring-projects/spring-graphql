/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.client.dgsgraphqlclient;

import com.netflix.graphql.dgs.client.codegen.BaseSubProjectionNode;

public class BooksProjectionRoot<PARENT extends BaseSubProjectionNode<?, ?>, ROOT extends BaseSubProjectionNode<?, ?>> extends BaseSubProjectionNode<PARENT, ROOT> {
	public BooksProjectionRoot() {
		super(null, null, java.util.Optional.of("Book"));
	}

	public BooksProjectionRoot<PARENT, ROOT> __typename() {
		getFields().put("__typename", null);
		return this;
	}

	public AuthorProjection<BooksProjectionRoot<PARENT, ROOT>, BooksProjectionRoot<PARENT, ROOT>> author(
	) {
		AuthorProjection<BooksProjectionRoot<PARENT, ROOT>, BooksProjectionRoot<PARENT, ROOT>> projection = new AuthorProjection<>(this, this);
		getFields().put("author", projection);
		return projection;
	}

	public BooksProjectionRoot<PARENT, ROOT> id() {
		getFields().put("id", null);
		return this;
	}

	public BooksProjectionRoot<PARENT, ROOT> name() {
		getFields().put("name", null);
		return this;
	}
}
