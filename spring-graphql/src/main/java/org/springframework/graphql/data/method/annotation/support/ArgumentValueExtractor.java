package org.springframework.graphql.data.method.annotation.support;

import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.UnwrapByDefault;
import jakarta.validation.valueextraction.ValueExtractor;
import org.springframework.graphql.data.ArgumentValue;

@UnwrapByDefault
public class ArgumentValueExtractor implements ValueExtractor<ArgumentValue<@ExtractedValue ?>> {

    @Override
    public void extractValues(ArgumentValue<?> originalValue, ValueReceiver receiver) {
        if (originalValue.isPresent()) {
            receiver.value(null, originalValue.value());
        }
    }
}
