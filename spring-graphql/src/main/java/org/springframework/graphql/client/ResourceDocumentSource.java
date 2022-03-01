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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;

/**
 * {@link DocumentSource} that looks under a set of locations for a
 * {@link Resource} with the document name and a list of configured extensions.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class ResourceDocumentSource implements DocumentSource {

	private static final List<String> FILE_EXTENSIONS = Arrays.asList(".graphql", ".gql");


	private final List<Resource> locations;

	private final List<String> extensions;


	/**
	 * Default constructor to look under {@code graphql/} on the classpath for
	 * resources with extensions ".graphql" and ".gql".
	 */
	public ResourceDocumentSource() {
		this(Collections.singletonList(new ClassPathResource("graphql/")));
	}

	/**
	 * Constructor with custom locations with extensions ".graphql" and ".gql".
	 */
	public ResourceDocumentSource(List<Resource> locations) {
		this(locations, FILE_EXTENSIONS);
	}

	/**
	 * Constructor with given locations and extensions.
	 */
	public ResourceDocumentSource(List<Resource> locations, List<String> extensions) {
		this.locations = new ArrayList<>(locations);
		this.extensions = new ArrayList<>(extensions);
	}


	/**
	 * Return the configured locations.
	 */
	public List<Resource> getLocations() {
		return this.locations;
	}

	/**
	 * Return the configured extensions.
	 */
	public List<String> getExtensions() {
		return this.extensions;
	}


	@Override
	public String getDocument(String name) {
		return this.locations.stream()
				.flatMap(location -> this.extensions.stream().map(ext -> getRelativeResource(location, name, ext)))
				.filter(Resource::exists)
				.findFirst()
				.map(resource -> {
					try {
						ByteArrayOutputStream out = new ByteArrayOutputStream();
						FileCopyUtils.copy(resource.getInputStream(), out);
						return new String(out.toByteArray(), StandardCharsets.UTF_8);
					}
					catch (IOException ex) {
						throw new IllegalArgumentException(
								"Found resource: " + resource.getDescription() + " but failed to read it", ex);
					}
				})
				.orElse(null);
	}

	private Resource getRelativeResource(Resource location, String name, String ext) {
		try {
			return location.createRelative(name + ext);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
