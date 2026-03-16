package gov.nystax.nimbus.codesnap.services.scanner.analyzer;

import gov.nystax.nimbus.codesnap.domain.CodeSnapperConfig.MavenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Resolves and downloads Maven dependency JARs by invoking Maven's
 * {@code dependency:copy-dependencies} goal on the project.
 * <p>
 * This delegates all resolution to Maven itself, which correctly handles:
 * <ul>
 *   <li>${property} placeholders</li>
 *   <li>Version ranges like [1.0.0,)</li>
 *   <li>Parent POM and BOM inheritance</li>
 *   <li>dependencyManagement</li>
 *   <li>Transitive dependencies</li>
 *   <li>Repository authentication via settings.xml</li>
 * </ul>
 * <p>
 * If {@link MavenRepository} entries are provided, a temporary {@code settings.xml}
 * is generated on the fly with the repository URLs and credentials, so no
 * pre-existing settings.xml is required.
 */
public class MavenDependencyResolver {

    private static final Logger logger = LoggerFactory.getLogger(MavenDependencyResolver.class);

    private static final long MAVEN_TIMEOUT_MINUTES = 10;
    private static final String OUTPUT_DIR_NAME = "codesnap-deps";

    private final Path mavenHome;
    private final Path settingsFile;
    private final List<MavenRepository> repositories;

    /**
     * Creates a new resolver.
     *
     * @param mavenHome    Path to Maven installation (containing bin/mvn). If null,
     *                     assumes {@code mvn} is on the system PATH.
     * @param settingsFile Optional path to an existing Maven settings.xml file.
     *                     If null and repositories are provided, a settings.xml is
     *                     generated automatically. If null and no repositories are
     *                     provided, Maven uses its default settings (~/.m2/settings.xml).
     * @param repositories Maven repository configurations. If non-empty, a settings.xml
     *                     is generated with these repos and their credentials.
     */
    public MavenDependencyResolver(Path mavenHome, Path settingsFile, List<MavenRepository> repositories) {
        this.mavenHome = mavenHome;
        this.settingsFile = settingsFile;
        this.repositories = repositories != null ? repositories : List.of();
    }

    /**
     * Resolves all dependencies for the Maven project at the given path and
     * copies the JARs into a local directory.
     *
     * @param projectPath Path to the Maven project root (containing pom.xml)
     * @return List of paths to downloaded JAR files
     */
    public List<Path> resolveAndDownload(Path projectPath) {
        Path outputDir = projectPath.resolve("target").resolve(OUTPUT_DIR_NAME);

        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            logger.error("Failed to create dependency output directory: {}", outputDir, e);
            return List.of();
        }

        // Determine which settings.xml to use
        Path effectiveSettings = resolveSettingsFile(projectPath);

