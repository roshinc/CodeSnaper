package gov.nystax.nimbus.codesnap.services.scanner;

import gov.nystax.nimbus.codesnap.domain.CodeSnapperConfig;
import gov.nystax.nimbus.codesnap.domain.NimbusServiceMeta;
import gov.nystax.nimbus.codesnap.services.scanner.analyzer.MavenProjectAnalyzer;
import gov.nystax.nimbus.codesnap.services.scanner.domain.FunctionInvocation;
import gov.nystax.nimbus.codesnap.services.scanner.domain.FunctionUsage;
import gov.nystax.nimbus.codesnap.services.scanner.domain.MethodReference;
import gov.nystax.nimbus.codesnap.services.scanner.domain.ProjectInfo;
import gov.nystax.nimbus.codesnap.services.scanner.observability.ScanContext;
import gov.nystax.nimbus.codesnap.services.scanner.observability.ScanProgressListener;
import gov.nystax.nimbus.codesnap.services.scanner.visitor.NimbaFunctionOnlyAnalysisVisitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.support.compiler.VirtualFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class NimbaProjectScannerTest {

    // ===== NimbaFunctionOnlyAnalysisVisitor unit tests =====

    @Test
    void visitor_detectsFunctionInvocations() {
        String source = """
                package sample;

                import gov.nystax.nimbus.function.client.RetrieveWTPendFilingFunction;

                class MyProcessor {
                    void process() {
                        RetrieveWTPendFilingFunction.instance().execute("input");
                    }
                }
                """;

        List<String> functionDeps = List.of("gov.nystax.functions:RetrieveWTPendFiling-func-client:1.0");

        NimbaFunctionOnlyAnalysisVisitor.FunctionAnalysisResults results = analyzeSource(
                "sample/MyProcessor.java", source, functionDeps);

        assertThat(results.functionInvocations).hasSize(1);
        assertThat(results.functionInvocations).containsKey("retrievewtpendfiling");

        List<FunctionInvocation> invocations = results.functionInvocations.get("retrievewtpendfiling");
        assertThat(invocations).hasSize(1);
        assertThat(invocations.getFirst().getInvocationType()).isEqualTo("execute");
    }

    @Test
    void visitor_detectsMultipleFunctionTypes() {
        String source = """
                package sample;

                import gov.nystax.nimbus.function.client.RetrieveWTPendFilingFunction;
                import gov.nystax.nimbus.function.client.UpdateRecordFunction;

                class MyProcessor {
                    void process() {
                        RetrieveWTPendFilingFunction.instance().execute("input");
                        UpdateRecordFunction.instance().executeAsync("data");
                    }
                }
                """;

        List<String> functionDeps = List.of(
                "gov.nystax.functions:RetrieveWTPendFiling-func-client:1.0",
                "gov.nystax.functions:UpdateRecord-func-client:2.0");

        NimbaFunctionOnlyAnalysisVisitor.FunctionAnalysisResults results = analyzeSource(
                "sample/MyProcessor.java", source, functionDeps);

        assertThat(results.functionInvocations).hasSize(2);
        assertThat(results.functionInvocations).containsKeys("retrievewtpendfiling", "updaterecord");
        assertThat(results.functionInvocations.get("updaterecord").getFirst().getInvocationType())
                .isEqualTo("executeAsync");
    }

    @Test
    void visitor_detectsExecuteOnOrAfter() {
        String source = """
                package sample;

                import gov.nystax.nimbus.function.client.ScheduleTaskFunction;

                class MyProcessor {
                    void process() {
                        ScheduleTaskFunction.instance().executeOnOrAfter("input");
                    }
                }
                """;

        List<String> functionDeps = List.of("gov.nystax.functions:ScheduleTask-func-client:1.0");

        NimbaFunctionOnlyAnalysisVisitor.FunctionAnalysisResults results = analyzeSource(
                "sample/MyProcessor.java", source, functionDeps);

        assertThat(results.functionInvocations).hasSize(1);
        assertThat(results.functionInvocations.get("scheduletask").getFirst().getInvocationType())
                .isEqualTo("executeOnOrAfter");
    }

    @Test
    void visitor_ignoresNonFunctionInvocations() {
        String source = """
                package sample;

                class MyProcessor {
                    void process() {
                        String result = "hello".toUpperCase();
                        System.out.println(result);
                    }
                }
                """;

        NimbaFunctionOnlyAnalysisVisitor.FunctionAnalysisResults results = analyzeSource(
                "sample/MyProcessor.java", source, Collections.emptyList());

        assertThat(results.functionInvocations).isEmpty();
        assertThat(results.typeCount).isEqualTo(1);
        assertThat(results.methodCount).isEqualTo(1);
    }

    @Test
    void visitor_countsTypesAndMethods() {
        String source = """
                package sample;

                interface Processor {
                    void process();
                }

                class MyProcessorImpl implements Processor {
                    public void process() {}
                    private void helper() {}
                }
                """;

        NimbaFunctionOnlyAnalysisVisitor.FunctionAnalysisResults results = analyzeSource(
                "sample/Processor.java", source, Collections.emptyList());

        assertThat(results.typeCount).isEqualTo(2);
        assertThat(results.methodCount).isEqualTo(3);
    }

    @Test
    void visitor_doesNotTrackServiceAnnotations() {
        String source = """
                package sample;

                import gov.nystax.nimbus.smart.SmartService;
                import gov.nystax.nimbus.smart.SmartImpl;

                @SmartService
                interface MyService {
                    void doWork();
                }

                @SmartImpl
                class MyServiceImpl implements MyService {
                    public void doWork() {}
                }
                """;

        NimbaFunctionOnlyAnalysisVisitor.FunctionAnalysisResults results = analyzeSource(
                "sample/MyService.java", source, Collections.emptyList());

        // Visitor should count types but not track service annotations
        assertThat(results.typeCount).isEqualTo(2);
        assertThat(results.functionInvocations).isEmpty();
    }

    // ===== Direct static call tests (Nimba-style, no .instance()) =====

    @Test
    void visitor_detectsDirectStaticFunctionInvocations() {
        String source = """
                package sample;

                import gov.nystax.nimbus.function.client.RetrieveWTPendFilingFunction;

                class MyProcessor {
                    void process() {
                        RetrieveWTPendFilingFunction.execute("input");
                    }
                }
                """;

        List<String> functionDeps = List.of("gov.nystax.functions:RetrieveWTPendFiling-func-client:1.0");

        NimbaFunctionOnlyAnalysisVisitor.FunctionAnalysisResults results = analyzeSource(
                "sample/MyProcessor.java", source, functionDeps);

        assertThat(results.functionInvocations).hasSize(1);
        assertThat(results.functionInvocations).containsKey("retrievewtpendfiling");

        List<FunctionInvocation> invocations = results.functionInvocations.get("retrievewtpendfiling");
        assertThat(invocations).hasSize(1);
        assertThat(invocations.getFirst().getInvocationType()).isEqualTo("execute");
    }

    @Test
    void visitor_detectsMultipleDirectStaticFunctionTypes() {
        String source = """
                package sample;

                import gov.nystax.nimbus.function.client.RetrieveWTPendFilingFunction;
                import gov.nystax.nimbus.function.client.UpdateRecordFunction;

                class MyProcessor {
                    void process() {
                        RetrieveWTPendFilingFunction.execute("input");
                        UpdateRecordFunction.executeAsync("data");
                    }
                }
                """;

        List<String> functionDeps = List.of(
                "gov.nystax.functions:RetrieveWTPendFiling-func-client:1.0",
                "gov.nystax.functions:UpdateRecord-func-client:2.0");

        NimbaFunctionOnlyAnalysisVisitor.FunctionAnalysisResults results = analyzeSource(
                "sample/MyProcessor.java", source, functionDeps);

        assertThat(results.functionInvocations).hasSize(2);
        assertThat(results.functionInvocations).containsKeys("retrievewtpendfiling", "updaterecord");
        assertThat(results.functionInvocations.get("updaterecord").getFirst().getInvocationType())
                .isEqualTo("executeAsync");
    }

    @Test
    void visitor_detectsDirectStaticExecuteOnOrAfter() {
        String source = """
                package sample;

                import gov.nystax.nimbus.function.client.ScheduleTaskFunction;

                class MyProcessor {
                    void process() {
                        ScheduleTaskFunction.executeOnOrAfter("input");
                    }
                }
                """;

        List<String> functionDeps = List.of("gov.nystax.functions:ScheduleTask-func-client:1.0");

        NimbaFunctionOnlyAnalysisVisitor.FunctionAnalysisResults results = analyzeSource(
                "sample/MyProcessor.java", source, functionDeps);

        assertThat(results.functionInvocations).hasSize(1);
        assertThat(results.functionInvocations.get("scheduletask").getFirst().getInvocationType())
                .isEqualTo("executeOnOrAfter");
    }

    @Test
    void visitor_detectsMixedInvocationPatterns() {
        String source = """
                package sample;

                import gov.nystax.nimbus.function.client.RetrieveWTPendFilingFunction;
                import gov.nystax.nimbus.function.client.UpdateRecordFunction;

                class MyProcessor {
                    void process() {
                        RetrieveWTPendFilingFunction.instance().execute("input");
                        UpdateRecordFunction.execute("data");
                    }
                }
                """;

        List<String> functionDeps = List.of(
                "gov.nystax.functions:RetrieveWTPendFiling-func-client:1.0",
                "gov.nystax.functions:UpdateRecord-func-client:2.0");

        NimbaFunctionOnlyAnalysisVisitor.FunctionAnalysisResults results = analyzeSource(
                "sample/MyProcessor.java", source, functionDeps);

        assertThat(results.functionInvocations).hasSize(2);
        assertThat(results.functionInvocations).containsKeys("retrievewtpendfiling", "updaterecord");
        assertThat(results.functionInvocations.get("retrievewtpendfiling").getFirst().getInvocationType())
                .isEqualTo("execute");
        assertThat(results.functionInvocations.get("updaterecord").getFirst().getInvocationType())
                .isEqualTo("execute");
    }

    @Test
    void visitor_ignoresStaticImportWithoutFunctionTarget() {
        String source = """
                package sample;

                import static gov.nystax.nimbus.function.client.RetrieveWTPendFilingFunction.execute;

                class MyProcessor {
                    void process() {
                        execute("input");
                    }
                }
                """;

        List<String> functionDeps = List.of("gov.nystax.functions:RetrieveWTPendFiling-func-client:1.0");

        NimbaFunctionOnlyAnalysisVisitor.FunctionAnalysisResults results = analyzeSource(
                "sample/MyProcessor.java", source, functionDeps);

        assertThat(results.functionInvocations).isEmpty();
    }

    @Test
    void visitor_ignoresDirectStaticCallsOnNonFunctionClasses() {
        String source = """
                package sample;

                class Utility {
                    static void execute(String input) {}
                }

                class MyProcessor {
                    void process() {
                        Utility.execute("input");
                    }
                }
                """;

        List<String> functionDeps = List.of("gov.nystax.functions:RetrieveWTPendFiling-func-client:1.0");

        NimbaFunctionOnlyAnalysisVisitor.FunctionAnalysisResults results = analyzeSource(
                "sample/MyProcessor.java", source, functionDeps);

        assertThat(results.functionInvocations).isEmpty();
    }

    // ===== NimbaProjectScanner integration test =====

    @Test
    void scanner_scansProjectWithFunctionDependenciesOnly(@TempDir Path tempDir) throws Exception {
        ProjectInfo projectInfo = scanNimbaProject(
                tempDir,
                "myproject",
                "my-nimba-project",
                List.of("RetrieveWTPendFiling-func-client"),
                Map.of("sample/MyProcessor.java", """
                        package sample;

                        import gov.nystax.nimbus.function.client.RetrieveWTPendFilingFunction;

                        public class MyProcessor {
                            public void process() {
                                RetrieveWTPendFilingFunction.execute("input");
                            }
                        }
                        """));

        String syntheticPackage = "gov.nystax.nimbus.codesnap.synthetic.nimba.my_nimba_project";
        String interfaceMethod = syntheticPackage + ".NimbaImplicitService.my_nimba_project_implicitFunction()";
        String implMethod = syntheticPackage + ".NimbaImplicitServiceImpl.my_nimba_project_implicitFunction()";

        assertThat(projectInfo.getGroupId()).isEqualTo("gov.nystax.nimba");
        assertThat(projectInfo.getArtifactId()).isEqualTo("my-nimba-project");
        assertThat(projectInfo.getFunctionDependencies()).hasSize(1);
        assertThat(projectInfo.getFunctionDependencies().getFirst())
                .contains("RetrieveWTPendFiling-func-client");
        assertThat(projectInfo.getServiceInterface()).isEqualTo(syntheticPackage + ".NimbaImplicitService");
        assertThat(projectInfo.getServiceImplementation()).isEqualTo(syntheticPackage + ".NimbaImplicitServiceImpl");
        assertThat(projectInfo.getFunctionMappings())
                .containsOnly(entry("my_nimba_project_implicitFunction", interfaceMethod));
        assertThat(projectInfo.getMethodImplementationMappings())
                .containsOnly(entry(interfaceMethod, implMethod));
        assertThat(projectInfo.isUIService()).isFalse();
        assertThat(projectInfo.getUIServiceMethodMappings()).isEmpty();
        assertThat(projectInfo.getFunctionUsages()).hasSize(1);
        assertThat(projectInfo.getFunctionUsages().getFirst().getInvocations().getFirst().getCallChain())
                .extracting(MethodReference::getMethodName)
                .containsExactly(implMethod, "sample.MyProcessor.process()");
    }

    @Test
    void scanner_prefixesSyntheticImplIntoFunctionCallChain(@TempDir Path tempDir) throws Exception {
        ProjectInfo projectInfo = scanNimbaProject(
                tempDir,
                "call-chain-project",
                "my-nimba-project",
                List.of("RetrieveWTPendFiling-func-client"),
                Map.of("sample/MyProcessor.java", """
                        package sample;

                        import gov.nystax.nimbus.function.client.RetrieveWTPendFilingFunction;

                        public class MyProcessor {
                            public void entry() {
                                doCall();
                            }

                            void doCall() {
                                RetrieveWTPendFilingFunction.execute("input");
                            }
                        }
                        """));

        FunctionInvocation invocation = projectInfo.getFunctionUsages().getFirst().getInvocations().getFirst();
        String implMethod = "gov.nystax.nimbus.codesnap.synthetic.nimba.my_nimba_project"
                + ".NimbaImplicitServiceImpl.my_nimba_project_implicitFunction()";

        assertThat(invocation.getEnclosingMethod().getMethodName()).isEqualTo("sample.MyProcessor.doCall()");
        assertThat(invocation.getCallChain())
                .extracting(MethodReference::getMethodName)
                .containsExactly(implMethod, "sample.MyProcessor.entry()");
    }

    @Test
    void scanner_usesSameSyntheticBridgeAcrossMultipleFunctionUsages(@TempDir Path tempDir) throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("sample/MyProcessor.java", """
                package sample;

                import gov.nystax.nimbus.function.client.RetrieveWTPendFilingFunction;
                import gov.nystax.nimbus.function.client.UpdateRecordFunction;

                public class MyProcessor {
                    public void runRetrieve() {
                        RetrieveWTPendFilingFunction.execute("input");
                    }

                    public void runUpdate() {
                        UpdateRecordFunction.executeAsync("data");
                    }
                }
                """);

        ProjectInfo projectInfo = scanNimbaProject(
                tempDir,
                "multi-usage-project",
                "my-nimba-project",
                List.of("RetrieveWTPendFiling-func-client", "UpdateRecord-func-client"),
                sources);

        String implMethod = "gov.nystax.nimbus.codesnap.synthetic.nimba.my_nimba_project"
                + ".NimbaImplicitServiceImpl.my_nimba_project_implicitFunction()";

        assertThat(projectInfo.getFunctionUsages())
                .extracting(FunctionUsage::getFunctionId)
                .containsExactlyInAnyOrder("retrievewtpendfiling", "updaterecord");
        assertThat(projectInfo.getFunctionUsages())
                .allSatisfy(functionUsage -> assertThat(functionUsage.getInvocations())
                        .allSatisfy(invocation -> assertThat(invocation.getCallChain().getFirst().getMethodName())
                                .isEqualTo(implMethod)));
    }

    // ===== Helper =====

    private NimbaFunctionOnlyAnalysisVisitor.FunctionAnalysisResults analyzeSource(
            String fileName, String source, List<String> functionDependencies) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(source, fileName));
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);

        CtModel model = launcher.buildModel();

        NimbaFunctionOnlyAnalysisVisitor visitor = new NimbaFunctionOnlyAnalysisVisitor(functionDependencies);
        model.getRootPackage().accept(visitor);

        return visitor.getResults();
    }

    private ProjectInfo scanNimbaProject(Path tempDir,
                                         String serviceId,
                                         String artifactId,
                                         List<String> functionDependencyArtifactIds,
                                         Map<String, String> sources) throws Exception {
        CodeSnapperConfig config = CodeSnapperConfig.builder()
                .serviceId(serviceId)
                .commitHash("test-commit")
                .branch("main")
                .gitGroups(List.of("test-group"))
                .gitToken("test-token")
                .localTempRootPath(tempDir)
                .flatProjectStructure(true)
                .build();

        NimbusServiceMeta meta = new NimbusServiceMeta(config);
        Path projectDir = meta.getLocalServiceRootPath();
        Files.createDirectories(projectDir);
        Files.writeString(meta.getLocalServicePomPath(), buildPomXml(artifactId, functionDependencyArtifactIds));

        for (Map.Entry<String, String> sourceEntry : sources.entrySet()) {
            Path sourcePath = projectDir.resolve("src/main/java").resolve(sourceEntry.getKey());
            Files.createDirectories(sourcePath.getParent());
            Files.writeString(sourcePath, sourceEntry.getValue());
        }

        MavenProjectAnalyzer mavenAnalyzer = new MavenProjectAnalyzer();
        ProjectInfo projectInfo = mavenAnalyzer.analyzeProject(projectDir);

        try (ScanContext context = new ScanContext("", ScanProgressListener.noOp())) {
            NimbaProjectScanner scanner = new NimbaProjectScanner(meta, context);
            scanner.scanProject(projectInfo);
        }

        return projectInfo;
    }

    private String buildPomXml(String artifactId, List<String> functionDependencyArtifactIds) {
        String dependencyXml = functionDependencyArtifactIds.stream()
                .map(dependencyArtifactId -> """
                        <dependency>
                            <groupId>gov.nystax.functions</groupId>
                            <artifactId>%s</artifactId>
                            <version>1.0.0</version>
                        </dependency>
                        """.formatted(dependencyArtifactId))
                .reduce("", String::concat);

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>gov.nystax.nimba</groupId>
                    <artifactId>%s</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                %s
                    </dependencies>
                </project>
                """.formatted(artifactId, dependencyXml);
    }
}
