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

import java.io.File;
import java.net.SocketException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import io.spring.github.actions.artifactoryaction.artifactory.payload.DeployableArtifact;
import io.spring.github.actions.artifactoryaction.artifactory.payload.DeployableByteArrayArtifact;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.test.web.client.ResponseCreator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests for {@link HttpArtifactoryRepository}.
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 */
@RestClientTest(HttpArtifactory.class)
class HttpArtifactoryRepositoryTests {

	private static final byte[] BYTES;

	static {
		BYTES = new byte[1024 * 11];
		new Random().nextBytes(BYTES);
	}

	@Autowired
	private MockRestServiceServer server;

	@Autowired
	private MockServerRestTemplateCustomizer customizer;

	@Autowired
	private Artifactory artifactory;

	private ArtifactoryRepository artifactoryRepository;

	@TempDir
	File tempDir;

	@BeforeEach
	void setup() {
		this.artifactoryRepository = this.artifactory
			.server(URI.create("https://repo.example.com"), "admin", "password")
			.repository("libs-snapshot-local");
	}

	@AfterEach
	void tearDown() {
		this.customizer.getExpectationManagers().clear();
	}

	@Test
	void deployUploadsTheDeployableArtifact() {
		DeployableArtifact artifact = new DeployableByteArrayArtifact("/foo/bar.jar", BYTES);
		String url = "https://repo.example.com/libs-snapshot-local/foo/bar.jar";
		this.server.expect(requestTo(url))
			.andExpect(method(HttpMethod.PUT))
			.andExpect(header("X-Checksum-Deploy", "true"))
			.andExpect(header("X-Checksum-Sha1", artifact.getChecksums().getSha1()))
			.andRespond(withStatus(HttpStatus.NOT_FOUND));
		this.server.expect(requestTo(url))
			.andExpect(header("Content-Length", Long.toString(artifact.getSize())))
			.andRespond(withSuccess());
		this.artifactoryRepository.deploy(artifact);
		this.server.verify();
	}

	@Test
	void deployUploadsTheDeployableArtifactWithMatrixParameters() {
		Map<String, String> properties = new HashMap<>();
		properties.put("buildNumber", "1");
		properties.put("revision", "123");
		DeployableArtifact artifact = new DeployableByteArrayArtifact("/foo/bar.jar", BYTES, properties);
		String url = "https://repo.example.com/libs-snapshot-local/foo/bar.jar;buildNumber=1;revision=123";
		this.server.expect(requestTo(url)).andRespond(withSuccess());
		this.artifactoryRepository.deploy(artifact);
		this.server.verify();
	}

	@Test
	void deployWhenChecksumMatchesDoesNotUpload() {
		DeployableArtifact artifact = new DeployableByteArrayArtifact("/foo/bar.jar", BYTES);
		String url = "https://repo.example.com/libs-snapshot-local/foo/bar.jar";
		this.server.expect(requestTo(url))
			.andExpect(method(HttpMethod.PUT))
			.andExpect(header("X-Checksum-Deploy", "true"))
			.andExpect(header("X-Checksum-Sha1", artifact.getChecksums().getSha1()))
			.andRespond(withSuccess());
		this.artifactoryRepository.deploy(artifact);
		this.server.verify();
	}

