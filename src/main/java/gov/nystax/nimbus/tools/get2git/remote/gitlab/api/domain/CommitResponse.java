package gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public class CommitResponse {
	private String id;
	@SerializedName(value = "short_id")
	private String shortId;
	private String title;
	@SerializedName(value = "author_name")
	private String authorName;
	@SerializedName(value = "author_email")
	private String authorEmail;
	@SerializedName(value = "committer_name")
	private String committerName;
	@SerializedName(value = "committer_email")
	private String committerEmail;
	@SerializedName(value = "created_at")
	private String createdAt;
	private String message;
	@SerializedName(value = "parent_ids")
	private List<String> parentIds;
	@SerializedName(value = "committed_date")
	private String committedDate;
	@SerializedName(value = "authored_date")
	private String authoredDate;
	private Stats stats;
	private String status;
	@SerializedName(value = "web_url")
	private String webUrl;

	public CommitResponse() {
		super();
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getShortId() {
		return shortId;
	}

	public void setShortId(String shortId) {
		this.shortId = shortId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getAuthorName() {
		return authorName;
	}

	public void setAuthorName(String authorName) {
		this.authorName = authorName;
	}

	public String getAuthorEmail() {
		return authorEmail;
	}

	public void setAuthorEmail(String authorEmail) {
		this.authorEmail = authorEmail;
	}

	public String getCommitterName() {
		return committerName;
	}

	public void setCommitterName(String committerName) {
		this.committerName = committerName;
	}

	public String getCommitterEmail() {
		return committerEmail;
	}

	public void setCommitterEmail(String committerEmail) {
		this.committerEmail = committerEmail;
	}

	public String getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(String createdAt) {
		this.createdAt = createdAt;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public List<String> getParentIds() {
		return parentIds;
	}

	public void setParentIds(List<String> parentIds) {
		this.parentIds = parentIds;
	}

	public String getCommittedDate() {
		return committedDate;
	}

	public void setCommittedDate(String committedDate) {
		this.committedDate = committedDate;
	}

	public String getAuthoredDate() {
		return authoredDate;
	}

	public void setAuthoredDate(String authoredDate) {
		this.authoredDate = authoredDate;
	}

	public Stats getStats() {
		return stats;
	}

	public void setStats(Stats stats) {
		this.stats = stats;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getWebUrl() {
		return webUrl;
	}

	public void setWebUrl(String webUrl) {
		this.webUrl = webUrl;
	}

	public static class Stats {

		private int additions;
		private int deletions;
		private int total;

		public int getAdditions() {
			return additions;
		}

		public Stats() {
			super();
		}

		public void setAdditions(int additions) {
			this.additions = additions;
		}

		public int getDeletions() {
			return deletions;
		}

		public void setDeletions(int deletions) {
			this.deletions = deletions;
		}

		public int getTotal() {
			return total;
		}

		public void setTotal(int total) {
			this.total = total;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("Stats [additions=");
			builder.append(additions);
			builder.append(", deletions=");
			builder.append(deletions);
			builder.append(", total=");
			builder.append(total);
			builder.append("]");
			return builder.toString();
		}

	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("CommitResponse [id=");
		builder.append(id);
		builder.append(", shortId=");
		builder.append(shortId);
		builder.append(", title=");
		builder.append(title);
		builder.append(", authorName=");
		builder.append(authorName);
		builder.append(", authorEmail=");
		builder.append(authorEmail);
		builder.append(", committerName=");
		builder.append(committerName);
		builder.append(", committerEmail=");
		builder.append(committerEmail);
		builder.append(", createdAt=");
		builder.append(createdAt);
		builder.append(", message=");
		builder.append(message);
		builder.append(", parentIds=");
		builder.append(parentIds);
		builder.append(", committedDate=");
		builder.append(committedDate);
		builder.append(", authoredDate=");
		builder.append(authoredDate);
		builder.append(", stats=");
		builder.append(stats);
		builder.append(", status=");
		builder.append(status);
		builder.append(", webUrl=");
		builder.append(webUrl);
		builder.append("]");
		return builder.toString();
	}

}