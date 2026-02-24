/**
 * 
 */
package gov.nystax.nimbus.tools.get2git.remote.domain;

import java.util.Optional;

/**
 * @author t63606
 *
 */
public interface IRemoteService {

	/**
	 * Checks if the repo exists and additional if a {@code fileSubPath} is given,
	 * that file would be searched for recursively ({@code depth} times)
	 * <p>
	 * 
	 * @param downloadRequest
	 */
	public ICheckerResult verifyExists(Optional<String> fileSubPath, int depth);

	/**
	 * Creates a branch with the given name and protects it giving developer's push
	 * and merge access
	 * 
	 * @param branchName
	 * @return
	 */
	public boolean createBranchAndProtectItWithDeveloperAccess(String branchName);

	/**
	 * Updates the protected default branch's permission giving developer's merge
	 * access
	 * 
	 * @return
	 */
	public boolean updateDefaultBranchToAllowDeveloperToMerge();

	/**
	 * Gets the id of the current project
	 * 
	 * @return
	 */
	public long getProjectID();

	/**
	 * Create the parent group if needed
	 */
	public void createParentIfNeeded();

	/**
	 * Set the short description in the remote repo
	 * 
	 * @param description
	 * @return
	 */
	boolean setDescription(String description);

	/**
	 * Creates a project on the remote
	 */
	public void createProject();

}
