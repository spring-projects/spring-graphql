/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
	 * @param closeStatusMessage the message received when the connection was closed
	 * @param request the ongoing request when the connection was closed
	 * @param status the received close status
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
