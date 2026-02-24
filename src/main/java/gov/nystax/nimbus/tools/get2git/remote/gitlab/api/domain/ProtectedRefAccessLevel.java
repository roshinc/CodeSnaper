/**
 * 
 */
package gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain;

/**
 * @author t63606
 *
 */
public enum ProtectedRefAccessLevel {

	DEVELOPER_ACCESS(30), MAINTAINERS_ACCESS(40);

	private int id;

	ProtectedRefAccessLevel(int id) {
		this.id = id;
	}

	public int getId() {
		return this.id;
	}

}