	@Test
	void deployWhenChecksumUploadFailsWithHttpClientErrorExceptionUploads() {
		DeployableArtifact artifact = new DeployableByteArrayArtifact("/foo/bar.jar", BYTES);
		String url = "https://repo.example.com/libs-snapshot-local/foo/bar.jar";
		this.server.expect(requestTo(url))
			.andExpect(method(HttpMethod.PUT))
			.andExpect(header("X-Checksum-Deploy", "true"))
			.andExpect(header("X-Checksum-Sha1", artifact.getChecksums().getSha1()))
			.andRespond(withStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE));
		this.server.expect(requestTo(url))
			.andExpect(method(HttpMethod.PUT))
			.andExpect(header("X-Checksum-Sha1", artifact.getChecksums().getSha1()))
			.andRespond(withSuccess());
		this.artifactoryRepository.deploy(artifact);
		this.server.verify();
	}

	@Test
	void deployWhenSmallFileDoesNotUseChecksum() {
		DeployableArtifact artifact = new DeployableByteArrayArtifact("/foo/bar.jar", "foo".getBytes());
		String url = "https://repo.example.com/libs-snapshot-local/foo/bar.jar";
		this.server.expect(requestTo(url))
			.andExpect(method(HttpMethod.PUT))
			.andExpect(noChecksumHeader())
			.andRespond(withSuccess());
		this.artifactoryRepository.deploy(artifact);
		this.server.verify();
	}

	@Test
	void deployWhenNoChecksumUploadOptionFileDoesNotUseChecksum() {
		DeployableArtifact artifact = new DeployableByteArrayArtifact("/foo/bar.jar", BYTES);
		String url = "https://repo.example.com/libs-snapshot-local/foo/bar.jar";
		this.server.expect(requestTo(url))
			.andExpect(method(HttpMethod.PUT))
			.andExpect(noChecksumHeader())
			.andRespond(withSuccess());
		this.artifactoryRepository.deploy(artifact, DeployOption.DISABLE_CHECKSUM_UPLOADS);
		this.server.verify();
	}

	@Test
	void deployWhenFlaky400AndLaterAttemptWorksDeploys() {
		deployWhenFlaky(false, HttpStatus.BAD_REQUEST);
	}

	@Test
	void deployWhenFlaky400AndLaterAttemptsFailThrowsException() {
		assertThatExceptionOfType(RuntimeException.class)
			.isThrownBy(() -> deployWhenFlaky(true, HttpStatus.BAD_REQUEST))
			.withMessageStartingWith("Error deploying artifact");
	}

	@Test
	void deployWhenFlaky404AndLaterAttemptWorksDeploys() {
		deployWhenFlaky(false, HttpStatus.NOT_FOUND);
	}

	@Test
	void deployWhenFlaky404AndLaterAttemptsFailThrowsException() {
		assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> deployWhenFlaky(true, HttpStatus.NOT_FOUND))
			.withMessageStartingWith("Error deploying artifact");
	}

	@Test
	void deployWhenFlakySocketExceptionAndLaterAttemptWorksDeploys() {
		deployWhenFlaky(false, withException(new SocketException()));
	}

	@Test
	void deployWhenFlakySocketExceptionAndLaterAttemptsFailThrowsException() {
		assertThatExceptionOfType(RuntimeException.class)
			.isThrownBy(() -> deployWhenFlaky(true, withException(new SocketException())))
			.withMessageStartingWith("Error deploying artifact");
	}

	private void deployWhenFlaky(boolean fail, HttpStatus flakyStatus) {
		deployWhenFlaky(fail, withStatus(flakyStatus));
	}

	private void deployWhenFlaky(boolean fail, ResponseCreator failResponse) {
		DeployableArtifact artifact = new DeployableByteArrayArtifact("/foo/bar.jar", BYTES);
		String url = "https://repo.example.com/libs-snapshot-local/foo/bar.jar";
		try {
			this.server.expect(requestTo(url))
				.andExpect(method(HttpMethod.PUT))
				.andExpect(header("X-Checksum-Deploy", "true"))
				.andExpect(header("X-Checksum-Sha1", artifact.getChecksums().getSha1()))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));
			this.server.expect(requestTo(url)).andRespond(failResponse);
			this.server.expect(requestTo(url)).andRespond(failResponse);
			this.server.expect(requestTo(url)).andRespond(fail ? failResponse : withStatus(HttpStatus.OK));
			this.artifactoryRepository.deploy(artifact);
		}
		finally {
			this.server.verify();
		}
	}

	private RequestMatcher noChecksumHeader() {
		return (request) -> assertThat(request.getHeaders().keySet()).doesNotContain("X-Checksum-Deploy");
	}

}
