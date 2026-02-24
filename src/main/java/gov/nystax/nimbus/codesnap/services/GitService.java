package gov.nystax.nimbus.codesnap.services;

import gov.nystax.nimbus.codesnap.domain.NimbusServiceMeta;
import gov.nystax.nimbus.codesnap.services.scanner.observability.ScanContext;
import gov.nystax.nimbus.codesnap.util.FileUtils;
import gov.nystax.nimbus.tools.get2git.GitRepositoryAccessor;
import gov.nystax.nimbus.tools.get2git.domain.GitConfig;
import gov.nystax.nimbus.tools.get2git.domain.GitRepoURL;
//import gov.nystax.nimbus.tools.problems.exceptions.base.ProblemsIllegalArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static gov.nystax.nimbus.codesnap.util.FileUtils.isPathNotEmpty;

public class GitService {
    private final String gitToken;
    private final ScanContext context;
    private final Logger logger = LoggerFactory.getLogger(GitService.class);


    public GitService(String gitToken, ScanContext context) {
        this.gitToken = gitToken;
        this.context = context;
    }

    public void cloneServiceRepo(NimbusServiceMeta nimbusServiceMeta, String commitHash, String branch)
            throws RuntimeException {


        GitConfig gitConfig = new GitConfig(gitToken, nimbusServiceMeta.getGitRepoURL(), Optional.empty(),
                Optional.empty(), Optional.empty());


        // Initialize cloner
        GitRepositoryAccessor cloner = new GitRepositoryAccessor(gitConfig, nimbusServiceMeta.getLocalServiceRootPath(),
                branch, commitHash, Optional.empty());

        // Check if the repo already exists
        // Check if the local path exists
        if (Files.exists(nimbusServiceMeta.getLocalServiceRootPath())) {
            logger.debug("The local path [{}] exists", nimbusServiceMeta.getLocalServiceRootPath());

            // Check if the commit hash we want
            if (isCommitHashCorrect(cloner, nimbusServiceMeta.getGitRepoURL(),
                    nimbusServiceMeta.getLocalServicePomPath(), commitHash)) {
                logger.debug("The local repo is at the right commit hash [{}]", commitHash);
                // Check if pom exists
                if (doesLocalServiceExists(nimbusServiceMeta.getLocalServicePomPath())) {
                    logger.debug("The local pom.xml path [{}] exists, returning ...",
                            nimbusServiceMeta.getLocalServicePomPath());
                } else {
                    // the repo directory already exists, a clone would be skipped anyway
                    logger.debug("The local pom path [{}] is empty, this commit is probably not a complete" +
                            " service project, returning ...", nimbusServiceMeta.getLocalServicePomPath());
                }
                return;
            } else {
                try {
                    String phaseMsg = String.format("The local repo is not at the right commit hash [%s]", commitHash);
                    this.context.phaseStart("Pre-Clone Cleanup", phaseMsg);
                    FileUtils.deleteFolder(nimbusServiceMeta.getLocalServiceRootPath());
                    context.phaseComplete("Pre-Clone Cleanup", true);
                } catch (Exception e) {
                    logger.error("The local repo could not be deleted [{}]",
                            nimbusServiceMeta.getLocalServiceRootPath(), e);
                    context.error("Pre-Clone Cleanup", e);
                    throw new RuntimeException("The local repo with the wrong commit hash could not be deleted", e);
                }
            }
        }

        try {
            // Make sure the directories exist
            Files.createDirectories(nimbusServiceMeta.getLocalServiceRootPath());

            logger.debug("Cloning ...");
            cloner.cloneRemote();

        } catch (Exception ex) {
            throw new RuntimeException("Could not clone remote", ex);
        }

        // Check if the commit hash we want
        if (isCommitHashCorrect(cloner, nimbusServiceMeta.getGitRepoURL(),
                nimbusServiceMeta.getLocalServicePomPath(), commitHash)) {
            logger.debug("After cloning, the local repo is at the right commit hash [{}]", commitHash);
            // Check if pom exists
            if (doesLocalServiceExists(nimbusServiceMeta.getLocalServicePomPath())) {
                logger.debug("After cloning, the local pom.xml path [{}] exists, returning ...",
                        nimbusServiceMeta.getLocalServicePomPath());
            } else {
                // the repo directory already exists, a clone would be skipped anyway
                logger.debug("After cloning, the local pom path [{}] is empty, this commit is probably not a complete" +
                        " service project, returning ...", nimbusServiceMeta.getLocalServicePomPath());
            }
            return;
        }

        throw new RuntimeException("Unable to get the repo");
    }


    private boolean doesLocalServiceExists(Path localServicePomPath) throws ProblemsIllegalArgumentException {
        if (isPathNotEmpty(localServicePomPath)) {
            logger.debug("The local pom.xml file @ [{}] exists", localServicePomPath);
            return true;
        }
        return false;
    }

    private boolean isCommitHashCorrect(GitRepositoryAccessor cloner, GitRepoURL gitRepoURL,
                                        Path localServicePomPath, String commitHash) {

        try {
            String localCommitHash = cloner.getLocalCommitHash().orElse(null);
            String localBranch = cloner.getLocalBranch();
            logger.debug("The local {} for repo {} exists. The ref is {} from branch {}", localServicePomPath,
                    gitRepoURL.getRepoName(), localCommitHash, localBranch);
            if (localCommitHash != null && commitHash != null) {
                if (localCommitHash.equals(commitHash)) {
                    logger.info("Verified correct commit hash [{}] and branch [{}]", commitHash, localBranch);
                    return true;
                } else {
                    logger.warn("Repo found for unexpected commit hash [{}]", commitHash);
                    return false;
                }
            }
        } catch (Exception ex) {
            logger.error("Could not get commit hash", ex);
            return false;
        }
        logger.warn("Could not get repo commits @ {}", localServicePomPath);
        return false;
    }
}
