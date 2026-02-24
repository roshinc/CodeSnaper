package gov.nystax.nimbus.tools.get2git;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

import gov.nystax.nimbus.tools.get2git.domain.Get2GitOption;
import gov.nystax.nimbus.tools.get2git.domain.GitConfig;
import gov.nystax.nimbus.tools.get2git.domain.StandardGet2GitOption;
import gov.nystax.nimbus.tools.get2git.domain.exceptions.G2GLocalException;
import gov.nystax.nimbus.tools.get2git.domain.exceptions.G2GRepoAlreadyExistsException;
import gov.nystax.nimbus.tools.get2git.remote.domain.ICheckerResult;
import gov.nystax.nimbus.tools.get2git.remote.domain.IRemoteService;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.GitLabService;

/**
 * Handles the initialization of a git repository, the creation of a .gitignore,
 * the commit and the push.
 */
public class InitCommitAndPusher {

	// Defaults
	private static final String DEFAULT_INITIAL_BRANCH_NAME = "main";
	private static final String DEFAULT_DEVELOPMENT_BRANCH_NAME = "dev";
	public static final String FILENAME_TO_CHECK_FOR_VERIFICATION = "pom.xml";
	public static final int DEPTH_TO_CHECK_FOR_VERIFICATION = 1;

	private static final Logger logger = LoggerFactory.getLogger(InitCommitAndPusher.class);

	private final GitConfig gitConfig;
	private final Path localRepo;
	private Git gitRepo;
	private boolean remoteRepoExists = false;
	private Optional<Long> remoteRepoId = Optional.empty(); // will only be set if remoteRepoExists is true
	private boolean pushToExistingRepo = false;

	private final Set<Get2GitOption> options;

	private final String commitMessage;

	public InitCommitAndPusher(GitConfig gitConfig, Path localRepo, Optional<Set<Get2GitOption>> options, String commitMessage) {
		this.gitConfig = gitConfig;
		this.localRepo = localRepo;
        this.options = options.orElseGet(Sets::newHashSet);
		this.commitMessage = commitMessage;
	}

	public InitCommitAndPusher(GitConfig gitConfig, Path localRepo, Optional<Set<Get2GitOption>> options) {
		this(gitConfig,localRepo,options, "Initial Commit");
	}

	public long run() throws G2GRepoAlreadyExistsException, G2GLocalException {
		// 0. Check if remote repo exists
		if (!this.remoteAlreadyExists()) {
			try {

				// 1. Initialize the git repository
				this.init();
				if (null == this.gitRepo) {
					throw new G2GRepoAlreadyExistsException(
							String.format("Local repo [%s] already exists, will not continue",
									this.localRepo.toString()),
							this.gitConfig.getRemoteUrl(), this.remoteRepoId, this.localRepo, logger);
				}
				// 2. Create a .gitignore file
				// this.createGitIgnore();
				// 3. Track all files
				this.trackFiles();
				// 4. Add a remote
				this.addRemote();
				// 5. Commit the files
				this.commit();
				// 6.pre This will merge existing with remote
				// as long as there are no conflicts
//				if (remoteRepoExists && pushToExistingRepo) {
//					this.pull();
//				} else
				if (remoteRepoExists) {
					logger.error(String.format(
							"Remote repo [%s] exists and pushToExistingRepo is false" + ", will not continue",
							this.gitConfig.getRemoteUrl().getRepoURL()));
					throw new G2GRepoAlreadyExistsException(
							String.format("Remote repo [%s] already exists, will not continue",
									this.gitConfig.getRemoteUrl().getRepoURL()),
							this.gitConfig.getRemoteUrl(), this.remoteRepoId, this.localRepo, logger);
				}

				// 5. Push the files
				this.ensureImmediateParentExists();

				// Create remote repo
				this.createRemoteRepo();

				this.push();

				// 6. Create dev and protected it
				this.createDevBranchAndProtectIt();
				// 7. Set description
				this.updateDescription();
				// 8. Allow developers to merge to main
				this.updateMainToAllowDeveloperMerge();

				// return project id
				return this.getProjectID();
			} catch (GitAPIException e) {
				logger.error("Error while initializing, committing or pushing the files", e);
				throw new G2GLocalException("Error while initializing, committing or pushing the files", e, logger);
			} catch (URISyntaxException e) {
				throw new G2GLocalException("Error while creating URIish", e, logger);
			} finally {
				gitRepo.close();
			}
		} else {
			throw new G2GRepoAlreadyExistsException(
					String.format("Remote repo [%s] already exists, will not continue",
							this.gitConfig.getRemoteUrl().getRepoURL()),
					this.gitConfig.getRemoteUrl(), this.remoteRepoId, this.localRepo, logger);
		}

	}

