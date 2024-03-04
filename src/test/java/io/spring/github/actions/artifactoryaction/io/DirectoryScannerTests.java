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

package io.spring.github.actions.artifactoryaction.io;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DirectoryScanner}.
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 * @author Andy Wilkinson
 */
class DirectoryScannerTests {

	private static final byte[] NO_BYTES = {};

	@TempDir
	File tempDir;

	private DirectoryScanner scanner = new DirectoryScanner();

	@Test
	void scanFindsAllFiles() throws Exception {
		File root = createFiles();
		FileSet files = this.scanner.scan(root);
		assertThat(files).extracting((f) -> relativePath(root, f))
			.containsExactly("/bar/bar.jar", "/bar/bar.pom", "/baz/baz.jar", "/baz/baz.pom");
	}

	private String relativePath(File rootFile, File file) {
		String root = StringUtils.cleanPath(rootFile.getPath());
		String path = StringUtils.cleanPath(file.getPath());
		return path.substring(root.length());
	}

	private File createFiles() throws IOException {
		File root = this.tempDir;
		File barDir = new File(root, "bar");
		touch(new File(barDir, "bar.jar"));
		touch(new File(barDir, "bar.pom"));
		File bazDir = new File(root, "baz");
		touch(new File(bazDir, "baz.jar"));
		touch(new File(bazDir, "baz.pom"));
		return root;
	}

	private void touch(File file) throws IOException {
		file.getParentFile().mkdirs();
		FileCopyUtils.copy(NO_BYTES, file);
	}

}
