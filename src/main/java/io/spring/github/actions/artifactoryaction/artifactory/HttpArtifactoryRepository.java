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
import java.util.Map;

import io.spring.github.actions.artifactoryaction.artifactory.payload.Checksums;
import io.spring.github.actions.artifactoryaction.artifactory.payload.DeployableArtifact;
import io.spring.github.actions.artifactoryaction.system.ConsoleLogger;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.RequestEntity.BodyBuilder;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Default {@link ArtifactoryRepository} implementation communicating over HTTP.
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 */
public class HttpArtifactoryRepository implements ArtifactoryRepository {

	private static final Duration RETRY_DELAY = Duration.ofSeconds(5);

	private static final ConsoleLogger console = new ConsoleLogger();

	private static final int KB = 1024;

	private static final long CHECKSUM_THRESHOLD = 10 * KB;

	private final RestTemplate restTemplate;

	private final URI uri;

	private final String repositoryName;

	public HttpArtifactoryRepository(RestTemplate restTemplate, URI uri, String repositoryName) {
		this.restTemplate = restTemplate;
		this.uri = uri;
		this.repositoryName = repositoryName;
	}

	@Override
	public void deploy(DeployableArtifact artifact, DeployOption... options) {
		try {
			Assert.notNull(artifact, "Artifact must not be null");
			if (artifact.getSize() <= CHECKSUM_THRESHOLD
					|| ObjectUtils.containsElement(options, DeployOption.DISABLE_CHECKSUM_UPLOADS)) {
				deployUsingContent(artifact);
				return;
			}
			try {
				deployUsingChecksum(artifact);
			}
			catch (Exception ex) {
				if (!(ex instanceof HttpClientErrorException || isCausedBySocketException(ex))) {
					throw ex;
				}
				deployUsingContent(artifact);
			}
		}
		catch (Exception ex) {
			throw new RuntimeException(
					"Error deploying artifact " + artifact.getPath() + " with checksums " + artifact.getChecksums(),
					ex);
		}
	}

	private void deployUsingChecksum(DeployableArtifact artifact) {
		RequestEntity<Void> request = deployRequest(artifact).header("X-Checksum-Deploy", "true").build();
		this.restTemplate.exchange(request, Void.class);
	}

	private void deployUsingContent(DeployableArtifact artifact) {
		int attempt = 0;
		while (true) {
			try {
				attempt++;
				RequestEntity<Resource> request = deployRequest(artifact).contentLength(artifact.getSize())
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
						HttpArtifactoryRepository.RETRY_DELAY.toMillis());
				trySleep(HttpArtifactoryRepository.RETRY_DELAY);
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

	private BodyBuilder deployRequest(DeployableArtifact artifact) {
		UriComponents uriComponents = UriComponentsBuilder.fromUri(this.uri)
			.path(this.repositoryName)
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

}
