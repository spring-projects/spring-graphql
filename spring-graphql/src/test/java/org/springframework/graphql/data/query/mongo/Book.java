/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.query.mongo;

import org.springframework.data.annotation.Id;

public class Book {

	@Id
	String id;

	String name;

	Author author = new Author();

	public Book() {
	}

	public Book(String id, String name, Author author) {
		this.id = id;
		this.name = name;
		this.author = author;
	}

	public String getId() {
		return this.id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Author getAuthor() {
		return this.author;
	}

	public void setAuthor(Author author) {
		this.author = author;
	}

}
