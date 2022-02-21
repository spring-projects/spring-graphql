/*
 * Copyright 2002-2022 the original author or authors.
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
package org.springframework.graphql.client;

import org.springframework.lang.Nullable;

/**
 * Strategy to load the content of a GraphQL operation from a key.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface OperationContentLoader {

	/**
	 * Return the operation for the given key.
	 * @param key the key to look up the operation content
	 * @return the content of the operation, if found
	 */
	@Nullable
	String loadOperation(String key);

}
