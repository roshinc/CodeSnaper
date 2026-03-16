package gov.nystax.nimbus.codesnap.services.scanner.analyzer;

import gov.nystax.nimbus.codesnap.domain.CodeSnapperConfig.MavenRepository;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // ========================================================================
    // Version range resolution
    // ========================================================================

    /**
     * Pattern matching Maven version range syntax:
     * [1.0,), [1.0,2.0], (1.0,2.0), [1.0,2.0), etc.
     */
    private static final Pattern VERSION_RANGE_PATTERN = Pattern.compile("^[\\[\\(].*[\\]\\)]$");

    /**
     * Returns true if the version string is a Maven version range expression.
     * Examples: [1.0.0,), [1.0,2.0], (,2.0)
     */
    private boolean isVersionRange(String version) {
        return version != null && VERSION_RANGE_PATTERN.matcher(version.trim()).matches();
    }

    /**
     * Resolves a Maven version range (e.g., [1.0.0,)) to a concrete version by
     * fetching maven-metadata.xml from the configured repositories and picking
     * the best matching version.
     *
     * @return the resolved concrete version, or empty if unresolvable
     */
    private Optional<String> resolveVersionRange(String groupId, String artifactId, String rangeSpec) {
        List<String> availableVersions = fetchAvailableVersions(groupId, artifactId);
        if (availableVersions.isEmpty()) {
            logger.warn("No versions found in maven-metadata.xml for {}:{}", groupId, artifactId);
            return Optional.empty();
        }

        Optional<String> resolved = pickVersionFromRange(rangeSpec, availableVersions);
        if (resolved.isPresent()) {
            logger.info("Resolved version range {} -> {} for {}:{}", rangeSpec, resolved.get(), groupId, artifactId);
        } else {
            logger.warn("No version matched range {} for {}:{} (available: {})",
                    rangeSpec, groupId, artifactId, availableVersions);
        }
        return resolved;
    }

    /**
     * Fetches available versions for an artifact from maven-metadata.xml across
     * all configured repositories.
     */
    private List<String> fetchAvailableVersions(String groupId, String artifactId) {
        String metadataPath = groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml";

        for (MavenRepository repo : repositories) {
            String url = repo.url().endsWith("/")
                    ? repo.url() + metadataPath
                    : repo.url() + "/" + metadataPath;

            try {
                String xml = downloadString(url, repo);
                if (xml != null) {
                    List<String> versions = parseVersionsFromMetadata(xml);
                    if (!versions.isEmpty()) {
                        return versions;
                    }
                }
            } catch (Exception e) {
                logger.debug("Failed to fetch metadata for {}:{} from {}: {}",
                        groupId, artifactId, repo.id(), e.getMessage());
            }
        }
        return List.of();
    }

    /**
     * Parses &lt;version&gt; elements from a maven-metadata.xml string.
     */
    private List<String> parseVersionsFromMetadata(String xml) {
        List<String> versions = new ArrayList<>();
        // Simple XML parsing — extract <version>...</version> inside <versions>
        Pattern versionTag = Pattern.compile("<version>([^<]+)</version>");
        // Find the <versions> block to avoid picking up the top-level <version>
        int versionsStart = xml.indexOf("<versions>");
        int versionsEnd = xml.indexOf("</versions>");
        if (versionsStart == -1 || versionsEnd == -1) {
            return versions;
        }
        String versionsBlock = xml.substring(versionsStart, versionsEnd);
        Matcher matcher = versionTag.matcher(versionsBlock);
        while (matcher.find()) {
            versions.add(matcher.group(1).trim());
        }
        return versions;
    }

    /**
     * Picks the best version from the available list that satisfies the given Maven
     * version range specification.
     * <p>
     * Supports: [min,max], [min,max), (min,max], (min,max), [min,), (min,), (,max], (,max)
     * Where [ = inclusive, ( = exclusive, empty bound = unbounded.
     */
    private Optional<String> pickVersionFromRange(String rangeSpec, List<String> availableVersions) {
        String spec = rangeSpec.trim();
        if (spec.length() < 3) {
            return Optional.empty();
        }

        boolean lowerInclusive = spec.charAt(0) == '[';
        boolean upperInclusive = spec.charAt(spec.length() - 1) == ']';
        String inner = spec.substring(1, spec.length() - 1);
        String[] parts = inner.split(",", -1);

        if (parts.length != 2) {
            return Optional.empty();
        }

        String lowerBound = parts[0].trim();
        String upperBound = parts[1].trim();

        // Filter versions that satisfy the range
        List<String> matching = new ArrayList<>();
        for (String v : availableVersions) {
            // Skip SNAPSHOT versions
            if (v.contains("SNAPSHOT")) {
                continue;
            }

            boolean satisfiesLower = true;
            boolean satisfiesUpper = true;

            if (!lowerBound.isEmpty()) {
                int cmp = compareVersions(v, lowerBound);
                satisfiesLower = lowerInclusive ? cmp >= 0 : cmp > 0;
            }

            if (!upperBound.isEmpty()) {
                int cmp = compareVersions(v, upperBound);
                satisfiesUpper = upperInclusive ? cmp <= 0 : cmp < 0;
            }

            if (satisfiesLower && satisfiesUpper) {
                matching.add(v);
            }
        }

        if (matching.isEmpty()) {
            return Optional.empty();
        }

        // Return the highest matching version
        matching.sort((a, b) -> compareVersions(b, a));
        return Optional.of(matching.get(0));
    }

    /**
     * Compares two Maven version strings numerically segment by segment.
     * Handles versions like 1.0.0, 1.2.3, 2.0.0-RC1, etc.
     *
     * @return negative if v1 &lt; v2, 0 if equal, positive if v1 &gt; v2
     */
    static int compareVersions(String v1, String v2) {
        // Split on dots and dashes
        String[] parts1 = v1.split("[.\\-]");
        String[] parts2 = v2.split("[.\\-]");
        int len = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < len; i++) {
            String p1 = i < parts1.length ? parts1[i] : "0";
            String p2 = i < parts2.length ? parts2[i] : "0";

            // Try numeric comparison first
            try {
                int n1 = Integer.parseInt(p1);
                int n2 = Integer.parseInt(p2);
                if (n1 != n2) return Integer.compare(n1, n2);
            } catch (NumberFormatException e) {
                // Fall back to string comparison (handles qualifiers like RC1, alpha, beta)
                // Numeric parts are "greater" than qualifier parts
                boolean p1Numeric = isNumeric(p1);
                boolean p2Numeric = isNumeric(p2);
                if (p1Numeric && !p2Numeric) return 1;   // 1.0.0 > 1.0.0-RC1
                if (!p1Numeric && p2Numeric) return -1;
                int cmp = p1.compareToIgnoreCase(p2);
                if (cmp != 0) return cmp;
            }
        }
        return 0;
    }

    private static boolean isNumeric(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Downloads a URL's content as a String (for maven-metadata.xml).
     *
     * @return the content string, or null if the request failed
     */
    private String downloadString(String url, MavenRepository repo) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");

            if (repo.hasCredentials()) {
                String auth = repo.username() + ":" + repo.password();
                String encoded = Base64.getEncoder().encodeToString(auth.getBytes());
                connection.setRequestProperty("Authorization", "Basic " + encoded);
            }

            if (connection.getResponseCode() != HTTP_OK) {
                return null;
            }

            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                in.transferTo(out);
                return out.toString(StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    // ========================================================================
    // Dependency download
    // ========================================================================

    /**
     * Attempts to download a single dependency JAR from the configured repositories.
     * Tries each repository in order until one succeeds.
     * Resolves version ranges and ${property} placeholders before downloading.
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

        // Resolve version ranges like [1.0.0,) to a concrete version
        if (isVersionRange(version)) {
            Optional<String> resolved = resolveVersionRange(groupId, artifactId, version);
            if (resolved.isEmpty()) {
                logger.warn("Could not resolve version range {} for {}:{}", version, groupId, artifactId);
                return Optional.empty();
            }
            version = resolved.get();
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
