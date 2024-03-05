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

import java.io.File;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.spring.github.actions.artifactoryaction.artifactory.Artifactory;
import io.spring.github.actions.artifactoryaction.artifactory.Artifactory.BuildRun;
import io.spring.github.actions.artifactoryaction.artifactory.ArtifactoryProperties;
import io.spring.github.actions.artifactoryaction.artifactory.payload.BuildModule;
import io.spring.github.actions.artifactoryaction.artifactory.payload.DeployableArtifact;
import io.spring.github.actions.artifactoryaction.artifactory.payload.DeployableFileArtifact;
import io.spring.github.actions.artifactoryaction.io.DirectoryScanner;
import io.spring.github.actions.artifactoryaction.io.FileSet;
import io.spring.github.actions.artifactoryaction.io.FileSet.Category;
import io.spring.github.actions.artifactoryaction.maven.MavenBuildModulesGenerator;
import io.spring.github.actions.artifactoryaction.maven.MavenCoordinates;
import io.spring.github.actions.artifactoryaction.maven.MavenVersionType;
import io.spring.github.actions.artifactoryaction.system.ConsoleLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;

/**
 * Deployer for deploying to Artifactory.
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 * @author Gabriel Petrovay
 * @author Andy Wilkinson
 */
@Component
public class Deployer {

	private static final Set<String> METADATA_FILES = Collections
		.unmodifiableSet(new HashSet<>(Arrays.asList("maven-metadata.xml", "maven-metadata-local.xml")));

	private static final Set<String> CHECKSUM_FILE_EXTENSIONS = Collections
		.unmodifiableSet(new HashSet<>(Arrays.asList(".md5", ".sha1", ".sha256", ".sha512")));

	private static final Logger logger = LoggerFactory.getLogger(Deployer.class);

	private static final ConsoleLogger console = new ConsoleLogger();

	private final ArtifactoryProperties properties;

	private final Artifactory artifactory;

	private final DirectoryScanner directoryScanner;

	public Deployer(ArtifactoryProperties properties, Artifactory artifactory, DirectoryScanner directoryScanner) {
		this.properties = properties;
		this.artifactory = artifactory;
		this.directoryScanner = directoryScanner;
	}

	public void deploy() {
		Instant started = Instant.now();
		MultiValueMap<Category, DeployableArtifact> batchedArtifacts = getBatchedArtifacts(
				this.properties.deploy().build().number(), started);
		int size = batchedArtifacts.values().stream().mapToInt(List::size).sum();
		Assert.state(size > 0, "No artifacts found to deploy");
		console.log("Deploying {} artifacts to {} as {} build {} using {} thread(s)", size,
				this.properties.server().uri(), this.properties.deploy().build().name(),
				this.properties.deploy().build().number(), this.properties.deploy().threads());
		deployArtifacts(batchedArtifacts);
		addBuildRun(this.properties.deploy().build().number(), started, batchedArtifacts);
		logger.debug("Done");
	}

	private MultiValueMap<Category, DeployableArtifact> getBatchedArtifacts(int buildNumber, Instant started) {
		File root = new File(this.properties.deploy().folder());
		Assert.state(!ObjectUtils.isEmpty(root.listFiles()), "No artifacts found in empty directory");
		logger.debug("Getting deployable artifacts from {}", root);
		FileSet fileSet = this.directoryScanner.scan(root).filter(getChecksumFilter()).filter(getMetadataFilter());
		MultiValueMap<Category, DeployableArtifact> batchedArtifacts = new LinkedMultiValueMap<>();
		Set<String> paths = new HashSet<>();
		fileSet.batchedByCategory().forEach((category, files) -> {
			files.forEach((file) -> {
				String path = DeployableFileArtifact.calculatePath(root, file);
				logger.debug("Including file {} with path {}", file, path);
				Map<String, String> properties = getDeployableArtifactProperties(path, buildNumber, started);
				path = stripSnapshotTimestamp(path);
				if (paths.add(path)) {
					batchedArtifacts.add(category, new DeployableFileArtifact(path, file, properties, null));
				}
			});
		});
		return batchedArtifacts;
	}

