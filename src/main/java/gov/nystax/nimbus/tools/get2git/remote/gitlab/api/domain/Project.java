package gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain;

import com.google.gson.annotations.SerializedName;

public class Project {
	private long id;
	private String description;
	private String name;
	private String nameWithNamespace;
	private String path;
	@SerializedName(value = "path_with_namespace")
	private String pathWithNamespace;
	@SerializedName(value = "default_branch")
	private String defaultBranch;
	@SerializedName(value = "empty_repo")
	private boolean emptyRepo;

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getNameWithNamespace() {
		return nameWithNamespace;
	}

	public void setNameWithNamespace(String nameWithNamespace) {
		this.nameWithNamespace = nameWithNamespace;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getPathWithNamespace() {
		return pathWithNamespace;
	}

	public void setPathWithNamespace(String pathWithNamespace) {
		this.pathWithNamespace = pathWithNamespace;
	}

	public String getDefaultBranch() {
		return defaultBranch;
	}

	public void setDefaultBranch(String defaultBranch) {
		this.defaultBranch = defaultBranch;
	}

	public boolean isEmptyRepo() {
		return emptyRepo;
	}

	public void setEmptyRepo(boolean emptyRepo) {
		this.emptyRepo = emptyRepo;
	}

}
