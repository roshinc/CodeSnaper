package gov.nystax.nimbus.codesnap.services.scanner.visitor;


import com.google.common.base.Strings;
import gov.nystax.nimbus.codesnap.services.scanner.analyzer.AnalyzerConstants;
import gov.nystax.nimbus.codesnap.services.scanner.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unified visitor that performs all code analysis in a single AST traversal.
 * This visitor replaces multiple separate passes with one efficient traversal.
 * <p>
 * Collects:
 * - Type and method counts
 * - Service interfaces and implementations
 * - Function mappings from @Function annotations
 * - Function dependency invocations
 * - Service dependency invocations
 * - Event publisher invocations
 */
public class UnifiedAnalysisVisitor extends CtScanner {

    private static final Logger logger = LoggerFactory.getLogger(UnifiedAnalysisVisitor.class);

    // ============================================================================
    // Constants - Function Analysis
    // ============================================================================
    private static final String FUNCTION_CLASS_SUFFIX = "Function";
    private static final Pattern ARTIFACT_PATTERN = Pattern.compile("(.+)-func-client$");
    private static final String METHOD_EXECUTE = "execute";
    private static final String METHOD_EXECUTE_ON_OR_AFTER = "executeOnOrAfter";
    private static final String METHOD_EXECUTE_ASYNC = "executeAsync";
    private static final String METHOD_INSTANCE = "instance";

    // ============================================================================
    // Constants - Event Publisher Analysis
    // ============================================================================
    private static final String PUBLISH_EVENT_METHOD = "publishEvent";
    private static final String RESOLUTION_RESOLVED = "RESOLVED";
    private static final String RESOLUTION_UNKNOWN_VARIABLE = "UNKNOWN_VARIABLE";
    private static final String RESOLUTION_UNKNOWN_COMPLEX = "UNKNOWN_COMPLEX";

    // ============================================================================
    // Constants - Legacy Gateway Http Client (SMART) Analysis
    // ============================================================================
    private static final String POST_LGHC_METHOD = "post";

    // ============================================================================
    // Constants - Common
    // ============================================================================
    private static final String UNKNOWN_CLASS = "UnknownClass";
    private static final String UNKNOWN = "unknown";
    private static final String COLON_SEPARATOR = ":";
    private static final String PERIOD_SEPARATOR = ".";
    private static final String DOUBLE_QUOTE = "\"";
    private static final String EMPTY_STRING = "";
    private static final String SPACE_OPEN_PAREN = " (";
    private static final String CLOSE_PAREN = ")";
    private static final String FUNCTION_ANNOTATION_VALUE_ATTRIBUTE = "id";
    private static final int MIN_DEPENDENCY_PARTS = 2;
    private static final int DEPENDENCY_ARTIFACT_INDEX = 1;
    private static final int DEPENDENCY_SERVICE_ID_INDEX = 1;

    // Map keys for event publisher
    private static final String KEY_INVOCATION_SITE = "invocationSite";
    private static final String KEY_ENCLOSING_METHOD = "enclosingMethod";
    private static final String KEY_TOPIC = "topic";
    private static final String KEY_TOPIC_RESOLUTION = "topicResolution";
    private static final String KEY_CALL_CHAIN = "callChain";
    private static final String KEY_RESOLUTION = "resolution";

    // ============================================================================
    // Analysis Results
    // ============================================================================
    private final AnalysisResults results = new AnalysisResults();

    // Input: Dependencies to analyze
    private final List<String> functionDependencies;
    private final List<String> serviceDependencies;

    // Caching: Function and service classes to look for
    private final Set<String> functionClassNames = new HashSet<>();
    private final Map<String, String> functionClassToId = new HashMap<>();
    private final Map<String, String> servicePackages = new HashMap<>();

    // Call chain building: All methods and their callers (populated after first pass)
    private Map<String, List<CtMethod<?>>> methodToCallers = null;

    /**
     * Creates a visitor with the dependencies to analyze.
     *
     * @param functionDependencies List of function dependencies
     * @param serviceDependencies  List of service dependencies
     */
    public UnifiedAnalysisVisitor(List<String> functionDependencies, List<String> serviceDependencies) {
        this.functionDependencies = functionDependencies != null ? functionDependencies : Collections.emptyList();
        this.serviceDependencies = serviceDependencies != null ? serviceDependencies : Collections.emptyList();

        // Pre-compute function classes to search for
        buildFunctionClassMappings();
        buildServicePackageMappings();
    }

