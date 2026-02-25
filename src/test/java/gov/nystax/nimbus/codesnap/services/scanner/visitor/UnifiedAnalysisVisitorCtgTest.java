package gov.nystax.nimbus.codesnap.services.scanner.visitor;

import gov.nystax.nimbus.codesnap.services.scanner.domain.CtgInvocation;
import org.junit.jupiter.api.Test;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.support.compiler.VirtualFile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedAnalysisVisitorCtgTest {

    @Test
    void shouldResolveSameFieldNamesByDeclaringClass() {
        String source = """
                package sample;

                import gov.nystax.nimbus.ctg.CTGClient;
                import gov.nystax.nimbus.ctg.ICTGClient;

                class FirstConsumer {
                    @CTGClient("TZ0001Z")
                    private ICTGClient client;

                    void call() {
                        client.invoke("input");
                    }
                }

                class SecondConsumer {
                    @CTGClient("TZ0002Z")
                    private ICTGClient client;

                    void call() {
                        client.invokeAsync("input");
                    }
                }
                """;

        UnifiedAnalysisVisitor.AnalysisResults results = analyzeSource("sample/Consumers.java", source);
        Map<String, List<CtgInvocation>> ctgInvocations = results.ctgInvocations;

        assertThat(ctgInvocations).hasSize(2);
        assertThat(ctgInvocations).containsKeys("TZ0001Z", "TZ0002Z");
        assertThat(ctgInvocations.get("TZ0001Z")).hasSize(1);
        assertThat(ctgInvocations.get("TZ0001Z").getFirst().getInvocationType()).isEqualTo("invoke");
        assertThat(ctgInvocations.get("TZ0002Z")).hasSize(1);
        assertThat(ctgInvocations.get("TZ0002Z").getFirst().getInvocationType()).isEqualTo("invokeAsync");
    }

    @Test
    void shouldSkipUnannotatedAndNonFieldTargets() {
        String source = """
                package sample;

                import gov.nystax.nimbus.ctg.CTGClient;
                import gov.nystax.nimbus.ctg.ICTGClient;

                class CtgConsumer {
                    @CTGClient("TZ0001Z")
                    private ICTGClient client;

                    ICTGClient getClient() {
                        return client;
                    }

                    void directCall() {
                        client.invoke("input");
                    }

                    void indirectCall() {
                        getClient().invokeAsync("input");
                    }
                }

                class MissingAnnotation {
                    private ICTGClient client;

                    void call() {
                        client.invoke("input");
                    }
                }
                """;

        UnifiedAnalysisVisitor.AnalysisResults results = analyzeSource("sample/CtgCalls.java", source);
        Map<String, List<CtgInvocation>> ctgInvocations = results.ctgInvocations;

        assertThat(ctgInvocations).hasSize(1);
        assertThat(ctgInvocations).containsKey("TZ0001Z");
        assertThat(ctgInvocations.get("TZ0001Z")).hasSize(1);
        assertThat(ctgInvocations.get("TZ0001Z").getFirst().getInvocationType()).isEqualTo("invoke");
    }

    @Test
    void shouldIgnoreCtgAnnotationOnNonCtgType() {
        String source = """
                package sample;

                import gov.nystax.nimbus.ctg.CTGClient;
                import gov.nystax.nimbus.ctg.ICTGClient;

                class NotCtgClient {
                    Object invoke(Object input) {
                        return input;
                    }
                }

                class WrongTypeField {
                    @CTGClient("SHOULD_NOT_BE_USED")
                    private NotCtgClient client;

                    void call() {
                        client.invoke("input");
                    }
                }

                class RealCtgButUnannotated {
                    private ICTGClient client;

                    void call() {
                        client.invoke("input");
                    }
                }
                """;

        UnifiedAnalysisVisitor.AnalysisResults results = analyzeSource("sample/WrongType.java", source);

        assertThat(results.ctgInvocations).isEmpty();
    }

    private UnifiedAnalysisVisitor.AnalysisResults analyzeSource(String sourceName, String sourceCode) {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("gov/nystax/nimbus/ctg/ICTGClient.java", """
                package gov.nystax.nimbus.ctg;

                public interface ICTGClient {
                    Object invoke(Object input);
                    String invokeAsync(Object input);
                }
                """);
        sources.put("gov/nystax/nimbus/ctg/CTGClient.java", """
                package gov.nystax.nimbus.ctg;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.FIELD)
                public @interface CTGClient {
                    String value();
                }
                """);
        sources.put(sourceName, sourceCode);
        return analyzeSources(sources);
    }

    private UnifiedAnalysisVisitor.AnalysisResults analyzeSources(Map<String, String> sources) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);

        for (Map.Entry<String, String> sourceEntry : sources.entrySet()) {
            launcher.addInputResource(new VirtualFile(sourceEntry.getValue(), sourceEntry.getKey()));
        }

        CtModel model = launcher.buildModel();
        UnifiedAnalysisVisitor visitor = new UnifiedAnalysisVisitor(Collections.emptyList(), Collections.emptyList());
        model.getRootPackage().accept(visitor);
        return visitor.getResults();
    }
}
