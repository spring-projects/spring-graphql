package org.springframework.graphql.security;

import java.util.Map;

import org.springframework.graphql.execution.ThreadLocalAccessor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link ThreadLocalAccessor} to extract and restore security context through
 * {@link SecurityContextHolder}.
 *
 * @author Rob Winch
 * @author Rossen Stoyanchev
 */
public class SecurityContextThreadLocalAccessor implements ThreadLocalAccessor {

	private static final String KEY = SecurityContext.class.getName();

	@Override
	public void extractValues(Map<String, Object> container) {
		container.put(KEY, SecurityContextHolder.getContext());
	}

	@Override
	public void restoreValues(Map<String, Object> values) {
		if (values.containsKey(KEY)) {
			SecurityContextHolder.setContext((SecurityContext) values.get(KEY));
		}
	}

	@Override
	public void resetValues(Map<String, Object> values) {
		SecurityContextHolder.clearContext();
	}

}