	private void createRemoteRepo() {
		if (isOptionEnabled(StandardGet2GitOption.CREATE_PROJECT_USING_API)) {
			IRemoteService service = new GitLabService(this.gitConfig);
			service.createProject();
		}
	}

	private void ensureImmediateParentExists() {
		if (isOptionEnabled(StandardGet2GitOption.CREATE_REMOTE_PARENT_IF_NEEDED)) {
			IRemoteService service = new GitLabService(this.gitConfig);
			service.createParentIfNeeded();
		}
	}

	private boolean remoteAlreadyExists() {
		IRemoteService checker = new GitLabService(this.gitConfig);
		ICheckerResult response = checker.verifyExists(Optional.of(FILENAME_TO_CHECK_FOR_VERIFICATION),
				DEPTH_TO_CHECK_FOR_VERIFICATION);

		this.remoteRepoExists = response.isRepoExists();
		this.remoteRepoId = response.getRepoId();

		if (response.isRepoExists() && response.isFileExists()) {
			return true;
		}

		return false;
	}

	private long getProjectID() {
		IRemoteService service = new GitLabService(this.gitConfig);
        return service.getProjectID();
	}

	private boolean createDevBranchAndProtectIt() {
		IRemoteService remoteSrv = new GitLabService(this.gitConfig);
		return remoteSrv.createBranchAndProtectItWithDeveloperAccess(DEFAULT_DEVELOPMENT_BRANCH_NAME);
	}

	private boolean updateMainToAllowDeveloperMerge() {
		IRemoteService remoteSrv = new GitLabService(this.gitConfig);
		return remoteSrv.updateDefaultBranchToAllowDeveloperToMerge();

	}

	/**
	 * Sets description on the remote repo if provided
	 * 
	 * @return
	 */
	private boolean updateDescription() {
		Optional<String> description = this.gitConfig.getDescription();
		if (description.isPresent()) {
			IRemoteService remoteSrv = new GitLabService(this.gitConfig);
			return remoteSrv.setDescription(description.get());
		}
		return false;
	}

	/**
	 * Initialize a git repository
	 *
	 * @throws GitAPIException
	 */
	private void init() throws GitAPIException {
		// Check if repo exists
		if (!repositoryExists(localRepo.toFile())) {
			this.gitRepo = Git.init().setDirectory(localRepo.toFile()).setInitialBranch(DEFAULT_INITIAL_BRANCH_NAME)
					.call();
			logger.info("Created repository: " + this.gitRepo.getRepository().getDirectory());
		} else {
			logger.info("Repository: " + localRepo + " already exists.");
		}
	}

	/**
	 * Checks if a directory is a git repo
	 * 
	 * TODO: use repo
	 * https://www.codeaffine.com/2015/05/06/jgit-initialize-repository/
	 */
	private boolean repositoryExists(File directory) {

		boolean gitDirExists = false;

		FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
		repositoryBuilder.addCeilingDirectory(directory);
		repositoryBuilder.findGitDir(directory);
		System.out.println(repositoryBuilder.getGitDir());
		if (repositoryBuilder.getGitDir() != null) {

			gitDirExists = true;
		}

		return gitDirExists;
	}

