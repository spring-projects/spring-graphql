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

package org.springframework.graphql.data.pagination;

import graphql.relay.DefaultConnection;
import graphql.relay.DefaultConnectionCursor;
import graphql.relay.DefaultEdge;
import graphql.relay.DefaultPageInfo;
import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * {@link RuntimeHintsRegistrar} that registers reflection hints for the
 * {@code graphql-java} Relay implementation classes returned by
 * {@link ConnectionFieldTypeVisitor}. {@code graphql-java}'s
 * {@code PropertyFetchingImpl} reads {@code cursor}, {@code node},
 * {@code edges}, {@code pageInfo}, {@code hasNextPage}, etc. reflectively,
 * so without these hints relay edges and page info are serialized as {@code null}
 * in a native image.
 *
 * @author Seonwoo Jung
 */
class RelayPaginationRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
		hints.reflection()
				.registerType(DefaultConnection.class, MemberCategory.INVOKE_PUBLIC_METHODS)
				.registerType(DefaultConnectionCursor.class, MemberCategory.INVOKE_PUBLIC_METHODS)
				.registerType(DefaultEdge.class, MemberCategory.INVOKE_PUBLIC_METHODS)
				.registerType(DefaultPageInfo.class, MemberCategory.INVOKE_PUBLIC_METHODS);
	}

}
