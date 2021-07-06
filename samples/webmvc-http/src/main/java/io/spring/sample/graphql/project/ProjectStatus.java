package io.spring.sample.graphql.project;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ProjectStatus {

	ACTIVE, COMMUNITY, INCUBATING, ATTIC;

	@JsonCreator
	public static ProjectStatus fromName(String name) {
		return Arrays.stream(ProjectStatus.values())
				.filter(type -> type.name().equals(name))
				.findFirst()
				.orElse(ProjectStatus.ACTIVE);
	}

}
