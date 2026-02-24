package gov.nystax.nimbus.tools.get2git.domain.exceptions;

import java.net.URISyntaxException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;

public class G2GLocalException extends G2GException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public G2GLocalException(String message, GitAPIException e, Logger logger) {
		super(message, e);
		logger.error(message, e);
	}

	public G2GLocalException(String message, URISyntaxException e, Logger logger) {
		super(message, e);
		logger.error(message, e);
	}

}
