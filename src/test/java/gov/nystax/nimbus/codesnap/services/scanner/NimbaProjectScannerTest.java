package gov.nystax.nimbus.codesnap.services.scanner;

import gov.nystax.nimbus.codesnap.services.scanner.domain.FunctionInvocation;
import gov.nystax.nimbus.codesnap.services.scanner.domain.FunctionUsage;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
        // Set up a project directory with pom.xml and source code
        Path projectDir = tempDir.resolve("myproject");
        Files.createDirectories(projectDir);

        // Write pom.xml with a function dependency
        String pomXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>gov.nystax.nimba</groupId>
                    <artifactId>my-nimba-project</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>gov.nystax.functions</groupId>
                            <artifactId>RetrieveWTPendFiling-func-client</artifactId>
                            <version>1.0.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """;
        Files.writeString(projectDir.resolve("pom.xml"), pomXml);

        // Write Java source that invokes the function (Nimba-style: no .instance())
        Path srcDir = projectDir.resolve("src/main/java/sample");
        Files.createDirectories(srcDir);
        String javaSource = """
                package sample;

                import gov.nystax.nimbus.function.client.RetrieveWTPendFilingFunction;

                public class MyProcessor {
                    public void process() {
                        RetrieveWTPendFilingFunction.execute("input");
                    }
                }
                """;
        Files.writeString(srcDir.resolve("MyProcessor.java"), javaSource);

        // Create a mock NimbusServiceMeta-like setup by calling the scanner directly
        // We need to test the scanner's analyzeSourceCode logic without NimbusServiceMeta
        // So we test via the MavenProjectAnalyzer + visitor path

        // First, verify Maven analysis works
        var mavenAnalyzer = new gov.nystax.nimbus.codesnap.services.scanner.analyzer.MavenProjectAnalyzer();
        ProjectInfo projectInfo = mavenAnalyzer.analyzeProject(projectDir);

        assertThat(projectInfo.getGroupId()).isEqualTo("gov.nystax.nimba");
        assertThat(projectInfo.getArtifactId()).isEqualTo("my-nimba-project");
        assertThat(projectInfo.getFunctionDependencies()).hasSize(1);
        assertThat(projectInfo.getFunctionDependencies().getFirst())
                .contains("RetrieveWTPendFiling-func-client");

        // Service-related fields should be null (no SmartService/SmartImpl)
        assertThat(projectInfo.getServiceInterface()).isNull();
        assertThat(projectInfo.getServiceImplementation()).isNull();

        // Now run code analysis using NimbaFunctionOnlyAnalysisVisitor
        Path srcPath = projectDir.resolve("src/main/java");
        Launcher launcher = new Launcher();
        launcher.addInputResource(srcPath.toString());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        CtModel model = launcher.buildModel();

        NimbaFunctionOnlyAnalysisVisitor visitor = new NimbaFunctionOnlyAnalysisVisitor(
                projectInfo.getFunctionDependencies());
        model.getRootPackage().accept(visitor);

        NimbaFunctionOnlyAnalysisVisitor.FunctionAnalysisResults results = visitor.getResults();

        assertThat(results.functionInvocations).hasSize(1);
        assertThat(results.functionInvocations).containsKey("retrievewtpendfiling");
        assertThat(results.typeCount).isEqualTo(1);
        assertThat(results.methodCount).isEqualTo(1);
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
}
