/*
 * Copyright 2020-2022 the original author or authors.
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multipart implementation of {@link ClientGraphQlRequest}.
 *
 * @author Nikita Konev
 * @since 1.0.0
 */
public final class MultipartClientGraphQlRequest extends DefaultClientGraphQlRequest implements ClientGraphQlRequest {

    private final Map<String, Object> fileVariables = new ConcurrentHashMap<>();

    public MultipartClientGraphQlRequest(
			String document, @Nullable String operationName,
			Map<String, Object> variables, Map<String, Object> extensions,
			Map<String, Object> attributes,
            Map<String, Object> fileVariables) {

		super(document, operationName, variables, extensions, attributes);
        this.fileVariables.putAll(fileVariables);
	}

    public Map<String, Object> getFileVariables() {
        return this.fileVariables;
    }

}