    /**
     * Gets the analysis results after visiting the AST.
     *
     * @return The collected analysis results
     */
    public AnalysisResults getResults() {
        return results;
    }

    /**
     * Sets the method-to-callers map for call chain building.
     * This must be called before analyzing invocations.
     *
     * @param methodToCallers Map from method signature to list of methods that call it
     */
    public void setMethodToCallers(Map<String, List<CtMethod<?>>> methodToCallers) {
        this.methodToCallers = methodToCallers;
    }

    // ============================================================================
    // Visit Methods - Entry Points
    // ============================================================================

    @Override
    public <T> void visitCtInterface(CtInterface<T> ctInterface) {
        results.typeCount++;

        // Check for @Service annotation
        if (hasAnnotation(ctInterface, AnalyzerConstants.SERVICE_ANNOTATION)) {
            results.serviceInterfaces.add(ctInterface);
        }

        super.visitCtInterface(ctInterface);
    }

    @Override
    public <T> void visitCtClass(CtClass<T> ctClass) {
        results.typeCount++;

        // Check for @ServiceImpl annotation
        if (hasAnnotation(ctClass, AnalyzerConstants.SERVICE_IMPL_ANNOTATION)) {
            results.serviceImplementations.add(ctClass);
        }

        super.visitCtClass(ctClass);
    }

    @Override
    public <A extends java.lang.annotation.Annotation> void visitCtAnnotationType(
            CtAnnotationType<A> annotationType) {
        results.typeCount++;
        super.visitCtAnnotationType(annotationType);
    }

    @Override
    public <T> void visitCtMethod(CtMethod<T> method) {
        results.methodCount++;

        // Check for @Function annotation (only if we have a service implementation)
        checkForFunctionAnnotation(method);

        super.visitCtMethod(method);
    }

    @Override
    public <T> void visitCtInvocation(CtInvocation<T> invocation) {
        // Categorize and analyze this invocation
        analyzeInvocation(invocation);

        super.visitCtInvocation(invocation);
    }

    // ============================================================================
    // Initialization - Pre-compute Lookups
    // ============================================================================

    /**
     * Builds mappings from artifact IDs to function class names.
     */
    private void buildFunctionClassMappings() {
        for (String dependency : functionDependencies) {
            String[] parts = dependency.split(COLON_SEPARATOR);
            if (parts.length < MIN_DEPENDENCY_PARTS) {
                continue;
            }

            String artifactId = parts[DEPENDENCY_ARTIFACT_INDEX];
            String functionId = extractFunctionId(artifactId);
            if (functionId == null) {
                continue;
            }

            String functionClass = AnalyzerConstants.FUNCTION_PACKAGE_PREFIX + PERIOD_SEPARATOR + functionId + FUNCTION_CLASS_SUFFIX;
            // Normalize the case by making them all lower
            functionClassNames.add(functionClass.toLowerCase());
            functionClassToId.put(functionClass.toLowerCase(), functionId.toLowerCase());

            // Store the full dependency string for this function ID
            results.functionIdToDependency.put(functionId.toLowerCase(), dependency);
        }
    }

    /**
     * Builds mappings from service IDs to package prefixes.
     */
    private void buildServicePackageMappings() {
        for (String dependency : serviceDependencies) {
            String[] parts = dependency.split(COLON_SEPARATOR);
            if (parts.length < MIN_DEPENDENCY_PARTS) {
                continue;
            }

            // The service id retrieved here is always in uppercase
            String serviceId = parts[DEPENDENCY_SERVICE_ID_INDEX];
            // For the package the service id needs to lowercased
            String servicePackage = AnalyzerConstants.SERVICE_PACKAGE_PREFIX + PERIOD_SEPARATOR
                    + serviceId.toLowerCase(Locale.ROOT);
            servicePackages.put(servicePackage, serviceId);

            // Store the full dependency string for this service ID
            results.serviceIdToDependency.put(serviceId, dependency);
        }
    }

