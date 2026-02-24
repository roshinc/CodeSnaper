package gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain;

import com.google.gson.annotations.SerializedName;

public class Branch {
	private String name;
	@SerializedName(value = "protected")
	private boolean protectedBranch;
	@SerializedName(value = "default")
	private boolean defaultBranch;
	@SerializedName(value = "web_url")
	private String url;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isProtectedBranch() {
		return protectedBranch;
	}

	public void setProtectedBranch(boolean protectedBranch) {
		this.protectedBranch = protectedBranch;
	}

	public boolean isDefaultBranch() {
		return defaultBranch;
	}

	public void setDefaultBranch(boolean defaultBranch) {
		this.defaultBranch = defaultBranch;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

}
