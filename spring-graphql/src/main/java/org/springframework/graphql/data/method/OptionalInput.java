package org.springframework.graphql.data.method;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Wrapper used to represent optionally defined input arguments that allows us to distinguish between undefined value, explicit NULL value
 * and specified value.
 */
public class OptionalInput<T> {

    public static<T> OptionalInput<T> defined(@Nullable T value) {
        return new OptionalInput.Defined<>(value);
    }

    @SuppressWarnings("unchecked")
    public static<T> OptionalInput<T> undefined() {
        return (OptionalInput<T>)new OptionalInput.Undefined();
    }

    /**
     * Represents missing/undefined value.
     */
    public static class Undefined extends OptionalInput<Void> {
        @Override
        public boolean equals(Object obj) {
            return obj.getClass() == this.getClass();
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }
    }

    /**
     * Wrapper holding explicitly specified value including NULL.
     */
    public static class Defined<T> extends OptionalInput<T> {
        @Nullable
        private final T value;

        public Defined(@Nullable T value) {
            this.value = value;
        }

        @Nullable
        public T getValue() {
            return value;
        }

        public Boolean isEmpty() {
            return value == null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Defined<?> defined = (Defined<?>) o;
            if (isEmpty() == defined.isEmpty()) return true;
            if (value == null) return false;
            return value.equals(defined.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }
}
