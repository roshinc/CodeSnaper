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
        ParsedPom pom = parsePom(pomFile);
        if (pom.model == null) {
            return List.of();
        }

        List<Dependency> dependencies = pom.model.getDependencies();
        if (dependencies == null || dependencies.isEmpty()) {
            logger.info("No dependencies found in POM");
            return List.of();
        }

        logger.info("Resolving {} dependencies from {} repositories (properties: {})",
                dependencies.size(), repositories.size(), pom.properties.size());

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

            Optional<Path> jarPath = downloadDependency(dependency, pom.properties);
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
     * Parses the POM file and returns a model with a resolved property map.
     */
    private ParsedPom parsePom(Path pomFile) {
        try (FileReader reader = new FileReader(pomFile.toFile())) {
            MavenXpp3Reader pomReader = new MavenXpp3Reader();
            Model model = pomReader.read(reader);
            Map<String, String> properties = buildPropertyMap(model);
            return new ParsedPom(model, properties);
        } catch (Exception e) {
            logger.error("Failed to parse POM file: {}", pomFile, e);
            return new ParsedPom(null, Map.of());
        }
    }

    /**
     * Builds a property map from the POM model, including built-in Maven properties
     * like project.version, project.groupId, and project.artifactId, plus all
     * user-defined properties from the &lt;properties&gt; section.
     */
    private Map<String, String> buildPropertyMap(Model model) {
        Map<String, String> props = new HashMap<>();

        // Built-in Maven properties
        if (model.getVersion() != null) {
            props.put("project.version", model.getVersion());
            props.put("pom.version", model.getVersion());
        }
        if (model.getGroupId() != null) {
            props.put("project.groupId", model.getGroupId());
            props.put("pom.groupId", model.getGroupId());
        }
        if (model.getArtifactId() != null) {
            props.put("project.artifactId", model.getArtifactId());
            props.put("pom.artifactId", model.getArtifactId());
        }

        // User-defined <properties> section
        if (model.getProperties() != null) {
            for (Map.Entry<Object, Object> entry : model.getProperties().entrySet()) {
                props.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }

        // Resolve properties that reference other properties (one pass)
        Map<String, String> resolved = new HashMap<>(props);
        for (Map.Entry<String, String> entry : resolved.entrySet()) {
            String val = entry.getValue();
            if (val != null && val.contains("${")) {
                entry.setValue(resolveString(val, props));
            }
        }

        return resolved;
    }

    /**
     * Resolves ${property} placeholders in a string using the given property map.
     * Returns the original string if a property cannot be resolved.
     */
    private String resolveString(String value, Map<String, String> properties) {
        if (value == null || !value.contains("${")) {
            return value;
        }

        String result = value;
        // Iterate to handle nested references (up to 5 levels deep)
        for (int i = 0; i < 5 && result.contains("${"); i++) {
            StringBuilder sb = new StringBuilder();
            int pos = 0;
            while (pos < result.length()) {
                int start = result.indexOf("${", pos);
                if (start == -1) {
                    sb.append(result, pos, result.length());
                    break;
                }
                sb.append(result, pos, start);
                int end = result.indexOf('}', start + 2);
                if (end == -1) {
                    sb.append(result, start, result.length());
                    break;
                }
                String key = result.substring(start + 2, end);
                String resolved = properties.get(key);
                if (resolved != null) {
                    sb.append(resolved);
                } else {
                    // Keep the placeholder as-is if unresolvable
                    sb.append(result, start, end + 1);
                }
                pos = end + 1;
            }
            result = sb.toString();
        }
        return result;
    }

    /**
     * Returns true if the string contains unresolved ${...} placeholders.
     */
    private boolean hasUnresolvedPlaceholders(String value) {
        return value != null && value.contains("${");
    }

    /**
     * Attempts to download a single dependency JAR from the configured repositories.
     * Tries each repository in order until one succeeds.
     */
    private Optional<Path> downloadDependency(Dependency dependency, Map<String, String> properties) {
        String groupId = resolveString(dependency.getGroupId(), properties);
        String artifactId = resolveString(dependency.getArtifactId(), properties);
        String version = resolveString(dependency.getVersion(), properties);

        if (groupId == null || artifactId == null || version == null) {
            logger.warn("Skipping dependency with missing coordinates: {}:{}:{}",
                    groupId, artifactId, version);
            return Optional.empty();
        }

        // Skip if any coordinate still has unresolved placeholders
        if (hasUnresolvedPlaceholders(groupId) || hasUnresolvedPlaceholders(artifactId)
                || hasUnresolvedPlaceholders(version)) {
            logger.warn("Skipping dependency with unresolved placeholders: {}:{}:{}",
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
     * Holds a parsed POM model together with its resolved property map.
     */
    private record ParsedPom(Model model, Map<String, String> properties) {}

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
