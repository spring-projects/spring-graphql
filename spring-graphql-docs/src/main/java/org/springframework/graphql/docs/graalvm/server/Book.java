/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.graalvm.server;

public class Book {

	Long id;

	String name;

	Long authorId;

	public Book() {
	}

	public Book(Long id, String name, Long authorId) {
		this.id = id;
		this.name = name;
		this.authorId = authorId;
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

}
