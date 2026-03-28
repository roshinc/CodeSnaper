package gov.nystax.nimbus.codesnap.services.scanner;

import com.google.common.base.Preconditions;
import gov.nystax.nimbus.codesnap.domain.NimbusServiceMeta;
import gov.nystax.nimbus.codesnap.exception.CodeSnapException;
import gov.nystax.nimbus.codesnap.exception.ScanException;
import gov.nystax.nimbus.codesnap.domain.ProjectSnap;
import gov.nystax.nimbus.codesnap.services.scanner.analyzer.AnalyzerConstants;
import gov.nystax.nimbus.codesnap.services.scanner.analyzer.MavenClasspathConfig;
import gov.nystax.nimbus.codesnap.services.scanner.analyzer.SpoonLauncherFactory;
import gov.nystax.nimbus.codesnap.services.scanner.domain.FunctionInvocation;
import gov.nystax.nimbus.codesnap.services.scanner.domain.FunctionUsage;
import gov.nystax.nimbus.codesnap.services.scanner.domain.ProjectInfo;
import gov.nystax.nimbus.codesnap.services.scanner.observability.ScanContext;
import gov.nystax.nimbus.codesnap.services.scanner.visitor.FunctionOnlyAnalysisVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scanner for projects that do not have SmartService/SmartImpl annotations.
 * Only detects function dependencies from pom.xml and their invocations in code.
 */
public class NimbaProjectScanner implements ProjectScanner {

    private static final Logger logger = LoggerFactory.getLogger(NimbaProjectScanner.class);

    private static final String SRC_MAIN_JAVA = "src/main/java";
    private static final String UNKNOWN_CLASS = "UnknownClass";
    private static final String PERIOD_SEPARATOR = ".";
    private static final String FUNCTION_CLASS_SUFFIX = "Function";
    private static final int DEFAULT_COUNT = 0;

    private final NimbusServiceMeta meta;
    private final ScanContext context;
    private final MavenClasspathConfig mavenConfig;

    public NimbaProjectScanner(NimbusServiceMeta meta, ScanContext context) {
        this(meta, context, MavenClasspathConfig.DISABLED);
    }

    public NimbaProjectScanner(NimbusServiceMeta meta, ScanContext context,
                               MavenClasspathConfig mavenConfig) {
        this.meta = meta;
        this.context = context;
        this.mavenConfig = mavenConfig;
    }

    @Override
    public ProjectSnap scanProject(ProjectInfo projectInfo) {
        Preconditions.checkNotNull(meta, "Project meta cannot be null");
        Preconditions.checkNotNull(context, "Scan context cannot be null");
        Preconditions.checkNotNull(projectInfo, "Project info cannot be null");

        Path projectPath = meta.getLocalServicePomPath().getParent();
        Preconditions.checkArgument(Files.exists(projectPath), "Project path does not exist: " + projectPath);

        logger.info("Scanning Nimba project at: {}", projectPath);

        try {
            // Analyze source code for function invocations only
            context.phaseStart("Code Analysis", "Analyzing source code for function invocations");
            Instant codeStart = Instant.now();

            analyzeSourceCode(projectPath, projectInfo);

            Duration codeDuration = Duration.between(codeStart, Instant.now());
            context.getMetrics().setCodeAnalysisDuration(codeDuration);
            context.getMetrics().setTypesAnalyzed(projectInfo.getClassCount());
            context.getMetrics().setMethodsAnalyzed(projectInfo.getMethodCount());

            context.phaseComplete("Code Analysis", true);
        } catch (CodeSnapException e) {
            context.error("Code Analysis", e);
            throw e;
        } catch (Exception e) {
            logger.error("Error analyzing source code", e);
            context.error("Code Analysis", e);
            throw new ScanException("Error analyzing source code", e);
        }

        logger.info("Scan metrics: {}", context.getMetrics().getSummary());
        return projectInfo;
    }

