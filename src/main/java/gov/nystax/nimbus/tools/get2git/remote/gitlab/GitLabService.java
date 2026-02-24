/**
 * 
 */
package gov.nystax.nimbus.tools.get2git.remote.gitlab;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import gov.nystax.nimbus.tools.get2git.domain.GitConfig;
import gov.nystax.nimbus.tools.get2git.remote.domain.IRemoteService;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.GitLabApi;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.GitlabBranchApi;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.GitlabGroupApi;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.GitlabProjectsApi;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.GitlabProtectedBranchApi;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.GitlabRepoFilesApi;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.Project;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.exceptions.G2GClientException;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.exceptions.G2GRestException;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.domain.response.GitLabCheckerResponse;

/**
 * @author t63606
 *
 */
public class GitLabService implements IRemoteService {

	private GitLabApi api;

	private static final Logger logger = LoggerFactory.getLogger(GitLabService.class);

	public GitLabService(GitConfig config) {

		Preconditions.checkNotNull(config, "GitLabService cannot be intialized with no config.");

		logger.debug("GitLabDownloader initializing with {}", config);
		this.api = new GitLabApi(config);

	}

	@Override
	public GitLabCheckerResponse verifyExists(Optional<String> fileSubPath, int depth) {
		GitLabCheckerResponse response = new GitLabCheckerResponse();

		// Check if Project exists
		GitlabProjectsApi projectApiInvoker = api.getProjectsApi();

		try {
			Project project = projectApiInvoker.getProject();
			response.setRepoExists(true);
			response.setRepoId(Optional.ofNullable(Long.valueOf(project.getId())));

			if ((!project.isEmptyRepo()) && fileSubPath.isPresent()) {
				// Check if fileSubPath exists
				GitlabRepoFilesApi filesApiInvoker = api.getRepoFilesApi();
				try {
					if (filesApiInvoker.findFileInAllBranches(fileSubPath.get(), depth) != null) {
						response.setFileExists(true);
					} else {
						response.setFileExists(false);
					}
				} catch (G2GRestException e) {

					if (e.getCode() == 404) {
						response.setFileExists(false);
						return response;
					}

				}
			}

			// If the project is empty and a file check was asked for, set it to null
			if (project.isEmptyRepo() && fileSubPath.isPresent()) {
				response.setFileExists(false);
			}

		} catch (G2GRestException e) {

			if (e.getCode() == 404) {
				response.setRepoExists(false);
				return response;
			}

		} catch (G2GClientException e) {

			throw new RuntimeException(e);

		}

		return response;
	}

	@Override
	public boolean createBranchAndProtectItWithDeveloperAccess(String branchName) {

		// Check if Project exists
		// GitlabProjectsApi projectApiInvoker = api.getProjectsApi();
		GitlabProtectedBranchApi protectedBranchApi = api.getProtectedBranchApi();

		try {
			GitlabBranchApi branchApiInvoker = api.getBranchApi();
			branchApiInvoker.createBranchFromDefaultBranch(branchName);
			// Protect the new branch

			protectedBranchApi.protectBranchWithDeveloperAccess(branchName);
			// projectApiInvoker.changeDefaultBranch(branchName);
			return true;
		} catch (G2GRestException e) {
			logger.error("Failed to create and protect branch {}", branchName, e);
			return false;
		} catch (G2GClientException e) {
			logger.error("Failed to create and protect branch {}", branchName, e);
			return false;
		}

	}

	@Override
	public boolean updateDefaultBranchToAllowDeveloperToMerge() {
		GitlabProtectedBranchApi protectedBranchApi = api.getProtectedBranchApi();

		try {
			// Update default branch permission
			protectedBranchApi.setDefaultBranchMergeAccessWithDeveloperAccess();
			// projectApiInvoker.changeDefaultBranch(branchName);
			return true;
		} catch (G2GRestException e) {
			logger.error("Failed to update default branch merge permissions", e);
			return false;
		} catch (G2GClientException e) {
			logger.error("Failed to update default branch merge permissions", e);
			return false;
		}
	}

	@Override
	public boolean setDescription(String description) {

		// Check if Project exists
		GitlabProjectsApi projectApiInvoker = api.getProjectsApi();

		try {
			projectApiInvoker.changeDescription(description);
			return true;
		} catch (G2GRestException e) {
			logger.error("Failed to set project description", e);
			return false;
		} catch (G2GClientException e) {
			logger.error("Failed to set project description", e);
			return false;
		}

	}

	public void cleanProject() {

	}

	@Override
	public long getProjectID() {
		// Check if Project exists
		GitlabProjectsApi projectApiInvoker = api.getProjectsApi();
		return projectApiInvoker.getProject().getId();
	}

	@Override
	public void createParentIfNeeded() {

		GitlabGroupApi groupApiInvoker = api.getGroupApi();
		try {
			// check if group exists
			groupApiInvoker.getGroup();
		} catch (G2GRestException e) {

			if (e.getCode() == 404) {
				// repo does not exists, try to create it
				groupApiInvoker.createGroup();
			}

		}
	}

	@Override
	public void createProject() {
		// Check if Project exists
		GitlabProjectsApi projectApiInvoker = api.getProjectsApi();
		projectApiInvoker.createProject();

	}

	// TODO: Set repo description

}
