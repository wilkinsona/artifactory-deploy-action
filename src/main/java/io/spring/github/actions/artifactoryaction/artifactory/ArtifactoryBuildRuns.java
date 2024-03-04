/*
 * Copyright 2017-2023 the original author or authors.
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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.spring.github.actions.artifactoryaction.artifactory.payload.BuildModule;
import io.spring.github.actions.artifactoryaction.artifactory.payload.BuildRun;
import io.spring.github.actions.artifactoryaction.artifactory.payload.ContinuousIntegrationAgent;
import io.spring.github.actions.artifactoryaction.artifactory.payload.DeployedArtifact;

import org.springframework.util.Assert;

/**
 * Access to artifactory build runs.
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 */
public interface ArtifactoryBuildRuns {

	/**
	 * Add a new build run.
	 * @param buildNumber the build number
	 * @param continuousIntegrationAgent the CI Agent
	 * @param started the time the build was started
	 * @param buildUri the build URL
	 * @param properties any build properties
	 * @param modules the modules for the build run
	 */
	void add(BuildNumber buildNumber, ContinuousIntegrationAgent continuousIntegrationAgent, Instant started,
			String buildUri, Map<String, String> properties, List<BuildModule> modules);

	/**
	 * Return all previous build runs.
	 * @param buildNumberPrefix an optional build number prefix
	 * @return the build runs
	 */
	List<BuildRun> getAll(String buildNumberPrefix);

	/**
	 * Return all builds started on or after the given time.
	 * @param buildNumberPrefix an optional build number prefix
	 * @param timestamp the started on or after timestamp
	 * @return the build runs started after the time
	 */
	List<BuildRun> getStartedOnOrAfter(String buildNumberPrefix, Instant timestamp);

	/**
	 * Return a string containing the build-info JSON as stored on the server.
	 * @param buildNumber the build number
	 * @return a string containing the build-info JSON
	 */
	String getRawBuildInfo(BuildNumber buildNumber);

	/**
	 * Return all artifacts that were deployed for the specified build run.
	 * @param buildRun the build run
	 * @return the deployed artifacts
	 */
	default List<DeployedArtifact> getDeployedArtifacts(BuildRun buildRun) {
		Assert.notNull(buildRun, "BuildRun must not be null");
		return getDeployedArtifacts(BuildNumber.of(buildRun.getBuildNumber()));
	}

	/**
	 * Return all artifacts that were deployed for the specified build number.
	 * @param buildNumber the build number
	 * @return the deployed artifacts
	 */
	List<DeployedArtifact> getDeployedArtifacts(BuildNumber buildNumber);

}