	/**
	 * Creates a gitignore file in the root of the repository.
	 *
	 * @throws IOException
	 */
	/*
	 * private void createGitIgnore() throws IOException { Path gitIgnorePath =
	 * this.localRepo.resolve(".gitignore"); try (FileWriter fileWriter = new
	 * FileWriter(gitIgnorePath.toFile()); PrintWriter printWriter = new
	 * PrintWriter(fileWriter)) { printWriter.println("target/");
	 * printWriter.println(".idea/"); } logger.info("Created .gitignore file"); }
	 */

	/**
	 * Adds all files to the staging area
	 *
	 * @throws GitAPIException if an error occurs during the Git API call
	 */
	private void trackFiles() throws GitAPIException {
		this.gitRepo.add().addFilepattern(".").call();
		this.gitRepo.add().setUpdate(true).addFilepattern(".").call();
		logger.info("Tracked files");
	}

	/**
	 * Adds a remote to the git repository.
	 *
	 * @throws GitAPIException if an error occurs during the Git API call
	 * @throws URISyntaxException if the URI for the remote URL is invalid
	 */
	private void addRemote() throws GitAPIException, URISyntaxException {
		RemoteAddCommand remoteAddCommand = this.gitRepo.remoteAdd();
		remoteAddCommand.setName("origin");
		remoteAddCommand.setUri(new URIish(this.gitConfig.getRemoteUrl().getRepoURL()));
		remoteAddCommand.call();
		logger.info("Added remote");
	}


	/**
	 * Commit all tracked files
	 *
	 * @throws GitAPIException if an error occurs during the commit process
	 */
	private void commit() throws GitAPIException {
		CommitCommand commitCommand = this.gitRepo.commit();
		commitCommand.setMessage(this.commitMessage);
		commitCommand.setAuthor(this.gitConfig.getAuthorName(), this.gitConfig.getAuthorEmail());
		commitCommand.setCommitter(this.gitConfig.getCommitterName(), this.gitConfig.getCommitterEmail());
		commitCommand.call();
		logger.info("Committed");
	}

	/*
	 * private void branch() throws RefAlreadyExistsException, RefNotFoundException,
	 * InvalidRefNameException, GitAPIException { CreateBranchCommand
	 * branchCreateCommand = this.gitRepo.branchCreate();
	 * branchCreateCommand.setName("dev"); branchCreateCommand.call();
	 * logger.info("Branch Created"); }
	 */

	/**
	 * Pulls from the remote repository
	 *
	 * @throws GitAPIException
	 */
	/*
	 * private void pull() throws GitAPIException { PullCommand pullCommand =
	 * this.gitRepo.pull(); pullCommand.setCredentialsProvider( new
	 * UsernamePasswordCredentialsProvider(this.gitConfig.getUsername(),
	 * this.gitConfig.getAccessToken())); pullCommand.call(); logger.info("Pulled");
	 * }
	 */

	/**
	 * Pushes the commits to the remote repository.
	 *
	 * @throws GitAPIException if an error occurs during the push process
	 */
	private void push() throws GitAPIException {
		PushCommand pushCommand = this.gitRepo.push();
		pushCommand.setCredentialsProvider(
				new UsernamePasswordCredentialsProvider(this.gitConfig.getUsername(), this.gitConfig.getAccessToken()));
		pushCommand.setPushAll();
		pushCommand.call();
		logger.info("Pushed");
	}

	/**
	 * Checks if the specified option is enabled.
	 *
	 * @param option the option to check
	 * @return true if the option is enabled, false otherwise
	 */
	private boolean isOptionEnabled(Get2GitOption option) {
		if (this.options == null || this.options.isEmpty()) {
			return false;
		}

		return this.options.contains(option);
	}
}
