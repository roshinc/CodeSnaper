package gov.nystax.nimbus.codesnap.services.scanner.analyzer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spoon.Launcher;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpoonLauncherFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void createLauncher_disabledConfig_setsNoClasspath() throws Exception {
        Path srcDir = createSourceDirectory();

        Launcher launcher = SpoonLauncherFactory.createLauncher(
                tempDir, srcDir, MavenClasspathConfig.DISABLED);

        assertThat(launcher.getEnvironment().getNoClasspath()).isTrue();
    }

    @Test
    void createLauncher_disabledConfig_setsAutoImportsAndDisablesComments() throws Exception {
        Path srcDir = createSourceDirectory();

        Launcher launcher = SpoonLauncherFactory.createLauncher(
                tempDir, srcDir, MavenClasspathConfig.DISABLED);

        assertThat(launcher.getEnvironment().isAutoImports()).isTrue();
        assertThat(launcher.getEnvironment().isCommentsEnabled()).isFalse();
    }

    @Test
    void createLauncher_throwsOnNullProjectPath() {
        assertThatThrownBy(() -> SpoonLauncherFactory.createLauncher(
                null, tempDir, MavenClasspathConfig.DISABLED))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("projectPath");
    }

    @Test
    void createLauncher_throwsOnNullSrcPath() {
        assertThatThrownBy(() -> SpoonLauncherFactory.createLauncher(
                tempDir, null, MavenClasspathConfig.DISABLED))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("srcPath");
    }

    @Test
    void createLauncher_throwsOnNullMavenConfig() {
        assertThatThrownBy(() -> SpoonLauncherFactory.createLauncher(
                tempDir, tempDir, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("mavenConfig");
    }

    private Path createSourceDirectory() throws Exception {
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);
        return srcDir;
    }
}
