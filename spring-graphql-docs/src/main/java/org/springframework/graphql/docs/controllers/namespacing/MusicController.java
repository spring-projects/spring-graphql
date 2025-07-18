/*
 * Copyright 2020-present the original author or authors.
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

package org.springframework.graphql.docs.controllers.namespacing;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
@SchemaMapping(typeName = "MusicQueries") // <1>
public class MusicController {

	@QueryMapping // <2>
	public MusicQueries music() {
		return new MusicQueries();
	}

	// <3>
	public record MusicQueries() {

	}

	@SchemaMapping // <4>
	public Album album(@Argument String id) {
		return new Album(id, "Spring GraphQL");
	}

	@SchemaMapping
	public List<Artist> searchForArtist(@Argument String name) {
		return List.of(new Artist("100", "the Spring team"));
	}


}
