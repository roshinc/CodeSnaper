package gov.nystax.nimbus.codesnap.services.scanner.visitor;

import com.google.common.base.Strings;
import gov.nystax.nimbus.codesnap.services.scanner.analyzer.AnalyzerConstants;
import gov.nystax.nimbus.codesnap.services.scanner.domain.FunctionInvocation;
import gov.nystax.nimbus.codesnap.services.scanner.domain.MethodReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight visitor that only detects function invocations from function dependencies.
 * Used for codebases that do not have SmartService/SmartImpl annotations.
 */
public class FunctionOnlyAnalysisVisitor extends CtScanner {

    private static final Logger logger = LoggerFactory.getLogger(FunctionOnlyAnalysisVisitor.class);

    private static final String FUNCTION_CLASS_SUFFIX = "Function";
    private static final Pattern ARTIFACT_PATTERN = Pattern.compile("(.+)-func-client$");
    private static final String METHOD_EXECUTE = "execute";
    private static final String METHOD_EXECUTE_ON_OR_AFTER = "executeOnOrAfter";
    private static final String METHOD_EXECUTE_ASYNC = "executeAsync";
    private static final String METHOD_INSTANCE = "instance";

    private static final String UNKNOWN_CLASS = "UnknownClass";
    private static final String UNKNOWN = "unknown";
    private static final String COLON_SEPARATOR = ":";
    private static final String PERIOD_SEPARATOR = ".";

    private static final int MIN_DEPENDENCY_PARTS = 2;
    private static final int DEPENDENCY_ARTIFACT_INDEX = 1;

    private final FunctionAnalysisResults results = new FunctionAnalysisResults();

    private final List<String> functionDependencies;
    private final Set<String> functionClassNames = new HashSet<>();
    private final Map<String, String> functionClassToId = new HashMap<>();

    private Map<String, List<CtMethod<?>>> methodToCallers = null;

    public FunctionOnlyAnalysisVisitor(List<String> functionDependencies) {
        this.functionDependencies = functionDependencies != null ? functionDependencies : Collections.emptyList();
        buildFunctionClassMappings();
    }

    public FunctionAnalysisResults getResults() {
        return results;
    }

    public void setMethodToCallers(Map<String, List<CtMethod<?>>> methodToCallers) {
        this.methodToCallers = methodToCallers;
    }

    // ============================================================================
    // Visit Methods
    // ============================================================================

    @Override
    public <T> void visitCtInterface(CtInterface<T> ctInterface) {
        results.typeCount++;
        super.visitCtInterface(ctInterface);
    }

    @Override
    public <T> void visitCtClass(CtClass<T> ctClass) {
        results.typeCount++;
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
        super.visitCtMethod(method);
    }

    @Override
    public <T> void visitCtInvocation(CtInvocation<T> invocation) {
        if (isFunctionInvocation(invocation)) {
            processFunctionInvocation(invocation);
        }
        super.visitCtInvocation(invocation);
    }

    // ============================================================================
    // Initialization
    // ============================================================================

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

            String functionClass = AnalyzerConstants.FUNCTION_PACKAGE_PREFIX + PERIOD_SEPARATOR
                    + functionId + FUNCTION_CLASS_SUFFIX;
            functionClassNames.add(functionClass.toLowerCase());
            functionClassToId.put(functionClass.toLowerCase(), functionId.toLowerCase());
            results.functionIdToDependency.put(functionId.toLowerCase(), dependency);
        }
    }

    private String extractFunctionId(String artifactId) {
        Matcher matcher = ARTIFACT_PATTERN.matcher(artifactId);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    // ============================================================================
    // Function Invocation Detection
    // ============================================================================

    private boolean isFunctionInvocation(CtInvocation<?> invocation) {
        String methodName = invocation.getExecutable().getSimpleName();

        if (!METHOD_EXECUTE.equals(methodName) &&
                !METHOD_EXECUTE_ON_OR_AFTER.equals(methodName) &&
                !METHOD_EXECUTE_ASYNC.equals(methodName)) {
            return false;
        }

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
        MethodReference enclosingMethodEntry = new MethodReference(
                getMethodSignature(enclosingMethod), getMethodAccessModifier(enclosingMethod));
        String invocationType = invocation.getExecutable().getSimpleName();

        FunctionInvocation funcInvocation = new FunctionInvocation(
                invocationSite, enclosingMethodEntry, invocationType);

        if (methodToCallers != null) {
            List<MethodReference> callChain = buildCallChain(enclosingMethod);
            funcInvocation.setCallChain(callChain);
        }

        results.functionInvocations.computeIfAbsent(functionId, k -> new ArrayList<>()).add(funcInvocation);
    }

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
    // Helper Methods
    // ============================================================================

    private String getInvocationSite(CtInvocation<?> invocation) {
        if (invocation.getPosition() != null && invocation.getPosition().isValidPosition()) {
            return invocation.getPosition().getFile().getName()
                    + COLON_SEPARATOR + invocation.getPosition().getLine();
        }
        return UNKNOWN;
    }

    private String getMethodSignature(CtMethod<?> method) {
        CtType<?> declaringType = method.getDeclaringType();
        String className = declaringType != null ? declaringType.getQualifiedName() : UNKNOWN_CLASS;
        return className + PERIOD_SEPARATOR + method.getSignature();
    }

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

    public static class FunctionAnalysisResults {
        public final Map<String, String> functionIdToDependency = new HashMap<>();
        public final Map<String, List<FunctionInvocation>> functionInvocations = new HashMap<>();
        public int typeCount = 0;
        public int methodCount = 0;
    }
}
