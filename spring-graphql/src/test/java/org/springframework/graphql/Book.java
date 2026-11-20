/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql;

public class Book {

	Long id;

	String name;

	Long authorId;

	Author author;

	public String publicField;

	public Book() {
	}

	public Book(Long id, String name, Long authorId) {
		this.id = id;
		this.name = name;
		this.authorId = authorId;
		this.author = null;
	}

	public Book(Long id, String name, Author author) {
		this.id = id;
		this.name = name;
		this.authorId = author.getId();
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

	public Long getAuthorId() {
		return this.authorId;
	}

	public Author getAuthor() {
		return this.author;
	}

	public void setAuthor(Author author) {
		this.author = author;
	}

}