    /**
     * Extracts the function ID from the artifact ID.
     * E.g., "RetrieveWTPendFiling-func-client" -> "RetrieveWTPendFiling"
     */
    private String extractFunctionId(String artifactId) {
        Matcher matcher = ARTIFACT_PATTERN.matcher(artifactId);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    // ============================================================================
    // Service & Function Annotation Detection
    // ============================================================================

    /**
     * Checks if a type has a specific annotation.
     */
    private boolean hasAnnotation(CtType<?> type, String annotationName) {
        return type.getAnnotations().stream()
                .anyMatch(annotation -> {
                    String qualifiedName = annotation.getAnnotationType().getQualifiedName();
                    return qualifiedName.equals(annotationName);
                });
    }

    /**
     * Checks if a method has @Function annotation and records the mapping.
     */
    private void checkForFunctionAnnotation(CtMethod<?> method) {
        method.getAnnotations().stream()
                .filter(annotation -> annotation.getAnnotationType()
                        .getQualifiedName().equals(AnalyzerConstants.FUNCTION_ANNOTATION))
                .forEach(annotation -> {
                    Object valueObj = annotation.getValue(FUNCTION_ANNOTATION_VALUE_ATTRIBUTE);
                    if (valueObj != null) {
                        String functionId = valueObj.toString().replace(DOUBLE_QUOTE, EMPTY_STRING);

                        CtType<?> declaringType = method.getDeclaringType();
                        String className = declaringType != null ? declaringType.getQualifiedName() : UNKNOWN_CLASS;
                        String qualifiedMethodName = className + PERIOD_SEPARATOR + method.getSignature();

                        results.functionMappings.put(functionId, qualifiedMethodName);

                        logger.debug("Found @Function annotation: functionId='{}', method='{}'",
                                functionId, qualifiedMethodName);
                    }
                });
    }

    // ============================================================================
    // Invocation Analysis - Main Dispatcher
    // ============================================================================

    /**
     * Analyzes an invocation and categorizes it as function/service/event publisher.
     */
    private void analyzeInvocation(CtInvocation<?> invocation) {
        // Check if this is a function invocation
        if (isFunctionInvocation(invocation)) {
            processFunctionInvocation(invocation);
            return;
        }

        // Check if this is a service invocation
        String servicePackage = getServicePackageIfMatch(invocation);
        if (servicePackage != null) {
            processServiceInvocation(invocation, servicePackage);
            return;
        }

        // Check if this is an event publisher invocation
        if (isPublishEventInvocation(invocation)) {
            processEventPublisherInvocation(invocation);
            return;
        }
        if (isLegacyGatewayHttpClientInvocation(invocation)) {
            processLegacyGatewayHttpClientInvocation(invocation);
            return;
        }
    }

    // ============================================================================
    // Function Invocation Analysis
    // ============================================================================

    /**
     * Checks if an invocation is a call to execute/executeOnOrAfter/executeAsync on a function.
     */
    private boolean isFunctionInvocation(CtInvocation<?> invocation) {
        String methodName = invocation.getExecutable().getSimpleName();

        if (!METHOD_EXECUTE.equals(methodName) &&
                !METHOD_EXECUTE_ON_OR_AFTER.equals(methodName) &&
                !METHOD_EXECUTE_ASYNC.equals(methodName)) {
            return false;
        }

        // Check if target is FunctionClass.instance() or a variable of function type
        if (invocation.getTarget() instanceof CtInvocation<?> targetInvocation) {
            if (METHOD_INSTANCE.equals(targetInvocation.getExecutable().getSimpleName())) {
                CtTypeReference<?> declaringType = targetInvocation.getExecutable().getDeclaringType();
                if (declaringType != null && !Strings.isNullOrEmpty(declaringType.getQualifiedName())) {
                    if (functionClassNames.stream()
                            .anyMatch(str -> str.equalsIgnoreCase(declaringType.getQualifiedName()))) {
                        return true;
                    } else if (isImpliedFunctionClass(declaringType.getQualifiedName())) {
                        return true;
                    }
                }
            }
        } else if (invocation.getTarget() instanceof CtVariableAccess<?> varAccess) {
            CtTypeReference<?> varType = varAccess.getType();
            if (varType != null) {
                if (functionClassNames.stream()
                        .anyMatch(str -> str.equalsIgnoreCase(varType.getQualifiedName()))) {
                    return true;
                } else if (isImpliedFunctionClass(varType.getQualifiedName())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Evaluates whether the given fully qualified name represents an implied function class.
     * This method determines if the class name belongs to a function package and ends with
     * a specific function class suffix. If so, it adds the class name (in lowercase) to
     * the `functionClassNames` set for tracking purposes.
     *
     * @param qualifiedName The fully qualified name of the class to check.
     *                      This should include the package and class name.
     * @return {@code true} if the given qualified name is an implied function class;
     * {@code false} otherwise.
     */
    private boolean isImpliedFunctionClass(String qualifiedName) {
        logger.debug("Checking if qualifiedName '{}' is an implied function class", qualifiedName);
        if (qualifiedName != null && qualifiedName.startsWith(AnalyzerConstants.FUNCTION_PACKAGE_PREFIX)
                && qualifiedName.endsWith(FUNCTION_CLASS_SUFFIX)) {
            int startOfFunctionName = qualifiedName.indexOf(AnalyzerConstants.FUNCTION_PACKAGE_PREFIX)
                    + AnalyzerConstants.FUNCTION_PACKAGE_PREFIX.length() + 1;
            int endOfFunctionName = qualifiedName.lastIndexOf(FUNCTION_CLASS_SUFFIX);
            String functionName = qualifiedName.substring(startOfFunctionName, endOfFunctionName);
            logger.debug("Found implied function: {}, adding to tracked functions", functionName);
            functionClassNames.add(qualifiedName.toLowerCase());
            functionClassToId.put(qualifiedName.toLowerCase(), functionName.toLowerCase());
            return true;
        }
        return false;
    }

    /**
     * Processes a function invocation and adds it to results.
     */
    private void processFunctionInvocation(CtInvocation<?> invocation) {
        String functionClass = getFunctionClass(invocation);
        if (functionClass == null) {
            return;
        }

        String functionId = functionClassToId.get(functionClass.toLowerCase());
        if (functionId == null) {
            return;
        }

        CtMethod<?> enclosingMethod = invocation.getParent(CtMethod.class);
        if (enclosingMethod == null) {
            return;
        }

        String invocationSite = getInvocationSite(invocation);

        MethodReference enclosingMethodEntry = new MethodReference(getMethodSignature(enclosingMethod), getMethodAccessModifier(enclosingMethod));

        String invocationType = invocation.getExecutable().getSimpleName();

        FunctionInvocation funcInvocation = new FunctionInvocation(invocationSite, enclosingMethodEntry, invocationType);

        if (methodToCallers != null) {
            List<MethodReference> callChain = buildCallChain(enclosingMethod);
            funcInvocation.setCallChain(callChain);
        }

        results.functionInvocations.computeIfAbsent(functionId, k -> new ArrayList<>()).add(funcInvocation);
    }

    /**
     * Gets the function class name from an invocation.
     */
    private String getFunctionClass(CtInvocation<?> invocation) {
        if (invocation.getTarget() instanceof CtInvocation<?> targetInvocation) {
            CtTypeReference<?> declaringType = targetInvocation.getExecutable().getDeclaringType();
            return declaringType != null ? declaringType.getQualifiedName() : null;
        } else if (invocation.getTarget() instanceof CtVariableAccess<?> varAccess) {
            CtTypeReference<?> varType = varAccess.getType();
            return varType != null ? varType.getQualifiedName() : null;
        }
        return null;
    }

    // ============================================================================
    // Service Invocation Analysis
    // ============================================================================

    /**
     * Checks if an invocation matches any service package and returns the package if so.
     */
    private String getServicePackageIfMatch(CtInvocation<?> invocation) {
        CtExecutableReference<?> execRef = invocation.getExecutable();
        if (execRef == null) {
            return null;
        }

        CtTypeReference<?> declaringType = execRef.getDeclaringType();
        if (declaringType == null) {
            return null;
        }

        String qualifiedName = declaringType.getQualifiedName();
        if (qualifiedName == null) {
            return null;
        }

        // Check if declaring type is in any service package
        for (String servicePackage : servicePackages.keySet()) {
            if (qualifiedName.startsWith(servicePackage + PERIOD_SEPARATOR) ||
                    qualifiedName.equals(servicePackage)) {
                return servicePackage;
            }
        }

        return null;
    }

    /**
     * Processes a service invocation and adds it to results.
     */
    private void processServiceInvocation(CtInvocation<?> invocation, String servicePackage) {
        String serviceId = servicePackages.get(servicePackage);
        if (serviceId == null) {
            return;
        }

        CtMethod<?> enclosingMethod = invocation.getParent(CtMethod.class);
        if (enclosingMethod == null) {
            return;
        }

        String invocationSite = getInvocationSite(invocation);
        MethodReference enclosingMethodEntry = new MethodReference(getMethodSignature(enclosingMethod), getMethodAccessModifier(enclosingMethod));
        String invokedMethod = getInvokedMethodSignature(invocation);


        ServiceInvocation serviceInvocation = new ServiceInvocation(invocationSite, enclosingMethodEntry, invokedMethod);

        if (methodToCallers != null) {
            List<MethodReference> callChain = buildCallChain(enclosingMethod);
            serviceInvocation.setCallChain(callChain);
        }

        results.serviceInvocations.computeIfAbsent(serviceId, k -> new ArrayList<>()).add(serviceInvocation);
    }

    /**
     * Gets the signature of the invoked method.
     */
    private String getInvokedMethodSignature(CtInvocation<?> invocation) {
        CtExecutableReference<?> execRef = invocation.getExecutable();
        if (execRef == null) {
            return UNKNOWN;
        }
        CtTypeReference<?> declaringType = execRef.getDeclaringType();
        String className = declaringType != null ? declaringType.getQualifiedName() : UNKNOWN_CLASS;
        return className + PERIOD_SEPARATOR + execRef.getSignature();
    }

    // ============================================================================
    // Event Publisher Invocation Analysis
    // ============================================================================

    /**
     * Checks if an invocation is a call to IEventPublisher.publishEvent().
     */
    private boolean isPublishEventInvocation(CtInvocation<?> invocation) {
        CtExecutableReference<?> execRef = invocation.getExecutable();
        if (execRef == null || !PUBLISH_EVENT_METHOD.equals(execRef.getSimpleName())) {
            return false;
        }

        CtExpression<?> target = invocation.getTarget();
        if (target == null) {
            return false;
        }

        CtTypeReference<?> targetType = target.getType();
        return targetType != null && isEventPublisherType(targetType);
    }

    /**
     * Checks if a type is or implements IEventPublisher.
     */
    private boolean isEventPublisherType(CtTypeReference<?> typeRef) {
        if (typeRef == null) {
            return false;
        }

        String qualifiedName = typeRef.getQualifiedName();
        if (AnalyzerConstants.EVENT_PUBLISHER_INTERFACE.equals(qualifiedName)) {
            return true;
        }

        try {
            CtType<?> typeDecl = typeRef.getTypeDeclaration();
            if (typeDecl != null) {
                for (CtTypeReference<?> superInterface : typeDecl.getSuperInterfaces()) {
                    if (isEventPublisherType(superInterface)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not resolve type declaration for: {}", qualifiedName);
        }

        return false;
    }

    /**
     * Checks if an invocation is a call to LegacyGatewayHttpClient.post().
     */
    private boolean isLegacyGatewayHttpClientInvocation(CtInvocation<?> invocation) {
        CtExecutableReference<?> execRef = invocation.getExecutable();
        if (execRef == null || !POST_LGHC_METHOD.equals(execRef.getSimpleName())) {
            return false;
        }

        CtExpression<?> target = invocation.getTarget();
        if (target == null) {
            return false;
        }

        CtTypeReference<?> targetType = target.getType();
        return targetType != null && isLegacyGatewayHttpClientType(targetType);
    }

    /**
     * Checks if a type is LegacyGatewayHttpClient.
     */
    private boolean isLegacyGatewayHttpClientType(CtTypeReference<?> typeRef) {
        if (typeRef == null) {
            return false;
        }

        String qualifiedName = typeRef.getQualifiedName();
        return AnalyzerConstants.LGHC_CLASS.equals(qualifiedName);
    }

    /**
     * Processes an event publisher invocation and adds it to results.
     */
    private void processLegacyGatewayHttpClientInvocation(CtInvocation<?> invocation) {
        CtMethod<?> enclosingMethod = invocation.getParent(CtMethod.class);
        if (enclosingMethod == null) {
            return;
        }

        String invocationSite = getInvocationSite(invocation);
        MethodReference methodRef = new MethodReference(
                getMethodSignature(enclosingMethod),
                getMethodAccessModifier(enclosingMethod)
        );

        LegacyGatewayHttpClientInvocation legacyGatewayHttpClientInvocation = new LegacyGatewayHttpClientInvocation(
                invocationSite, methodRef
        );
        if (methodToCallers != null) {
            legacyGatewayHttpClientInvocation.setCallChain(buildCallChain(enclosingMethod));
        }

        results.legacyGatewayHttpClientInvocations.add(legacyGatewayHttpClientInvocation);
    }

    /**
     * Processes an event publisher invocation and adds it to results.
     */
    private void processEventPublisherInvocation(CtInvocation<?> invocation) {
        CtMethod<?> enclosingMethod = invocation.getParent(CtMethod.class);
        if (enclosingMethod == null) {
            return;
        }

        recordEventPublisherUsage(invocation, enclosingMethod);
    }

    private void recordEventPublisherUsage(CtInvocation<?> invocation, CtMethod<?> enclosingMethod) {
        String invocationSite = getInvocationSite(invocation);
        MethodReference methodRef = new MethodReference(
                getMethodSignature(enclosingMethod),
                getMethodAccessModifier(enclosingMethod)
        );

        String topic = UNKNOWN;
        TopicResolution resolution = TopicResolution.UNKNOWN_COMPLEX;
        List<CtExpression<?>> arguments = invocation.getArguments();
        if (arguments != null && !arguments.isEmpty()) {
            CtExpression<?> topicExpr = arguments.getFirst();
            Map<String, String> topicInfo = analyzeTopicExpression(topicExpr);
            topic = topicInfo.get(KEY_TOPIC);
            resolution = TopicResolution.valueOf(topicInfo.get(KEY_RESOLUTION));
        }

        EventPublisherInvocation eventInvocation = new EventPublisherInvocation(
                invocationSite, methodRef, topic, resolution
        );
        if (methodToCallers != null) {
            eventInvocation.setCallChain(buildCallChain(enclosingMethod));
        }

        results.eventPublisherInvocations.add(eventInvocation);
    }

    /**
     * Analyzes a topic expression to determine its value and resolution status.
     */
    private Map<String, String> analyzeTopicExpression(CtExpression<?> topicExpr) {
        Map<String, String> result = new HashMap<>();

        // String literal
        if (topicExpr instanceof CtLiteral<?> literal) {
            Object value = literal.getValue();
            if (value instanceof String) {
                result.put(KEY_TOPIC, (String) value);
                result.put(KEY_RESOLUTION, RESOLUTION_RESOLVED);
                return result;
            }
        }

        // Variable access
        if (topicExpr instanceof CtVariableRead<?> varRead) {
            String varName = varRead.getVariable().getSimpleName();
            CtTypeReference<?> varType = varRead.getType();
            String typeInfo = varType != null ? varType.getQualifiedName() : UNKNOWN;

            // Try to resolve if it's a constant field
            if (varRead instanceof CtFieldRead<?> fieldRead) {
                try {
                    CtField<?> field = fieldRead.getVariable().getFieldDeclaration();
                    if (field != null && field.isFinal() && field.isStatic()) {
                        CtExpression<?> initializer = field.getDefaultExpression();
                        if (initializer instanceof CtLiteral<?> initLiteral) {
                            Object value = initLiteral.getValue();
                            if (value instanceof String) {
                                result.put(KEY_TOPIC, (String) value);
                                result.put(KEY_RESOLUTION, RESOLUTION_RESOLVED);
                                return result;
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Could not resolve constant field: {}", varName);
                }
            }

            result.put(KEY_TOPIC, varName + SPACE_OPEN_PAREN + typeInfo + CLOSE_PAREN);
            result.put(KEY_RESOLUTION, RESOLUTION_UNKNOWN_VARIABLE);
            return result;
        }

        // Complex expression
        result.put(KEY_TOPIC, topicExpr.toString());
        result.put(KEY_RESOLUTION, RESOLUTION_UNKNOWN_COMPLEX);
        return result;
    }

    // ============================================================================
    // Helper Methods - Common
    // ============================================================================

    /**
     * Gets a string representation of the invocation site.
     */
    private String getInvocationSite(CtInvocation<?> invocation) {
        if (invocation.getPosition() != null && invocation.getPosition().isValidPosition()) {
            return invocation.getPosition().getFile().getName() + COLON_SEPARATOR + invocation.getPosition().getLine();
        }
        return UNKNOWN;
    }

    /**
     * Gets the method signature.
     */
    private String getMethodSignature(CtMethod<?> method) {
        CtType<?> declaringType = method.getDeclaringType();
        String className = declaringType != null ? declaringType.getQualifiedName() : UNKNOWN_CLASS;
        return className + PERIOD_SEPARATOR + method.getSignature();
    }

    /**
     * Determines the access modifier of the specified method.
     * It evaluates the method's modifiers and returns the corresponding
     * {@code CallChainEntry.MethodAccessModifier} value based on its visibility.
     *
     * @return the access modifier of the given method as a {@code CallChainEntry.MethodAccessModifier}.
     * It returns {@code PRIVATE}, {@code PROTECTED}, or {@code PUBLIC}
     * based on the detected modifier. Returns DEFAULT if no relevant
     * modifier is found.
     */
    private MethodReference.MethodAccessModifier getMethodAccessModifier(CtMethod<?> currentMethod) {
        MethodReference.MethodAccessModifier response = MethodReference.MethodAccessModifier.DEFAULT;
        for (ModifierKind modifier : currentMethod.getModifiers()) {
            if (modifier == ModifierKind.PRIVATE) {
                response = MethodReference.MethodAccessModifier.PRIVATE;
                break;
            } else if (modifier == ModifierKind.PROTECTED) {
                response = MethodReference.MethodAccessModifier.PROTECTED;
                break;
            } else if (modifier == ModifierKind.PUBLIC) {
                response = MethodReference.MethodAccessModifier.PUBLIC;
                break;
            }
        }
        return response;
    }

    /**
     * Builds the call chain from the enclosing method up to methods with no callers.
     */
    private List<MethodReference> buildCallChain(CtMethod<?> startMethod) {
        if (methodToCallers == null) {
            return Collections.emptyList();
        }

        LinkedHashSet<MethodReference> callChain = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();

        Queue<CtMethod<?>> queue = new LinkedList<>();
        queue.add(startMethod);
        visited.add(getMethodSignature(startMethod));

        while (!queue.isEmpty()) {
            CtMethod<?> currentMethod = queue.poll();
            String methodSig = getMethodSignature(currentMethod);
            MethodReference.MethodAccessModifier methodModifier = getMethodAccessModifier(currentMethod);

            List<CtMethod<?>> callers = methodToCallers.getOrDefault(methodSig, Collections.emptyList());

            if (callers.isEmpty()) {
                callChain.add(new MethodReference(methodSig, methodModifier));
            } else {
                for (CtMethod<?> caller : callers) {
                    String callerSig = getMethodSignature(caller);
                    MethodReference.MethodAccessModifier callerModifier = getMethodAccessModifier(caller);
                    if (!visited.contains(callerSig)) {
                        visited.add(callerSig);
                        queue.add(caller);
                        callChain.add(new MethodReference(callerSig, callerModifier));
                    }
                }
            }
        }

        return new ArrayList<>(callChain);
    }


    // ============================================================================
    // Results Container
    // ============================================================================

    /**
     * Container for all analysis results collected by the visitor.
     */
    public static class AnalysisResults {
        // Service components
        public final List<CtInterface<?>> serviceInterfaces = new ArrayList<>();
        public final List<CtClass<?>> serviceImplementations = new ArrayList<>();
        // Function mappings from @Function annotations
        public final Map<String, String> functionMappings = new LinkedHashMap<>();
        // Dependency mappings
        public final Map<String, String> functionIdToDependency = new HashMap<>();
        public final Map<String, String> serviceIdToDependency = new HashMap<>();
        // Invocations categorized by function/service ID
        public final Map<String, List<FunctionInvocation>> functionInvocations = new HashMap<>();
        public final Map<String, List<ServiceInvocation>> serviceInvocations = new HashMap<>();
        public final List<EventPublisherInvocation> eventPublisherInvocations = new ArrayList<>();
        public final List<LegacyGatewayHttpClientInvocation> legacyGatewayHttpClientInvocations = new ArrayList<>();
        // Type and method counts
        public int typeCount = 0;
        public int methodCount = 0;
    }
}
