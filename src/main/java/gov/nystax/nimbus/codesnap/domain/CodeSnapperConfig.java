package gov.nystax.nimbus.codesnap.domain;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import gov.nystax.nimbus.codesnap.exception.ProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CodeSnapperConfig {

    private final String serviceId;
    private final String commitHash;
    private final String branch;
    private final List<String> gitGroups;
    private final String gitToken;
    private final Path localTempRootPath;
    private final boolean lenientPairMatch;
    private final boolean inferImpl;
    private final boolean inferInterface;
    private final boolean resolveMavenClasspath;
    private final Path mavenSettingsXmlPath;
    private final Path mavenHomePath;

    private CodeSnapperConfig(Builder builder) {
        this.serviceId = builder.serviceId;
        this.commitHash = builder.commitHash;
        this.branch = builder.branch;
        this.gitGroups = builder.gitGroups;
        this.gitToken = builder.gitToken;
        this.localTempRootPath = builder.localTempRootPath;
        this.lenientPairMatch = builder.lenientPairMatch;
        this.inferImpl = builder.inferImpl;
        this.inferInterface = builder.inferInterface;
        this.resolveMavenClasspath = builder.resolveMavenClasspath;
        this.mavenSettingsXmlPath = builder.mavenSettingsXmlPath;
        this.mavenHomePath = builder.mavenHomePath;
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

    public boolean lenientPairMatch() {
        return lenientPairMatch;
    }

    public boolean inferImpl() {
        return inferImpl;
    }

    public boolean inferInterface() {
        return inferInterface;
    }

    public boolean resolveMavenClasspath() {
        return resolveMavenClasspath;
    }

    public Path mavenSettingsXmlPath() {
        return mavenSettingsXmlPath;
    }

    public Path mavenHomePath() {
        return mavenHomePath;
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
        private boolean lenientPairMatch;
        private boolean inferImpl;
        private boolean inferInterface;
        private boolean resolveMavenClasspath;
        private Path mavenSettingsXmlPath;
        private Path mavenHomePath;

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

        public Builder lenientPairMatch(boolean lenientPairMatch) {
            this.lenientPairMatch = lenientPairMatch;
            return this;
        }

        public Builder inferImpl(boolean inferImpl) {
            this.inferImpl = inferImpl;
            return this;
        }

        public Builder inferInterface(boolean inferInterface) {
            this.inferInterface = inferInterface;
            return this;
        }

        public Builder resolveMavenClasspath(boolean resolveMavenClasspath) {
            this.resolveMavenClasspath = resolveMavenClasspath;
            return this;
        }

        public Builder mavenSettingsXmlPath(Path mavenSettingsXmlPath) {
            this.mavenSettingsXmlPath = mavenSettingsXmlPath;
            return this;
        }

        public Builder mavenHomePath(Path mavenHomePath) {
            this.mavenHomePath = mavenHomePath;
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
            if (resolveMavenClasspath && mavenSettingsXmlPath == null) {
                msg = "Maven settings.xml path is required when resolveMavenClasspath is enabled";
                logger.warn(msg);
                validationErrors.add(msg);
            }
            if (resolveMavenClasspath && mavenSettingsXmlPath != null && !Files.isRegularFile(mavenSettingsXmlPath)) {
                msg = "Maven settings.xml path does not exist: " + mavenSettingsXmlPath;
                logger.warn(msg);
                validationErrors.add(msg);
            }
            if (resolveMavenClasspath && mavenHomePath != null && !Files.isDirectory(mavenHomePath)) {
                msg = "Maven home path does not exist: " + mavenHomePath;
                logger.warn(msg);
                validationErrors.add(msg);
            }
            if (!validationErrors.isEmpty()) {
                throw new ProcessingException(validationErrors.toString());
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
