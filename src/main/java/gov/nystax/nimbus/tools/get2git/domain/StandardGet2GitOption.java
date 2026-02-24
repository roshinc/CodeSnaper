/**
 * 
 */
package gov.nystax.nimbus.tools.get2git.domain;

/**
 * @author t63606
 *
 */
public enum StandardGet2GitOption implements Comparable<StandardGet2GitOption>, Get2GitOption {
	/**
	 * Creates the parent subgroup if it does not already exist on the remote. If
	 * not specified an exception maybe thrown if the parent subgroup doesn't
	 * already exist.
	 */
	CREATE_REMOTE_PARENT_IF_NEEDED,

	/**
	 * By default the project is created by the pushing of file, this option
	 * explicitly creates the project using the api.
	 */
	CREATE_PROJECT_USING_API;
}
