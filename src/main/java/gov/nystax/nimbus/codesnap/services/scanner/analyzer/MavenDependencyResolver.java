package gov.nystax.nimbus.codesnap.services.scanner.analyzer;

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
 */
public class MavenDependencyResolver {

    private static final Logger logger = LoggerFactory.getLogger(MavenDependencyResolver.class);

    private static final long MAVEN_TIMEOUT_MINUTES = 10;
    private static final String OUTPUT_DIR_NAME = "codesnap-deps";

    private final Path mavenHome;
    private final Path settingsFile;

    /**
     * Creates a new resolver.
     *
     * @param mavenHome    Path to Maven installation (containing bin/mvn). If null,
     *                     assumes {@code mvn} is on the system PATH.
     * @param settingsFile Optional path to a Maven settings.xml file (e.g., for
     *                     Artifactory credentials). If null, Maven uses its defaults.
     */
    public MavenDependencyResolver(Path mavenHome, Path settingsFile) {
        this.mavenHome = mavenHome;
        this.settingsFile = settingsFile;
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

        List<String> command = buildMavenCommand(projectPath, outputDir);
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
     * Builds the Maven command to copy dependencies.
     */
    private List<String> buildMavenCommand(Path projectPath, Path outputDir) {
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

        // Optional settings.xml
        if (settingsFile != null && Files.exists(settingsFile)) {
            command.add("-s");
            command.add(settingsFile.toAbsolutePath().toString());
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
