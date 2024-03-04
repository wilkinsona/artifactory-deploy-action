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

import java.net.SocketException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.spring.github.actions.artifactoryaction.artifactory.payload.BuildAgent;
import io.spring.github.actions.artifactoryaction.artifactory.payload.BuildModule;
import io.spring.github.actions.artifactoryaction.artifactory.payload.Checksums;
import io.spring.github.actions.artifactoryaction.artifactory.payload.ContinuousIntegrationAgent;
import io.spring.github.actions.artifactoryaction.artifactory.payload.DeployableArtifact;
import io.spring.github.actions.artifactoryaction.jackson.JsonIsoDateFormat;
import io.spring.github.actions.artifactoryaction.system.ConsoleLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.RequestEntity.BodyBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Default {@link Artifactory} implementation communicating over HTTP.
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 * @author Gabriel Petrovay
 */
class HttpArtifactory implements Artifactory {

	private static final Logger logger = LoggerFactory.getLogger(HttpArtifactory.class);

	private static final long CHECKSUM_THRESHOLD = 10 * 1024;

	private static final ConsoleLogger console = new ConsoleLogger();

	private final RestTemplate restTemplate;

	private final String uri;

	private final Duration retryDelay;

	HttpArtifactory(RestTemplateBuilder restTemplateBuilder, URI uri, String username, String password) {
		this(restTemplateBuilder, uri, username, password, Duration.ofSeconds(5));
	}

	HttpArtifactory(RestTemplateBuilder restTemplateBuilder, URI uri, String username, String password,
			Duration retryDelay) {
		RestTemplateBuilder builder = restTemplateBuilder.setConnectTimeout(Duration.ofMinutes(1))
			.setReadTimeout(Duration.ofMinutes(5));
		if (StringUtils.hasText(username)) {
			builder = builder.basicAuthentication(username, password);
		}
		this.restTemplate = builder.build();
		String uriString = uri.toString();
		this.uri = uriString.endsWith("/") ? uriString : uriString + "/";
		this.retryDelay = retryDelay;
	}

	@Override
	public void deploy(String repository, DeployableArtifact artifact, DeployOption... options) {
		try {
			Assert.notNull(artifact, "Artifact must not be null");
			if (artifact.getSize() <= CHECKSUM_THRESHOLD
					|| ObjectUtils.containsElement(options, DeployOption.DISABLE_CHECKSUM_UPLOADS)) {
				deployUsingContent(repository, artifact);
				return;
			}
			try {
				deployUsingChecksum(repository, artifact);
			}
			catch (Exception ex) {
				if (!(ex instanceof HttpClientErrorException || isCausedBySocketException(ex))) {
					throw ex;
				}
				deployUsingContent(repository, artifact);
			}
		}
		catch (Exception ex) {
			throw new RuntimeException(
					"Error deploying artifact " + artifact.getPath() + " with checksums " + artifact.getChecksums(),
					ex);
		}
	}

	private void deployUsingChecksum(String repository, DeployableArtifact artifact) {
		RequestEntity<Void> request = deployRequest(repository, artifact).header("X-Checksum-Deploy", "true").build();
		this.restTemplate.exchange(request, Void.class);
	}

	private void deployUsingContent(String repository, DeployableArtifact artifact) {
		int attempt = 0;
		while (true) {
			try {
				attempt++;
				RequestEntity<Resource> request = deployRequest(repository, artifact).contentLength(artifact.getSize())
					.body(artifact.getContent());
				this.restTemplate.exchange(request, Void.class);
				return;
			}
			catch (RestClientResponseException | ResourceAccessException ex) {
				HttpStatusCode statusCode = (ex instanceof RestClientResponseException restClientException)
						? restClientException.getStatusCode() : null;
				boolean flaky = (statusCode == HttpStatus.BAD_REQUEST || statusCode == HttpStatus.NOT_FOUND)
						|| isCausedBySocketException(ex);
				if (!flaky || attempt >= 3) {
					throw ex;
				}
				console.log("Deploy failed with {} response. Retrying in {}ms.", statusCode,
						this.retryDelay.toMillis());
				trySleep(this.retryDelay);
			}
		}
	}

