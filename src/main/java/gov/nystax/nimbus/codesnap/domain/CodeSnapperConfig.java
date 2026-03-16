package gov.nystax.nimbus.codesnap.domain;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

public final class CodeSnapperConfig {

    private final String serviceId;
    private final String commitHash;
    private final String branch;
    private final List<String> gitGroups;
    private final String gitToken;
    private final Path localTempRootPath;
    private final boolean useClasspath;
    private final Path mavenHome;
    private final Path mavenSettingsFile;
    private final List<MavenRepository> mavenRepositories;

    private CodeSnapperConfig(Builder builder) {
        this.serviceId = builder.serviceId;
        this.commitHash = builder.commitHash;
        this.branch = builder.branch;
        this.gitGroups = builder.gitGroups;
        this.gitToken = builder.gitToken;
        this.localTempRootPath = builder.localTempRootPath;
        this.useClasspath = builder.useClasspath;
        this.mavenHome = builder.mavenHome;
        this.mavenSettingsFile = builder.mavenSettingsFile;
        this.mavenRepositories = builder.mavenRepositories != null ? builder.mavenRepositories : List.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters (record-style naming optional)
    public String serviceId() {
        return serviceId;
    }

    public String commitHash() {
        return commitHash;
    }

    public String branch() {
        return branch;
    }

    public List<String> gitGroups() {
        return gitGroups;
    }

    public String gitToken() {
        return gitToken;
    }

    public Path localTempRootPath() {
        return localTempRootPath;
    }

    public boolean useClasspath() {
        return useClasspath;
    }

    /**
     * Path to the Maven installation directory (containing bin/mvn).
     * If null, assumes mvn is on the system PATH.
     */
    public Path mavenHome() {
        return mavenHome;
    }

    /**
     * Path to a Maven settings.xml file for repository configuration
     * (e.g., Artifactory credentials, mirror settings).
     * If null, Maven uses its default settings (~/.m2/settings.xml).
     */
    public Path mavenSettingsFile() {
        return mavenSettingsFile;
    }

    public List<MavenRepository> mavenRepositories() {
        return mavenRepositories;
    }

    /**
     * Represents a Maven repository configuration for downloading dependency JARs.
     * When provided, a settings.xml is generated on the fly for Maven invocation.
     */
    public record MavenRepository(String id, String url, String username, String password) {

        /**
         * Creates a Maven repository with no authentication.
         */
        public MavenRepository(String id, String url) {
            this(id, url, null, null);
        }

        public boolean hasCredentials() {
            return username != null && !username.isEmpty() && password != null && !password.isEmpty();
        }
    }

    public static final class Builder {
        private final static String DEFAULT_BRANCH_NAME = "main";
        private final Logger logger = LoggerFactory.getLogger(Builder.class);
        private String serviceId;
        private String commitHash;
        private String branch;
        private List<String> gitGroups;
        private String gitToken;
        private Path localTempRootPath;
        private boolean useClasspath;
        private Path mavenHome;
        private Path mavenSettingsFile;
        private List<MavenRepository> mavenRepositories;

        private Builder() {
        }

        public Builder serviceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public Builder commitHash(String commitHash) {
            this.commitHash = commitHash;
            return this;
        }

        public Builder branch(String branch) {
            this.branch = branch;
            return this;
        }

        public Builder gitGroups(List<String> gitGroups) {
            this.gitGroups = gitGroups;
            return this;
        }

        public Builder gitToken(String gitToken) {
            this.gitToken = gitToken;
            return this;
        }

        public Builder localTempRootPath(Path localTempRootPath) {
            this.localTempRootPath = localTempRootPath;
            return this;
        }

        public Builder useClasspath(boolean useClasspath) {
            this.useClasspath = useClasspath;
            return this;
        }

        public Builder mavenHome(Path mavenHome) {
            this.mavenHome = mavenHome;
            return this;
        }

        public Builder mavenSettingsFile(Path mavenSettingsFile) {
            this.mavenSettingsFile = mavenSettingsFile;
            return this;
        }

        public Builder mavenRepositories(List<MavenRepository> mavenRepositories) {
            this.mavenRepositories = mavenRepositories;
            return this;
        }

        public CodeSnapperConfig build() {
            List<String> validationErrors = Lists.newArrayList();
            String msg = null;
            // Required fields
            if (Strings.isNullOrEmpty(serviceId)) {
                msg = "Service Id is null or empty";
                logger.warn(msg);
                validationErrors.add(msg);
            }
            if (gitGroups == null || gitGroups.isEmpty()) {
                msg = "Git Groups are empty";
                logger.warn(msg);
                validationErrors.add(msg);
            }
            if (Strings.isNullOrEmpty(gitToken)) {
                msg = "Git Token is null or empty";
                logger.warn(msg);
                validationErrors.add(msg);
            }
            if (localTempRootPath == null) {
                msg = "Local Temp Root Path is null";
                logger.warn(msg);
                validationErrors.add(msg);
            }
            if (!validationErrors.isEmpty()) {
                throw new IllegalArgumentException(validationErrors.toString());
            }
            // optional fields
            if (Strings.isNullOrEmpty(branch)) {
                branch = DEFAULT_BRANCH_NAME;
                logger.warn("Branch is null or empty. Defaulting to {}", branch);
            }

            return new CodeSnapperConfig(this);
        }

    }
}