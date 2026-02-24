/**
 * 
 */
package gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain;

import com.google.common.base.Strings;

/**
 * @author t63606
 *
 */
public class TreeItem {
	private String id;
	private String name;
	private FILE_TYPE type;
	private String path;
	private String mode;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public FILE_TYPE getType() {
		return type;
	}

	public void setType(FILE_TYPE fileType) {
		this.type = fileType;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public enum FILE_TYPE {
		TREE("tree"), BLOB("blob");

		private String value;

		private FILE_TYPE(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

		public static FILE_TYPE getFileType(String fileType) {
			for (FILE_TYPE ft : FILE_TYPE.values()) {
				if (ft.getValue().equals(Strings.nullToEmpty(fileType).trim())) {
					return ft;
				}
			}

			return null;
		}
	}
}
