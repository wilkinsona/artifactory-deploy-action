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

package io.spring.github.actions.artifactoryaction.artifactory.payload;

import io.spring.github.actions.artifactoryaction.util.ArtifactoryDateFormat;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BuildRunsRestResponse}.
 *
 * @author Madhura Bhave
 * @author Phillip Webb
 */
@JsonTest
class BuildRunsRestResponseTests {

	@Autowired
	private JacksonTester<BuildRunsRestResponse> json;

	@Test
	void readDeserializesJson() throws Exception {
		BuildRunsRestResponse response = this.json.readObject("build-runs-rest-response.json");
		assertThat(response.getUri()).isEqualTo("http://localhost:8081/artifactory/api/build/my-build");
		assertThat(response.getBuildsRuns()).hasSize(3);
		assertThat(response.getBuildsRuns().get(0).getBuildNumber()).isEqualTo("my-1234");
		assertThat(response.getBuildsRuns().get(0).getStarted())
			.isEqualTo(ArtifactoryDateFormat.parse("2014-09-28T12:00:19.893Z"));
		assertThat(response.getBuildsRuns().get(1).getBuildNumber()).isEqualTo("my-5678");
		assertThat(response.getBuildsRuns().get(1).getStarted())
			.isEqualTo(ArtifactoryDateFormat.parse("2014-09-30T12:00:19.893Z"));
	}

}
