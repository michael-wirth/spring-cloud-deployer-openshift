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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class GitReference {

	private String uri;

	private String branch = "master";

	public GitReference(String uri) {
		this.uri = uri;
	}

	public GitReference(String uri, String branch) {
		this.uri = uri;
		this.branch = branch;
	}

	public String getUri() {
		return this.uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	/**
	 * Take the Maven <scm><connection>...</connection></scm> value and replace everything
	 * up until the '@' character with 'ssh://git@' so that OpenShift can clone the
	 * repository.
	 *
	 * Example:
	 *
	 * <code>
	 *   <connection>scm:git:git@github.com:spring-cloud/spring-cloud-dataflow.git</connection>
	 * </code>
	 *
	 * becomes:
	 *
	 * <code>
	 *     ssh://git@github.com:spring-cloud/spring-cloud-dataflow.git
	 * </code>
	 */
	public String getParsedUri() {
		String parsedUri = this.uri;
		if (StringUtils.isNotBlank(this.uri)) {
			parsedUri = this.uri.replaceFirst(".*@", "ssh://git@");
		}

		return parsedUri;
	}

	public String getBranch() {
		return this.branch;
	}

	public void setBranch(String branch) {
		this.branch = branch;
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}

		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		final GitReference that = (GitReference) o;

		return new EqualsBuilder().append(this.uri, that.uri).isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37).append(this.uri).toHashCode();
	}

}
