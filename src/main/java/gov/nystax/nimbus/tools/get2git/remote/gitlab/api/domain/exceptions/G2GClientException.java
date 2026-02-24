/**
 * 
 */
package gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.exceptions;

import java.io.IOException;
import java.net.URISyntaxException;

import org.slf4j.Logger;

import gov.nystax.nimbus.tools.get2git.domain.exceptions.G2GException;

/**
 * @author t63606
 *
 */
public class G2GClientException extends G2GException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public G2GClientException(String message, URISyntaxException e) {
		super(message, e);
	}

	public G2GClientException(String message, URISyntaxException e, Logger logger) {
		super(message, e);
		logger.error(message, e);
	}

	public G2GClientException(String message, InterruptedException e) {
		super(message, e);
	}

	public G2GClientException(String message, InterruptedException e, Logger logger) {
		super(message, e);
		logger.error(message, e);
	}

	public G2GClientException(String message, IOException e) {
		super(message, e);
	}

	public G2GClientException(String message, IOException e, Logger logger) {
		super(message, e);
		logger.error(message, e);
	}

}
