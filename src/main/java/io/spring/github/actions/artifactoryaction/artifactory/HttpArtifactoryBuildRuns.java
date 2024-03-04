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
import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.spring.github.actions.artifactoryaction.artifactory.payload.BuildAgent;
import io.spring.github.actions.artifactoryaction.artifactory.payload.BuildInfo;
import io.spring.github.actions.artifactoryaction.artifactory.payload.BuildModule;
import io.spring.github.actions.artifactoryaction.artifactory.payload.ContinuousIntegrationAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Default {@link ArtifactoryBuildRuns} implementation communicating over HTTP.
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 * @author Andy Wilkinson
 */
public final class HttpArtifactoryBuildRuns implements ArtifactoryBuildRuns {

	private static final Logger logger = LoggerFactory.getLogger(HttpArtifactoryBuildRuns.class);

	private final RestTemplate restTemplate;

	private final URI uri;

	private final String buildName;

	private final String project;

	public HttpArtifactoryBuildRuns(RestTemplate restTemplate, URI uri, String buildName, String project) {
		this.restTemplate = restTemplate;
		this.uri = uri;
		this.buildName = buildName;
		this.project = project;
	}

	@Override
	public void add(int buildNumber, ContinuousIntegrationAgent continuousIntegrationAgent, Instant started,
			URI buildUri, Map<String, String> properties, List<BuildModule> modules) {
		logger.debug("Adding {} from CI agent {}", buildNumber, continuousIntegrationAgent);
		add(new BuildInfo(this.buildName, Integer.toString(buildNumber), continuousIntegrationAgent, new BuildAgent(),
				started, (buildUri != null) ? buildUri.toString() : null, properties, modules));
	}

	private void add(BuildInfo buildInfo) {
		UriComponentsBuilder builder = UriComponentsBuilder.fromUri(this.uri).path("api/build");
		if (this.project != null) {
			logger.debug("Publishing to project {}", this.project);
			builder = builder.queryParam("project", this.project);
		}
		UriComponents uriComponents = builder.build();
		URI uri = uriComponents.encode().toUri();
		logger.info("Publishing build info to {}", uri);
		RequestEntity<BuildInfo> request = RequestEntity.put(uri)
			.contentType(MediaType.APPLICATION_JSON)
			.body(buildInfo);
		ResponseEntity<Void> exchange = this.restTemplate.exchange(request, Void.class);
		exchange.getBody();
	}

	/**
	 * Simple JSON builder support class.
	 */
	static class Json {

		private final StringBuilder json = new StringBuilder();

		Json and(String field, Object value) {
			this.json.append((!this.json.isEmpty()) ? ", " : "");
			appendJson(field);
			this.json.append(" : ");
			appendJson(value);
			return this;
		}

		private void appendJson(Object value) {
			if (value instanceof Json) {
				this.json.append(value);
			}
			else {
				this.json.append("\"%s\"".formatted(value));
			}
		}

		@Override
		public String toString() {
			return "{%s}".formatted(this.json);
		}

		static Json of(String field, Object value) {
			return new Json().and(field, value);
		}

	}

}
