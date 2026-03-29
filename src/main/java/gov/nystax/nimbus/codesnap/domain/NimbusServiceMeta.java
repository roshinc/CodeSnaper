package gov.nystax.nimbus.codesnap.domain;

import gov.nystax.nimbus.codesnap.exception.ProcessingException;
import gov.nystax.nimbus.tools.get2git.domain.GitRepoURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class NimbusServiceMeta {
    private final String serviceId;
    private final Path localServiceRootPath;
    private final Path localServicePomPath;
    private final GitRepoURL gitRepoURL;

    private final Logger logger = LoggerFactory.getLogger(NimbusServiceMeta.class);

    public NimbusServiceMeta(CodeSnapperConfig config) {
        this(config, Optional.empty());
    }

    NimbusServiceMeta(CodeSnapperConfig config, Optional<String> gitBaseUrl) {
        this.serviceId = config.serviceId();
        this.localServiceRootPath = createServiceRootPath(this.serviceId,
                config.commitHash(), config.localTempRootPath());
        this.localServicePomPath = createServicePomPath(this.localServiceRootPath, this.serviceId,
                config.flatProjectStructure());

        try {
            gitRepoURL = createGitRepoURL(gitBaseUrl, config.gitGroups(), this.serviceId);
        } catch (MalformedURLException e) {
            logger.error("GitRepoURL malformed exception", e);
            throw new ProcessingException("Invalid Git repository metadata", e);
        }
    }

    GitRepoURL createGitRepoURL(Optional<String> gitBaseUrl, List<String> gitGroups, String serviceId)
            throws MalformedURLException {
        return new GitRepoURL(gitBaseUrl, gitGroups, serviceId);
    }

    public String getServiceId() {
        return serviceId;
    }

    public Path getLocalServiceRootPath() {
        return localServiceRootPath;
    }

    public Path getLocalServicePomPath() {
        return localServicePomPath;
    }

    public GitRepoURL getGitRepoURL() {
        return gitRepoURL;
    }

    private Path createServiceRootPath(String repoName, String commitHash, Path localPath) {
        return localPath.resolve(commitHash).resolve(repoName);
    }

    private Path createServicePomPath(Path serviceRoot, String repoName, boolean flatProjectStructure) {
        if (flatProjectStructure) {
            return serviceRoot.resolve("pom.xml");
        }
        return serviceRoot.resolve(repoName).resolve("pom.xml");
    }

    @Override
    public String toString() {
        return "NimbusServiceMeta{" +
                "serviceId='" + serviceId + '\'' +
                ", localServiceRootPath=" + localServiceRootPath +
                ", localServicePomPath=" + localServicePomPath +
                ", gitRepoURL=" + gitRepoURL +
                '}';
    }
}
