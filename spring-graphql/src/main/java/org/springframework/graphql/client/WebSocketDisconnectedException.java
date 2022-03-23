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

import org.springframework.graphql.GraphQlRequest;
import org.springframework.web.reactive.socket.CloseStatus;

/**
 * WebSocket related {@link GraphQlTransportException} raised when the connection
 * is closed while a request or subscription is in progress.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
@SuppressWarnings("serial")
public class WebSocketDisconnectedException extends GraphQlTransportException {

	private final CloseStatus closeStatus;


	/**
	 * Constructor with an explanation about the closure, along with the request
	 * details and the status used to close the WebSocket session.
	 */
	public WebSocketDisconnectedException(String closeStatusMessage, GraphQlRequest request, CloseStatus status) {
		super(closeStatusMessage, null, request);
		this.closeStatus = status;
	}


	/**
	 * Return the {@link CloseStatus} used to close the WebSocket session.
	 */
	public CloseStatus getCloseStatus() {
		return this.closeStatus;
	}

}
