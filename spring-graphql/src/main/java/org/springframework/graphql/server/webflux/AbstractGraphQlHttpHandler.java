/*
 * Copyright 2020-2024 the original author or authors.
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

package org.springframework.graphql.server.webflux;


import reactor.core.publisher.Mono;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.support.SerializableGraphQlRequest;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.server.ServerRequest;

/**
 * Abstract class for GraphQL Handler implementations using the HTTP transport.
 *
 * @author Brian Clozel
 * @since 1.3.0
 */
class AbstractGraphQlHttpHandler {

    protected final WebGraphQlHandler graphQlHandler;

    @Nullable
    protected final HttpCodecDelegate codecDelegate;

    public AbstractGraphQlHttpHandler(WebGraphQlHandler graphQlHandler, @Nullable HttpCodecDelegate codecDelegate) {
        Assert.notNull(graphQlHandler, "WebGraphQlHandler is required");
        this.graphQlHandler = graphQlHandler;
        this.codecDelegate = codecDelegate;
    }

    protected Mono<SerializableGraphQlRequest> readRequest(ServerRequest serverRequest) {
        if (this.codecDelegate != null) {
            MediaType contentType = serverRequest.headers().contentType().orElse(MediaType.APPLICATION_JSON);
            return this.codecDelegate.decode(serverRequest.bodyToFlux(DataBuffer.class), contentType);
        }
        else {
            return serverRequest.bodyToMono(SerializableGraphQlRequest.class);
        }
    }


}
