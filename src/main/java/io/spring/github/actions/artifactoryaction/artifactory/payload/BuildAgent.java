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

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * The build agent information included in {@link BuildInfo}.
 *
 * @author Andy Wilkinson
 */
@JsonInclude(Include.NON_NULL)
public class BuildAgent {

	private final String name = "Artifactory Action";

	private final String version = BuildAgent.class.getPackage().getImplementationVersion();

	public String getName() {
		return this.name;
	}

	public String getVersion() {
		return this.version;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		BuildAgent other = (BuildAgent) obj;
		return Objects.equals(this.name, other.name) && Objects.equals(this.version, other.version);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.name, this.version);
	}

	@Override
	public String toString() {
		return this.name + ":" + ((this.version != null) ? this.version : "unknown");
	}

}
