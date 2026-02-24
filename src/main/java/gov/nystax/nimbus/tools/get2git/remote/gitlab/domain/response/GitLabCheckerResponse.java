/**
 * 
 */
package gov.nystax.nimbus.tools.get2git.remote.gitlab.domain.response;

import java.util.Optional;

import gov.nystax.nimbus.tools.get2git.remote.domain.ICheckerResult;

/**
 * @author t63606
 *
 */
public class GitLabCheckerResponse implements ICheckerResult {

	private Boolean repoExists = null;
	private Boolean fileExists = null;
	private Optional<Long> repoId = Optional.empty();

	@Override
	public Boolean isRepoExists() {
		return repoExists;
	}

	public void setRepoExists(boolean repoExists) {
		this.repoExists = repoExists;
	}

	@Override
	public Boolean isFileExists() {
		return fileExists;
	}

	public void setFileExists(boolean pathExists) {
		this.fileExists = pathExists;
	}

	@Override
	public Optional<Long> getRepoId() {
		return this.repoId;
	}

	public void setRepoId(Optional<Long> repoId) {
		this.repoId = repoId;
	}

}
