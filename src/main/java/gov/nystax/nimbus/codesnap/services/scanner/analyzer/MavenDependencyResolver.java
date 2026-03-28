package gov.nystax.nimbus.codesnap.services.scanner.analyzer;

import com.google.common.base.Preconditions;
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
 * Resolves Maven dependencies by invoking {@code dependency:copy-dependencies}
 * with an explicit {@code settings.xml} file.
 */
public class MavenDependencyResolver {

    private static final Logger logger = LoggerFactory.getLogger(MavenDependencyResolver.class);

    private static final long MAVEN_TIMEOUT_MINUTES = 10;
    private static final String OUTPUT_DIR_NAME = "codesnap-deps";

    private final Path settingsFile;

    public MavenDependencyResolver(Path settingsFile) {
        Preconditions.checkNotNull(settingsFile, "settings.xml path cannot be null");
        Preconditions.checkArgument(Files.isRegularFile(settingsFile),
                "settings.xml path does not exist: %s", settingsFile);
        this.settingsFile = settingsFile;
    }

    public List<Path> resolveAndDownload(Path projectPath) {
        Preconditions.checkNotNull(projectPath, "projectPath cannot be null");

        Path outputDir = projectPath.resolve("target").resolve(OUTPUT_DIR_NAME);
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            logger.error("Failed to create dependency output directory: {}", outputDir, e);
            return List.of();
        }

        List<String> command = buildMavenCommand(projectPath, outputDir);
        logger.info("Invoking Maven dependency resolution for {}", projectPath);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(projectPath.toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            String output = readProcessOutput(process);

            boolean finished = process.waitFor(MAVEN_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                logger.error("Maven dependency resolution timed out after {} minutes", MAVEN_TIMEOUT_MINUTES);
                return List.of();
            }

            if (process.exitValue() != 0) {
                logger.error("Maven dependency resolution failed with exit code {}. Output:\n{}",
                        process.exitValue(), output);
                return List.of();
            }

            return collectJars(outputDir);
        } catch (IOException e) {
            logger.error("Failed to invoke Maven for dependency resolution", e);
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Maven dependency resolution was interrupted", e);
            return List.of();
        }
    }

    private String readProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
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
        return output.toString();
    }

    private List<String> buildMavenCommand(Path projectPath, Path outputDir) {
        List<String> command = new ArrayList<>();
        command.add(isWindows() ? "mvn.cmd" : "mvn");
        command.add("dependency:copy-dependencies");
        command.add("-DoutputDirectory=" + outputDir.toAbsolutePath());
        command.add("-DincludeScope=compile");
        command.add("-DskipTests");
        command.add("-B");
        command.add("-s");
        command.add(settingsFile.toAbsolutePath().toString());
        command.add("-f");
        command.add(projectPath.resolve("pom.xml").toAbsolutePath().toString());
        return command;
    }

    private List<Path> collectJars(Path outputDir) {
        List<Path> jars = new ArrayList<>();
        try (Stream<Path> files = Files.list(outputDir)) {
            files.filter(path -> path.toString().endsWith(".jar"))
                    .forEach(jars::add);
        } catch (IOException e) {
            logger.error("Failed to list JARs in {}", outputDir, e);
        }

        logger.info("Collected {} dependency JARs from {}", jars.size(), outputDir);
        return jars;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
