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
package org.springframework.graphql.test.tester;

import org.springframework.graphql.RequestInput;

/**
 * Abstracts how a GraphQL request is performed, given {@link RequestInput}, and
 * resulting in the creation of a response spec.
 *
 * <p>For internal use use from {@link DefaultGraphQlTester}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
interface RequestStrategy {

	/**
	 * Perform a request with the given {@link RequestInput} container.
	 * @param input the request input
	 * @return the response spec
	 */
	GraphQlTester.ResponseSpec execute(RequestInput input);

	/**
	 * Perform a subscription with the given {@link RequestInput} container.
	 * @param input the request input
	 * @return the subscription spec
	 */
	GraphQlTester.SubscriptionSpec executeSubscription(RequestInput input);

}
