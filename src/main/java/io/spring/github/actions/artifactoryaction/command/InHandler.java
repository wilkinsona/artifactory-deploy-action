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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.spring.github.actions.artifactoryaction.artifactory.Artifactory;
import io.spring.github.actions.artifactoryaction.artifactory.ArtifactoryBuildRuns;
import io.spring.github.actions.artifactoryaction.artifactory.ArtifactoryServer;
import io.spring.github.actions.artifactoryaction.artifactory.BuildNumber;
import io.spring.github.actions.artifactoryaction.artifactory.payload.DeployedArtifact;
import io.spring.github.actions.artifactoryaction.command.payload.InRequest;
import io.spring.github.actions.artifactoryaction.command.payload.InRequest.Params;
import io.spring.github.actions.artifactoryaction.command.payload.InResponse;
import io.spring.github.actions.artifactoryaction.command.payload.Source;
import io.spring.github.actions.artifactoryaction.command.payload.Version;
import io.spring.github.actions.artifactoryaction.io.Directory;
import io.spring.github.actions.artifactoryaction.maven.MavenMetadataGenerator;
import io.spring.github.actions.artifactoryaction.system.ConsoleLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Delegate used to handle operations triggered from the {@link InCommand}.
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 * @author Gabriel Petrovay
 */
@Component
public class InHandler {

	private static final Logger logger = LoggerFactory.getLogger(InHandler.class);

	private static final ConsoleLogger console = new ConsoleLogger();

	private final Artifactory artifactory;

	private final MavenMetadataGenerator mavenMetadataGenerator;

	public InHandler(Artifactory artifactory, MavenMetadataGenerator mavenMetadataGenerator) {
		this.artifactory = artifactory;
		this.mavenMetadataGenerator = mavenMetadataGenerator;
	}

	public InResponse handle(InRequest request, Directory directory) {
		Source source = request.getSource();
		Version version = request.getVersion();
		logger.debug("Handling in for source '{}' version '{}'", source, version);
		BuildNumber buildNumber = BuildNumber.of(version.getBuildNumber());
		Params params = request.getParams();
		DebugLogging.setEnabled(params.isDebug());
		ArtifactoryServer artifactoryServer = getArtifactoryServer(request.getSource());
		ArtifactoryBuildRuns buildRuns = artifactoryServer.buildRuns(source.getBuildName(), source.getProject());
		if (params.isDownloadArtifacts()) {
			List<DeployedArtifact> artifacts = buildRuns.getDeployedArtifacts(buildNumber);
			console.log("Downloading build {} artifacts from {} using {} thread(s)", buildNumber, source.getUri(),
					params.getThreads());
			download(artifactoryServer, groupByRepo(artifacts), directory.getFile(), params.isDownloadChecksums(),
					params.getThreads());
			if (params.isGenerateMavenMetadata()) {
				logger.debug("Generating maven metadata");
				this.mavenMetadataGenerator.generate(directory, params.isDownloadChecksums());
			}
		}
		if (params.isSaveBuildInfo()) {
			String buildInfo = buildRuns.getRawBuildInfo(buildNumber);
			saveBuildInfo(buildInfo, new File(directory.getFile(), "build-info.json"));
		}
		logger.debug("Done");
		return new InResponse(version);
	}

	private void saveBuildInfo(String buildInfo, File buildInfoFile) {
		try {
			FileCopyUtils.copy(buildInfo, new FileWriter(buildInfoFile));
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private ArtifactoryServer getArtifactoryServer(Source source) {
		logger.debug("Using artifactory server " + source.getUri());
		if (source.getProxy() != null) {
			logger.debug("Artifactory server configured to use proxy: {}", source.getProxy());
		}
		return this.artifactory.server(source.getUri(), source.getUsername(), source.getPassword(), source.getProxy());
	}

	private MultiValueMap<String, DeployedArtifact> groupByRepo(List<DeployedArtifact> artifacts) {
		MultiValueMap<String, DeployedArtifact> artifactsByRepo = new LinkedMultiValueMap<>();
		artifacts.stream().forEach((a) -> artifactsByRepo.add(a.getRepo(), a));
		return artifactsByRepo;
	}

	private void download(ArtifactoryServer artifactoryServer, MultiValueMap<String, DeployedArtifact> artifactsByRepo,
			File destination, boolean downloadChecksums, int threads) {
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		try {
			CompletableFuture
				.allOf(artifactsByRepo.values()
					.stream()
					.flatMap((artifacts) -> artifacts.stream())
					.map((artifact) -> CompletableFuture.runAsync(() -> download(artifactoryServer, destination,
							downloadChecksums, artifact.getRepo(), artifact), executor))
					.toArray(CompletableFuture[]::new))
				.get();
		}
		catch (ExecutionException ex) {
			throw new RuntimeException(ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		finally {
			executor.shutdown();
		}
	}

	private void download(ArtifactoryServer artifactoryServer, File destination, boolean downloadChecksums, String repo,
			DeployedArtifact artifact) {
		console.log("Downloading {}/{} from {}", artifact.getPath(), artifact.getName(), repo);
		artifactoryServer.repository(repo)
			.download(artifact, destination,
					downloadChecksums && !DeployableArtifactsSigner.isSignatureFile(artifact.getName()));
	}

}
