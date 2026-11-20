/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.server;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ConsumeOneAndNeverCompleteInterceptor implements WebGraphQlInterceptor {

	@Override
	public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
		return chain.next(request).map(response -> response.transform(builder -> {
			Object originalData = response.getData();
			if (originalData instanceof Publisher) {
				Flux<?> updatedData = Flux.from((Publisher<?>) originalData).take(1).concatWith(Flux.never());
				builder.data(updatedData);
			}
		}));
	}

}
