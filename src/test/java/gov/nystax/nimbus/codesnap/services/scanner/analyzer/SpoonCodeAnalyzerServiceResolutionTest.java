package gov.nystax.nimbus.codesnap.services.scanner.analyzer;

import gov.nystax.nimbus.codesnap.exception.CodeViolationException;
import gov.nystax.nimbus.codesnap.services.scanner.domain.ProjectInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpoonCodeAnalyzerServiceResolutionTest {

    private static final String SMART_SERVICE_ANNOTATION = """
            package gov.nystax.nimbus.smart;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.TYPE)
            public @interface SmartService {}
            """;

    private static final String SMART_IMPL_ANNOTATION = """
            package gov.nystax.nimbus.smart;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.TYPE)
            public @interface SmartImpl {}
            """;

    @TempDir
    Path tempDir;

    private Path srcMainJava;
    private SpoonCodeAnalyzer analyzer;

    @BeforeEach
    void setUp() throws IOException {
        srcMainJava = tempDir.resolve("src/main/java");
        analyzer = new SpoonCodeAnalyzer();

        // Write annotation definitions
        writeSource("gov/nystax/nimbus/smart/SmartService.java", SMART_SERVICE_ANNOTATION);
        writeSource("gov/nystax/nimbus/smart/SmartImpl.java", SMART_IMPL_ANNOTATION);
    }

    // ===== STRICT MODE (default) =====

    @Test
    void strict_exactlyOneOfEach_succeeds() throws Exception {
        writeSource("sample/MyService.java", """
                package sample;
                import gov.nystax.nimbus.smart.SmartService;
                @SmartService
                public interface MyService {
                    String doWork(String input);
                }
                """);
        writeSource("sample/MyServiceImpl.java", """
                package sample;
                import gov.nystax.nimbus.smart.SmartImpl;
                @SmartImpl
                public class MyServiceImpl implements MyService {
                    public String doWork(String input) { return input; }
                }
                """);

        ProjectInfo info = analyze(false, false, false);

        assertThat(info.getServiceInterface()).isEqualTo("sample.MyService");
        assertThat(info.getServiceImplementation()).isEqualTo("sample.MyServiceImpl");
    }

    @Test
    void strict_multipleInterfaces_throws() throws IOException {
        writeSource("sample/MyService.java", """
                package sample;
                import gov.nystax.nimbus.smart.SmartService;
                @SmartService
                public interface MyService { void doWork(); }
                """);
        writeSource("sample/OtherService.java", """
                package sample;
                import gov.nystax.nimbus.smart.SmartService;
                @SmartService
                public interface OtherService { void doOther(); }
                """);
        writeSource("sample/MyServiceImpl.java", """
                package sample;
                import gov.nystax.nimbus.smart.SmartImpl;
                @SmartImpl
                public class MyServiceImpl implements MyService {
                    public void doWork() {}
                }
                """);

        assertThatThrownBy(() -> analyze(false, false, false))
                .isInstanceOf(CodeViolationException.class)
                .hasMessageContaining("Expected exactly one interface");
    }

    @Test
    void strict_noImpl_throws() throws IOException {
        writeSource("sample/MyService.java", """
                package sample;
                import gov.nystax.nimbus.smart.SmartService;
                @SmartService
                public interface MyService { void doWork(); }
                """);

        assertThatThrownBy(() -> analyze(false, false, false))
                .isInstanceOf(CodeViolationException.class)
                .hasMessageContaining("No class found with @");
    }

    @Test
    void strict_noInterface_throws() throws IOException {
        writeSource("sample/MyServiceImpl.java", """
                package sample;
                import gov.nystax.nimbus.smart.SmartImpl;
                @SmartImpl
                public class MyServiceImpl {
                    public void doWork() {}
                }
                """);

        assertThatThrownBy(() -> analyze(false, false, false))
                .isInstanceOf(CodeViolationException.class)
                .hasMessageContaining("No interface found with @");
    }

    // ===== LENIENT PAIR MATCH =====

    @Test
    void lenientPair_multipleAnnotations_validPairFound() throws Exception {
        writeSource("sample/MyService.java", """
                package sample;
                import gov.nystax.nimbus.smart.SmartService;
                @SmartService
                public interface MyService { void doWork(); }
                """);
        writeSource("sample/OtherService.java", """
                package sample;
                import gov.nystax.nimbus.smart.SmartService;
                @SmartService
                public interface OtherService { void doOther(); }
                """);
        writeSource("sample/MyServiceImpl.java", """
                package sample;
                import gov.nystax.nimbus.smart.SmartImpl;
                @SmartImpl
                public class MyServiceImpl implements MyService {
                    public void doWork() {}
                }
                """);
        writeSource("sample/UnrelatedImpl.java", """
                package sample;
                import gov.nystax.nimbus.smart.SmartImpl;
                @SmartImpl
                public class UnrelatedImpl {
                    public void something() {}
                }
                """);

        ProjectInfo info = analyze(true, false, false);

        assertThat(info.getServiceInterface()).isEqualTo("sample.MyService");
        assertThat(info.getServiceImplementation()).isEqualTo("sample.MyServiceImpl");
    }

    @Test
    void lenientPair_ambiguousMultiplePairs_throws() throws IOException {
        writeSource("sample/ServiceA.java", """
                package sample;
                import gov.nystax.nimbus.smart.SmartService;
                @SmartService
                public interface ServiceA { void a(); }
                """);
        writeSource("sample/ServiceB.java", """
                package sample;
                import gov.nystax.nimbus.smart.SmartService;
                @SmartService
                public interface ServiceB { void b(); }
                """);
        writeSource("sample/ImplA.java", """
                package sample;
                import gov.nystax.nimbus.smart.SmartImpl;
                @SmartImpl
                public class ImplA implements ServiceA {
                    public void a() {}
                }
                """);
        writeSource("sample/ImplB.java", """
                package sample;
                import gov.nystax.nimbus.smart.SmartImpl;
                @SmartImpl
                public class ImplB implements ServiceB {
                    public void b() {}
                }
                """);

        assertThatThrownBy(() -> analyze(true, false, false))
                .isInstanceOf(CodeViolationException.class)
                .hasMessageContaining("Ambiguous service resolution");
    }

    // ===== INFER IMPL =====

    @Test
    void inferImpl_noSmartImplAnnotation_findsImplementor() throws Exception {
        writeSource("sample/MyService.java", """
                package sample;
                import gov.nystax.nimbus.smart.SmartService;
                @SmartService
                public interface MyService { void doWork(); }
                """);
        writeSource("sample/MyServiceImpl.java", """
                package sample;
                public class MyServiceImpl implements MyService {
                    public void doWork() {}
                }
                """);

        ProjectInfo info = analyze(false, true, false);

        assertThat(info.getServiceInterface()).isEqualTo("sample.MyService");
        assertThat(info.getServiceImplementation()).isEqualTo("sample.MyServiceImpl");
    }

    @Test
    void inferImpl_ignoresAbstractBaseAndFindsConcreteSubclass() throws Exception {
        writeSource("sample/MyService.java", """
                package sample;
                import gov.nystax.nimbus.smart.SmartService;
                @SmartService
                public interface MyService { void doWork(); }
                """);
        writeSource("sample/AbstractMyServiceBase.java", """
                package sample;
                public abstract class AbstractMyServiceBase implements MyService {
                }
                """);
        writeSource("sample/MyServiceImpl.java", """
                package sample;
                public class MyServiceImpl extends AbstractMyServiceBase {
                    public void doWork() {}
                }
                """);

        ProjectInfo info = analyze(false, true, false);

        assertThat(info.getServiceInterface()).isEqualTo("sample.MyService");
        assertThat(info.getServiceImplementation()).isEqualTo("sample.MyServiceImpl");
    }

    @Test
    void inferImpl_noImplementorFound_throws() throws IOException {
        writeSource("sample/MyService.java", """
                package sample;
                import gov.nystax.nimbus.smart.SmartService;
                @SmartService
                public interface MyService { void doWork(); }
                """);

        assertThatThrownBy(() -> analyze(false, true, false))
                .isInstanceOf(CodeViolationException.class)
                .hasMessageContaining("No class found that implements");
    }

    @Test
    void inferImpl_multipleImplementors_throws() throws IOException {
        writeSource("sample/MyService.java", """
                package sample;
                import gov.nystax.nimbus.smart.SmartService;
                @SmartService
                public interface MyService { void doWork(); }
                """);
        writeSource("sample/ImplA.java", """
                package sample;
                public class ImplA implements MyService {
                    public void doWork() {}
                }
                """);
        writeSource("sample/ImplB.java", """
                package sample;
                public class ImplB implements MyService {
                    public void doWork() {}
                }
                """);

        assertThatThrownBy(() -> analyze(false, true, false))
                .isInstanceOf(CodeViolationException.class)
                .hasMessageContaining("Multiple classes implement");
    }

    // ===== INFER INTERFACE =====

    @Test
    void inferInterface_noSmartServiceAnnotation_findsInterface() throws Exception {
        writeSource("sample/MyService.java", """
                package sample;
                public interface MyService { void doWork(); }
                """);
        writeSource("sample/MyServiceImpl.java", """
                package sample;
                import gov.nystax.nimbus.smart.SmartImpl;
                @SmartImpl
                public class MyServiceImpl implements MyService {
                    public void doWork() {}
                }
                """);

        ProjectInfo info = analyze(false, false, true);

        assertThat(info.getServiceInterface()).isEqualTo("sample.MyService");
        assertThat(info.getServiceImplementation()).isEqualTo("sample.MyServiceImpl");
    }

    @Test
    void inferInterface_multipleProjectLocalInterfaces_throws() throws IOException {
        writeSource("sample/ServiceA.java", """
                package sample;
                public interface ServiceA { void a(); }
                """);
        writeSource("sample/ServiceB.java", """
                package sample;
                public interface ServiceB { void b(); }
                """);
        writeSource("sample/MyImpl.java", """
                package sample;
                import gov.nystax.nimbus.smart.SmartImpl;
                @SmartImpl
                public class MyImpl implements ServiceA, ServiceB {
                    public void a() {}
                    public void b() {}
                }
                """);

        assertThatThrownBy(() -> analyze(false, false, true))
                .isInstanceOf(CodeViolationException.class)
                .hasMessageContaining("implements multiple project-local interfaces");
    }

    @Test
    void inferInterface_noProjectLocalInterface_throws() throws IOException {
        // Impl only implements a JDK interface (java.io.Serializable) — not project-local
        writeSource("sample/MyImpl.java", """
                package sample;
                import gov.nystax.nimbus.smart.SmartImpl;
                @SmartImpl
                public class MyImpl implements java.io.Serializable {
                    public void doWork() {}
                }
                """);

        assertThatThrownBy(() -> analyze(false, false, true))
                .isInstanceOf(CodeViolationException.class)
                .hasMessageContaining("does not implement any project-local interface");
    }

    // ===== NEITHER FOUND =====

    @Test
    void noAnnotationsAtAll_throws() throws IOException {
        writeSource("sample/PlainClass.java", """
                package sample;
                public class PlainClass { void doWork() {} }
                """);

        assertThatThrownBy(() -> analyze(false, false, false))
                .isInstanceOf(CodeViolationException.class)
                .hasMessageContaining("annotations found");
    }

    // ===== HELPERS =====

    private void writeSource(String relativePath, String content) throws IOException {
        Path file = srcMainJava.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private ProjectInfo analyze(boolean lenientPairMatch, boolean inferImpl, boolean inferInterface) throws Exception {
        ProjectInfo info = new ProjectInfo();
        ServiceResolutionConfig config = new ServiceResolutionConfig(lenientPairMatch, inferImpl, inferInterface);
        analyzer.analyzeSourceCode(tempDir, info, null, config);
        return info;
    }
}
