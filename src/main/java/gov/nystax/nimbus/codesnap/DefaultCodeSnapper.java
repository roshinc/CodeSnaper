package gov.nystax.nimbus.codesnap;

import gov.nystax.nimbus.codesnap.domain.CodeSnapperConfig;
import gov.nystax.nimbus.codesnap.domain.NimbusServiceMeta;
import gov.nystax.nimbus.codesnap.domain.ProjectSnap;
import gov.nystax.nimbus.codesnap.services.GitService;
import gov.nystax.nimbus.codesnap.services.scanner.NimbaProjectScanner;
import gov.nystax.nimbus.codesnap.services.scanner.NimbusServiceProjectScanner;
import gov.nystax.nimbus.codesnap.services.scanner.ProjectScanner;
import gov.nystax.nimbus.codesnap.services.scanner.analyzer.MavenProjectAnalyzer;
import gov.nystax.nimbus.codesnap.services.scanner.analyzer.ServiceResolutionConfig;
import gov.nystax.nimbus.codesnap.services.scanner.domain.ProjectInfo;
import gov.nystax.nimbus.codesnap.services.scanner.observability.ScanContext;
import gov.nystax.nimbus.codesnap.services.scanner.observability.ScanProgressListener;
import gov.nystax.nimbus.codesnap.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

public class DefaultCodeSnapper implements CodeSnapper {
    private static final String NIMBA_MARKER = "nimba";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final NimbusServiceMeta nimbusServiceMeta;
    private final String commitHash;
    private final String gitToken;
    private final ServiceResolutionConfig resolutionConfig;
    private final boolean resolveMavenClasspath;
    private final Path mavenSettingsXmlPath;

    public DefaultCodeSnapper(CodeSnapperConfig config) {
        if (config != null) {
            this.nimbusServiceMeta = new NimbusServiceMeta(config);
            this.commitHash = config.commitHash();
            this.gitToken = config.gitToken();
            this.resolutionConfig = new ServiceResolutionConfig(
                    config.lenientPairMatch(), config.inferImpl(), config.inferInterface());
            this.resolveMavenClasspath = config.resolveMavenClasspath();
            this.mavenSettingsXmlPath = config.mavenSettingsXmlPath();
        } else {
            throw new RuntimeException("Invalid Snapper Config");
        }

    }


    @Override
    public ProjectSnap generateSnapShotForProject() {
        ProjectSnap projectSnap = null;
        try (ScanContext context = new ScanContext("", ScanProgressListener.loggingListener());) {
            ProjectInfo projectInfo;

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

            try {
                context.phaseStart("Maven Analysis", "Analyzing pom.xml");
                Instant pomStart = Instant.now();

                MavenProjectAnalyzer mavenAnalyzer = new MavenProjectAnalyzer();
                projectInfo = mavenAnalyzer.analyzeProject(this.nimbusServiceMeta.getLocalServicePomPath().getParent());

                Duration pomDuration = Duration.between(pomStart, Instant.now());
                context.getMetrics().setPomAnalysisDuration(pomDuration);

                if (projectInfo.getDependencies() != null) {
                    context.getMetrics().setDependenciesFound(projectInfo.getDependencies().size());
                }
                if (projectInfo.getSourceFiles() != null) {
                    context.getMetrics().setSourceFilesScanned(projectInfo.getSourceFiles().size());
                }
                context.phaseComplete("Maven Analysis", true);
            } catch (Exception e) {
                logger.error("Error scanning project maven project structure and POM", e);
                context.error("Maven Analysis", e);
                throw new RuntimeException("Error scanning project", e);
            }

            // Scan the project source
            try {
                ProjectScanner scanner = createProjectScanner(projectInfo, context);
                projectSnap = scanner.scanProject(projectInfo);
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

    ProjectScanner createProjectScanner(ProjectInfo projectInfo, ScanContext context) {
        if (isNimbaProject(projectInfo)) {
            logger.info("Selected Nimba project scanner for groupId={}", projectInfo.getGroupId());
            return new NimbaProjectScanner(
                    this.nimbusServiceMeta, context, this.resolveMavenClasspath, this.mavenSettingsXmlPath);
        }

        logger.info("Selected Nimbus service scanner for groupId={}", projectInfo.getGroupId());
        return new NimbusServiceProjectScanner(
                this.nimbusServiceMeta,
                context,
                this.resolutionConfig,
                this.resolveMavenClasspath,
                this.mavenSettingsXmlPath);
    }

    static boolean isNimbaProject(ProjectInfo projectInfo) {
        if (projectInfo == null || projectInfo.getGroupId() == null) {
            return false;
        }

        return projectInfo.getGroupId().toLowerCase(Locale.ROOT).contains(NIMBA_MARKER);
    }
}
