package gov.nystax.nimbus.codesnap.services.scanner.analyzer;

import gov.nystax.nimbus.codesnap.domain.CodeSnapperConfig.MavenRepository;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Resolves and downloads Maven dependency JARs from configured repositories.
 * <p>
 * Downloads all dependencies declared in a project's pom.xml from one or more
 * Maven repositories (including Artifactory) and returns the paths to the
 * downloaded JARs for use as Spoon's source classpath.
 */
public class MavenDependencyResolver {

    private static final Logger logger = LoggerFactory.getLogger(MavenDependencyResolver.class);

    private static final String JAR_EXTENSION = ".jar";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int HTTP_OK = 200;

    private final List<MavenRepository> repositories;
    private final Path downloadDir;

    /**
     * Creates a new resolver.
     *
     * @param repositories The Maven repositories to download from (tried in order)
     * @param downloadDir  The directory to download JARs into
     */
    public MavenDependencyResolver(List<MavenRepository> repositories, Path downloadDir) {
        this.repositories = repositories;
        this.downloadDir = downloadDir;
    }

    /**
     * Resolves all dependencies from the given POM file and downloads their JARs.
     *
     * @param pomFile Path to the pom.xml file
     * @return List of paths to downloaded JAR files
     */
    public List<Path> resolveAndDownload(Path pomFile) {
        List<Dependency> dependencies = parseDependencies(pomFile);
        if (dependencies.isEmpty()) {
            logger.info("No dependencies found in POM");
            return List.of();
        }

        logger.info("Resolving {} dependencies from {} repositories",
                dependencies.size(), repositories.size());

        try {
            Files.createDirectories(downloadDir);
        } catch (IOException e) {
            logger.error("Failed to create download directory: {}", downloadDir, e);
            return List.of();
        }

        List<Path> downloadedJars = new ArrayList<>();
        int resolved = 0;
        int failed = 0;

        for (Dependency dependency : dependencies) {
            // Skip test and provided scope dependencies
            String scope = dependency.getScope();
            if ("test".equals(scope) || "provided".equals(scope)) {
                logger.debug("Skipping {} scope dependency: {}:{}",
                        scope, dependency.getGroupId(), dependency.getArtifactId());
                continue;
            }

            // Skip non-jar types (pom, war, etc.)
            String type = dependency.getType();
            if (type != null && !"jar".equals(type)) {
                logger.debug("Skipping non-jar dependency: {}:{} (type={})",
                        dependency.getGroupId(), dependency.getArtifactId(), type);
                continue;
            }

            Optional<Path> jarPath = downloadDependency(dependency);
            if (jarPath.isPresent()) {
                downloadedJars.add(jarPath.get());
                resolved++;
            } else {
                failed++;
            }
        }

        logger.info("Dependency resolution complete: {} resolved, {} failed", resolved, failed);
        return downloadedJars;
    }

    /**
     * Parses dependencies from a POM file.
     */
    private List<Dependency> parseDependencies(Path pomFile) {
        try (FileReader reader = new FileReader(pomFile.toFile())) {
            MavenXpp3Reader pomReader = new MavenXpp3Reader();
            Model model = pomReader.read(reader);
            return model.getDependencies() != null ? model.getDependencies() : List.of();
        } catch (Exception e) {
            logger.error("Failed to parse POM file: {}", pomFile, e);
            return List.of();
        }
    }

    /**
     * Attempts to download a single dependency JAR from the configured repositories.
     * Tries each repository in order until one succeeds.
     */
    private Optional<Path> downloadDependency(Dependency dependency) {
        String groupId = dependency.getGroupId();
        String artifactId = dependency.getArtifactId();
        String version = dependency.getVersion();

        if (groupId == null || artifactId == null || version == null) {
            logger.warn("Skipping dependency with missing coordinates: {}:{}:{}",
                    groupId, artifactId, version);
            return Optional.empty();
        }

        String jarFileName = artifactId + "-" + version + JAR_EXTENSION;
        Path targetPath = downloadDir.resolve(jarFileName);

        // Skip if already downloaded
        if (Files.exists(targetPath)) {
            logger.debug("Already downloaded: {}", jarFileName);
            return Optional.of(targetPath);
        }

        // Build the Maven repository path: groupId/artifactId/version/artifactId-version.jar
        String repoPath = groupId.replace('.', '/')
                + "/" + artifactId
                + "/" + version
                + "/" + jarFileName;

        for (MavenRepository repo : repositories) {
            String url = repo.url().endsWith("/")
                    ? repo.url() + repoPath
                    : repo.url() + "/" + repoPath;

            try {
                if (downloadFile(url, targetPath, repo)) {
                    logger.debug("Downloaded {}:{}:{} from {}", groupId, artifactId, version, repo.id());
                    return Optional.of(targetPath);
                }
            } catch (Exception e) {
                logger.debug("Failed to download {}:{}:{} from {}: {}",
                        groupId, artifactId, version, repo.id(), e.getMessage());
            }
        }

        logger.warn("Could not resolve {}:{}:{} from any repository", groupId, artifactId, version);
        return Optional.empty();
    }

    /**
     * Downloads a file from the given URL to the target path.
     *
     * @return true if the download succeeded
     */
    private boolean downloadFile(String url, Path targetPath, MavenRepository repo) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");

            // Add Basic auth if repository has credentials
            if (repo.hasCredentials()) {
                String auth = repo.username() + ":" + repo.password();
                String encoded = Base64.getEncoder().encodeToString(auth.getBytes());
                connection.setRequestProperty("Authorization", "Basic " + encoded);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != HTTP_OK) {
                return false;
            }

            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } finally {
            connection.disconnect();
        }
    }
}
