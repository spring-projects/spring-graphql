/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
