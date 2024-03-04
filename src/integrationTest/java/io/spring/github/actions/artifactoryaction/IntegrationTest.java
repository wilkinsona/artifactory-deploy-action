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

import java.io.IOException;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class IntegrationTest {

	@Container
	static GenericContainer<?> container = new GenericContainer<>("docker.bintray.io/jfrog/artifactory-oss:7.12.10")
		.waitingFor(Wait.forHttp("/artifactory/api/system/ping").withStartupTimeout(Duration.ofMinutes(15)))
		.withExposedPorts(8081);

	@Test
	void deploy() throws IOException {
		Application.main(new String[] {
				String.format("--artifactory.server.uri=http://%s:%s/artifactory", container.getHost(),
						container.getFirstMappedPort()),
				"--artifactory.server.username=admin", "--artifactory.server.password=password",
				"--artifactory.deploy.repository=example-repo-local", "--artifactory.deploy.folder=config",
				"--artifactory.deploy.build.name=integration-test" });
	}

}
