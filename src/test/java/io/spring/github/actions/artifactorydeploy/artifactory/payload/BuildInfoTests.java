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

package io.spring.github.actions.artifactorydeploy.artifactory.payload;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link BuildInfo}.
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 */
@JsonTest
@ActiveProfiles("test")
class BuildInfoTests {

	private static final String BUILD_NAME = "my-build";

	private static final String BUILD_NUMBER = "5678";

	private static final CiAgent CI_AGENT = new CiAgent("GitHub Actions", "3.0.0");

	private static final BuildAgent BUILD_AGENT = new BuildAgent("Artifactory Action", "0.1.2");

	private static final Instant STARTED = ZonedDateTime
		.parse("2014-09-30T12:00:19.893123Z", DateTimeFormatter.ISO_DATE_TIME)
		.toInstant();

	private static final String BUILD_URI = "https://ci.example.com";

	private static final BuildArtifact ARTIFACT = new BuildArtifact("jar", "a9993e364706816aba3e25717850c26c9cd0d89d",
			"900150983cd24fb0d6963f7d28e17f72", "foo.jar");

	private static final List<BuildModule> MODULES = Collections.singletonList(
			new BuildModule("com.example.module:my-module:1.0.0-SNAPSHOT", Collections.singletonList(ARTIFACT)));

	@Autowired
	private JacksonTester<BuildInfo> json;

	@Test
	void createWhenBuildNameIsEmptyThrowsException() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new BuildInfo("", BUILD_NUMBER, CI_AGENT, BUILD_AGENT, STARTED, BUILD_URI, MODULES))
			.withMessage("Name must not be empty");
	}

	@Test
	void createWhenBuildNumberIsEmptyThrowsException() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new BuildInfo(BUILD_NAME, "", CI_AGENT, BUILD_AGENT, STARTED, BUILD_URI, MODULES))
			.withMessage("Number must not be empty");
	}

	@Test
	void createWhenModulesIsNullUsesEmptyList() {
		BuildInfo buildInfo = new BuildInfo(BUILD_NAME, BUILD_NUMBER, CI_AGENT, BUILD_AGENT, STARTED, BUILD_URI, null);
		assertThat(buildInfo.modules()).isNotNull().isEmpty();
	}

	@Test
	void writeSerializesJson() throws Exception {
		BuildInfo buildInfo = new BuildInfo(BUILD_NAME, BUILD_NUMBER, CI_AGENT, BUILD_AGENT, STARTED, BUILD_URI,
				MODULES);
		assertThat(this.json.write(buildInfo)).isEqualToJson("build-info.json");
	}

}
