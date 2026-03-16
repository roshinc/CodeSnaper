package gov.nystax.nimbus.codesnap.services.scanner;

import com.google.common.base.Preconditions;
import gov.nystax.nimbus.codesnap.domain.CodeSnapperConfig;
import gov.nystax.nimbus.codesnap.domain.NimbusServiceMeta;
import gov.nystax.nimbus.codesnap.domain.ProjectSnap;
import gov.nystax.nimbus.codesnap.services.scanner.analyzer.MavenDependencyResolver;
import gov.nystax.nimbus.codesnap.services.scanner.analyzer.MavenProjectAnalyzer;
import gov.nystax.nimbus.codesnap.services.scanner.analyzer.SpoonCodeAnalyzer;
import gov.nystax.nimbus.codesnap.services.scanner.domain.ProjectInfo;
import gov.nystax.nimbus.codesnap.services.scanner.observability.ScanContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class NimbusServiceProjectScanner {

    private static final Logger logger = LoggerFactory.getLogger(NimbusServiceProjectScanner.class);
    private final NimbusServiceMeta meta;
    private final ScanContext context;
    private final CodeSnapperConfig config;


    public NimbusServiceProjectScanner(NimbusServiceMeta meta, ScanContext context) {
        this(meta, context, null);
    }

    public NimbusServiceProjectScanner(NimbusServiceMeta meta, ScanContext context, CodeSnapperConfig config) {
        this.meta = meta;
        this.context = context;
        this.config = config;
    }

    public ProjectSnap scanProject() {
        Preconditions.checkNotNull(meta, "Project meta cannot be null");
        Preconditions.checkNotNull(context, "Scan context cannot be null");

        Path projectPath = meta.getLocalServicePomPath().getParent();
        Preconditions.checkArgument(Files.exists(projectPath), "Project path does not exist: " + projectPath);

        logger.info("Scanning Maven project at: {}", projectPath);

        ProjectInfo projectInfo = null;

        try {
            // Analyze Maven project structure and POM
            context.phaseStart("Maven Analysis", "Analyzing pom.xml");
            Instant pomStart = Instant.now();
            MavenProjectAnalyzer mavenAnalyzer = new MavenProjectAnalyzer();
            projectInfo = mavenAnalyzer.analyzeProject(projectPath);

            // Update complete metrics
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
        // Optionally resolve Maven dependencies for classpath
        List<Path> classpathJars = List.of();
        if (config != null && config.useClasspath()) {
            try {
                context.phaseStart("Dependency Resolution", "Downloading Maven dependency JARs");
                Instant depStart = Instant.now();

                MavenDependencyResolver resolver = new MavenDependencyResolver(
                        config.mavenHome(), config.mavenSettingsFile(), config.mavenRepositories());
                classpathJars = resolver.resolveAndDownload(projectPath);

                Duration depDuration = Duration.between(depStart, Instant.now());
                logger.info("Dependency resolution took {} ms, resolved {} JARs",
                        depDuration.toMillis(), classpathJars.size());
                context.phaseComplete("Dependency Resolution", true);
            } catch (Exception e) {
                logger.warn("Dependency resolution failed, falling back to no-classpath mode", e);
                context.phaseComplete("Dependency Resolution", false);
                classpathJars = List.of();
            }
        }

        try {
            // Analyze source code with Spoon
            context.phaseStart("Code Analysis", "Analyzing source code with Spoon");
            Instant codeStart = Instant.now();

            SpoonCodeAnalyzer spoonAnalyzer = new SpoonCodeAnalyzer();
            spoonAnalyzer.setClasspathJars(classpathJars);
            spoonAnalyzer.analyzeSourceCode(projectPath, projectInfo, context);

            // Update complete metrics
            Duration codeDuration = Duration.between(codeStart, Instant.now());
            context.getMetrics().setCodeAnalysisDuration(codeDuration);
            context.getMetrics().setTypesAnalyzed(projectInfo.getClassCount());
            context.getMetrics().setMethodsAnalyzed(projectInfo.getMethodCount());

            context.phaseComplete("Code Analysis", true);
        } catch (Exception e) {
            logger.error("Error analyzing source code", e);
            context.error("Code Analysis", e);
            throw new RuntimeException("Error analyzing source code", e);
        }
        //logger.info("Scan completed: {}", projectInfo);
        logger.info("Scan metrics: {}", context.getMetrics().getSummary());
        return projectInfo;
    }
}
