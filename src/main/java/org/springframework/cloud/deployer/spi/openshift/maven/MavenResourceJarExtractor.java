/*
 * Copyright 2018-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.deployer.spi.openshift.maven;

import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.zip.ZipUtil;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

public class MavenResourceJarExtractor {

	private static Logger log = LoggerFactory.getLogger(MavenResourceJarExtractor.class);

	/**
	 * Extract a single file in the specified {@link Resource} that must be a zip archive.
	 * This single file will be represented as a {@link Resource}. The reference to the
	 * file in the archive must be <b>the absolute path</b> to the file. I.e.
	 * <code>/the/path/to/the/file.txt</code>, where <code>file.txt</code> is the file to
	 * extract.
	 * @param resource zip file
	 * @param file to extract
	 * @return a {@link Resource} representing the extracted file
	 * @throws IOException if file is not readable
	 */
	public Optional<Resource> extractFile(Resource resource, String file)
			throws IOException {
		log.debug("Extracting [{}] from: [{}]", file, resource.getFile());

		Optional<Resource> extractedResource = Optional.empty();

		byte[] unpackedEntry = ZipUtil.unpackEntry(resource.getFile(), file);
		if (unpackedEntry != null) {
			extractedResource = Optional.of(new ByteArrayResource(unpackedEntry));
		}

		return extractedResource;
	}

}