	private boolean isCausedBySocketException(Throwable ex) {
		while (ex != null) {
			if (ex instanceof SocketException) {
				return true;
			}
			ex = ex.getCause();
		}
		return false;
	}

	private void trySleep(Duration time) {
		try {
			Thread.sleep(time.toMillis());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private BodyBuilder deployRequest(String repository, DeployableArtifact artifact) {
		UriComponents uriComponents = UriComponentsBuilder.fromUriString(this.uri)
			.path(repository)
			.path(artifact.getPath())
			.path(buildMatrixParams(artifact.getProperties()))
			.build();
		URI uri = uriComponents.encode().toUri();
		Checksums checksums = artifact.getChecksums();
		return RequestEntity.put(uri)
			.contentType(MediaType.APPLICATION_OCTET_STREAM)
			.header("X-Checksum-Sha1", checksums.getSha1())
			.header("X-Checksum-Md5", checksums.getMd5());
	}

	private String buildMatrixParams(Map<String, String> matrixParams) {
		StringBuilder matrix = new StringBuilder();
		if (matrixParams != null && !matrixParams.isEmpty()) {
			for (Map.Entry<String, String> entry : matrixParams.entrySet()) {
				matrix.append(";" + entry.getKey() + "=" + entry.getValue());
			}
		}
		return matrix.toString();
	}

	@Override
	public void addBuildRun(String project, String buildName, BuildRun buildRun) {
		logger.debug("Adding {} build {}", buildName, buildRun.number());
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(this.uri).path("api/build");
		if (project != null) {
			logger.debug("Publishing to project {}", project);
			builder = builder.queryParam("project", project);
		}
		UriComponents uriComponents = builder.build();
		URI uri = uriComponents.encode().toUri();
		logger.info("Publishing build info to {}", uri);
		RequestEntity<BuildInfo> request = RequestEntity.put(uri)
			.contentType(MediaType.APPLICATION_JSON)
			.body(new BuildInfo(buildName, Integer.toString(buildRun.number()), new ContinuousIntegrationAgent(),
					new BuildAgent(), buildRun.started(), (buildRun.uri() != null) ? buildRun.uri().toString() : null,
					buildRun.modules()));
		ResponseEntity<Void> exchange = this.restTemplate.exchange(request, Void.class);
		exchange.getBody();
	}

	@JsonInclude(Include.NON_NULL)
	public class BuildInfo {

		@JsonProperty("name")
		private final String buildName;

		@JsonProperty("number")
		private final String buildNumber;

		@JsonProperty("agent")
		private final ContinuousIntegrationAgent continuousIntegrationAgent;

		private final BuildAgent buildAgent;

		@JsonIsoDateFormat
		private final Instant started;

		@JsonProperty("url")
		private final String buildUri;

		@JsonProperty("modules")
		private final List<BuildModule> modules;

		BuildInfo(String buildName, String buildNumber, ContinuousIntegrationAgent continuousIntegrationAgent,
				BuildAgent buildAgent, Instant started, String buildUri, List<BuildModule> modules) {
			Assert.hasText(buildName, "BuildName must not be empty");
			Assert.hasText(buildNumber, "BuildNumber must not be empty");
			this.buildName = buildName;
			this.buildNumber = buildNumber;
			this.continuousIntegrationAgent = continuousIntegrationAgent;
			this.buildAgent = buildAgent;
			this.started = (started != null) ? started : Instant.now();
			this.buildUri = buildUri;
			this.modules = (modules != null) ? Collections.unmodifiableList(new ArrayList<>(modules))
					: Collections.emptyList();
		}

		public String getBuildName() {
			return this.buildName;
		}

		public String getBuildNumber() {
			return this.buildNumber;
		}

		public ContinuousIntegrationAgent getContinuousIntegrationAgent() {
			return this.continuousIntegrationAgent;
		}

		public BuildAgent getBuildAgent() {
			return this.buildAgent;
		}

		public Instant getStarted() {
			return this.started;
		}

		public String getBuildUri() {
			return this.buildUri;
		}

		public List<BuildModule> getModules() {
			return this.modules;
		}

	}

}
