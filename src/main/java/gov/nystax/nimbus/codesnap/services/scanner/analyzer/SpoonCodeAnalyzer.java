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
import spoon.reflect.reference.CtTypeReference;
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
        analyzeSourceCode(projectPath, projectInfo, null, ServiceResolutionConfig.STRICT, false, null);
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
    public void analyzeSourceCode(Path projectPath, ProjectInfo projectInfo, ScanContext context,
                                   ServiceResolutionConfig resolutionConfig) throws Exception {
        analyzeSourceCode(projectPath, projectInfo, context, resolutionConfig, false, null);
    }

    public void analyzeSourceCode(Path projectPath, ProjectInfo projectInfo, ScanContext context,
                                  ServiceResolutionConfig resolutionConfig,
                                  boolean resolveMavenClasspath,
                                  Path mavenSettingsXmlPath) throws Exception {
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
            Launcher launcher = SpoonLauncherFactory.createLauncher(
                    projectPath, srcPath, resolveMavenClasspath, mavenSettingsXmlPath);

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
                        results.ctgInvocations.values().stream()
                                .mapToInt(List::size).sum() +
                        results.eventPublisherInvocations.size() +
                        results.legacyGatewayHttpClientInvocations.size();
                context.getMetrics().setInvocationsAnalyzed(totalInvocations);
            }

            // Validate and set service components
            validateAndSetServiceComponents(results, projectInfo, model, resolutionConfig);

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

            // Build CTG usages from invocations
            List<CtgUsage> ctgUsages = buildCtgUsages(results);
            projectInfo.setCtgUsages(ctgUsages);
            logger.info("Found {} CTG usages", ctgUsages.size());

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
     * Builds CtgUsage objects from collected CTG invocations.
     */
    private List<CtgUsage> buildCtgUsages(UnifiedAnalysisVisitor.AnalysisResults results) {
        List<CtgUsage> ctgUsages = new ArrayList<>();

        for (Map.Entry<String, List<CtgInvocation>> entry :
                results.ctgInvocations.entrySet()) {
            String componentId = entry.getKey();
            List<CtgInvocation> invocations = entry.getValue();

            CtgUsage usage = new CtgUsage(componentId);
            usage.setInvocations(invocations);
            ctgUsages.add(usage);
        }

        return ctgUsages;
    }

    /**
     * Validates and sets the service interface and implementation.
     * Supports multiple resolution strategies controlled by flags:
     * - Strict (default): exactly one @SmartService + exactly one @SmartImpl
     * - lenientPairMatch: if a valid impl→interface pair exists among multiple, use it
     * - inferImpl: if only @SmartService found, search all classes for an implementor
     * - inferInterface: if only @SmartImpl found, derive interface from impl's super-interfaces
     *
     * @param results            The results from the unified analysis
     * @param projectInfo        The ProjectInfo to update
     * @param model              The Spoon model (needed for inferImpl to search all classes)
     * @param resolutionConfig   Configuration controlling how service pairs are resolved
     */
    private void validateAndSetServiceComponents(
            UnifiedAnalysisVisitor.AnalysisResults results,
            ProjectInfo projectInfo,
            CtModel model,
            ServiceResolutionConfig resolutionConfig) {

        List<CtInterface<?>> serviceInterfaces = results.serviceInterfaces;
        List<CtClass<?>> serviceImplementations = results.serviceImplementations;

        CtInterface<?> resolvedInterface;
        CtClass<?> resolvedImpl;

        if (!serviceInterfaces.isEmpty() && !serviceImplementations.isEmpty()) {
            // Both annotations found — resolve the pair
            if (serviceInterfaces.size() == 1 && serviceImplementations.size() == 1) {
                resolvedInterface = serviceInterfaces.getFirst();
                resolvedImpl = serviceImplementations.getFirst();
            } else if (resolutionConfig.lenientPairMatch()) {
                Map.Entry<CtInterface<?>, CtClass<?>> pair = resolveLenientPair(serviceInterfaces, serviceImplementations);
                resolvedInterface = pair.getKey();
                resolvedImpl = pair.getValue();
            } else {
                // Strict mode — report the violation
                if (serviceInterfaces.size() > 1) {
                    String found = serviceInterfaces.stream()
                            .map(CtTypeInformation::getQualifiedName)
                            .collect(Collectors.joining(COMMA_SEPARATOR));
                    throw new IllegalStateException(
                            "Expected exactly one interface with @" + AnalyzerConstants.SERVICE_ANNOTATION
                                    + " annotation, but found " + serviceInterfaces.size()
                                    + COLON_SEPARATOR + SPACE_OPEN_PAREN + found + CLOSE_PAREN);
                }
                String found = serviceImplementations.stream()
                        .map(CtTypeInformation::getQualifiedName)
                        .collect(Collectors.joining(COMMA_SEPARATOR));
                throw new IllegalStateException(
                        "Expected exactly one class with @" + AnalyzerConstants.SERVICE_IMPL_ANNOTATION
                                + " annotation, but found " + serviceImplementations.size()
                                + COLON_SEPARATOR + SPACE_OPEN_PAREN + found + CLOSE_PAREN);
            }
        } else if (!serviceInterfaces.isEmpty()) {
            // Only interface(s) found, no impl
            if (!resolutionConfig.inferImpl()) {
                throw new IllegalStateException(
                        "No class found with @" + AnalyzerConstants.SERVICE_IMPL_ANNOTATION + " annotation");
            }
            resolvedInterface = pickSingleInterface(serviceInterfaces, resolutionConfig.lenientPairMatch());
            resolvedImpl = inferImplFromInterface(resolvedInterface, model);
        } else if (!serviceImplementations.isEmpty()) {
            // Only impl(s) found, no interface
            if (!resolutionConfig.inferInterface()) {
                throw new IllegalStateException(
                        "No interface found with @" + AnalyzerConstants.SERVICE_ANNOTATION + " annotation");
            }
            resolvedImpl = pickSingleImpl(serviceImplementations, resolutionConfig.lenientPairMatch());
            resolvedInterface = inferInterfaceFromImpl(resolvedImpl);
        } else {
            throw new IllegalStateException(
                    "No @" + AnalyzerConstants.SERVICE_ANNOTATION + " or @"
                            + AnalyzerConstants.SERVICE_IMPL_ANNOTATION + " annotations found");
        }

        // Validate the resolved pair and apply to projectInfo
        validateImplementation(resolvedInterface, resolvedImpl);
        applyServiceComponents(resolvedInterface, resolvedImpl, projectInfo);
    }

    /**
     * Resolves a valid interface/impl pair from multiple annotated types using lenient matching.
     * Finds impl classes that actually implement one of the annotated interfaces.
     *
     * @return Entry with interface as key and implementation as value
     * @throws IllegalStateException if zero or multiple valid pairs are found
     */
    private Map.Entry<CtInterface<?>, CtClass<?>> resolveLenientPair(
            List<CtInterface<?>> interfaces, List<CtClass<?>> implementations) {

        List<Map.Entry<CtInterface<?>, CtClass<?>>> validPairs = new ArrayList<>();

        for (CtClass<?> impl : implementations) {
            for (CtInterface<?> iface : interfaces) {
                boolean implementsIt = impl.getSuperInterfaces().stream()
                        .anyMatch(ref -> ref.getQualifiedName().equals(iface.getQualifiedName()));
                if (implementsIt) {
                    validPairs.add(Map.entry(iface, impl));
                }
            }
        }

        if (validPairs.isEmpty()) {
            throw new IllegalStateException(
                    "No valid @" + AnalyzerConstants.SERVICE_IMPL_ANNOTATION
                            + " class implements any @" + AnalyzerConstants.SERVICE_ANNOTATION + " interface");
        }
        if (validPairs.size() > 1) {
            String pairs = validPairs.stream()
                    .map(p -> p.getValue().getQualifiedName() + " -> " + p.getKey().getQualifiedName())
                    .collect(Collectors.joining(COMMA_SEPARATOR));
            throw new IllegalStateException(
                    "Ambiguous service resolution: found " + validPairs.size()
                            + " valid pairs" + COLON_SEPARATOR + SPACE_OPEN_PAREN + pairs + CLOSE_PAREN);
        }

        Map.Entry<CtInterface<?>, CtClass<?>> pair = validPairs.getFirst();
        logger.info("Lenient pair match resolved: {} implements {}",
                pair.getValue().getQualifiedName(), pair.getKey().getQualifiedName());
        return pair;
    }

    /**
     * Picks a single interface from the list. If multiple exist and lenientPairMatch is not enabled, throws.
     */
    private CtInterface<?> pickSingleInterface(List<CtInterface<?>> interfaces, boolean lenientPairMatch) {
        if (interfaces.size() == 1) {
            return interfaces.getFirst();
        }
        if (!lenientPairMatch) {
            String found = interfaces.stream()
                    .map(CtTypeInformation::getQualifiedName)
                    .collect(Collectors.joining(COMMA_SEPARATOR));
            throw new IllegalStateException(
                    "Expected exactly one interface with @" + AnalyzerConstants.SERVICE_ANNOTATION
                            + " annotation, but found " + interfaces.size()
                            + COLON_SEPARATOR + SPACE_OPEN_PAREN + found + CLOSE_PAREN);
        }
        // With lenient mode, if multiple @SmartService interfaces exist but we're inferring impl,
        // we cannot determine which one to use
        String found = interfaces.stream()
                .map(CtTypeInformation::getQualifiedName)
                .collect(Collectors.joining(COMMA_SEPARATOR));
        throw new IllegalStateException(
                "Cannot infer implementation: multiple @" + AnalyzerConstants.SERVICE_ANNOTATION
                        + " interfaces found" + COLON_SEPARATOR + SPACE_OPEN_PAREN + found + CLOSE_PAREN);
    }

    /**
     * Picks a single impl from the list. If multiple exist and lenientPairMatch is not enabled, throws.
     */
    private CtClass<?> pickSingleImpl(List<CtClass<?>> implementations, boolean lenientPairMatch) {
        if (implementations.size() == 1) {
            return implementations.getFirst();
        }
        if (!lenientPairMatch) {
            String found = implementations.stream()
                    .map(CtTypeInformation::getQualifiedName)
                    .collect(Collectors.joining(COMMA_SEPARATOR));
            throw new IllegalStateException(
                    "Expected exactly one class with @" + AnalyzerConstants.SERVICE_IMPL_ANNOTATION
                            + " annotation, but found " + implementations.size()
                            + COLON_SEPARATOR + SPACE_OPEN_PAREN + found + CLOSE_PAREN);
        }
        String found = implementations.stream()
                .map(CtTypeInformation::getQualifiedName)
                .collect(Collectors.joining(COMMA_SEPARATOR));
        throw new IllegalStateException(
                "Cannot infer interface: multiple @" + AnalyzerConstants.SERVICE_IMPL_ANNOTATION
                        + " classes found" + COLON_SEPARATOR + SPACE_OPEN_PAREN + found + CLOSE_PAREN);
    }

    /**
     * Infers the implementation class by searching all classes in the model for one
     * that implements the given service interface.
     */
    private CtClass<?> inferImplFromInterface(CtInterface<?> serviceInterface, CtModel model) {
        String interfaceName = serviceInterface.getQualifiedName();
        logger.info("Inferring implementation for @{} interface: {}",
                AnalyzerConstants.SERVICE_ANNOTATION, interfaceName);

        List<CtClass<?>> allClasses = model.getElements(new TypeFilter<>(CtClass.class));
        List<CtClass<?>> candidates = allClasses.stream()
                .filter(cls -> !cls.getModifiers().contains(ModifierKind.ABSTRACT))
                .filter(cls -> implementsInterfaceTransitively(cls, interfaceName, new HashSet<>()))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                    "No class found that implements @" + AnalyzerConstants.SERVICE_ANNOTATION
                            + " interface " + interfaceName);
        }
        if (candidates.size() > 1) {
            String found = candidates.stream()
                    .map(CtTypeInformation::getQualifiedName)
                    .collect(Collectors.joining(COMMA_SEPARATOR));
            throw new IllegalStateException(
                    "Multiple classes implement @" + AnalyzerConstants.SERVICE_ANNOTATION
                            + " interface " + interfaceName
                            + COLON_SEPARATOR + SPACE_OPEN_PAREN + found + CLOSE_PAREN);
        }

        CtClass<?> inferred = candidates.getFirst();
        logger.info("Inferred implementation: {}", inferred.getQualifiedName());
        return inferred;
    }

    private boolean implementsInterfaceTransitively(CtClass<?> candidate, String interfaceName, Set<String> visitedTypes) {
        String candidateName = candidate.getQualifiedName();
        if (candidateName != null && !visitedTypes.add(candidateName)) {
            return false;
        }

        boolean directlyImplements = candidate.getSuperInterfaces().stream()
                .anyMatch(ref -> isOrExtendsInterface(ref, interfaceName, visitedTypes));
        if (directlyImplements) {
            return true;
        }

        CtTypeReference<?> superClassRef = candidate.getSuperclass();
        if (superClassRef == null) {
            return false;
        }

        CtType<?> superType = superClassRef.getTypeDeclaration();
        return superType instanceof CtClass<?> superClass
                && implementsInterfaceTransitively(superClass, interfaceName, visitedTypes);
    }

    private boolean isOrExtendsInterface(CtTypeReference<?> typeRef, String interfaceName, Set<String> visitedTypes) {
        if (typeRef == null) {
            return false;
        }

        String qualifiedName = typeRef.getQualifiedName();
        if (interfaceName.equals(qualifiedName)) {
            return true;
        }
        if (qualifiedName != null && !visitedTypes.add(qualifiedName)) {
            return false;
        }

        CtType<?> typeDeclaration = typeRef.getTypeDeclaration();
        if (!(typeDeclaration instanceof CtInterface<?> interfaceDeclaration)) {
            return false;
        }

        return interfaceDeclaration.getSuperInterfaces().stream()
                .anyMatch(parentRef -> isOrExtendsInterface(parentRef, interfaceName, visitedTypes));
    }

    /**
     * Infers the service interface from the impl class's super-interfaces.
     * Filters to project-local interfaces (those with declarations in the Spoon model).
     */
    private CtInterface<?> inferInterfaceFromImpl(CtClass<?> serviceImpl) {
        String implName = serviceImpl.getQualifiedName();
        logger.info("Inferring interface for @{} class: {}",
                AnalyzerConstants.SERVICE_IMPL_ANNOTATION, implName);

        List<CtInterface<?>> candidates = serviceImpl.getSuperInterfaces().stream()
                .map(ref -> ref.getTypeDeclaration())
                .filter(decl -> decl != null && decl instanceof CtInterface<?>)
                .map(decl -> (CtInterface<?>) decl)
                .filter(iface -> !iface.isShadow() && iface.getPosition().isValidPosition())
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                    "@" + AnalyzerConstants.SERVICE_IMPL_ANNOTATION + " class " + implName
                            + " does not implement any project-local interface");
        }
        if (candidates.size() > 1) {
            String found = candidates.stream()
                    .map(CtTypeInformation::getQualifiedName)
                    .collect(Collectors.joining(COMMA_SEPARATOR));
            throw new IllegalStateException(
                    "@" + AnalyzerConstants.SERVICE_IMPL_ANNOTATION + " class " + implName
                            + " implements multiple project-local interfaces"
                            + COLON_SEPARATOR + SPACE_OPEN_PAREN + found + CLOSE_PAREN);
        }

        CtInterface<?> inferred = candidates.getFirst();
        logger.info("Inferred interface: {}", inferred.getQualifiedName());
        return inferred;
    }

    /**
     * Applies the resolved service interface and implementation to the ProjectInfo.
     * Sets names, detects UI service, and builds method mappings.
     */
    private void applyServiceComponents(CtInterface<?> serviceInterface, CtClass<?> serviceImplementation,
                                        ProjectInfo projectInfo) {
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

            Optional<CtMethod<?>> implMethodOpt = findOverridingMethod(serviceImplementation, interfaceMethod);

            if (implMethodOpt.isPresent()) {
                CtMethod<?> implMethod = implMethodOpt.get();
                String implMethodFQN = getMethodSignature(implMethod);
                methodImplementationMapping.put(interfaceMethodFQN, implMethodFQN);
            } else {
                logger.warn("No implementation for method {} found in {}", interfaceMethodFQN, serviceImplementationName);
            }
            if (isUIService) {
                uiServiceMethodMapping.put(interfaceMethod.getSimpleName(), interfaceMethodFQN);
            }
        }
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

        boolean implementsInterface = implementsInterfaceTransitively(
                serviceImplementation, interfaceName, new HashSet<>());

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
