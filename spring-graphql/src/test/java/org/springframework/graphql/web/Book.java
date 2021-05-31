package org.springframework.graphql.web;

public class Book {

	Long id;

	String name;

	String author;


	public Book() {
	}

	public Book(Long id, String name, String author) {
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

	public String getAuthor() {
		return this.author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}
}
