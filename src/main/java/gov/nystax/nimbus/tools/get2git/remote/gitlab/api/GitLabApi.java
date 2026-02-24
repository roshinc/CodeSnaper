/**
 * 
 */
package gov.nystax.nimbus.tools.get2git.remote.gitlab.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nystax.nimbus.tools.get2git.domain.GitConfig;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.exceptions.G2GClientException;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.exceptions.G2GRestException;

/**
 * @author t63606
 *
 */
public class GitLabApi {

	private GitConfig config;

	private GitlabGroupApi groupApi;
	private GitlabProjectsApi projectsApi;
	private GitlabRepoFilesApi repoFilesApi;
	private GitlabBranchApi branchApi;
	private GitlabCommitApi commitApi;
	private GitlabProtectedBranchApi protectedBranchApi;

	private static final Logger logger = LoggerFactory.getLogger(GitLabApi.class);

	public GitLabApi(GitConfig config) {
		super();
		this.config = config;
		this.groupApi = new GitlabGroupApi(config);
		this.projectsApi = new GitlabProjectsApi(config, groupApi);
		try {
			this.init();
		} catch (G2GClientException | G2GRestException e) {
			logger.debug("Init in constructor failed project probably doesn't exist yet.", e);
		}

	}

	private void init() throws G2GClientException, G2GRestException {
		long projectID = this.projectsApi.getProject().getId();
		this.repoFilesApi = new GitlabRepoFilesApi(config, this.projectsApi, projectID);
		this.branchApi = new GitlabBranchApi(config, this.projectsApi, projectID);
		this.commitApi = new GitlabCommitApi(config, projectID);
		this.protectedBranchApi = new GitlabProtectedBranchApi(config, this.projectsApi, projectID);
	}

	public GitConfig getConfig() {
		return config;
	}

	public GitlabGroupApi getGroupApi() throws G2GClientException, G2GRestException {
		return groupApi;
	}

	public GitlabProjectsApi getProjectsApi() {
		return projectsApi;
	}

	public GitlabRepoFilesApi getRepoFilesApi() throws G2GClientException, G2GRestException {
		if (repoFilesApi == null) {
			init();
		}
		return repoFilesApi;
	}

	public GitlabBranchApi getBranchApi() throws G2GClientException, G2GRestException {
		if (branchApi == null) {
			init();
		}
		return branchApi;
	}

	public GitlabCommitApi getCommitApi() {
		if (branchApi == null) {
			init();
		}
		return commitApi;
	}

	public GitlabProtectedBranchApi getProtectedBranchApi() throws G2GClientException, G2GRestException {
		if (protectedBranchApi == null) {
			init();
		}
		return protectedBranchApi;
	}

}
