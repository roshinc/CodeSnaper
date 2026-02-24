package gov.nystax.nimbus.codesnap.services.scanner.analyzer;

import com.google.common.base.Preconditions;
import gov.nystax.nimbus.codesnap.services.scanner.domain.ProjectInfo;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for analyzing Maven project structure and extracting POM information.
 */
public class MavenProjectAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(MavenProjectAnalyzer.class);

    // File and path constants
    private static final String POM_XML = "pom.xml";
    private static final String SRC_MAIN_JAVA = "src/main/java";
    private static final String JAVA_FILE_EXTENSION = ".java";
    private static final String DEFAULT_PACKAGING = "jar";

    // Dependency types
    private static final String DEPENDENCY_TYPE_SERVICE = "service";
    private static final String DEPENDENCY_TYPE_FUNCTION = "function";

    // Formatting
    private static final String COLON_SEPARATOR = ":";

    /**
     * Analyzes a Maven project and extracts basic information.
     *
     * @param projectPath The path to the Maven project
     * @return ProjectInfo containing the analyzed information
     * @throws Exception if there's an error during analysis
     */
    public ProjectInfo analyzeProject(Path projectPath) throws Exception {
        Preconditions.checkNotNull(projectPath, "Project path cannot be null");
        Preconditions.checkArgument(Files.exists(projectPath), "Project path does not exist: " + projectPath);

        logger.info("Analyzing Maven project at: {}", projectPath);

        Path pomPath = projectPath.resolve(POM_XML);
        if (!Files.exists(pomPath)) {
            throw new IllegalArgumentException("No pom.xml found at: " + projectPath);
        }

        ProjectInfo projectInfo = new ProjectInfo();
        projectInfo.setProjectPath(projectPath.toString());

        // Parse POM file
        parsePomFile(pomPath.toFile(), projectInfo);

        // Find source files
        List<String> sourceFiles = findSourceFiles(projectPath);
        projectInfo.setSourceFiles(sourceFiles);

        logger.info("Successfully analyzed project: {}", projectInfo);
        return projectInfo;
    }

    /**
     * Parses the pom.xml file to extract project information.
     *
     * @param pomFile     The pom.xml file
     * @param projectInfo The ProjectInfo object to populate
     * @throws Exception if there's an error parsing the POM
     */
    private void parsePomFile(File pomFile, ProjectInfo projectInfo) throws Exception {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model;

        try (FileReader fileReader = new FileReader(pomFile)) {
            model = reader.read(fileReader);
        }

        // Extract basic project information
        projectInfo.setGroupId(model.getGroupId());
        projectInfo.setArtifactId(model.getArtifactId());
        projectInfo.setVersion(model.getVersion());
        projectInfo.setPackaging(model.getPackaging() != null ? model.getPackaging() : DEFAULT_PACKAGING);

        // Extract dependencies
        List<String> dependencies = extractDependencies(model);
        projectInfo.setDependencies(dependencies);

        // Extract and categorize service and function dependencies
        extractServiceAndFunctionDependencies(model, projectInfo);
    }

    /**
     * Extracts dependency information from the Maven Model.
     *
     * @param model The Maven Model
     * @return List of dependency strings
     */
    private List<String> extractDependencies(Model model) {
        return parseDependencies(model).stream()
                .map(DependencyInfo::toDepString)
                .collect(Collectors.toList());
    }

    /**
     * Parses all dependencies from the Maven Model.
     *
     * @param model The Maven Model
     * @return List of parsed DependencyInfo objects
     */
    private List<DependencyInfo> parseDependencies(Model model) {
        List<DependencyInfo> dependencies = new ArrayList<>();

        if (model.getDependencies() != null) {
            for (Dependency dependency : model.getDependencies()) {
                String groupId = dependency.getGroupId();
                String artifactId = dependency.getArtifactId();
                String version = dependency.getVersion();

                if (groupId != null && artifactId != null) {
                    dependencies.add(new DependencyInfo(groupId, artifactId, version));
                }
            }
        }

        return dependencies;
    }

    /**
     * Extracts and categorizes dependencies from SERVICE_GROUP_ID and FUNCTION_GROUP_ID groups.
     *
     * @param model       The Maven Model
     * @param projectInfo The ProjectInfo object to populate
     */
    private void extractServiceAndFunctionDependencies(Model model, ProjectInfo projectInfo) {
        List<DependencyInfo> allDependencies = parseDependencies(model);

        List<String> serviceDependencies = filterDependenciesByGroup(allDependencies, AnalyzerConstants.SERVICE_GROUP_ID, DEPENDENCY_TYPE_SERVICE);
        List<String> functionDependencies = filterDependenciesByGroup(allDependencies, AnalyzerConstants.FUNCTION_GROUP_ID, DEPENDENCY_TYPE_FUNCTION);

        projectInfo.setServiceDependencies(serviceDependencies);
        projectInfo.setFunctionDependencies(functionDependencies);

        if (!serviceDependencies.isEmpty() || !functionDependencies.isEmpty()) {
            logger.info("Found {} service dependencies and {} function dependencies",
                    serviceDependencies.size(), functionDependencies.size());
        }
    }

    /**
     * Filters dependencies by group ID and logs them.
     *
     * @param dependencies The list of all dependencies
     * @param groupId      The group ID to filter by
     * @param type         The type of dependency (for logging)
     * @return List of filtered dependency strings
     */
    private List<String> filterDependenciesByGroup(List<DependencyInfo> dependencies, String groupId, String type) {
        return dependencies.stream()
                .filter(dep -> groupId.equals(dep.groupId))
                .peek(dep -> logger.debug("Found {} dependency: {}", type, dep.toDepString()))
                .map(DependencyInfo::toDepString)
                .collect(Collectors.toList());
    }

    /**
     * Finds all Java source files in the project.
     *
     * @param projectPath The path to the project
     * @return List of source file paths
     * @throws Exception if there's an error finding files
     */
    private List<String> findSourceFiles(Path projectPath) throws Exception {
        Path srcPath = projectPath.resolve(SRC_MAIN_JAVA);
        if (!Files.exists(srcPath)) {
            logger.warn("Source directory not found: {}", srcPath);
            return new ArrayList<>();
        }

        try (Stream<Path> paths = Files.walk(srcPath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(JAVA_FILE_EXTENSION))
                    .map(p -> projectPath.relativize(p).toString())
                    .collect(Collectors.toList());
        }
    }

    /**
     * Helper class to represent a parsed dependency.
     */
    private record DependencyInfo(String groupId, String artifactId, String version) {

        String toDepString() {
            return groupId + COLON_SEPARATOR + artifactId + (version != null ? COLON_SEPARATOR + version : "");
        }
    }
}
