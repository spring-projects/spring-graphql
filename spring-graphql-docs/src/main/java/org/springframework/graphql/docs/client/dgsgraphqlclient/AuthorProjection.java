/*
 * Copyright 2025-present the original author or authors.
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
