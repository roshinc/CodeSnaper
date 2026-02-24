package gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain;

public class CommitAction {
	private String action;
	private String file_path;
	private String content; // Optional, depending on the action
	private String previous_path; // Optional, for 'move' action
	private Boolean execute_filemode; // Optional, for 'chmod' action

	// Constructor
	public CommitAction(String action, String file_path, String content, String previous_path,
			Boolean execute_filemode) {
		this.action = action;
		this.file_path = file_path;
		this.content = content;
		this.previous_path = previous_path;
		this.execute_filemode = execute_filemode;
	}

	// Overloaded constructors for actions without optional fields
	// For example, for 'delete' action which doesn't need 'content',
	// 'previous_path', or 'execute_filemode'
	public CommitAction(String action, String file_path) {
		this(action, file_path, null, null, null);
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getFile_path() {
		return file_path;
	}

	public void setFile_path(String file_path) {
		this.file_path = file_path;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getPrevious_path() {
		return previous_path;
	}

	public void setPrevious_path(String previous_path) {
		this.previous_path = previous_path;
	}

	public Boolean getExecute_filemode() {
		return execute_filemode;
	}

	public void setExecute_filemode(Boolean execute_filemode) {
		this.execute_filemode = execute_filemode;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("CommitAction [action=");
		builder.append(action);
		builder.append(", file_path=");
		builder.append(file_path);
		builder.append(", content=");
		builder.append(content);
		builder.append(", previous_path=");
		builder.append(previous_path);
		builder.append(", execute_filemode=");
		builder.append(execute_filemode);
		builder.append("]");
		return builder.toString();
	}

}