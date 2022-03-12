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

package org.springframework.graphql.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import reactor.core.publisher.Mono;

/**
 * Base class for {@link DocumentSource} implementations providing support for
 * caching loaded documents.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class CachingDocumentSource implements DocumentSource {

	private final DocumentSource delegate;

	private boolean cacheEnabled = true;

	private final Map<String, Mono<String>> documentCache = new ConcurrentHashMap<>();


	/**
	 * Constructor with the {@code DocumentSource} to actually load documents.
	 */
	public CachingDocumentSource(DocumentSource delegate) {
		this.delegate = delegate;
	}


	/**
	 * Enable or disable caching of resolved documents.
	 * <p>By default, set to {@code true}.
	 * @param cacheEnabled enable if {@code true} and disable if {@code false}
	 */
	public void setCacheEnabled(boolean cacheEnabled) {
		this.cacheEnabled = cacheEnabled;
		if (!cacheEnabled) {
			this.documentCache.clear();
		}
	}

	/**
	 * Whether {@link #setCacheEnabled(boolean) caching} is enabled.
	 */
	public boolean isCacheEnabled() {
		return cacheEnabled;
	}


	@Override
	public Mono<String> getDocument(String name) {
		return (isCacheEnabled() ?
				this.documentCache.computeIfAbsent(name, k -> this.delegate.getDocument(name).cache()) :
				this.delegate.getDocument(name));
	}

	/**
	 * Remove all entries from the document cache.
 	 */
	public void clearCache() {
		this.documentCache.clear();
	}

}
