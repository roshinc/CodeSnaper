package gov.nystax.nimbus.codesnap.services.scanner.analyzer;

import java.nio.file.Path;

/**
 * Configuration for optional Maven classpath resolution during Spoon analysis.
 *
 * @param enabled          whether to resolve Maven dependencies for the classpath
 * @param settingsXmlPath  path to the Maven settings.xml (required when enabled)
 */
public record MavenClasspathConfig(boolean enabled, Path settingsXmlPath) {

    /**
     * Disabled mode: Spoon runs in noClasspath mode without resolving Maven dependencies.
     */
    public static final MavenClasspathConfig DISABLED = new MavenClasspathConfig(false, null);
}