        List<String> command = buildMavenCommand(projectPath, outputDir, effectiveSettings);
        logger.info("Invoking Maven dependency resolution: {}", String.join(" ", command));

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(projectPath.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Capture and log Maven output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                    if (line.contains("ERROR") || line.contains("WARNING")) {
                        logger.warn("mvn: {}", line);
                    } else {
                        logger.debug("mvn: {}", line);
                    }
                }
            }

            boolean finished = process.waitFor(MAVEN_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                logger.error("Maven dependency resolution timed out after {} minutes", MAVEN_TIMEOUT_MINUTES);
                return List.of();
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logger.error("Maven dependency resolution failed with exit code {}. Output:\n{}",
                        exitCode, output);
                return List.of();
            }

            return collectJars(outputDir);

        } catch (IOException e) {
            logger.error("Failed to invoke Maven", e);
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Maven dependency resolution was interrupted", e);
            return List.of();
        }
    }

    /**
     * Determines which settings.xml to use:
     * 1. If an explicit settingsFile was provided, use it.
     * 2. If MavenRepository entries were provided, generate a temporary settings.xml.
     * 3. Otherwise, return null (Maven uses its default ~/.m2/settings.xml).
     */
    private Path resolveSettingsFile(Path projectPath) {
        if (settingsFile != null && Files.exists(settingsFile)) {
            return settingsFile;
        }
        if (!repositories.isEmpty()) {
            return generateSettingsXml(projectPath);
        }
        return null;
    }

    /**
     * Generates a temporary Maven settings.xml containing the configured
     * repositories and their credentials (as servers).
     */
    private Path generateSettingsXml(Path projectPath) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<settings xmlns=\"http://maven.apache.org/SETTINGS/1.2.0\"\n");
        xml.append("          xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        xml.append("          xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.2.0 ");
        xml.append("https://maven.apache.org/xsd/settings-1.2.0.xsd\">\n");

        // Servers (credentials)
        List<MavenRepository> authenticated = repositories.stream()
                .filter(MavenRepository::hasCredentials)
                .toList();
        if (!authenticated.isEmpty()) {
            xml.append("  <servers>\n");
            for (MavenRepository repo : authenticated) {
                xml.append("    <server>\n");
                xml.append("      <id>").append(escapeXml(repo.id())).append("</id>\n");
                xml.append("      <username>").append(escapeXml(repo.username())).append("</username>\n");
                xml.append("      <password>").append(escapeXml(repo.password())).append("</password>\n");
                xml.append("    </server>\n");
            }
            xml.append("  </servers>\n");
        }

        // Profiles with repositories
        xml.append("  <profiles>\n");
        xml.append("    <profile>\n");
        xml.append("      <id>codesnap-repos</id>\n");
        xml.append("      <repositories>\n");
        for (MavenRepository repo : repositories) {
            xml.append("        <repository>\n");
            xml.append("          <id>").append(escapeXml(repo.id())).append("</id>\n");
            xml.append("          <url>").append(escapeXml(repo.url())).append("</url>\n");
            xml.append("          <releases><enabled>true</enabled></releases>\n");
            xml.append("          <snapshots><enabled>true</enabled></snapshots>\n");
            xml.append("        </repository>\n");
        }
        xml.append("      </repositories>\n");
        xml.append("    </profile>\n");
        xml.append("  </profiles>\n");

        // Activate the profile
        xml.append("  <activeProfiles>\n");
        xml.append("    <activeProfile>codesnap-repos</activeProfile>\n");
        xml.append("  </activeProfiles>\n");

        xml.append("</settings>\n");

        try {
            Path settingsPath = projectPath.resolve("target").resolve("codesnap-settings.xml");
            Files.createDirectories(settingsPath.getParent());
            Files.writeString(settingsPath, xml.toString());
            logger.info("Generated settings.xml with {} repositories at {}", repositories.size(), settingsPath);
            return settingsPath;
        } catch (IOException e) {
            logger.error("Failed to generate settings.xml", e);
            return null;
        }
    }

    /**
     * Escapes special XML characters in a string.
     */
    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Builds the Maven command to copy dependencies.
     */
    private List<String> buildMavenCommand(Path projectPath, Path outputDir, Path effectiveSettings) {
        List<String> command = new ArrayList<>();

        // Maven executable
        if (mavenHome != null) {
            Path mvnBin = mavenHome.resolve("bin").resolve("mvn");
            command.add(mvnBin.toString());
        } else {
            command.add("mvn");
        }

        // Goal: copy all dependency JARs to output directory
        command.add("dependency:copy-dependencies");

        // Output directory for the copied JARs
        command.add("-DoutputDirectory=" + outputDir.toAbsolutePath());

        // Exclude test and provided scope
        command.add("-DincludeScope=runtime");

        // Skip building the project itself — just resolve deps
        command.add("-DskipTests");

        // Non-interactive / batch mode
        command.add("-B");

        // Settings file (explicit, generated, or Maven default)
        if (effectiveSettings != null) {
            command.add("-s");
            command.add(effectiveSettings.toAbsolutePath().toString());
        }

        // POM file
        command.add("-f");
        command.add(projectPath.resolve("pom.xml").toAbsolutePath().toString());

        return command;
    }

    /**
     * Collects all JAR files from the output directory.
     */
    private List<Path> collectJars(Path outputDir) {
        List<Path> jars = new ArrayList<>();
        try (Stream<Path> files = Files.list(outputDir)) {
            files.filter(p -> p.toString().endsWith(".jar"))
                    .forEach(jars::add);
        } catch (IOException e) {
            logger.error("Failed to list JARs in {}", outputDir, e);
        }
        logger.info("Collected {} dependency JARs from {}", jars.size(), outputDir);
        return jars;
    }
}
