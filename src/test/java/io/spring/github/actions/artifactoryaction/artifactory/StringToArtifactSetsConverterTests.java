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

import java.util.List;

import io.spring.github.actions.artifactoryaction.artifactory.ArtifactoryProperties.Deploy.ArtifactSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StringToArtifactSetsConverter}.
 *
 * @author Andy Wilkinson
 */
class StringToArtifactSetsConverterTests {

	private final StringToArtifactSetsConverter converter = new StringToArtifactSetsConverter();

	@Test
	void convertWithSingleLineProducesOneArtifactSet() {
		List<ArtifactSet> artifactSets = this.converter.convert("one,two:three:a=alpha,b=bravo");
		assertThat(artifactSets).hasSize(1).first().satisfies((artifactSet) -> {
			assertThat(artifactSet.include()).containsExactly("one", "two");
			assertThat(artifactSet.exclude()).containsExactly("three");
			assertThat(artifactSet.properties()).hasSize(2).containsEntry("a", "alpha").containsEntry("b", "bravo");
		});
	}

	@Test
	void convertWithEmptyIncludeHasEmptyListInArtifactSet() {
		List<ArtifactSet> artifactSets = this.converter.convert(":one,two:a=alpha");
		assertThat(artifactSets).hasSize(1).first().satisfies((artifactSet) -> {
			assertThat(artifactSet.include()).isEmpty();
			assertThat(artifactSet.exclude()).containsExactly("one", "two");
			assertThat(artifactSet.properties()).hasSize(1).containsEntry("a", "alpha");
		});
	}

	@Test
	void convertWithEmptyExcludeHasEmptyListInArtifactSet() {
		List<ArtifactSet> artifactSets = this.converter.convert("one,two::a=alpha");
		assertThat(artifactSets).hasSize(1).first().satisfies((artifactSet) -> {
			assertThat(artifactSet.include()).containsExactly("one", "two");
			assertThat(artifactSet.exclude()).isEmpty();
			assertThat(artifactSet.properties()).hasSize(1).containsEntry("a", "alpha");
		});
	}

	@Test
	void convertWithPropertyWithEqualsInValueHasProperty() {
		List<ArtifactSet> artifactSets = this.converter.convert("one:two:a=alpha=bravo");
		assertThat(artifactSets).hasSize(1).first().satisfies((artifactSet) -> {
			assertThat(artifactSet.include()).containsExactly("one");
			assertThat(artifactSet.exclude()).containsExactly("two");
			assertThat(artifactSet.properties()).hasSize(1).containsEntry("a", "alpha=bravo");
		});
	}

	@Test
	void convertWithMultipleLinesProducesMultipleArtifactSets() {
		List<ArtifactSet> artifactSets = this.converter
			.convert("one,two:three:a=alpha,b=bravo%nfour:five:c=charlie%nsix:seven:d=delta".formatted());
		assertThat(artifactSets).hasSize(3).first().satisfies((artifactSet) -> {
			assertThat(artifactSet.include()).containsExactly("one", "two");
			assertThat(artifactSet.exclude()).containsExactly("three");
			assertThat(artifactSet.properties()).hasSize(2).containsEntry("a", "alpha").containsEntry("b", "bravo");
		});
		assertThat(artifactSets).element(1).satisfies((artifactSet) -> {
			assertThat(artifactSet.include()).containsExactly("four");
			assertThat(artifactSet.exclude()).containsExactly("five");
			assertThat(artifactSet.properties()).hasSize(1).containsEntry("c", "charlie");
		});
		assertThat(artifactSets).element(2).satisfies((artifactSet) -> {
			assertThat(artifactSet.include()).containsExactly("six");
			assertThat(artifactSet.exclude()).containsExactly("seven");
			assertThat(artifactSet.properties()).hasSize(1).containsEntry("d", "delta");
		});
	}

}
