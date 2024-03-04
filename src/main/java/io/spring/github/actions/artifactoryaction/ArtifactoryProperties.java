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

package io.spring.github.actions.artifactoryaction;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for deploying to Artifactory.
 *
 * @param server server properties
 * @param deploy deploy properties
 * @author Andy Wilkinson
 */
@ConfigurationProperties(prefix = "artifactory")
public record ArtifactoryProperties(@DefaultValue ArtifactoryProperties.Server server,
		@DefaultValue ArtifactoryProperties.Deploy deploy) {

	public record Server(URI uri, String username, String password) {

	}

	public record Deploy(String folder, String repository, @DefaultValue("1") int threads,
			@DefaultValue Deploy.Build build) {

		public record Build(String name, int number, URI uri) {

		}

	}

}
