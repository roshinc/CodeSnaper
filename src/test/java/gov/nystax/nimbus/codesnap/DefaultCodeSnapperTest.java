package gov.nystax.nimbus.codesnap;

import gov.nystax.nimbus.codesnap.domain.CodeSnapperConfig;
import gov.nystax.nimbus.codesnap.services.scanner.NimbaProjectScanner;
import gov.nystax.nimbus.codesnap.services.scanner.NimbusServiceProjectScanner;
import gov.nystax.nimbus.codesnap.services.scanner.ProjectScanner;
import gov.nystax.nimbus.codesnap.services.scanner.domain.ProjectInfo;
import gov.nystax.nimbus.codesnap.services.scanner.observability.ScanContext;
import gov.nystax.nimbus.codesnap.services.scanner.observability.ScanProgressListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
