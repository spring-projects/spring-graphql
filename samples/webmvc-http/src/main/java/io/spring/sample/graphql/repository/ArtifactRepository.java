package io.spring.sample.graphql.repository;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class ArtifactRepository {

	@Id
	private String id;

	private String name;

	private String url;

	private boolean snapshotsEnabled;

	public ArtifactRepository(String id, String name, String url) {
		this.id = id;
		this.name = name;
		this.url = url;
	}

	public ArtifactRepository() {
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

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public boolean isSnapshotsEnabled() {
		return snapshotsEnabled;
	}

	public void setSnapshotsEnabled(boolean snapshotsEnabled) {
		this.snapshotsEnabled = snapshotsEnabled;
	}

}
