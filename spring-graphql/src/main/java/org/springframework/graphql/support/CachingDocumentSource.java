/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
	 * @param delegate the delegate document source
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
		return this.cacheEnabled;
	}


	@Override
	public Mono<String> getDocument(String name) {
		return ((isCacheEnabled()) ?
				this.documentCache.computeIfAbsent(name, (k) -> this.delegate.getDocument(name).cache()) :
				this.delegate.getDocument(name));
	}

	/**
	 * Remove all entries from the document cache.
 	 */
	public void clearCache() {
		this.documentCache.clear();
	}

}
