package org.springframework.graphql.data.method.annotation.support;

/**
 * the wrapped type of parent source.
 *
 * @param <V> the value of parent source.
 */
public class FetchSource<V> {

    private final V value;

    public FetchSource(V value) {
        this.value = value;
    }

    public V getValue() {
        return value;
    }
}