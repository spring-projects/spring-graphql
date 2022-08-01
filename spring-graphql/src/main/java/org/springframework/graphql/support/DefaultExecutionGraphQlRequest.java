/*
 * Copyright 2002-2022 the original author or authors.
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

package org.springframework.graphql.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;

import graphql.ExecutionInput;
import graphql.execution.ExecutionId;

import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * {@link GraphQlRequest} for server side handling, adding the transport (e.g. HTTP
 * or WebSocket handler) assigned {@link #getId() id} and {@link #getLocale()
 * locale} in the addition to the {@code GraphQlRequest} inputs.
 *
 * <p>Supports the initialization of {@link ExecutionInput} that is passed to
 * {@link graphql.GraphQL}. You can customize that via
 * {@link #configureExecutionInput(BiFunction)}.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 1.0.0
 */
public class DefaultExecutionGraphQlRequest extends DefaultGraphQlRequest implements ExecutionGraphQlRequest {

	private final String id;

	@Nullable
	private ExecutionId executionId;

	private final Locale locale;

	private final List<BiFunction<ExecutionInput, ExecutionInput.Builder, ExecutionInput>> executionInputConfigurers = new ArrayList<>();


	/**
	 * Create an instance.
	 * @param document textual representation of the operation(s)
	 * @param operationName optionally, the name of the operation to execute
	 * @param variables variables by which the query is parameterized
	 * @param extensions implementor specific, protocol extensions
	 * @param id the request id, to be used as the {@link ExecutionId}
	 * @param locale the locale associated with the request
	 */
	public DefaultExecutionGraphQlRequest(
			String document, @Nullable String operationName,
			@Nullable Map<String, Object> variables, @Nullable Map<String, Object> extensions,
			String id, @Nullable Locale locale) {

		super(document, operationName, variables, extensions);
		Assert.notNull(id, "'id' is required");
		this.id = id;
		this.locale = (locale != null) ? locale : Locale.getDefault();
	}


	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public void executionId(ExecutionId executionId) {
		Assert.notNull(executionId, "executionId is required");
		this.executionId = executionId;
	}

	@Override
	@Nullable
	public ExecutionId getExecutionId() {
		return this.executionId;
	}

	@Override
	public Locale getLocale() {
		return this.locale;
	}

	@Override
	public void configureExecutionInput(BiFunction<ExecutionInput, ExecutionInput.Builder, ExecutionInput> configurer) {
		this.executionInputConfigurers.add(configurer);
	}

	@Override
	public ExecutionInput toExecutionInput() {
		ExecutionInput.Builder inputBuilder = ExecutionInput.newExecutionInput()
				.query(getDocument())
				.operationName(getOperationName())
				.variables(getVariables())
				.extensions(getExtensions())
				.locale(this.locale)
				.executionId(this.executionId != null ? this.executionId : ExecutionId.from(this.id));

		ExecutionInput executionInput = inputBuilder.build();

		for (BiFunction<ExecutionInput, ExecutionInput.Builder, ExecutionInput> configurer : this.executionInputConfigurers) {
			ExecutionInput current = executionInput;
			executionInput = executionInput.transform(builder -> configurer.apply(current, builder));
		}

		return executionInput;
	}

	@Override
	public String toString() {
		return super.toString() + ", id=" + getId() + (getLocale() != null ? ", Locale=" + getLocale() : "");
	}

}
