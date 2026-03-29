package gov.nystax.nimbus.codesnap.services.scanner.analyzer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MavenDependencyResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void constructor_throwsOnNullSettingsFile() {
        assertThatThrownBy(() -> new MavenDependencyResolver(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("settings.xml path cannot be null");
    }

    @Test
    void constructor_throwsOnNonexistentSettingsFile() {
        Path nonexistent = tempDir.resolve("nonexistent-settings.xml");

        assertThatThrownBy(() -> new MavenDependencyResolver(nonexistent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("settings.xml path does not exist");
    }

    @Test
    void constructor_acceptsExistingSettingsFile() throws Exception {
        Path settingsXml = createSettingsXml();

        MavenDependencyResolver resolver = new MavenDependencyResolver(settingsXml);

        assertThat(resolver).isNotNull();
    }

    @Test
    void constructor_acceptsExistingMavenHomePath() throws Exception {
        Path settingsXml = createSettingsXml();
        Path mavenHome = Files.createDirectories(tempDir.resolve("apache-maven"));

        MavenDependencyResolver resolver = new MavenDependencyResolver(settingsXml, mavenHome);

        assertThat(resolver).isNotNull();
        assertThat(resolver.resolveMavenExecutable()).startsWith(mavenHome.toAbsolutePath().toString());
    }

    @Test
    void constructor_throwsOnNonexistentMavenHomePath() throws Exception {
        Path settingsXml = createSettingsXml();
        Path nonexistentMavenHome = tempDir.resolve("missing-maven-home");

        assertThatThrownBy(() -> new MavenDependencyResolver(settingsXml, nonexistentMavenHome))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mavenHome path does not exist");
    }

    @Test
    void buildMavenCommand_usesSuppliedMavenHomeExecutable() throws Exception {
        Path settingsXml = createSettingsXml();
        Path mavenHome = Files.createDirectories(tempDir.resolve("apache-maven"));
        Path projectDir = Files.createDirectories(tempDir.resolve("project"));
        Path outputDir = Files.createDirectories(projectDir.resolve("target/codesnap-deps"));
        MavenDependencyResolver resolver = new MavenDependencyResolver(settingsXml, mavenHome);

        List<String> command = resolver.buildMavenCommand(projectDir, outputDir);

        assertThat(command.get(0)).isEqualTo(resolver.resolveMavenExecutable());
        assertThat(command.get(0)).startsWith(mavenHome.toAbsolutePath().toString());
    }

    @Test
    void resolveAndDownload_returnsEmptyListWhenMavenFails() throws Exception {
        Path settingsXml = createSettingsXml();
        MavenDependencyResolver resolver = new MavenDependencyResolver(settingsXml);

        // Project path with no pom.xml — Maven will fail
        Path emptyProject = tempDir.resolve("empty-project");
        Files.createDirectories(emptyProject);

        List<Path> jars = resolver.resolveAndDownload(emptyProject);

        assertThat(jars).isEmpty();
    }

    @Test
    void resolveAndDownload_throwsOnNullProjectPath() throws Exception {
        Path settingsXml = createSettingsXml();
        MavenDependencyResolver resolver = new MavenDependencyResolver(settingsXml);

        assertThatThrownBy(() -> resolver.resolveAndDownload(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("projectPath");
    }

    private Path createSettingsXml() throws Exception {
        Path settingsXml = tempDir.resolve("settings.xml");
        Files.writeString(settingsXml, "<settings/>");
        return settingsXml;
    }
}
