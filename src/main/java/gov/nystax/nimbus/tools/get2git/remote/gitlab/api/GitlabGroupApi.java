/**
 * 
 */
package gov.nystax.nimbus.tools.get2git.remote.gitlab.api;

import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.Maps;

import gov.nystax.nimbus.tools.get2git.domain.GitConfig;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.Group;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.exceptions.G2GClientException;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.exceptions.G2GRestException;

/**
 * @author t63606
 *
 */
public class GitlabGroupApi extends AbstractGitlabApi<Group> {
	private static String API_URI = "groups/";
	private Long groupId = -1L;

	private String groupPathWithNamespace;

	protected GitlabGroupApi(GitConfig config) {
		super(config);

		// get group path without the trailing /
		this.groupPathWithNamespace = config.getRemoteUrl().getGroupPathWithNamespace().substring(0,
				config.getRemoteUrl().getGroupPathWithNamespace().length() - 1);

		// Check we have everything needed
		Preconditions.checkNotNull(groupPathWithNamespace);

	}

	public Group getGroup() throws G2GRestException, G2GClientException {
		Group parsed = doGetToGitLabInternal(API_URI, this.groupPathWithNamespace, super.gitlabParameters,
				super.gitlabHeaders).getResponseAs(Group.class);
		this.groupId = parsed.getId();
		return parsed;
	}

	public Group createGroup() throws G2GRestException, G2GClientException {
		// the group should not already exist.
		if (this.groupId < 0) {
			// get the name of the group we want to create
			String groupName = this.groupPathWithNamespace.substring(this.groupPathWithNamespace.lastIndexOf("/") + 1);

			// get the parent of the group and check that it exists and get the id
			String parentGroupPath = this.groupPathWithNamespace.substring(0,
					this.groupPathWithNamespace.lastIndexOf("/"));
			Group parentGroup = doGetToGitLabInternal(API_URI, parentGroupPath, super.gitlabParameters,
					super.gitlabHeaders).getResponseAs(Group.class);
			long parentGroupId = parentGroup.getId();

			Verify.verify(parentGroupId > 0, "Parent group does not exist, cannot create group");

			// Parent exists create the group
			Map<String, String> body = Maps.newHashMap();
			body.put("path", groupName);
			body.put("name", groupName);
			body.put("parent_id", String.valueOf(parentGroupId));

			Group parsed = super.doPostToGitLabInternal(body, API_URI, "", super.gitlabParameters, super.gitlabHeaders)
					.getResponseAs(Group.class);

			return parsed;
		}
		return getGroup();
	}
}
