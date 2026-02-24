package gov.nystax.nimbus.tools.get2git;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import gov.nystax.nimbus.tools.get2git.domain.Get2GitOption;
import gov.nystax.nimbus.tools.get2git.domain.GitConfig;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;


/**
 * Represents a Git repository accessor that provides methods for cloning, pulling, a local Git repository.
 * </p>
 *
 * <p><strong>Thread safety:</strong> This class is thread-safe when each thread
 * creates and owns its own instance.</p>
 */
public class GitRepositoryAccessor {

    // Defaults
    private static final String DEFAULT_BRANCH_NAME = "main";

    private final Logger logger = LoggerFactory.getLogger(GitRepositoryAccessor.class);

    private final GitConfig gitConfig;
    private final Path localRepo;
    private final CredentialsProvider credentialsProvider;
    private final String desiredBranch;
    private final String specificCommit;

    private final Set<Get2GitOption> options;

    public GitRepositoryAccessor(GitConfig gitConfig, Path localRepo, String desiredBranch, String specificCommit, Optional<Set<Get2GitOption>> options) {
        this.gitConfig = gitConfig;
        this.localRepo = localRepo;
        this.credentialsProvider = new UsernamePasswordCredentialsProvider(this.gitConfig.getUsername(),
                this.gitConfig.getAccessToken());

        this.options = options.orElseGet(Sets::newHashSet);
        this.desiredBranch = Strings.isNullOrEmpty(desiredBranch) ? DEFAULT_BRANCH_NAME : desiredBranch;
        this.specificCommit = specificCommit;
    }

    public GitRepositoryAccessor(GitConfig gitConfig, Path localRepo, String desiredBranch, Optional<Set<Get2GitOption>> options) {
        this(gitConfig, localRepo, desiredBranch, null, options);
    }

    public GitRepositoryAccessor(GitConfig gitConfig, Path localRepo, Optional<Set<Get2GitOption>> options) {
        this(gitConfig, localRepo, DEFAULT_BRANCH_NAME, null, options);
    }

    /**
     * Clones the remote repository to the local path if the local path is empty.
     *
     * @throws GitAPIException If an error occurs while cloning the repository.
     * @throws IOException     If an error occurs while accessing the local repository directory.
     */
    public void cloneRemote() throws GitAPIException, IOException {
        Files.createDirectories(this.localRepo);
        if (Files.exists(this.localRepo)) {
            try (Stream<Path> list = Files.list(this.localRepo)) {
                if (list.findAny().isEmpty()) {
                    CloneCommand cloneCommand = Git.cloneRepository();
                    cloneCommand.setURI(this.gitConfig.getRemoteUrl().getRepoURL());
                    cloneCommand.setDirectory(this.localRepo.toFile());
                    cloneCommand.setCredentialsProvider(this.credentialsProvider);
                    cloneCommand.setBranch(this.desiredBranch);
                    try (Git git = cloneCommand.call()) {
                        logger.info("Cloned repository on branch {}", this.desiredBranch);

                        // If specific commit is requested, validate and checkout that commit
                        if (!Strings.isNullOrEmpty(this.specificCommit)) {
                            if (validateCommitExists(git, this.specificCommit)) {
                                checkoutCommit(git, this.specificCommit);
                                logger.info("Successfully checked out commit: {}", this.specificCommit);
                            } else {
                                String errorMsg = String.format("Commit %s does not exist in repository",
                                        this.specificCommit);
                                logger.error(errorMsg);
                                throw new IllegalArgumentException(errorMsg);
                            }
                        }
                    }
                }
            }
        } else {
            logger.debug("Local directory is not empty. Skipping clone operation.");
        }
    }


    /**
     * Pulls the latest changes from the remote repository into the local repository.
     * <p>
     * Pulling is disabled when a specific commit is configured to ensure
     * deterministic repository.
     * </p>
     *
     * @throws GitAPIException If an error occurs while executing the pull operation.
     * @throws IOException     If an error occurs while accessing the local repository directory.
     */
    public boolean pull() throws GitAPIException, IOException {
        if (specificCommit != null) {
            logger.warn("Pull skipped because specific commit is configured");
            return false;
        }
        if (Files.exists(this.localRepo)) {
            try (Git git = Git.open(this.localRepo.toFile())) {
                logger.debug("Doing a checkout on branch: {}", this.desiredBranch);
                checkOutBranch(git);
                PullResult result = git.pull().setCredentialsProvider(this.credentialsProvider).call();
                if (result.isSuccessful()) {
                    logger.debug("Pulled latest changes from {}", result.getFetchedFrom());
                    return true;
                }
                logger.error("Pull failed from {}", this.desiredBranch);
            }
        } else {
            logger.error("Local repository[{}] not found. Skipping pull operation.", this.localRepo);
        }
        return false;
    }

    /**
     * Validates that a commit exists in the repository.
     *
     * @param git        The Git object representing the repository.
     * @param commitHash The full commit hash to validate.
     * @return true if the commit exists, false otherwise.
     */
    private boolean validateCommitExists(Git git, String commitHash) {
        Repository repository = git.getRepository();
        try (RevWalk revWalk = new RevWalk(repository)) {

            ObjectId commitId = repository.resolve(commitHash);
            if (commitId == null) {
                logger.warn("Commit {} could not be resolved", commitHash);
                return false;
            }

            // Verify it's actually a commit
            RevCommit commit = revWalk.parseCommit(commitId);
            logger.debug("Validated commit: {} - {}", commitHash, commit.getShortMessage());
            return true;

        } catch (IOException e) {
            logger.error("Error validating commit {}", commitHash, e);
            return false;
        }
    }

