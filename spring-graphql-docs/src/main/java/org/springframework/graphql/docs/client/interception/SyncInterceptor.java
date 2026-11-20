/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.client.interception;

import org.springframework.graphql.client.ClientGraphQlRequest;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.SyncGraphQlClientInterceptor;

public class SyncInterceptor implements SyncGraphQlClientInterceptor {

	@Override
	public ClientGraphQlResponse intercept(ClientGraphQlRequest request, Chain chain) {
		// ...
		return chain.next(request);
	}
}
