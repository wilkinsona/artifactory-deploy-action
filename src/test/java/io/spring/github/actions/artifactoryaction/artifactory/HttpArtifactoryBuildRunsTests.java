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

import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.spring.github.actions.artifactoryaction.artifactory.payload.BuildArtifact;
import io.spring.github.actions.artifactoryaction.artifactory.payload.BuildModule;
import io.spring.github.actions.artifactoryaction.artifactory.payload.ContinuousIntegrationAgent;
import io.spring.github.actions.artifactoryaction.util.ArtifactoryDateFormat;
import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.util.FileCopyUtils;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests for {@link HttpArtifactoryBuildRuns}.
 *
 * @author Madhura Bhave
 * @author Phillip Webb
 */
@RestClientTest(HttpArtifactory.class)
class HttpArtifactoryBuildRunsTests {

	@Autowired
	private MockRestServiceServer server;

	@Autowired
	private MockServerRestTemplateCustomizer customizer;

	@Autowired
	private Artifactory artifactory;

	@AfterEach
	void tearDown() {
		this.customizer.getExpectationManagers().clear();
	}

	@Test
	void addAddsBuildInfo() {
		ArtifactoryBuildRuns buildRuns = buildRuns();
		this.server.expect(requestTo("https://repo.example.com/api/build"))
			.andExpect(method(HttpMethod.PUT))
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonContent(getResource("payload/build-info.json")))
			.andRespond(withSuccess());
		ContinuousIntegrationAgent agent = new ContinuousIntegrationAgent("Concourse", "3.0.0");
		BuildArtifact artifact = new BuildArtifact("jar", "a9993e364706816aba3e25717850c26c9cd0d89d",
				"900150983cd24fb0d6963f7d28e17f72", "foo.jar");
		List<BuildArtifact> artifacts = Collections.singletonList(artifact);
		List<BuildModule> modules = Collections
			.singletonList(new BuildModule("com.example.module:my-module:1.0.0-SNAPSHOT", artifacts));
		Instant started = ArtifactoryDateFormat.parse("2014-09-30T12:00:19.893Z");
		Map<String, String> properties = Collections.singletonMap("made-by", "concourse");
		buildRuns.add(5678, agent, started, URI.create("https://ci.example.com"), properties, modules);
		this.server.verify();
	}

	@Test
	void addWithProjectAddsBuildInfo() {
		ArtifactoryBuildRuns buildRuns = buildRuns("my-project");
		this.server.expect(requestTo("https://repo.example.com/api/build?project=my-project"))
			.andExpect(method(HttpMethod.PUT))
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonContent(getResource("payload/build-info.json")))
			.andRespond(withSuccess());
		ContinuousIntegrationAgent agent = new ContinuousIntegrationAgent("Concourse", "3.0.0");
		BuildArtifact artifact = new BuildArtifact("jar", "a9993e364706816aba3e25717850c26c9cd0d89d",
				"900150983cd24fb0d6963f7d28e17f72", "foo.jar");
		List<BuildArtifact> artifacts = Collections.singletonList(artifact);
		List<BuildModule> modules = Collections
			.singletonList(new BuildModule("com.example.module:my-module:1.0.0-SNAPSHOT", artifacts));
		Instant started = ArtifactoryDateFormat.parse("2014-09-30T12:00:19.893Z");
		Map<String, String> properties = Collections.singletonMap("made-by", "concourse");
		buildRuns.add(5678, agent, started, URI.create("https://ci.example.com"), properties, modules);
		this.server.verify();
	}

	private RequestMatcher jsonContent(Resource expected) {
		return (request) -> {
			String actualJson = ((MockClientHttpRequest) request).getBodyAsString();
			String expectedJson = FileCopyUtils
				.copyToString(new InputStreamReader(expected.getInputStream(), Charset.forName("UTF-8")));
			assertJson(actualJson, expectedJson);
		};
	}

	private void assertJson(String actualJson, String expectedJson) throws AssertionError {
		try {
			JSONAssert.assertEquals(expectedJson, actualJson, true);
		}
		catch (JSONException ex) {
			throw new AssertionError(ex.getMessage(), ex);
		}
	}

	private Resource getResource(String path) {
		return new ClassPathResource(path, getClass());
	}

	private ArtifactoryBuildRuns buildRuns() {
		return buildRuns(false);
	}

	private ArtifactoryBuildRuns buildRuns(String project) {
		return buildRuns(false, project);
	}

	private ArtifactoryBuildRuns buildRuns(boolean admin) {
		return artifactoryServer(admin).buildRuns("my-build");
	}

	private ArtifactoryBuildRuns buildRuns(boolean admin, String project) {
		return artifactoryServer(admin).buildRuns("my-build", project);
	}

	private ArtifactoryServer artifactoryServer(boolean admin) {
		return this.artifactory.server(URI.create("repo.example.com"), "admin", "password");
	}

}
