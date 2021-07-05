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
package io.spring.sample.graphql.greeting;

import java.util.Map;

import org.springframework.graphql.execution.ThreadLocalAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * {@link ThreadLocalAccessor} to expose a thread-bound RequestAttributes object to data
 * fetchers in Spring GraphQL.
 */
@Component
public class RequestAttributesAccessor implements ThreadLocalAccessor {

	private static final String KEY = RequestAttributesAccessor.class.getName();

	@Override
	public void extractValues(Map<String, Object> container) {
		container.put(KEY, RequestContextHolder.getRequestAttributes());
	}

	@Override
	public void restoreValues(Map<String, Object> values) {
		if (values.containsKey(KEY)) {
			RequestContextHolder.setRequestAttributes((RequestAttributes) values.get(KEY));
		}
	}

	@Override
	public void resetValues(Map<String, Object> values) {
		RequestContextHolder.resetRequestAttributes();
	}

}
