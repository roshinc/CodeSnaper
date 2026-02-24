/**
 * 
 */
package gov.nystax.nimbus.tools.get2git.remote.domain;

import java.util.Optional;

/**
 * @author t63606
 *
 */
public interface ICheckerResult {

	/**
	 * Will only have a non empty value if repo exists
	 * 
	 * @return
	 */
	public Optional<Long> getRepoId();

	public Boolean isRepoExists();

	public Boolean isFileExists();

}
