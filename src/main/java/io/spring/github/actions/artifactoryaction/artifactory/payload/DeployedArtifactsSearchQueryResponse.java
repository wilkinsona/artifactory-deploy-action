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

package io.spring.github.actions.artifactoryaction.artifactory.payload;

import java.util.List;

/**
 * A single response from an Artifactory Query Language request for deployed artifacts.
 *
 * @author Madhura Bhave
 * @author Phillip Webb
 */
public class DeployedArtifactsSearchQueryResponse extends SearchQueryResponse<DeployedArtifact> {

	public DeployedArtifactsSearchQueryResponse(List<DeployedArtifact> results, Range range) {
		super(results, range);
	}

}
