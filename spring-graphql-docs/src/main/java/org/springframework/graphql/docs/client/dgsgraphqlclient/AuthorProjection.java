/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.client.dgsgraphqlclient;

import com.netflix.graphql.dgs.client.codegen.BaseSubProjectionNode;

public class AuthorProjection<PARENT extends BaseSubProjectionNode<?, ?>, ROOT extends BaseSubProjectionNode<?, ?>> extends BaseSubProjectionNode<PARENT, ROOT> {
	public AuthorProjection(PARENT parent, ROOT root) {
		super(parent, root, java.util.Optional.of("Author"));
	}

	public AuthorProjection<PARENT, ROOT> __typename() {
		getFields().put("__typename", null);
		return this;
	}

	public AuthorProjection<PARENT, ROOT> id() {
		getFields().put("id", null);
		return this;
	}

	public AuthorProjection<PARENT, ROOT> firstName() {
		getFields().put("firstName", null);
		return this;
	}

	public AuthorProjection<PARENT, ROOT> lastName() {
		getFields().put("lastName", null);
		return this;
	}
}
