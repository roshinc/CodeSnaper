package gov.nystax.nimbus.codesnap;

import gov.nystax.nimbus.codesnap.domain.CodeSnapperConfig;
import gov.nystax.nimbus.codesnap.domain.NimbusServiceMeta;
import gov.nystax.nimbus.codesnap.domain.ProjectSnap;
import gov.nystax.nimbus.codesnap.services.GitService;
import gov.nystax.nimbus.codesnap.services.scanner.NimbusServiceProjectScanner;
import gov.nystax.nimbus.codesnap.services.scanner.analyzer.ServiceResolutionConfig;
import gov.nystax.nimbus.codesnap.services.scanner.observability.ScanContext;
import gov.nystax.nimbus.codesnap.services.scanner.observability.ScanProgressListener;
import gov.nystax.nimbus.codesnap.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public class DefaultCodeSnapper implements CodeSnapper {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final NimbusServiceMeta nimbusServiceMeta;
    private final String commitHash;
    private final String gitToken;
    private final ServiceResolutionConfig resolutionConfig;

    public DefaultCodeSnapper(CodeSnapperConfig config) {
        if (config != null) {
            this.nimbusServiceMeta = new NimbusServiceMeta(config);
            this.commitHash = config.commitHash();
            this.gitToken = config.gitToken();
            this.resolutionConfig = new ServiceResolutionConfig(
                    config.lenientPairMatch(), config.inferImpl(), config.inferInterface());
        } else {
            throw new RuntimeException("Invalid Snapper Config");
        }

    }


    @Override
    public ProjectSnap generateSnapShotForProject() {
        ProjectSnap projectSnap = null;
        try (ScanContext context = new ScanContext("", ScanProgressListener.loggingListener());) {

            // Clone the repo
            String cloneMessage = String.format("Cloning Repo: %s @ commit: %s", nimbusServiceMeta.getGitRepoURL().toString(), commitHash);
            try {
                context.phaseStart("Git Clone", cloneMessage);
                Instant cloneStart = Instant.now();

                GitService gitService = new GitService(this.gitToken, context);
                gitService.cloneServiceRepo(this.nimbusServiceMeta, this.commitHash, null);

                // Set complete metrics
                Duration cloneDuration = Duration.between(cloneStart, Instant.now());
                context.getMetrics().setCloneDuration(cloneDuration);
                context.phaseComplete("Git Clone", true);
            } catch (Exception e) {
                logger.error("Could not clone repo", e);
                context.error("Git Clone", e);
                throw new RuntimeException("Could not clone repo", e);
            }

            // Scan the project
            try {
                NimbusServiceProjectScanner scanner = new NimbusServiceProjectScanner(
                        this.nimbusServiceMeta, context, this.resolutionConfig);
                projectSnap = scanner.scanProject();
            } catch (Exception e) {
                logger.error("Error scanning project", e);
                throw (RuntimeException) e;
            }

            // Delete the cloned repo
            try {
                context.phaseStart("Cleanup", "Deleting temporary directory");
                // Get commit folder
                Path commitFolder = this.nimbusServiceMeta.getLocalServiceRootPath().getParent();
                logger.debug("Deleting cloned repo: {}", commitFolder);
                FileUtils.deleteFolder(commitFolder);
                context.phaseComplete("Cleanup", true);
            } catch (Exception e) {
                logger.warn("Could not delete cloned repo", e);
                context.phaseComplete("Cleanup", false);
            }

            context.complete(true);
            return projectSnap;
        }
    }
}
