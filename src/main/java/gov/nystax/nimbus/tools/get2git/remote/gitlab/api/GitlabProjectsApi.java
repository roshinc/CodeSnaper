/**
 * 
 */
package gov.nystax.nimbus.tools.get2git.remote.gitlab.api;

import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.Maps;

import gov.nystax.nimbus.tools.get2git.domain.GitConfig;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.Project;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.exceptions.G2GClientException;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.exceptions.G2GRestException;

/**
 * @author t63606
 *
 */
public class GitlabProjectsApi extends AbstractGitlabApi<Project> {
	private static String API_URI = "projects/";
	private Long projectId = -1L;

	private String projectPathWithNamespace;

	private GitlabGroupApi groupApi;

	protected GitlabProjectsApi(GitConfig config, GitlabGroupApi groupApi) {
		super(config);

		// Set base URL
		this.projectPathWithNamespace = config.getRemoteUrl().getPathWithNamespace();

		// Check we have everything needed
		Preconditions.checkNotNull(projectPathWithNamespace);

		this.groupApi = groupApi;

	}

	public Project createProject() {
		long groupId = groupApi.getGroup().getId();
		// the group should already exist.
		// get the name of the group we want to create

		Verify.verify(groupId > 0, "Parent group does not exist, cannot create group");

		// Parent exists create the group
		Map<String, String> body = Maps.newHashMap();
		body.put("name", super.getConfig().getRemoteUrl().getRepoName());
		body.put("initialize_with_readme", String.valueOf(false));
		body.put("namespace_id", String.valueOf(groupId));

		Project parsed = super.doPostToGitLabInternal(body, API_URI, "", super.gitlabParameters, super.gitlabHeaders)
				.getResponseAs(Project.class);

		this.projectId = parsed.getId();

		return parsed;

	}

	public Project getProject() throws G2GRestException, G2GClientException {
		Project parsed = doGetToGitLabInternal(API_URI, this.projectPathWithNamespace, super.gitlabParameters,
				super.gitlabHeaders).getResponseAs(Project.class);
		this.projectId = parsed.getId();
		return parsed;
	}

	public Project changeDefaultBranch(String newDefaultBranchName) throws G2GRestException, G2GClientException {

		Map<String, String> body = Maps.newHashMap();
		body.put("default_branch", newDefaultBranchName);

		Project parsed = super.doPutToGitLabInternal(body, API_URI, String.valueOf(this.getProjectID()),
				super.gitlabParameters, super.gitlabHeaders).getResponseAs(Project.class);

		return parsed;
	}

	public Project changeDescription(String description) throws G2GRestException, G2GClientException {

		Map<String, String> body = Maps.newHashMap();
		body.put("description", description);

		Project parsed = super.doPutToGitLabInternal(body, API_URI, String.valueOf(this.getProjectID()),
				super.gitlabParameters, super.gitlabHeaders).getResponseAs(Project.class);

		return parsed;
	}

	protected long getProjectID() throws G2GRestException, G2GClientException {
		if (this.projectId < 0) {
			this.getProject();
		}
		return this.projectId;
	}
}
