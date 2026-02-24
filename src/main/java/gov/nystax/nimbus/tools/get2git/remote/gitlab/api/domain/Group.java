/**
 * 
 */
package gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain;

import com.google.gson.annotations.SerializedName;

/**
 * @author t63606
 *
 */
public class Group {
	private long id;
	private String name;
	private String path;
	private String description;
	@SerializedName(value = "web_url")
	private String webURL;
	@SerializedName(value = "full_name")
	private String fullName;
	@SerializedName(value = "full_path")
	private String fullPath;
	@SerializedName(value = "parent_id")
	private Long parentID;

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getWebURL() {
		return webURL;
	}

	public void setWebURL(String webURL) {
		this.webURL = webURL;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public String getFullPath() {
		return fullPath;
	}

	public void setFullPath(String fullPath) {
		this.fullPath = fullPath;
	}

	public Long getParentID() {
		return parentID;
	}

	public void setParentID(Long parentID) {
		this.parentID = parentID;
	}

}
