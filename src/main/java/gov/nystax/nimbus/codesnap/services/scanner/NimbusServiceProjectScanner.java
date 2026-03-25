package gov.nystax.nimbus.codesnap.services.scanner;

import com.google.common.base.Preconditions;
import gov.nystax.nimbus.codesnap.domain.NimbusServiceMeta;
import gov.nystax.nimbus.codesnap.domain.ProjectSnap;
import gov.nystax.nimbus.codesnap.services.scanner.analyzer.ServiceResolutionConfig;
import gov.nystax.nimbus.codesnap.services.scanner.analyzer.SpoonCodeAnalyzer;
import gov.nystax.nimbus.codesnap.services.scanner.domain.ProjectInfo;
import gov.nystax.nimbus.codesnap.services.scanner.observability.ScanContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public class NimbusServiceProjectScanner implements ProjectScanner {

    private static final Logger logger = LoggerFactory.getLogger(NimbusServiceProjectScanner.class);
    private final NimbusServiceMeta meta;
    private final ScanContext context;
    private final ServiceResolutionConfig resolutionConfig;

    public NimbusServiceProjectScanner(NimbusServiceMeta meta, ScanContext context) {
        this(meta, context, ServiceResolutionConfig.STRICT);
    }

    public NimbusServiceProjectScanner(NimbusServiceMeta meta, ScanContext context,
                                       ServiceResolutionConfig resolutionConfig) {
        this.meta = meta;
        this.context = context;
        this.resolutionConfig = resolutionConfig;
    }

    @Override
    public ProjectSnap scanProject(ProjectInfo projectInfo) {
        Preconditions.checkNotNull(meta, "Project meta cannot be null");
        Preconditions.checkNotNull(context, "Scan context cannot be null");
        Preconditions.checkNotNull(projectInfo, "Project info cannot be null");

        Path projectPath = meta.getLocalServicePomPath().getParent();
        Preconditions.checkArgument(Files.exists(projectPath), "Project path does not exist: " + projectPath);

        logger.info("Scanning Nimbus service project at: {}", projectPath);

        try {
            // Analyze source code with Spoon
            context.phaseStart("Code Analysis", "Analyzing source code with Spoon");
            Instant codeStart = Instant.now();

            SpoonCodeAnalyzer spoonAnalyzer = new SpoonCodeAnalyzer();
            spoonAnalyzer.analyzeSourceCode(projectPath, projectInfo, context, resolutionConfig);

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
