package gov.nystax.nimbus.tools.get2git.domain;

import java.util.Optional;

public class GitConfig {

	private static final String FW_USERNAME = "srvdtf_srv_git";
	private static final String FW_EMAIL = "srvdtf_srv_git@svc.ny.gov";
	private static final String FW_NAME = "srvdtf_srv_git";

	private final String username;
	private final String accessToken;
	private final GitRepoURL remoteUrl;
	private final String authorName;
	private final String authorEmail;
	private final String committerName;
	private final String committerEmail;

	private final Optional<String> description;

	/**
	 * @param username       The username to use for authentication
	 * @param accessToken    The token to use for authentication
	 * @param remoteUrl      The remote url to push to
	 * @param authorName     The name of the author
	 * @param authorEmail    The email of the author
	 * @param committerName  The name of the committer
	 * @param committerEmail the email of the committer
	 */
	public GitConfig(String username, String accessToken, GitRepoURL remoteUrl, Optional<String> authorName,
			Optional<String> authorEmail, String committerName, String committerEmail, Optional<String> description) {
		this.username = username;
		this.accessToken = accessToken;
		this.remoteUrl = remoteUrl;
		this.authorName = authorName.isPresent() ? authorName.get() : committerName;
		this.authorEmail = authorEmail.isPresent() ? authorEmail.get() : committerEmail;
		this.committerName = committerName;
		this.committerEmail = committerEmail;
		this.description = description;

	}

	/**
	 * Overloaded Constructor that assumes fw defaults
	 * 
	 * @param accessToken
	 * @param remoteUrl
	 * @param authorName
	 * @param authorEmail
	 */
	public GitConfig(String accessToken, GitRepoURL remoteUrl, Optional<String> authorName,
			Optional<String> authorEmail, Optional<String> description) {
		this(FW_USERNAME, accessToken, remoteUrl, authorName, authorEmail, FW_NAME, FW_EMAIL, description);
	}

	public String getUsername() {
		return username;
	}

	public String getAccessToken() {
		return accessToken;
	}

	public GitRepoURL getRemoteUrl() {
		return remoteUrl;
	}

	public String getAuthorName() {
		return authorName;
	}

	public String getAuthorEmail() {
		return authorEmail;
	}

	public String getCommitterName() {
		return committerName;
	}

	public String getCommitterEmail() {
		return committerEmail;
	}

	public Optional<String> getDescription() {
		return description;
	}

}
