package org.springframework.graphql.boot;

public class Book {

	String id;

	String name;

	int pageCount;

	String author;

	public Book() {
	}

	public Book(String id, String name, int pageCount, String author) {
		this.id = id;
		this.name = name;
		this.pageCount = pageCount;
		this.author = author;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getPageCount() {
		return pageCount;
	}

	public void setPageCount(int pageCount) {
		this.pageCount = pageCount;
	}

	public String getAuthor() {
		return author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}
}
