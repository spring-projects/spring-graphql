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

package org.springframework.graphql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;

import graphql.ExecutionInput;
import graphql.execution.ExecutionId;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Extension of {@link GraphQlRequest} for server side handling, adding the
 * transport (e.g. HTTP or WebSocket handler) assigned {@link #getId() id} and
 * {@link #getLocale() locale} in the addition to the {@link GraphQlRequest}
 * inputs.
 *
 * <p>{@code RequestInput} supports the initialization of {@link ExecutionInput}
 * that is passed to {@link graphql.GraphQL}. You can customize that via
 * {@link #configureExecutionInput(BiFunction)}.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 1.0.0
 */
public class RequestInput extends GraphQlRequest {

	@Nullable
	private final Locale locale;

	private final String id;

	private final List<BiFunction<ExecutionInput, ExecutionInput.Builder, ExecutionInput>> executionInputConfigurers = new ArrayList<>();

	@Nullable
	private ExecutionId executionId;


	/**
	 * Create an instance.
	 * @param document textual representation of the operation(s)
	 * @param operationName optionally, the name of the operation to execute
	 * @param variables variables by which the query is parameterized
	 * @param locale the locale associated with the request
	 * @param id the request id, to be used as the {@link ExecutionId}
	 */
	public RequestInput(
			String document, @Nullable String operationName, @Nullable Map<String, Object> variables,
			@Nullable Locale locale, String id) {

		super(document, operationName, variables);
		Assert.notNull(id, "'id' is required");
		this.locale = locale;
		this.id = id;
	}


	/**
	 * Return the transport assigned id for the request which is then used to set
	 * {@link ExecutionInput.Builder#executionId(ExecutionId) executionId}.
	 * The is initialized as follows:
	 * <ul>
	 * <li>For WebFlux, this is the {@code ServerHttpRequest} id which correlates
	 * to WebFlux log messages. For Reactor Netty, it also correlates to server
	 * log messages.
	 * <li>For Spring MVC, the id is generated via
	 * {@link org.springframework.util.AlternativeJdkIdGenerator}, which does
	 * not correlate to anything, but is more efficient than the default
	 * {@link graphql.execution.ExecutionIdProvider} which relies on
	 * {@code UUID.randomUUID()}.
	 * <li>For WebSocket, this is the GraphQL over WebSocket {@code "subscribe"}
	 * message id, which correlates to WebSocket messages.
	 * </ul>
	 * <p>To override this id, use {@link #executionId(ExecutionId)} or configure
	 * {@link graphql.GraphQL} with an {@link graphql.execution.ExecutionIdProvider}.
	 * @return the request id
	 */
	public String getId() {
		return this.id;
	}

	/**
	 * Configure the {@link ExecutionId} to set on
	 * {@link ExecutionInput#getExecutionId()}, overriding the transport assigned
	 * {@link #getId() id}.
	 * @param executionId the id to use
	 */
	public void executionId(ExecutionId executionId) {
		Assert.notNull(executionId, "executionId is required");
		this.executionId = executionId;
	}

	/**
	 * Return the configured {@link #executionId(ExecutionId) executionId}.
	 */
	@Nullable
	public ExecutionId getExecutionId() {
		return this.executionId;
	}

	/**
	 * Return the transport assigned locale value, if any.
	 */
	@Nullable
	public Locale getLocale() {
		return this.locale;
	}

	/**
	 * Provide a {@code BiFunction} to help initialize the {@link ExecutionInput}
	 * passed to {@link graphql.GraphQL}. The {@code ExecutionInput} is first
	 * pre-populated with values from "this" {@code RequestInput}, and is then
	 * customized with the functions provided here.
	 * @param configurer a {@code BiFunction} that accepts the
	 * {@code ExecutionInput} initialized so far, and a builder to customize it.
	 */
	public void configureExecutionInput(BiFunction<ExecutionInput, ExecutionInput.Builder, ExecutionInput> configurer) {
		this.executionInputConfigurers.add(configurer);
	}

	/**
	 * Create the {@link ExecutionInput} to pass to {@link graphql.GraphQL}.
	 * passed to {@link graphql.GraphQL}. The {@code ExecutionInput} is populated
	 * with values from "this" {@code RequestInput}, and then customized with
	 * functions provided via {@link #configureExecutionInput(BiFunction)}.
	 * @return the resulting {@code ExecutionInput}
	 */
	public ExecutionInput toExecutionInput() {
		ExecutionInput.Builder inputBuilder = ExecutionInput.newExecutionInput()
				.query(getDocument())
				.operationName(getOperationName())
				.variables(getVariables())
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
		return super.toString() + (getLocale() != null ? ", Locale=" + getLocale() : "");
	}

}
