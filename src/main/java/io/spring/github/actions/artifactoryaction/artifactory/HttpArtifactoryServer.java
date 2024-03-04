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

package io.spring.github.actions.artifactoryaction.artifactory;

import java.net.URI;

import org.springframework.web.client.RestTemplate;

/**
 * Default {@link ArtifactoryServer} implementation communicating over HTTP.
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 * @author Andy Wilkinson
 */
public class HttpArtifactoryServer implements ArtifactoryServer {

	private final URI uri;

	private final RestTemplate restTemplate;

	HttpArtifactoryServer(RestTemplate restTemplate, URI uri) {
		this.uri = uri;
		this.restTemplate = restTemplate;
	}

	@Override
	public ArtifactoryRepository repository(String repositoryName) {
		return new HttpArtifactoryRepository(this.restTemplate, this.uri, repositoryName);
	}

	@Override
	public ArtifactoryBuildRuns buildRuns(String buildName, String project) {
		return new HttpArtifactoryBuildRuns(this.restTemplate, this.uri, buildName, project);
	}

}
