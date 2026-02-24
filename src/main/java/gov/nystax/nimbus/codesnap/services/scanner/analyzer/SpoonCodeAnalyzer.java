package gov.nystax.nimbus.codesnap.services.scanner.analyzer;

import com.google.common.base.Preconditions;
import gov.nystax.nimbus.codesnap.services.scanner.domain.*;
import gov.nystax.nimbus.codesnap.services.scanner.observability.ScanContext;
import gov.nystax.nimbus.codesnap.services.scanner.visitor.UnifiedAnalysisVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for analyzing Java source code using Spoon.
 */
public class SpoonCodeAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(SpoonCodeAnalyzer.class);

    // File and path constants
    private static final String SRC_MAIN_JAVA = "src/main/java";
    private static final String UNKNOWN_CLASS = "UnknownClass";

    // Class name suffix
    private static final String FUNCTION_CLASS_SUFFIX = "Function";

    // Formatting
    private static final String COMMA_SEPARATOR = ", ";
    private static final String COLON_SEPARATOR = ": ";
    private static final String PERIOD_SEPARATOR = ".";
    private static final String SPACE_OPEN_PAREN = " (";
    private static final String CLOSE_PAREN = ")";

    // Default values
    private static final int DEFAULT_COUNT = 0;

    /**
     * Analyzes Java source code and updates the ProjectInfo with statistics.
     *
     * @param projectPath The path to the Maven project
     * @param projectInfo The ProjectInfo to update with analysis results
     * @throws Exception if there's an error during analysis
     */
    public void analyzeSourceCode(Path projectPath, ProjectInfo projectInfo) throws Exception {
        analyzeSourceCode(projectPath, projectInfo, null);
    }

    /**
     * Analyzes Java source code and updates the ProjectInfo with statistics.
     * <p>
     * Reports progress through the provided ScanContext.
     *
     * @param projectPath The path to the Maven project
     * @param projectInfo The ProjectInfo to update with analysis results
     * @param context     The scan context for progress reporting (optional)
     * @throws Exception if there's an error during analysis
     */
    public void analyzeSourceCode(Path projectPath, ProjectInfo projectInfo, ScanContext context) throws Exception {
        Preconditions.checkNotNull(projectPath, "Project path cannot be null");
        Preconditions.checkNotNull(projectInfo, "ProjectInfo cannot be null");

        Path srcPath = projectPath.resolve(SRC_MAIN_JAVA);
        if (!Files.exists(srcPath)) {
            logger.warn("Source directory not found: {}", srcPath);
            projectInfo.setClassCount(DEFAULT_COUNT);
            projectInfo.setMethodCount(DEFAULT_COUNT);
            return;
        }

        logger.info("Analyzing source code at: {}", srcPath);

        if (context != null) {
            context.progress("Code Analysis", 1, 5, "Building Spoon model");
        }

        try {
            // Build the Spoon model
            Launcher launcher = new Launcher();
            launcher.addInputResource(srcPath.toString());
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(false);

            if (context != null) {
                context.progress("Code Analysis", 2, 5, "Parsing Java files");
            }

            CtModel model = launcher.buildModel();

            if (context != null) {
                context.progress("Code Analysis", 3, 5, "Pre-computing method call relationships");
            }

            // Pre-compute method-to-callers map for call chain building
            Map<String, List<CtMethod<?>>> methodToCallers = precomputeMethodCallers(model);

            if (context != null) {
                context.progress("Code Analysis", 4, 5, "Running unified visitor analysis");
            }

            // Create and configure the unified visitor with dependencies
            UnifiedAnalysisVisitor visitor = new UnifiedAnalysisVisitor(
                    projectInfo.getFunctionDependencies(),
                    projectInfo.getServiceDependencies()
            );
            visitor.setMethodToCallers(methodToCallers);

            // Run the single-pass analysis, use model's root package for full traversal
            model.getRootPackage().accept(visitor);

            // Extract results from visitor
            UnifiedAnalysisVisitor.AnalysisResults results = visitor.getResults();

            // Set type and method counts
            projectInfo.setClassCount(results.typeCount);
            projectInfo.setMethodCount(results.methodCount);

            logger.info("Found {} classes and {} methods", results.typeCount, results.methodCount);

            if (context != null) {
                context.progress("Code Analysis", 5, 5, "Processing analysis results");
                // Track invocations analyzed
                int totalInvocations = results.functionInvocations.values().stream()
                        .mapToInt(List::size).sum() +
                        results.serviceInvocations.values().stream()
                                .mapToInt(List::size).sum() +
                        results.eventPublisherInvocations.size() +
                        results.legacyGatewayHttpClientInvocations.size();
                context.getMetrics().setInvocationsAnalyzed(totalInvocations);
            }

            // Validate and set service components
            validateAndSetServiceComponents(results, projectInfo);

            // Set function mappings (always set, empty map if no mappings)
            projectInfo.setFunctionMappings(results.functionMappings);

            // Build function usages from invocations
            List<FunctionUsage> functionUsages = buildFunctionUsages(results);
            projectInfo.setFunctionUsages(functionUsages);
            logger.info("Found {} function usages", functionUsages.size());

            // Build service usages from invocations
            List<ServiceUsage> serviceUsages = buildServiceUsages(results);
            projectInfo.setServiceUsages(serviceUsages);
            logger.info("Found {} service usages", serviceUsages.size());


            // Set event publisher invocations
            projectInfo.setEventPublisherInvocations(results.eventPublisherInvocations);
            logger.info("Found {} IEventPublisher.publishEvent() usage", results.eventPublisherInvocations.size());

            // set legacy gateway http client invocations
            projectInfo.setLegacyGatewayHttpClientInvocations(results.legacyGatewayHttpClientInvocations);
            logger.info("Found {} LegacyGatewayHttpClient.post() usage", results.legacyGatewayHttpClientInvocations.size());

        } catch (IllegalStateException e) {
            // Re-throw validation exceptions for service annotations
            throw e;
        } catch (Exception e) {
            logger.error("Error analyzing source code", e);
            // Set counts to 0 if analysis fails
            projectInfo.setClassCount(DEFAULT_COUNT);
            projectInfo.setMethodCount(DEFAULT_COUNT);
            throw e;
        }
    }


    /**
     * Pre-computes the caller relationships for all methods.
     *
     * @param model The Spoon model
     * @return Map from method signature to list of methods that call it
     */
    private Map<String, List<CtMethod<?>>> precomputeMethodCallers(CtModel model) {
        Map<String, List<CtMethod<?>>> methodToCallers = new HashMap<>();

        // Get all methods
        List<CtMethod<?>> allMethods = model.getElements(new TypeFilter<>(CtMethod.class));

        // Initialize empty lists for all methods
        for (CtMethod<?> method : allMethods) {
            methodToCallers.put(getMethodSignature(method), new ArrayList<>());
        }

        // Build the caller relationships
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

    /**
     * Gets the method signature as a string.
     */
    private String getMethodSignature(CtMethod<?> method) {
        String className = method.getDeclaringType() != null
                ? method.getDeclaringType().getQualifiedName()
                : UNKNOWN_CLASS;
        return className + PERIOD_SEPARATOR + method.getSignature();
    }

    /**
     * Gets method signature from an executable reference.
     */
    private String getMethodSignatureFromReference(CtExecutableReference<?> execRef) {
        String className = execRef.getDeclaringType() != null
                ? execRef.getDeclaringType().getQualifiedName()
                : UNKNOWN_CLASS;
        return className + PERIOD_SEPARATOR + execRef.getSignature();
    }

    /**
     * Builds FunctionUsage objects from collected invocations.
     */
    private List<FunctionUsage> buildFunctionUsages(UnifiedAnalysisVisitor.AnalysisResults results) {
        List<FunctionUsage> functionUsages = new ArrayList<>();

        for (Map.Entry<String, List<FunctionInvocation>> entry :
                results.functionInvocations.entrySet()) {
            String functionId = entry.getKey();
            List<FunctionInvocation> invocations = entry.getValue();

            String dependency = results.functionIdToDependency.get(functionId);
            String functionClass = AnalyzerConstants.FUNCTION_PACKAGE_PREFIX + PERIOD_SEPARATOR + functionId + FUNCTION_CLASS_SUFFIX;

            FunctionUsage usage = new FunctionUsage(functionId, functionClass, dependency);
            usage.setInvocations(invocations);
            functionUsages.add(usage);
        }

        return functionUsages;
    }

    /**
     * Builds ServiceUsage objects from collected invocations.
     */
    private List<ServiceUsage> buildServiceUsages(UnifiedAnalysisVisitor.AnalysisResults results) {
        List<ServiceUsage> serviceUsages = new ArrayList<>();

        for (Map.Entry<String, List<ServiceInvocation>> entry :
                results.serviceInvocations.entrySet()) {
            String serviceId = entry.getKey();
            List<ServiceInvocation> invocations = entry.getValue();

            String dependency = results.serviceIdToDependency.get(serviceId);
            String servicePackage = AnalyzerConstants.FUNCTION_PACKAGE_PREFIX + PERIOD_SEPARATOR + serviceId;

            ServiceUsage usage = new ServiceUsage(serviceId, servicePackage, dependency);
            usage.setInvocations(invocations);
            serviceUsages.add(usage);
        }

        return serviceUsages;
    }


    /**
     * Validates and sets the service interface and implementation.
     * Validates that:
     * - Exactly one service interface exists
     * - Exactly one service implementation exists
     * - The implementation actually implements the interface
     *
     * @param results     The results from the unified analysis
     * @param projectInfo The ProjectInfo to update
     */
    private void validateAndSetServiceComponents(
            UnifiedAnalysisVisitor.AnalysisResults results,
            ProjectInfo projectInfo) {

        List<CtInterface<?>> serviceInterfaces = results.serviceInterfaces;
        List<CtClass<?>> serviceImplementations = results.serviceImplementations;

        // Validate exactly one service interface
        if (serviceInterfaces.isEmpty()) {
            throw new IllegalStateException("No interface found with @" + AnalyzerConstants.SERVICE_ANNOTATION + " annotation");
        }
        if (serviceInterfaces.size() > 1) {
            String foundInterfaces = serviceInterfaces.stream()
                    .map(CtTypeInformation::getQualifiedName)
                    .collect(Collectors.joining(COMMA_SEPARATOR));
            throw new IllegalStateException(
                    "Expected exactly one interface with @" + AnalyzerConstants.SERVICE_ANNOTATION + " annotation, but found "
                            + serviceInterfaces.size() + COLON_SEPARATOR + SPACE_OPEN_PAREN + foundInterfaces + CLOSE_PAREN);
        }

        // Validate exactly one service implementation
        if (serviceImplementations.isEmpty()) {
            throw new IllegalStateException("No class found with @" + AnalyzerConstants.SERVICE_IMPL_ANNOTATION + " annotation");
        }
        if (serviceImplementations.size() > 1) {
            String foundImplementations = serviceImplementations.stream()
                    .map(CtTypeInformation::getQualifiedName)
                    .collect(Collectors.joining(COMMA_SEPARATOR));
            throw new IllegalStateException(
                    "Expected exactly one class with @" + AnalyzerConstants.SERVICE_IMPL_ANNOTATION + " annotation, but found "
                            + serviceImplementations.size() + COLON_SEPARATOR + SPACE_OPEN_PAREN + foundImplementations + CLOSE_PAREN);
        }

        CtInterface<?> serviceInterface = serviceInterfaces.getFirst();
        CtClass<?> serviceImplementation = serviceImplementations.getFirst();

        // Validate that the implementation actually implements the interface
        validateImplementation(serviceInterface, serviceImplementation);

        // Set the found service interface and implementation
        String serviceInterfaceName = serviceInterface.getQualifiedName();
        String serviceImplementationName = serviceImplementation.getQualifiedName();

        projectInfo.setServiceInterface(serviceInterfaceName);
        projectInfo.setServiceImplementation(serviceImplementationName);

        logger.info("Found service interface: {} and implementation: {}",
                serviceInterfaceName, serviceImplementationName);

        // Detect if this is a UI service
        boolean isUIService = hasAnnotation(serviceInterface, AnalyzerConstants.UI_SERVICE_ANNOTATION);
        projectInfo.setUIService(isUIService);

        if (isUIService) {
            logger.info("Service is a UI service");
        }

        // Create mapping of interface methods to implementation methods
        Map<String, String> methodImplementationMapping = new LinkedHashMap<>();
        Map<String, String> uiServiceMethodMapping = new LinkedHashMap<>();
        for (CtMethod<?> interfaceMethod : serviceInterface.getMethods()) {
            String interfaceMethodFQN = getMethodSignature(interfaceMethod);

            // Find the overriding method in the implementation by matching the signature
            Optional<CtMethod<?>> implMethodOpt = findOverridingMethod(serviceImplementation, interfaceMethod);

            if (implMethodOpt.isPresent()) {
                CtMethod<?> implMethod = implMethodOpt.get();
                String implMethodFQN = getMethodSignature(implMethod);
                methodImplementationMapping.put(interfaceMethodFQN, implMethodFQN);
//                logger.debug("Found method implementation: {} -> {}", interfaceMethodFQN, implMethodFQN);
            } else {
                logger.warn("No implementation for method {} found in {}", interfaceMethodFQN, serviceImplementationName);
            }
            if (isUIService) {
                // Add a UI service name to the service interface method mapping
                uiServiceMethodMapping.put(interfaceMethod.getSimpleName(), interfaceMethodFQN);
            }
        }
        // Set implementation mappings (always set, empty map if no mappings)
        projectInfo.setMethodImplementationMappings(methodImplementationMapping);
        projectInfo.setUIServiceMethodMappings(uiServiceMethodMapping);


    }

    /**
     * Validates that the service implementation actually implements the service interface.
     *
     * @param serviceInterface      The service interface
     * @param serviceImplementation The service implementation
     * @throws IllegalStateException if the implementation does not implement the interface
     */
    private void validateImplementation(CtInterface<?> serviceInterface, CtClass<?> serviceImplementation) {
        String interfaceName = serviceInterface.getQualifiedName();
        String implementationName = serviceImplementation.getQualifiedName();

        // Check if the class directly implements the interface
        boolean implementsInterface = serviceImplementation.getSuperInterfaces().stream()
                .anyMatch(iface -> iface.getQualifiedName().equals(interfaceName));

        if (!implementsInterface) {
            throw new IllegalStateException(
                    "Service implementation " + implementationName +
                            " does not implement service interface " + interfaceName);
        }

        logger.info("Validated that {} implements {}", implementationName, interfaceName);
    }

    /**
     * Finds the overriding method in the implementation class that matches the interface method.
     *
     * @param implementation  The implementation class
     * @param interfaceMethod The interface method to find
     * @return Optional containing the overriding method, or empty if not found
     */
    private Optional<CtMethod<?>> findOverridingMethod(CtClass<?> implementation, CtMethod<?> interfaceMethod) {
        return implementation.getMethods().stream()
                .filter(implMethod -> isOverriding(implMethod, interfaceMethod))
                .findFirst();
    }

    /**
     * Checks if the implementation method overrides the interface method.
     *
     * @param implMethod      The implementation method
     * @param interfaceMethod The interface method
     * @return true if implMethod overrides interfaceMethod
     */
    private boolean isOverriding(CtMethod<?> implMethod, CtMethod<?> interfaceMethod) {
        // Check method name matches
        if (!implMethod.getSimpleName().equals(interfaceMethod.getSimpleName())) {
            return false;
        }

        // Check parameter count matches
        List<CtParameter<?>> implParams = implMethod.getParameters();
        List<CtParameter<?>> ifaceParams = interfaceMethod.getParameters();

        if (implParams.size() != ifaceParams.size()) {
            return false;
        }

        // Check parameter types match
        for (int i = 0; i < implParams.size(); i++) {
            String implParamType = implParams.get(i).getType().getQualifiedName();
            String ifaceParamType = ifaceParams.get(i).getType().getQualifiedName();
            if (!implParamType.equals(ifaceParamType)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if a type has a specific annotation.
     *
     * @param type           The type to check
     * @param annotationName The fully qualified annotation name
     * @return true if the type has the annotation
     */
    private boolean hasAnnotation(CtInterface<?> type, String annotationName) {
        return type.getAnnotations().stream()
                .anyMatch(annotation -> {
                    String qualifiedName = annotation.getAnnotationType().getQualifiedName();
                    return qualifiedName.equals(annotationName);
                });
    }
}
