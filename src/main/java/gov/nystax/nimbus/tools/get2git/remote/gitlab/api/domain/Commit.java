package gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public class Commit {
	private String branch;
	@SerializedName(value = "commit_message")
	private String commitMessage;
	private List<CommitAction> actions;

	// Constructor
	public Commit(String branch, String commit_message, List<CommitAction> commitActions) {
		this.branch = branch;
		this.commitMessage = commit_message;
		this.actions = commitActions;
	}

	public String getBranch() {
		return branch;
	}

	public void setBranch(String branch) {
		this.branch = branch;
	}

	public String getCommitMessage() {
		return commitMessage;
	}

	public void setCommitMessage(String commitMessage) {
		this.commitMessage = commitMessage;
	}

	public List<CommitAction> getActions() {
		return actions;
	}

	public void setActions(List<CommitAction> actions) {
		this.actions = actions;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Commit [branch=");
		builder.append(branch);
		builder.append(", commitMessage=");
		builder.append(commitMessage);
		builder.append(", actions=");
		builder.append(actions);
		builder.append("]");
		return builder.toString();
	}

}