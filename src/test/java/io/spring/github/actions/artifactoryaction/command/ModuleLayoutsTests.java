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

package io.spring.github.actions.artifactoryaction.command;

import io.spring.github.actions.artifactoryaction.artifactory.BuildModulesGenerator;
import io.spring.github.actions.artifactoryaction.maven.MavenBuildModulesGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Tests for {@link ModuleLayouts}.
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 */
class ModuleLayoutsTests {

	private ModuleLayouts moduleLayouts = new ModuleLayouts();

	@Test
	void getBuildModulesGeneratorWhenLayoutIsNullReturnsMaven() {
		assertThat(this.moduleLayouts.getBuildModulesGenerator(null)).isInstanceOf(MavenBuildModulesGenerator.class);
	}

	@Test
	void getBuildModulesGeneratorWhenLayoutIsEmptyReturnsMaven() {
		assertThat(this.moduleLayouts.getBuildModulesGenerator("")).isInstanceOf(MavenBuildModulesGenerator.class);
	}

	@Test
	void getBuildModulesGeneratorWhenLayoutIsMavenReturnsMaven() {
		assertThat(this.moduleLayouts.getBuildModulesGenerator("mAvEN")).isInstanceOf(MavenBuildModulesGenerator.class);
	}

	@Test
	void getBuildModulesGeneratorWhenLayoutIsNoneReturnsNone() {
		assertThat(this.moduleLayouts.getBuildModulesGenerator("none")).isSameAs(BuildModulesGenerator.NONE);
	}

	@Test
	void getBuildModulesGeneratorWhenLayoutIsUnknownThrowsException() {
		assertThatIllegalStateException().isThrownBy(() -> this.moduleLayouts.getBuildModulesGenerator("foo"))
			.withMessage("Unknown module layout 'foo'");
	}

}
