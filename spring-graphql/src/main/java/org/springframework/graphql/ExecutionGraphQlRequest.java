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

import java.util.Locale;
import java.util.function.BiFunction;

import graphql.ExecutionInput;
import graphql.execution.ExecutionId;

import org.springframework.lang.Nullable;


/**
 * Implementation of {@link GraphQlRequest} for request handling through GraphQL
 * Java with support for customizing the {@link ExecutionInput} passed into
 * {@link graphql.GraphQL}.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 1.0.0
 */
public interface ExecutionGraphQlRequest extends GraphQlRequest {

	/**
	 * Return the transport assigned id for the request that in turn sets
	 * {@link ExecutionInput.Builder#executionId(ExecutionId) executionId}.
	 * <p>By default, the id is initialized as follows:
	 * <ul>
	 * <li>On WebFlux, this is the {@code ServerHttpRequest} id which correlates
	 * to WebFlux log messages. For Reactor Netty, it also correlates to server
	 * log messages.
	 * <li>On Spring MVC, the id is generated via
	 * {@link org.springframework.util.AlternativeJdkIdGenerator}, which does
	 * not correlate to anything, but is more efficient than the default
	 * {@link graphql.execution.ExecutionIdProvider} which relies on
	 * {@code UUID.randomUUID()}.
	 * <li>On WebSocket, the id is set to the message id of the {@code "subscribe"}
	 * message from the GraphQL over WebSocket protocol that is used to correlate
	 * request and response messages on the the WebSocket.
	 * </ul>
	 * <p>To override this id, use {@link #executionId(ExecutionId)} or configure
	 * {@link graphql.GraphQL} with an {@link graphql.execution.ExecutionIdProvider}.
	 * @return the request id
	 */
	String getId();

	/**
	 * Configure the {@link ExecutionId} to set on
	 * {@link ExecutionInput#getExecutionId()}, overriding the transport assigned
	 * {@link #getId() id}.
	 * @param executionId the id to use
	 */
	void executionId(ExecutionId executionId);

	/**
	 * Return the configured {@link #executionId(ExecutionId) executionId}.
	 */
	@Nullable
	ExecutionId getExecutionId();

	/**
	 * Return the transport assigned locale value, if any.
	 */
	@Nullable
	Locale getLocale();

	/**
	 * Provide a {@code BiFunction} to help initialize the {@link ExecutionInput}
	 * passed to {@link graphql.GraphQL}. The {@code ExecutionInput} is first
	 * pre-populated with values from "this" {@code ExecutionGraphQlRequest}, and
	 * is then customized with the functions provided here.
	 * @param configurer a {@code BiFunction} that accepts the
	 * {@code ExecutionInput} initialized so far, and a builder to customize it.
	 */
	void configureExecutionInput(BiFunction<ExecutionInput, ExecutionInput.Builder, ExecutionInput> configurer);

	/**
	 * Create the {@link ExecutionInput} to pass to {@link graphql.GraphQL}.
	 * passed to {@link graphql.GraphQL}. The {@code ExecutionInput} is populated
	 * with values from "this" {@code ExecutionGraphQlRequest}, and then customized
	 * with functions provided via {@link #configureExecutionInput(BiFunction)}.
	 * @return the resulting {@code ExecutionInput}
	 */
	ExecutionInput toExecutionInput();

}
