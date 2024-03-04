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

import io.spring.github.actions.artifactoryaction.artifactory.payload.BuildModule;
import io.spring.github.actions.artifactoryaction.artifactory.payload.ContinuousIntegrationAgent;

/**
 * Access to artifactory build runs.
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 * @author Andy Wilkinson
 */
public interface ArtifactoryBuildRuns {

	/**
	 * Add a new build run.
	 * @param buildNumber the build number
	 * @param continuousIntegrationAgent the CI Agent
	 * @param started the time the build was started
	 * @param buildUri the build URI
	 * @param properties any build properties
	 * @param modules the modules for the build run
	 */
	void add(int buildNumber, ContinuousIntegrationAgent continuousIntegrationAgent, Instant started, URI buildUri,
			Map<String, String> properties, List<BuildModule> modules);

}
