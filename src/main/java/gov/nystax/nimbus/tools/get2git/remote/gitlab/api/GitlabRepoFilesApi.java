/**
 * 
 */
package gov.nystax.nimbus.tools.get2git.remote.gitlab.api;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import gov.nystax.nimbus.tools.get2git.domain.GitConfig;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.Branch;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.RepoFile;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.TreeItem;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.TreeItem.FILE_TYPE;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.exceptions.G2GClientException;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.exceptions.G2GRestException;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.response.RESTResponse;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.util.CommonUtils;

/**
 * @author t63606
 *
 */
public class GitlabRepoFilesApi extends AbstractGitlabApi<RepoFile> {
	private static String API_URI_FORMAT = "projects/%d/repository/files/";
	private static String API_URI_FORMAT_FOR_TREE = "projects/%d/repository/tree";
	private static String API_URI_FORMAT_FOR_BRANCH = "projects/%d/repository/branches";

	/**
	 * Provides a reference to a GitLab repository.
	 */
	private GitlabProjectsApi projectsApi;
	private long projectID;
	private String apiUrl;

	protected GitlabRepoFilesApi(GitConfig config, GitlabProjectsApi projectsApi, long projectID) {
		super(config);

		// Check we have everything needed
		Preconditions.checkNotNull(config, "Config is required");
		Preconditions.checkNotNull(projectsApi, "Project Api is required");
		Preconditions.checkNotNull(projectID, "Project ID is required");

		// Set projectApi
		this.projectsApi = projectsApi;
		// Set base URL
		this.projectID = projectID;
		// create api url
		this.apiUrl = String.format(API_URI_FORMAT, this.projectID);

	}

	public RepoFile getFile(String filePath) throws G2GClientException, G2GRestException {
		super.gitlabParameters.put("ref", this.projectsApi.getProject().getDefaultBranch());

		RESTResponse<RepoFile> parsed = doGetToGitLabInternal(this.apiUrl, filePath, super.gitlabParameters,
				super.gitlabHeaders);

		super.gitlabParameters.remove("ref");

		return parsed.getResponseAs(RepoFile.class);
	}

	public RepoFile getFile(String filePath, String branch) throws G2GClientException, G2GRestException {
		super.gitlabParameters.put("ref", branch);
		RESTResponse<RepoFile> parsed = doGetToGitLabInternal(this.apiUrl, filePath, super.gitlabParameters,
				super.gitlabHeaders);
		super.gitlabParameters.remove("ref");

		return parsed.getResponseAs(RepoFile.class);
	}

	public RepoFile findFileInDefaultBranch(String fileName, int depth)
			throws G2GClientException, G2GRestException {
		return findFileInternal(fileName, "", depth, this.projectsApi.getProject().getDefaultBranch());
	}

	public RepoFile findFileInAllBranches(String fileName, int depth)
			throws G2GClientException, G2GRestException {

		RepoFile response = null;

		List<Branch> branches = listBranches().toList();
		for (Branch branch : branches) {
			response = findFileInternal(fileName, "", depth, branch.getName());
			if (response != null) {
				return response;
			}
		}

		return response;
	}

	private RepoFile findFileInternal(String fileName, String path, int depth, String branch)
			throws G2GClientException, G2GRestException {
		RepoFile response = null;
		List<TreeItem> foundFiles = listDirectory(path, branch).filter(tf -> tf.getName().equalsIgnoreCase(fileName))
				.collect(Collectors.toList());
		if (foundFiles.isEmpty()) {
			if (depth > 0) {
				List<TreeItem> foundTrees = listDirectory(path, branch).filter(tf -> tf.getType() == FILE_TYPE.TREE)
						.collect(Collectors.toList());

				for (TreeItem aTree : foundTrees) {
					response = findFileInternal(fileName, aTree.getPath(), depth - 1, branch);
					if (response != null) {
						return response;
					}
				}
			}
		} else {
			return getFile(CommonUtils.getFirstElement(foundFiles).getPath(), branch);
		}

		return response;
	}

	/**
	 * An ls of a GitLab directory.
	 * <p>
	 * 
	 * @param path
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws URISyntaxException
	 */
	private Stream<TreeItem> listDirectory(String path, String branch) throws G2GClientException, G2GRestException {
		List<TreeItem> items = new ArrayList<TreeItem>();
		super.gitlabParameters.put("ref", branch);

		JsonArray responseJsonArray = new JsonArray();
		gitlabParameters.put("path", path);

		String contentString = super.doGetToGitLabInternal(String.format(API_URI_FORMAT_FOR_TREE, this.projectID), "",
				gitlabParameters, gitlabHeaders).getResponse();
		// We follow next so we may have one or more responses

		// Put the json into a generic array
		JsonArray aJsonArray = new Gson().fromJson(contentString, JsonArray.class);
		responseJsonArray.addAll(aJsonArray);

		Iterator<JsonElement> iter = responseJsonArray.iterator();
		while (iter.hasNext()) {
			JsonObject jsonObj = iter.next().getAsJsonObject();

			TreeItem item = new TreeItem();
			item.setId(jsonObj.get("id").getAsString().trim());
			item.setName(jsonObj.get("name").getAsString().trim());
			item.setPath(jsonObj.get("path").getAsString().trim());
			item.setType(FILE_TYPE.getFileType(jsonObj.get("type").getAsString().trim()));
			items.add(item);

		}

		gitlabParameters.remove("ref");
		gitlabParameters.remove("path");
		return CommonUtils.streamOf(items);
	}

	/**
	 * An list of a repo branches.
	 * <p>
	 * 
	 * @param path
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws URISyntaxException
	 */
	public Stream<Branch> listBranches() throws G2GClientException, G2GRestException {
		List<Branch> items = new ArrayList<Branch>();

		JsonArray responseJsonArray = new JsonArray();

		String contentString = super.doGetToGitLabInternal(String.format(API_URI_FORMAT_FOR_BRANCH, this.projectID), "",
				gitlabParameters, gitlabHeaders).getResponse();
		// We follow next so we may have one or more responses

		// Put the json into a generic array
		JsonArray aJsonArray = new Gson().fromJson(contentString, JsonArray.class);
		responseJsonArray.addAll(aJsonArray);

		Iterator<JsonElement> iter = responseJsonArray.iterator();
		while (iter.hasNext()) {
			JsonObject jsonObj = iter.next().getAsJsonObject();

			Branch item = new Branch();
			item.setName(jsonObj.get("name").getAsString().trim());
			item.setProtectedBranch(jsonObj.get("protected").getAsBoolean());
			item.setDefaultBranch(jsonObj.get("default").getAsBoolean());
			item.setUrl(jsonObj.get("web_url").getAsString().trim());
			items.add(item);

		}

		return CommonUtils.streamOf(items);
	}

}
