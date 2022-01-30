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

package org.springframework.graphql.web;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ConsumeOneAndNeverCompleteInterceptor implements WebInterceptor {

	@Override
	public Mono<WebOutput> intercept(WebInput webInput, WebInterceptorChain chain) {
		return chain.next(webInput).map((output) -> output.transform(builder -> {
			Object originalData = output.getData();
			if (originalData instanceof Publisher) {
				Flux<?> updatedData = Flux.from((Publisher<?>) originalData).take(1).concatWith(Flux.never());
				builder.data(updatedData);
			}
		}));
	}

}
