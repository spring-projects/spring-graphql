/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.query;

import org.springframework.data.annotation.Id;
import org.springframework.graphql.Author;

public class Book {

	@Id
	Long id;

	String name;

	Author author;

	public Book() {
	}

	public Book(Long id, String name, Author author) {
		this.id = id;
		this.name = name;
		this.author = author;
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

	public Author getAuthor() {
		return this.author;
	}

	public void setAuthor(Author author) {
		this.author = author;
	}

}