    private void analyzeSourceCode(Path projectPath, ProjectInfo projectInfo) throws Exception {
        Path srcPath = projectPath.resolve(SRC_MAIN_JAVA);
        if (!Files.exists(srcPath)) {
            logger.warn("Source directory not found: {}", srcPath);
            projectInfo.setClassCount(DEFAULT_COUNT);
            projectInfo.setMethodCount(DEFAULT_COUNT);
            return;
        }

        logger.info("Analyzing source code at: {}", srcPath);

        // Build the Spoon model
        Launcher launcher = SpoonLauncherFactory.createLauncher(
                projectPath, srcPath, mavenConfig);

        CtModel model = launcher.buildModel();

        // Pre-compute method-to-callers map for call chain building
        Map<String, List<CtMethod<?>>> methodToCallers = precomputeMethodCallers(model);

        // Create and run the function-only visitor
        FunctionOnlyAnalysisVisitor visitor = new FunctionOnlyAnalysisVisitor(
                projectInfo.getFunctionDependencies());
        visitor.setMethodToCallers(methodToCallers);

        model.getRootPackage().accept(visitor);

        // Extract results
        FunctionOnlyAnalysisVisitor.FunctionAnalysisResults results = visitor.getResults();

        projectInfo.setClassCount(results.typeCount);
        projectInfo.setMethodCount(results.methodCount);

        logger.info("Found {} classes and {} methods", results.typeCount, results.methodCount);

        // Track invocations analyzed
        int totalInvocations = results.functionInvocations.values().stream()
                .mapToInt(List::size).sum();
        context.getMetrics().setInvocationsAnalyzed(totalInvocations);

        // Build function usages from invocations
        List<FunctionUsage> functionUsages = buildFunctionUsages(results);
        projectInfo.setFunctionUsages(functionUsages);
        logger.info("Found {} function usages", functionUsages.size());
    }

    private List<FunctionUsage> buildFunctionUsages(FunctionOnlyAnalysisVisitor.FunctionAnalysisResults results) {
        List<FunctionUsage> functionUsages = new ArrayList<>();

        for (Map.Entry<String, List<FunctionInvocation>> entry : results.functionInvocations.entrySet()) {
            String functionId = entry.getKey();
            List<FunctionInvocation> invocations = entry.getValue();

            String dependency = results.functionIdToDependency.get(functionId);
            String functionClass = AnalyzerConstants.FUNCTION_PACKAGE_PREFIX + PERIOD_SEPARATOR
                    + functionId + FUNCTION_CLASS_SUFFIX;

            FunctionUsage usage = new FunctionUsage(functionId, functionClass, dependency);
            usage.setInvocations(invocations);
            functionUsages.add(usage);
        }

        return functionUsages;
    }

    private Map<String, List<CtMethod<?>>> precomputeMethodCallers(CtModel model) {
        Map<String, List<CtMethod<?>>> methodToCallers = new HashMap<>();

        List<CtMethod<?>> allMethods = model.getElements(new TypeFilter<>(CtMethod.class));

        for (CtMethod<?> method : allMethods) {
            methodToCallers.put(getMethodSignature(method), new ArrayList<>());
        }

        for (CtMethod<?> method : allMethods) {
            List<CtInvocation<?>> invocations = method.getElements(new TypeFilter<>(CtInvocation.class));
            for (CtInvocation<?> invocation : invocations) {
                CtExecutableReference<?> execRef = invocation.getExecutable();
                if (execRef != null) {
                    String invokedMethodSig = getMethodSignatureFromReference(execRef);
                    methodToCallers.computeIfAbsent(invokedMethodSig, k -> new ArrayList<>()).add(method);
                }
            }
        }

        return methodToCallers;
    }

    private String getMethodSignature(CtMethod<?> method) {
        String className = method.getDeclaringType() != null
                ? method.getDeclaringType().getQualifiedName()
                : UNKNOWN_CLASS;
        return className + PERIOD_SEPARATOR + method.getSignature();
    }

    private String getMethodSignatureFromReference(CtExecutableReference<?> execRef) {
        String className = execRef.getDeclaringType() != null
                ? execRef.getDeclaringType().getQualifiedName()
                : UNKNOWN_CLASS;
        return className + PERIOD_SEPARATOR + execRef.getSignature();
    }
}
