package gov.nystax.nimbus.codesnap;

import gov.nystax.nimbus.codesnap.domain.CodeSnapperConfig;
import gov.nystax.nimbus.codesnap.exception.CodeSnapErrorCategory;
import gov.nystax.nimbus.codesnap.exception.ProcessingException;
import gov.nystax.nimbus.codesnap.services.scanner.NimbaProjectScanner;
import gov.nystax.nimbus.codesnap.services.scanner.NimbusServiceProjectScanner;
import gov.nystax.nimbus.codesnap.services.scanner.ProjectScanner;
import gov.nystax.nimbus.codesnap.services.scanner.domain.ProjectInfo;
import gov.nystax.nimbus.codesnap.services.scanner.observability.ScanContext;
import gov.nystax.nimbus.codesnap.services.scanner.observability.ScanProgressListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class DefaultCodeSnapperTest {

    @TempDir
    Path tempDir;

    @Test
    void isNimbaProject_returnsTrueWhenGroupIdContainsNimba() {
        ProjectInfo projectInfo = new ProjectInfo();
        projectInfo.setGroupId("gov.nystax.nimba.services");

        assertThat(DefaultCodeSnapper.isNimbaProject(projectInfo)).isTrue();
    }

    @Test
    void isNimbaProject_returnsFalseWhenGroupIdDoesNotContainNimba() {
        ProjectInfo projectInfo = new ProjectInfo();
        projectInfo.setGroupId("gov.nystax.nimbus.services");

        assertThat(DefaultCodeSnapper.isNimbaProject(projectInfo)).isFalse();
    }

    @Test
    void createProjectScanner_returnsNimbaScannerForNimbaProject() {
        DefaultCodeSnapper snapper = new DefaultCodeSnapper(testConfig());
        ProjectInfo projectInfo = new ProjectInfo();
        projectInfo.setGroupId("gov.nystax.nimba.services");

        try (ScanContext context = new ScanContext("", ScanProgressListener.loggingListener())) {
            ProjectScanner scanner = snapper.createProjectScanner(projectInfo, context);
            assertThat(scanner).isInstanceOf(NimbaProjectScanner.class);
        }
    }

    @Test
    void createProjectScanner_returnsNimbusScannerForNonNimbaProject() {
        DefaultCodeSnapper snapper = new DefaultCodeSnapper(testConfig());
        ProjectInfo projectInfo = new ProjectInfo();
        projectInfo.setGroupId("gov.nystax.nimbus.services");

        try (ScanContext context = new ScanContext("", ScanProgressListener.loggingListener())) {
            ProjectScanner scanner = snapper.createProjectScanner(projectInfo, context);
            assertThat(scanner).isInstanceOf(NimbusServiceProjectScanner.class);
        }
    }

    @Test
    void config_requiresSettingsXmlWhenMavenClasspathResolutionIsEnabled() {
        ProcessingException exception = catchThrowableOfType(() -> CodeSnapperConfig.builder()
                .serviceId("sample-service")
                .commitHash("abc123")
                .gitGroups(List.of("sample-group"))
                .gitToken("token")
                .localTempRootPath(tempDir)
                .resolveMavenClasspath(true)
                .build(), ProcessingException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getCategory()).isEqualTo(CodeSnapErrorCategory.PROCESSING_ERROR);
        assertThat(exception).hasMessageContaining("Maven settings.xml path is required");
    }

    @Test
    void config_acceptsExistingSettingsXmlWhenMavenClasspathResolutionIsEnabled() throws Exception {
        Path settingsXml = tempDir.resolve("settings.xml");
        Files.writeString(settingsXml, "<settings/>");

        CodeSnapperConfig config = CodeSnapperConfig.builder()
                .serviceId("sample-service")
                .commitHash("abc123")
                .gitGroups(List.of("sample-group"))
                .gitToken("token")
                .localTempRootPath(tempDir)
                .resolveMavenClasspath(true)
                .mavenSettingsXmlPath(settingsXml)
                .build();

        assertThat(config.resolveMavenClasspath()).isTrue();
        assertThat(config.mavenSettingsXmlPath()).isEqualTo(settingsXml);
    }

    @Test
    void config_acceptsOptionalMavenHomePath() throws Exception {
        Path settingsXml = tempDir.resolve("settings.xml");
        Path mavenHome = Files.createDirectories(tempDir.resolve("apache-maven"));
        Files.writeString(settingsXml, "<settings/>");

        CodeSnapperConfig config = CodeSnapperConfig.builder()
                .serviceId("sample-service")
                .commitHash("abc123")
                .gitGroups(List.of("sample-group"))
                .gitToken("token")
                .localTempRootPath(tempDir)
                .resolveMavenClasspath(true)
                .mavenSettingsXmlPath(settingsXml)
                .mavenHomePath(mavenHome)
                .build();

        assertThat(config.mavenHomePath()).isEqualTo(mavenHome);
    }

    @Test
    void constructor_throwsProcessingExceptionWhenConfigIsNull() {
        assertThatThrownBy(() -> new DefaultCodeSnapper(null))
                .isInstanceOf(ProcessingException.class)
                .hasMessageContaining("Invalid Snapper Config");
    }

    @Test
    void config_aggregatesMissingRequiredFieldsAsProcessingException() {
        ProcessingException exception = catchThrowableOfType(() -> CodeSnapperConfig.builder().build(),
                ProcessingException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getCategory()).isEqualTo(CodeSnapErrorCategory.PROCESSING_ERROR);
        assertThat(exception).hasMessageContaining("Service Id is null or empty");
        assertThat(exception).hasMessageContaining("Git Groups are empty");
        assertThat(exception).hasMessageContaining("Git Token is null or empty");
        assertThat(exception).hasMessageContaining("Local Temp Root Path is null");
    }

    @Test
    void config_rejectsMissingSettingsFileAsProcessingException() {
        Path missingSettingsXml = tempDir.resolve("missing-settings.xml");

        ProcessingException exception = catchThrowableOfType(() -> CodeSnapperConfig.builder()
                .serviceId("sample-service")
                .commitHash("abc123")
                .gitGroups(List.of("sample-group"))
                .gitToken("token")
                .localTempRootPath(tempDir)
                .resolveMavenClasspath(true)
                .mavenSettingsXmlPath(missingSettingsXml)
                .build(), ProcessingException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getCategory()).isEqualTo(CodeSnapErrorCategory.PROCESSING_ERROR);
        assertThat(exception).hasMessageContaining("Maven settings.xml path does not exist");
    }

    @Test
    void config_rejectsMissingMavenHomePathAsProcessingException() throws Exception {
        Path settingsXml = tempDir.resolve("settings.xml");
        Path missingMavenHome = tempDir.resolve("missing-maven-home");
        Files.writeString(settingsXml, "<settings/>");

        ProcessingException exception = catchThrowableOfType(() -> CodeSnapperConfig.builder()
                .serviceId("sample-service")
                .commitHash("abc123")
                .gitGroups(List.of("sample-group"))
                .gitToken("token")
                .localTempRootPath(tempDir)
                .resolveMavenClasspath(true)
                .mavenSettingsXmlPath(settingsXml)
                .mavenHomePath(missingMavenHome)
                .build(), ProcessingException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getCategory()).isEqualTo(CodeSnapErrorCategory.PROCESSING_ERROR);
        assertThat(exception).hasMessageContaining("Maven home path does not exist");
    }

    private CodeSnapperConfig testConfig() {
        return CodeSnapperConfig.builder()
                .serviceId("sample-service")
                .commitHash("abc123")
                .gitGroups(List.of("sample-group"))
                .gitToken("token")
                .localTempRootPath(tempDir)
                .build();
    }
}
