package io.spring.sample.graphql.project;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.client.Hop;
import org.springframework.hateoas.client.Traverson;
import org.springframework.hateoas.server.core.TypeReferences;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class SpringProjectsClient {

	private static final TypeReferences.CollectionModelType<Release> releaseCollection =
			new TypeReferences.CollectionModelType<Release>() {};

	private final Traverson traverson;

	public SpringProjectsClient(RestTemplateBuilder builder) {
		List<HttpMessageConverter<?>> converters = Traverson.getDefaultMessageConverters(MediaTypes.HAL_JSON);
		RestTemplate restTemplate = builder.messageConverters(converters).build();
		this.traverson = new Traverson(URI.create("https://spring.io/api/"), MediaTypes.HAL_JSON);
		this.traverson.setRestOperations(restTemplate);
	}

	public Project fetchProject(String projectSlug) {
		return this.traverson.follow("projects")
				.follow(Hop.rel("project").withParameter("id", projectSlug))
				.toObject(Project.class);
	}

	public List<Release> fetchProjectReleases(String projectSlug) {
		CollectionModel<Release> releases = this.traverson.follow("projects")
				.follow(Hop.rel("project").withParameter("id", projectSlug)).follow(Hop.rel("releases"))
				.toObject(releaseCollection);
		return new ArrayList(releases.getContent());
	}

}
