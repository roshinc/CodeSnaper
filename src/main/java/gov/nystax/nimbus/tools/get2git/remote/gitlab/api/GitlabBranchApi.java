package gov.nystax.nimbus.tools.get2git.remote.gitlab.api;

import com.google.common.collect.Maps;

import gov.nystax.nimbus.tools.get2git.domain.GitConfig;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.Branch;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.exceptions.G2GClientException;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.exceptions.G2GRestException;

public class GitlabBranchApi extends AbstractGitlabApi<Branch> {
	private static String API_URI_FORMAT = "projects/%d/repository/branches";

	private long projectID;
	GitlabProjectsApi projectsApi;

	protected GitlabBranchApi(GitConfig config, GitlabProjectsApi projectsApi, long projectID) {
		super(config);
		this.projectID = projectID;
		this.projectsApi = projectsApi;
	}

	public Branch createBranchFromDefaultBranch(String name) throws G2GRestException, G2GClientException {
		super.gitlabParameters.put("branch", name);
		super.gitlabParameters.put("ref", this.projectsApi.getProject().getDefaultBranch());

		Branch parsed = doPostToGitLabInternal(Maps.newHashMap(), String.format(API_URI_FORMAT, this.projectID),
				"", super.gitlabParameters, super.gitlabHeaders).getResponseAs(Branch.class);
		return parsed;

	}

}
