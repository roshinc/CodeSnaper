package gov.nystax.nimbus.tools.get2git.remote.gitlab.api;

import java.util.List;

import com.google.gson.Gson;

import gov.nystax.nimbus.tools.get2git.domain.GitConfig;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.Commit;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.CommitAction;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.CommitResponse;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.exceptions.G2GClientException;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.exceptions.G2GRestException;

public class GitlabCommitApi extends AbstractGitlabApi<CommitResponse> {
	private static String API_URI_FORMAT = "projects/%d/repository/commits";

	private long projectID;
	GitlabProjectsApi projectsApi;

	protected GitlabCommitApi(GitConfig config, long projectID) {
		super(config);
		this.projectID = projectID;
	}

	public CommitResponse createCommit(List<CommitAction> commitActions, String branchName, String commitMessage)
			throws G2GRestException, G2GClientException {

		Commit commit = new Commit(branchName, commitMessage, commitActions);
		Gson gsonObj = new Gson();

		CommitResponse parsed = doPostToGitLabInternal(gsonObj.toJson(commit),
				String.format(API_URI_FORMAT, this.projectID), "", super.gitlabParameters, super.gitlabHeaders)
				.getResponseAs(CommitResponse.class);
		return parsed;

	}

}
