package gov.nystax.nimbus.tools.get2git.domain.exceptions;

import java.nio.file.Path;
import java.util.Optional;

import org.slf4j.Logger;

import gov.nystax.nimbus.tools.get2git.domain.GitRepoURL;

/**
 * Exception to indicate that either a remote or local repo (or both) already
 * exists.
 * <p>
 * 
 * @author t63606
 *
 */
public class G2GRepoAlreadyExistsException extends G2GException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private GitRepoURL remote;
	private Optional<Long> remoteRepoID = Optional.empty();
	private Path local;

	private G2GRepoAlreadyExistsException(String message, GitRepoURL remote, Path local, Logger logger) {
		super(message);
		this.local = local;
		this.remote = remote;
		logger.error("Creating G2GRepoAlreadyExistsException: {}", message);
	}

	public G2GRepoAlreadyExistsException(String message, GitRepoURL remote, Optional<Long> remoteRepoId, Path local,
			Logger logger) {
		this(message, remote, local, logger);
		this.remoteRepoID = remoteRepoId;
	}

	public GitRepoURL getRemote() {
		return remote;
	}

	public Path getLocal() {
		return local;
	}

	public Optional<Long> getRemoteRepoID() {
		return remoteRepoID;
	}

}
