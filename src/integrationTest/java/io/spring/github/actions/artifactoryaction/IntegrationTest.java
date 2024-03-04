/*
 * Copyright 2017-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.spring.github.actions.artifactoryaction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.boot.test.json.BasicJsonTester;
import org.springframework.boot.test.json.JsonContent;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class IntegrationTest {

	@Container
	static GenericContainer<?> container = new GenericContainer<>("docker.bintray.io/jfrog/artifactory-oss:7.12.10")
		.waitingFor(Wait.forHttp("/artifactory/api/system/ping").withStartupTimeout(Duration.ofMinutes(15)))
		.withExposedPorts(8081);

	@Test
	void deploy(@TempDir File temp) throws IOException {
		File example = new File(temp, "com/example/1.0.0");
		example.mkdirs();
		Files.writeString(new File(example, "example-1.0.0.jar").toPath(), "jar-file-content");
		Files.writeString(new File(example, "example-1.0.0.pom").toPath(), "pom-file-content");
		Application.main(new String[] {
				String.format("--artifactory.server.uri=http://%s:%s/artifactory", container.getHost(),
						container.getFirstMappedPort()),
				"--artifactory.server.username=admin", "--artifactory.server.password=password",
				"--artifactory.deploy.repository=example-repo-local", "--artifactory.deploy.build.number=13",
				"--artifactory.deploy.build.name=integration-test", "--artifactory.deploy.folder=" + temp,
				"--artifactory.deploy.threads=2" });
		RestTemplate rest = new RestTemplateBuilder().basicAuthentication("admin", "password")
			.rootUri("http://%s:%s/artifactory/".formatted(container.getHost(), container.getFirstMappedPort()))
			.build();
		assertThat(rest.getForObject("/example-repo-local/com/example/1.0.0/example-1.0.0.jar", String.class))
			.isEqualTo("jar-file-content");
		assertThat(rest.getForObject("/example-repo-local/com/example/1.0.0/example-1.0.0.pom", String.class))
			.isEqualTo("pom-file-content");
		JsonContent<?> json = new BasicJsonTester(getClass()).from(rest.getForObject("/api/builds", String.class));
		assertThat(json).extractingJsonPathValue("data[0].buildNumber").isEqualTo("13");
		assertThat(json).extractingJsonPathValue("data[0].buildName").isEqualTo("integration-test");
	}

}
