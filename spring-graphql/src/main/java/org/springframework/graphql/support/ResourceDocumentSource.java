/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;

/**
 * {@link DocumentSource} that searches for document {@link Resource}s across
 * multiple locations while trying different file extensions.
 *
 * @author Rossen Stoyanchev
 * @author Marco Schäck
 * @since 1.0.0
 */
public class ResourceDocumentSource implements DocumentSource {

	/**
	 * The default file extensions, ".graphql" and ".gql".
	 */
	public static final List<String> FILE_EXTENSIONS = Arrays.asList(".graphql", ".gql");


	private final List<Resource> locations;

	private final List<String> extensions;


	/**
	 * Default constructor that sets the location to {@code "classpath:graphql/"}
	 * and the extensions to ".graphql" and ".gql".
	 */
	public ResourceDocumentSource() {
		this(Collections.singletonList(new ClassPathResource("graphql/")), FILE_EXTENSIONS);
	}

	/**
	 * Constructor with a single location and the default file extensions.
	 * @param location the resource location
	 * @since 2.0.0
	 */
	public ResourceDocumentSource(Resource location) {
		this(Collections.singletonList(location), FILE_EXTENSIONS);
	}

	/**
	 * Constructor with given locations and extensions.
	 * @param locations the resource locations
	 * @param extensions the file extensions for document sources
	 */
	public ResourceDocumentSource(List<Resource> locations, List<String> extensions) {
		this.locations = Collections.unmodifiableList(new ArrayList<>(locations));
		this.extensions = Collections.unmodifiableList(new ArrayList<>(extensions));
	}


	/**
	 * Return a read-only list with the configured locations where to check for
	 * documents.
	 */
	public List<Resource> getLocations() {
		return this.locations;
	}

	/**
	 * Return a read-only list with the file extensions to try when checking
	 * for documents by name.
	 */
	public List<String> getExtensions() {
		return this.extensions;
	}


	@Override
	public Mono<String> getDocument(String name) {
		return Flux.fromIterable(this.locations)
				.flatMapIterable((location) -> getCandidateResources(name, location))
				.filter(Resource::exists)
				.next()
				.map(this::resourceToString)
				.switchIfEmpty(Mono.fromRunnable(() -> {
					throw new IllegalStateException(
							"Failed to find document, name='" + name + "', under location(s)=" +
									this.locations.stream().map(Resource::toString).toList());
				}))
				.subscribeOn(Schedulers.boundedElastic());
	}

	private List<Resource> getCandidateResources(String name, Resource location) {
		return this.extensions.stream()
				.map((ext) -> {
					try {
						return location.createRelative(name + ext);
					}
					catch (IOException ex) {
						throw new IllegalStateException(ex);
					}
				})
				.collect(Collectors.toList());
	}

	private String resourceToString(Resource resource) {
		try {
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			FileCopyUtils.copy(resource.getInputStream(), outputStream);
			return outputStream.toString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalArgumentException(
					"Found resource: " + resource.getDescription() + " but failed to read it", ex);
		}
	}

}
