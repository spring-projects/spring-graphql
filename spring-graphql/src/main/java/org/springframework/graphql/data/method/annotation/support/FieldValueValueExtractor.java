/*
 * Copyright 2002-2025 the original author or authors.
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

package org.springframework.graphql.data.method.annotation.support;

import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.UnwrapByDefault;
import jakarta.validation.valueextraction.ValueExtractor;

import org.springframework.graphql.FieldValue;

/**
 * {@link ValueExtractor} that enables {@code @Valid} with {@link FieldValue},
 * and helps to extract the value from it.
 *
 * @author Rossen Stoyanchev
 * @since 1.4.0
 */
@UnwrapByDefault
public final class FieldValueValueExtractor implements ValueExtractor<FieldValue<@ExtractedValue ?>> {

	@Override
	public void extractValues(FieldValue<?> fieldValue, ValueReceiver receiver) {
		if (!fieldValue.isOmitted()) {
			receiver.value(null, fieldValue.value());
		}
	}

}
