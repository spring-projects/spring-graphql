package org.springframework.graphql.data.method.annotation.support;

import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.graphql.data.method.OptionalInput;

import javax.swing.text.html.Option;
import java.util.Optional;

public class OptionalInputArgumentConversionService implements ConversionService {
    @Override
    public boolean canConvert(Class<?> sourceType, Class<?> targetType) {
        return false;
    }

    @Override
    public boolean canConvert(TypeDescriptor sourceType, TypeDescriptor targetType) {
        return targetType.getType() == OptionalInput.class || targetType.getType() == Optional.class;
    }

    @Override
    public <T> T convert(Object source, Class<T> targetType) {
        return null;
    }

    @Override
    public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
        if (targetType.getType() == Optional.class) {
            return Optional.of(source);
        } else {
            return OptionalInput.defined(source);
        }
    }
}
