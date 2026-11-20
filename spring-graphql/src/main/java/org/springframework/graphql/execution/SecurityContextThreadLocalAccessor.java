/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.execution;

import io.micrometer.context.ThreadLocalAccessor;
import org.jspecify.annotations.Nullable;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ClassUtils;

/**
 * {@link ThreadLocalAccessor} to extract and restore security context through
 * {@link SecurityContextHolder}. This accessor is automatically registered via
 * {@link java.util.ServiceLoader} but applies if Spring Security is present on
 * the classpath.
 *
 * @author Rob Winch
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class SecurityContextThreadLocalAccessor implements ThreadLocalAccessor<Object> {

	private static final boolean springSecurityPresent = ClassUtils.isPresent(
			"org.springframework.security.core.context.SecurityContext",
			SecurityContextThreadLocalAccessor.class.getClassLoader());


	private final ThreadLocalAccessor<?> delegate;


	public SecurityContextThreadLocalAccessor() {
		if (springSecurityPresent) {
			this.delegate = new DelegateAccessor();
		}
		else {
			this.delegate = new NoOpAccessor();
		}
	}


	@Override
	public Object key() {
		return this.delegate.key();
	}

	@Override
	public @Nullable Object getValue() {
		return this.delegate.getValue();
	}

	@Override
	public void setValue(Object value) {
		setValueInternal(value);
	}

	@SuppressWarnings("unchecked")
	private <V> void setValueInternal(Object value) {
		((ThreadLocalAccessor<V>) this.delegate).setValue((V) value);
	}

	@Override
	public void setValue() {
		this.delegate.setValue();
	}

	@Override
	@Deprecated(since = "1.3.0", forRemoval = true)
	public void reset() {
		this.delegate.reset();
	}

	@Override
	public void restore(Object previousValue) {
		restoreInternal(previousValue);
	}

	@SuppressWarnings("unchecked")
	public <V> void restoreInternal(Object previousValue) {
		((ThreadLocalAccessor<V>) this.delegate).restore((V) previousValue);
	}

	@Override
	public void restore() {
		this.delegate.restore();
	}

	private static final class DelegateAccessor implements ThreadLocalAccessor<Object> {

		@Override
		public Object key() {
			return SecurityContext.class.getName();
		}

		@Override
		public Object getValue() {
			return SecurityContextHolder.getContext();
		}

		@Override
		public void setValue(Object value) {
			SecurityContextHolder.setContext((SecurityContext) value);
		}

		@Override
		public void setValue() {
			SecurityContextHolder.clearContext();
		}

		@Override
		public void restore(Object previousValue) {
			SecurityContextHolder.setContext((SecurityContext) previousValue);
		}

		@Override
		public void restore() {
			SecurityContextHolder.clearContext();
		}

		@Override
		@Deprecated(since = "1.3.0", forRemoval = true)
		public void reset() {
			SecurityContextHolder.clearContext();
		}

	}


	private static final class NoOpAccessor implements ThreadLocalAccessor<Object> {

		@Override
		public Object key() {
			return getClass().getName();
		}

		@Override
		public @Nullable Object getValue() {
			return null;
		}

		@Override
		public void setValue(Object value) {
		}

		@Override
		public void setValue() {
		}

		@Override
		public void restore(Object previousValue) {
		}

		@Override
		public void restore() {
		}

		@Override
		@Deprecated(since = "1.3.0", forRemoval = true)
		public void reset() {
		}

	}

}
