package io.spring.sample.graphql.project;

public class Release {

	private String version;

	private ReleaseStatus status;

	private String referenceDocUrl;

	private String apiDocUrl;

	private boolean current;

	public String getVersion() {
		return this.version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public ReleaseStatus getStatus() {
		return this.status;
	}

	public void setStatus(ReleaseStatus status) {
		this.status = status;
	}

	public String getReferenceDocUrl() {
		return this.referenceDocUrl;
	}

	public void setReferenceDocUrl(String referenceDocUrl) {
		this.referenceDocUrl = referenceDocUrl;
	}

	public String getApiDocUrl() {
		return this.apiDocUrl;
	}

	public void setApiDocUrl(String apiDocUrl) {
		this.apiDocUrl = apiDocUrl;
	}

	public boolean isCurrent() {
		return this.current;
	}

	public void setCurrent(boolean current) {
		this.current = current;
	}

}
