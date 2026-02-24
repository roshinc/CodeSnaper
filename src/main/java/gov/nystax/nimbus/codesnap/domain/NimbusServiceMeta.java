package gov.nystax.nimbus.codesnap.domain;

import gov.nystax.nimbus.tools.get2git.domain.GitRepoURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.Optional;

public class NimbusServiceMeta {
    private final String serviceId;
    private final Path localServiceRootPath;
    private final Path localServicePomPath;
    private final GitRepoURL gitRepoURL;

    private final Logger logger = LoggerFactory.getLogger(NimbusServiceMeta.class);

    public NimbusServiceMeta(CodeSnapperConfig config) {
        this.serviceId = config.serviceId();
        this.localServiceRootPath = createServiceRootPath(this.serviceId,
                config.commitHash(), config.localTempRootPath());
        this.localServicePomPath = createServicePomPath(this.localServiceRootPath, this.serviceId);

        try {
            gitRepoURL = new GitRepoURL(Optional.empty(), config.gitGroups(), this.serviceId);
        } catch (MalformedURLException e) {
            logger.error("GitRepoURL malformed exception", e);
            throw new RuntimeException(e);
        }
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

    private Path createServicePomPath(Path serviceRoot, String repoName) {
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
