/*
 * Copyright 2002-2021 the original author or authors.
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

import org.springframework.core.MethodParameter;
import org.springframework.core.NestedRuntimeException;

/**
 * Indicates that an input argument value in the method parameters of an
 * annotated DataFetcher method is not present.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
@SuppressWarnings("serial")
public class MissingArgumentException extends NestedRuntimeException {

	private final String argumentName;

	private final MethodParameter parameter;


	public MissingArgumentException(String argumentName, MethodParameter parameter) {
		super("");
		this.argumentName = argumentName;
		this.parameter = parameter;
	}


	/**
	 * Return the expected name of the input argument.
	 */
	public String getArgumentName() {
		return this.argumentName;
	}

	/**
	 * Return the method parameter bound to the input argument.
	 */
	public MethodParameter getParameter() {
		return this.parameter;
	}

	@Override
	public String getMessage() {
		return "Required argument '" + this.argumentName +"' for method parameter type " +
				this.parameter.getNestedParameterType().getSimpleName() + " is not present";
	}

}
