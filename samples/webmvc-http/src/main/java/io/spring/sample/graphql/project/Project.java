package io.spring.sample.graphql.project;

import java.util.List;

public class Project {

	private String slug;

	private String name;

	private String repositoryUrl;

	private ProjectStatus status;

	private List<Release> releases;

	public Project() {
	}

	public Project(String slug, String name, String repositoryUrl, ProjectStatus status) {
		this.slug = slug;
		this.name = name;
		this.repositoryUrl = repositoryUrl;
		this.status = status;
	}

	public String getSlug() {
		return this.slug;
	}

	public void setSlug(String slug) {
		this.slug = slug;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getRepositoryUrl() {
		return this.repositoryUrl;
	}

	public void setRepositoryUrl(String repositoryUrl) {
		this.repositoryUrl = repositoryUrl;
	}

	public ProjectStatus getStatus() {
		return this.status;
	}

	public void setStatus(ProjectStatus status) {
		this.status = status;
	}

	public List<Release> getReleases() {
		return this.releases;
	}

	public void setReleases(List<Release> releases) {
		this.releases = releases;
	}

}
