package gov.nystax.nimbus.tools.get2git.remote.gitlab.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import gov.nystax.nimbus.tools.get2git.domain.GitConfig;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.Branch;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.ProtectedRefAccessLevel;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.exceptions.G2GClientException;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.exceptions.G2GRestException;

public class GitlabProtectedBranchApi extends AbstractGitlabApi<Branch> {
	private static String API_URI_FORMAT = "projects/%d/protected_branches";

	private long projectID;
	GitlabProjectsApi projectsApi;

	protected GitlabProtectedBranchApi(GitConfig config, GitlabProjectsApi projectsApi, long projectID) {
		super(config);
		this.projectID = projectID;
		this.projectsApi = projectsApi;
	}

	public Branch protectBranchWithDeveloperAccess(String name) throws G2GRestException, G2GClientException {
		super.gitlabParameters.put("name", name);
		super.gitlabParameters.put("push_access_level",
				String.valueOf(ProtectedRefAccessLevel.DEVELOPER_ACCESS.getId()));

		super.gitlabParameters.put("merge_access_level",
				String.valueOf(ProtectedRefAccessLevel.DEVELOPER_ACCESS.getId()));

		super.gitlabParameters.put("unprotect_access_level",
				String.valueOf(ProtectedRefAccessLevel.MAINTAINERS_ACCESS.getId()));

		Branch parsed = doPostToGitLabInternal(Maps.newHashMap(), String.format(API_URI_FORMAT, this.projectID), "",
				super.gitlabParameters, super.gitlabHeaders).getResponseAs(Branch.class);
		return parsed;

	}

	public Branch setProtectedBranchMergeAccessWithDeveloperAccess(String name)
			throws G2GRestException, G2GClientException {

		HashMap<String, List<Map<String, String>>> body = Maps.newHashMap();
		List<Map<String, String>> accessParams = Lists.newArrayList();
		HashMap<String, String> mergeAccess = Maps.newHashMap();
		mergeAccess.put("access_level", String.valueOf(ProtectedRefAccessLevel.DEVELOPER_ACCESS.getId()));
		accessParams.add(mergeAccess);
		body.put("allowed_to_merge", accessParams);

		Branch parsed = doPatchToGitLabInternal(body, String.format(API_URI_FORMAT + "/" + name, this.projectID), "",
				super.gitlabParameters, super.gitlabHeaders).getResponseAs(Branch.class);
		return parsed;

	}

	public Branch setDefaultBranchMergeAccessWithDeveloperAccess() throws G2GRestException, G2GClientException {

		String defaultBranchName = this.projectsApi.getProject().getDefaultBranch();

		return setProtectedBranchMergeAccessWithDeveloperAccess(defaultBranchName);

	}

}