    /**
     * Checks out a specific commit in the repository.
     * This will put the repository in a "detached HEAD" state.
     *
     * @param git        The Git object representing the repository.
     * @param commitHash The full commit hash to checkout.
     * @throws GitAPIException If an error occurs during checkout.
     */
    private void checkoutCommit(Git git, String commitHash) throws GitAPIException {
        try {
            git.checkout()
                    .setName(commitHash)
                    .call();
            logger.info("Checked out commit: {} (detached HEAD state)", commitHash);
        } catch (GitAPIException e) {
            logger.error("Failed to checkout commit: {}", commitHash, e);
            throw e;
        }
    }

    /**
     * Checks out the specified branch in the local Git repository.
     *
     * @param localRepoGitObj The Git object representing the local repository.
     */
    private void checkOutBranch(Git localRepoGitObj) {
        try {
            // Check out the desired branch
            localRepoGitObj.checkout().setName(this.desiredBranch).call();
            logger.info("Checked out existing branch {}", this.desiredBranch);
        } catch (RefNotFoundException e) {
            // Branch doesn't exist locally, create it
            try {
                localRepoGitObj.checkout().setCreateBranch(true).setName(this.desiredBranch)
                        .setStartPoint("origin/" + this.desiredBranch).call();
                logger.info("Something went wrong trying to create the local branch for {}, Created and checked out " +
                        "branch from origin", this.desiredBranch);
            } catch (RefNotFoundException refNotFoundException) {
                logger.error("Something went wrong trying to create the local branch for {}, Branch does not exist " +
                        "on the remote repository", this.desiredBranch);
            } catch (RefAlreadyExistsException ex) {
                logger.error("Something went wrong trying to create the local branch for {}, ref already exists not " +
                        "checking out. ", this.desiredBranch, e);
            } catch (InvalidRefNameException ex) {
                logger.error("Something went wrong trying to create the local branch for {}, branch name is invalid. "
                        , this.desiredBranch, e);
            } catch (CheckoutConflictException ex) {
                logger.error("Something went wrong trying to create the local branch for {}, " +
                        "There is a change that is conflicting somehow. ", this.desiredBranch, e);
            } catch (GitAPIException ex) {
                logger.error("Something went wrong trying to create the local branch for {}", this.desiredBranch, e);
            }
        } catch (RefAlreadyExistsException e) {
            logger.error("Branch {} does not exist on the remote repository", this.desiredBranch);
        } catch (InvalidRefNameException e) {
            logger.error("Something went wrong trying to checkout the branch {}, branch name is invalid. ",
                    this.desiredBranch, e);
        } catch (CheckoutConflictException e) {
            logger.error("Something went wrong trying to checkout the branch {}, There is a change that is " +
                    "conflicting somehow. ", this.desiredBranch, e);
        } catch (GitAPIException e) {
            logger.error("Something went wrong trying to create the checkout branch {}", this.desiredBranch, e);
        }
    }

    /**
     * Retrieves the commit hash of the HEAD branch from the local Git repository.
     *
     * @return An Optional containing the commit hash if the local repository exists and the commit hash is resolved successfully,
     * or an empty Optional if the local repository doesn't exist.
     * @throws IOException If an error occurs while accessing the local repository.
     */
    public Optional<String> getLocalCommitHash() throws IOException {
        if (!isRepositoryPresent()) {
            return Optional.empty();
        }
        try (Repository repository = openRepository(); RevWalk walk = new RevWalk(repository)) {
            ObjectId head = repository.resolve("HEAD");
            RevCommit commit = walk.parseCommit(head);
            return Optional.of(commit.getName());

        }

    }

    /**
     * Retrieves the name of the local branch from the local Git repository.
     *
     * @return The name of the local branch if the local repository exists, or null if the local repository doesn't exist.
     * @throws IOException If an error occurs while accessing the local repository.
     */
    public String getLocalBranch() throws IOException {
        if (!isRepositoryPresent()) {
            return null;
        }
        try (Repository repository = openRepository()) {
            return repository.getBranch();
        }
    }

    /**
     * Deletes the local Git repository.
     *
     * @throws IOException If an error occurs while deleting the repository.
     */
    public void delete() throws IOException {
        if (Files.exists(this.localRepo)) {
            deleteDirectory(this.localRepo.toFile());
            logger.info("Local repository deleted.");
        } else {
            logger.warn("Local repository not found. Skipping delete operation.");
        }
    }

    /**
     * Recursively deletes a directory and its contents.
     *
     * @param directory The directory to delete.
     * @throws IOException If an error occurs while deleting the directory.
     */
    private void deleteDirectory(File directory) throws IOException {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else if (!file.delete()) {
                    throw new IOException("Failed to delete file: " + file);

                }
            }
        }
        if (!directory.delete()) {
            throw new IOException("Failed to delete directory: " + directory);
        }
    }

    /**
     * Checks if the given option is enabled.
     *
     * @param option The Get2GitOption to check.
     * @return true if the option is enabled, false otherwise.
     */
    private boolean isOptionEnabled(Get2GitOption option) {
        if (this.options == null || this.options.isEmpty()) {
            return false;
        }

        return this.options.contains(option);
    }

    /**
     * Checks if the repository exists
     *
     * @return true if the .git folder exists within the local repo; false otherwise
     */
    private boolean isRepositoryPresent() {
        return Files.exists(this.localRepo.resolve(".git"));
    }

    /**
     * Opens the local Git repository using the configured local repository path.
     * <p>
     * Close the returned {@code Repository} object after use.
     * </p>
     *
     * @return The {@code Repository} object representing the opened Git repository.
     * @throws IOException If an error occurs while accessing the local repository or its configuration.
     */
    private Repository openRepository() throws IOException {
        return new FileRepositoryBuilder()
                .setGitDir(this.localRepo.resolve(".git").toFile())
                .readEnvironment()
                .build();
    }
}