	private String stripSnapshotTimestamp(String path) {
		MavenCoordinates coordinates = MavenCoordinates.fromPath(path);
		if (coordinates.getVersionType() != MavenVersionType.TIMESTAMP_SNAPSHOT) {
			return path;
		}
		String stripped = path.replace(coordinates.getSnapshotVersion(), coordinates.getVersion());
		logger.debug("Stripped timestamp version {} to {}", path, stripped);
		return stripped;
	}

	private Map<String, String> getDeployableArtifactProperties(String path, int buildNumber, Instant started) {
		Map<String, String> properties = new LinkedHashMap<>();
		addBuildProperties(buildNumber, started, properties);
		return properties;
	}

	// TODO Support for artifact sets
	// private PathFilter getFilter(ArtifactSet artifactSet) {
	// logger.debug("Creating artifact set filter including {} and excluding {}",
	// artifactSet.getInclude(),
	// artifactSet.getExclude());
	// return new PathFilter(artifactSet.getInclude(), artifactSet.getExclude());
	// }

	private void addBuildProperties(int buildNumber, Instant started, Map<String, String> properties) {
		properties.put("build.name", this.properties.deploy().build().name());
		properties.put("build.number", Integer.toString(buildNumber));
		properties.put("build.timestamp", Long.toString(started.toEpochMilli()));
	}

	private void deployArtifacts(MultiValueMap<Category, DeployableArtifact> batchedArtifacts) {
		logger.debug("Deploying artifacts to {}", this.properties.deploy().repository());
		ExecutorService executor = Executors.newFixedThreadPool(this.properties.deploy().threads());
		Function<DeployableArtifact, CompletableFuture<?>> deployer = (deployableArtifact) -> getArtifactDeployer(
				deployableArtifact);
		try {
			batchedArtifacts.forEach((category, artifacts) -> deploy(category, artifacts, deployer));
		}
		finally {
			executor.shutdown();
		}
	}

	private void deploy(Category category, List<DeployableArtifact> artifacts,
			Function<DeployableArtifact, CompletableFuture<?>> deployer) {
		logger.debug("Deploying {} artifacts", category);
		deploy(artifacts.stream().map(deployer).toArray(CompletableFuture[]::new));
	}

	private void deploy(CompletableFuture<?>[] batch) {
		try {
			CompletableFuture.allOf(batch).get();
		}
		catch (ExecutionException ex) {
			throw new RuntimeException(ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private CompletableFuture<?> getArtifactDeployer(DeployableArtifact deployableArtifact) {
		return CompletableFuture.runAsync(() -> deployArtifact(deployableArtifact));
	}

	private void deployArtifact(DeployableArtifact deployableArtifact) {
		console.log("Deploying {} {} ({}/{})", deployableArtifact.getPath(), deployableArtifact.getProperties(),
				deployableArtifact.getChecksums().getSha1(), deployableArtifact.getChecksums().getMd5());
		this.artifactory.deploy(this.properties.deploy().repository(), deployableArtifact);
	}

	private Predicate<File> getMetadataFilter() {
		return (file) -> !METADATA_FILES.contains(file.getName().toLowerCase());
	}

	private Predicate<File> getChecksumFilter() {
		return (file) -> {
			String name = file.getName().toLowerCase();
			for (String extension : CHECKSUM_FILE_EXTENSIONS) {
				if (name.endsWith(extension)) {
					return false;
				}
			}
			return true;
		};
	}

	private void addBuildRun(int buildNumber, Instant started,
			MultiValueMap<Category, DeployableArtifact> batchedArtifacts) {
		List<DeployableArtifact> artifacts = batchedArtifacts.values()
			.stream()
			.flatMap(List::stream)
			.collect(Collectors.toList());
		logger.debug("Adding build run {}", buildNumber);
		List<BuildModule> modules = new MavenBuildModulesGenerator().getBuildModules(artifacts);
		this.artifactory.addBuildRun(this.properties.deploy().project(), this.properties.deploy().build().name(),
				new BuildRun(buildNumber, started, this.properties.deploy().build().uri(), modules));
	}

}
