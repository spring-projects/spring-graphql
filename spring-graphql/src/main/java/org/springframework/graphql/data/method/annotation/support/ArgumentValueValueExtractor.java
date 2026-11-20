/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.method.annotation.support;

import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.UnwrapByDefault;
import jakarta.validation.valueextraction.ValueExtractor;

import org.springframework.graphql.data.ArgumentValue;

/**
 * {@link ValueExtractor} that enables {@code @Valid} with {@link ArgumentValue},
 * and helps to extract the value from it.
 *
 * @author Rossen Stoyanchev
 * @since 1.2.2
 */
@UnwrapByDefault
public final class ArgumentValueValueExtractor implements ValueExtractor<ArgumentValue<@ExtractedValue ?>> {

	@Override
	public void extractValues(ArgumentValue<?> argumentValue, ValueReceiver receiver) {
		if (!argumentValue.isOmitted()) {
			receiver.value(null, argumentValue.value());
		}
	}

}
