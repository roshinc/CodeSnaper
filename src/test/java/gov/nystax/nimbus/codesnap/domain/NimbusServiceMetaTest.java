package gov.nystax.nimbus.codesnap.domain;

import gov.nystax.nimbus.codesnap.exception.CodeSnapErrorCategory;
import gov.nystax.nimbus.codesnap.exception.ProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class NimbusServiceMetaTest {

    @TempDir
    Path tempDir;

    @Test
    void pomPath_usesNestedStructureByDefault() {
        CodeSnapperConfig config = CodeSnapperConfig.builder()
                .serviceId("sample-service")
                .commitHash("abc123")
                .gitGroups(List.of("sample-group"))
                .gitToken("token")
                .localTempRootPath(tempDir)
                .build();

        NimbusServiceMeta meta = new NimbusServiceMeta(config);

        assertThat(meta.getLocalServiceRootPath())
                .isEqualTo(tempDir.resolve("abc123").resolve("sample-service"));
        assertThat(meta.getLocalServicePomPath())
                .isEqualTo(tempDir.resolve("abc123").resolve("sample-service")
                        .resolve("sample-service").resolve("pom.xml"));
    }

    @Test
    void pomPath_usesFlatStructureWhenFlagIsTrue() {
        CodeSnapperConfig config = CodeSnapperConfig.builder()
                .serviceId("sample-service")
                .commitHash("abc123")
                .gitGroups(List.of("sample-group"))
                .gitToken("token")
                .localTempRootPath(tempDir)
                .flatProjectStructure(true)
                .build();

        NimbusServiceMeta meta = new NimbusServiceMeta(config);

        assertThat(meta.getLocalServiceRootPath())
                .isEqualTo(tempDir.resolve("abc123").resolve("sample-service"));
        assertThat(meta.getLocalServicePomPath())
                .isEqualTo(tempDir.resolve("abc123").resolve("sample-service").resolve("pom.xml"));
    }

    @Test
    void constructor_wrapsMalformedGitMetadataAsProcessingException() {
        CodeSnapperConfig config = CodeSnapperConfig.builder()
                .serviceId("sample-service")
                .commitHash("abc123")
                .gitGroups(List.of("sample-group"))
                .gitToken("token")
                .localTempRootPath(tempDir)
                .build();

        ProcessingException exception = catchThrowableOfType(
                () -> new NimbusServiceMeta(config, Optional.of("not a valid url")),
                ProcessingException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getCategory()).isEqualTo(CodeSnapErrorCategory.PROCESSING_ERROR);
        assertThat(exception).hasMessage("Invalid Git repository metadata");
        assertThat(exception.getCause()).isInstanceOf(MalformedURLException.class);
    }
}
