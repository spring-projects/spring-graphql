package org.springframework.graphql.data;

import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.UnwrapByDefault;
import jakarta.validation.valueextraction.ValueExtractor;

@UnwrapByDefault
public class ArgumentValueExtractor implements ValueExtractor<ArgumentValue<@ExtractedValue ?>> {

    @Override
    public void extractValues(ArgumentValue<?> originalValue, ValueReceiver receiver) {
        if (originalValue.isPresent()) {
            receiver.value(null, originalValue.value());
        }
    }
}
