package io.spring.sample.graphql.repository;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ArtifactRepositoriesInitializer implements ApplicationRunner {

	private final ArtifactRepositories repositories;

	public ArtifactRepositoriesInitializer(ArtifactRepositories repositories) {
		this.repositories = repositories;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		List<ArtifactRepository> repositoryList = Arrays.asList(
				new ArtifactRepository("spring-releases", "Spring Releases", "https://repo.spring.io/libs-releases"),
				new ArtifactRepository("spring-milestones", "Spring Milestones", "https://repo.spring.io/libs-milestones"),
				new ArtifactRepository("spring-snapshots", "Spring Snapshots", "https://repo.spring.io/libs-snapshots"));
		repositories.saveAll(repositoryList);
	}

}
