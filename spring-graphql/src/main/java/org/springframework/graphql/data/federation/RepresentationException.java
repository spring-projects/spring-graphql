/*
 * Copyright 2002-2024 the original author or authors.
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

package org.springframework.graphql.data.federation;


import java.util.Map;

import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.lang.Nullable;

/**
 * Raised when a representation could not be resolved because:
 * <ul>
 * <li>The "__typename" argument is missing.
 * <li>The "__typename" could not be mapped to a controller method.
 * </ul>
 *
 * <p>The {@link RepresentationNotResolvedException} subtype is raised when a
 * resolver returned {@code null} or completed empty.
 *
 * @author Rossen Stoyanchev
 * @since 1.3
 */
@SuppressWarnings("serial")
public class RepresentationException extends RuntimeException {

	private final Map<String, Object> representation;

	@Nullable
	private final HandlerMethod handlerMethod;

	private final ErrorType errorType;


	public RepresentationException(Map<String, Object> representation, String msg) {
		this(representation, null, msg);
	}

	public RepresentationException(Map<String, Object> representation, @Nullable HandlerMethod hm, String msg) {
		super(msg);
		this.representation = representation;
		this.handlerMethod = hm;
		this.errorType = (representation.get("__typename") == null) ? ErrorType.BAD_REQUEST : ErrorType.INTERNAL_ERROR;
	}


	/**
	 * Return the entity "representation" input map.
	 */
	@Nullable
	public Map<String, Object> getRepresentation() {
		return this.representation;
	}

	/**
	 * Return the mapped controller method, or {@code null} if it could not be mapped.
	 */
	@Nullable
	public HandlerMethod getHandlerMethod() {
		return this.handlerMethod;
	}

	/**
	 * The classification for the error, {@link ErrorType#BAD_REQUEST} if the
	 * "__typename" argument was missing, or {@link ErrorType#INTERNAL_ERROR} otherwise.
	 */
	public ErrorType getErrorType() {
		return this.errorType;
	}

}
