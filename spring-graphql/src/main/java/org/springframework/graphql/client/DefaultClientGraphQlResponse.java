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

package org.springframework.graphql.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlResponse;


/**
 * Default implementation of {@link ClientGraphQlResponse}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultClientGraphQlResponse extends MapGraphQlResponse implements ClientGraphQlResponse {

	private final GraphQlRequest request;

	private final Encoder<?> encoder;

	private final Decoder<?> decoder;


	DefaultClientGraphQlResponse(
			GraphQlRequest request, GraphQlResponse response, Encoder<?> encoder, Decoder<?> decoder) {

		super(response);

		this.request = request;
		this.encoder = encoder;
		this.decoder = decoder;
	}


	@Override
	public GraphQlRequest getRequest() {
		return this.request;
	}

	Encoder<?> getEncoder() {
		return this.encoder;
	}

	Decoder<?> getDecoder() {
		return this.decoder;
	}


	@Override
	public ClientGraphQlResponseField field(String path) {
		return new DefaultClientGraphQlResponseField(this, path);
	}

	@Override
	public <D> D toEntity(Class<D> type) {
		return field("").toEntity(type);
	}

	@Override
	public <D> D toEntity(ParameterizedTypeReference<D> type) {
		return field("").toEntity(type);
	}

}
