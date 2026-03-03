package gov.nystax.nimbus.tools.get2git.domain;

import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import org.apache.logging.log4j.util.Strings;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class GitRepoURL {

    private URL baseURL = null;
    private List<String> groups = null;
    private String repoName = null;
    private String repoURL = null;
    private String repoSshURL = null;
    private String pathWithNamespace = null;
    private String groupPathWithNamespace = null;

    public static final String DEFAULT_BASE_URL = "https://gitlab.com";
    private static final String REGEX = "^(([A-Z])|([a-z])|([0-9]))+((((-)|(_))?((([A-Z])|([a-z])|([0-9]))+))|(((([A-Z])|([a-z])|([0-9]))+((-)|(_))?)))*$";

    /**
     * Parses a slash-delimited path into a {@link GitRepoURL} using the default base URL.
     * All segments except the last are treated as groups; the last segment is the repository name.
     *
     * @param path slash-delimited path, e.g. {@code "ORG/java/services/product/service"}
     * @return a fully constructed {@link GitRepoURL}
     * @throws MalformedURLException if the base URL is malformed
     * @throws IllegalArgumentException if the path is null, empty, or contains fewer than two segments
     */
    public static GitRepoURL fromPath(String path) throws MalformedURLException {
        return fromPath(path, Optional.empty());
    }

    /**
     * Parses a slash-delimited path into a {@link GitRepoURL}.
     * All segments except the last are treated as groups; the last segment is the repository name.
     *
     * @param path    slash-delimited path, e.g. {@code "ORG/java/services/product/service"}
     * @param baseURL optional base URL; defaults to {@link #DEFAULT_BASE_URL} when empty
     * @return a fully constructed {@link GitRepoURL}
     * @throws MalformedURLException    if the base URL is malformed
     * @throws IllegalArgumentException if the path is null, empty, or contains fewer than two segments
     */
    public static GitRepoURL fromPath(String path, Optional<String> baseURL) throws MalformedURLException {
        Preconditions.checkArgument(Strings.isNotEmpty(path), "Path must not be null or empty");

        List<String> segments = Arrays.stream(path.split("/"))
                .filter(Strings::isNotEmpty)
                .toList();

        Preconditions.checkArgument(segments.size() >= 2,
                "Path must contain at least a group and a repository name, got: %s", path);

        List<String> groups = segments.subList(0, segments.size() - 1);
        String repoName = segments.get(segments.size() - 1);

        return new GitRepoURL(baseURL, groups, repoName);
    }

    public GitRepoURL(Optional<String> baseURL, List<String> groups, String repoName) throws MalformedURLException {
        super();
        if (baseURL.isPresent()) {
            setBaseURL(baseURL.get());
        } else {
            setBaseURL(DEFAULT_BASE_URL);
        }
        setGroups(groups);
        setRepoName(repoName);

        createURLs();
    }

    public String getBaseURL() {
        return baseURL.toString();
    }

    public void setBaseURL(String baseURL) throws MalformedURLException {
        this.baseURL = new URL(baseURL);
    }

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

    public String getRepoName() {
        return repoName;
    }

    public void setRepoName(String repoName) {
        Preconditions.checkArgument(repoName.matches(REGEX), "Repo name not valid");
        this.repoName = repoName;
    }

    public String getRepoURL() {
        return repoURL;
    }

    public String getRepoSshURL() {
        return repoSshURL;
    }

    public String getPathWithNamespace() {
        return pathWithNamespace;
    }

    public String getGroupPathWithNamespace() {
        return groupPathWithNamespace;
    }

    private void createURLs() {
        Verify.verify(Strings.isNotEmpty(getBaseURL()), "Base URL is required");
        Verify.verifyNotNull(getGroups(), "At least one group is needed");
        Verify.verify(!getGroups().isEmpty(), "At least one group is needed");
        Verify.verify(Strings.isNotEmpty(getBaseURL()), "Base URL is required");
        Verify.verify(Strings.isNotEmpty(getRepoName()), "Repo name is required");

        StringBuilder groupPathWithNamespaceBuilder = new StringBuilder();
        getGroups().forEach(group -> {
            groupPathWithNamespaceBuilder.append(group);
            groupPathWithNamespaceBuilder.append("/");
        });
        this.groupPathWithNamespace = groupPathWithNamespaceBuilder.toString();

        StringBuilder pathWithNamespaceBuilder = new StringBuilder(groupPathWithNamespaceBuilder);
        pathWithNamespaceBuilder.append(this.repoName);
        this.pathWithNamespace = pathWithNamespaceBuilder.toString();

        StringBuilder repoURLBuilder = new StringBuilder(this.getBaseURL());
        repoURLBuilder.append("/");
        repoURLBuilder.append(pathWithNamespaceBuilder);
        repoURLBuilder.append(".git");
        this.repoURL = repoURLBuilder.toString();

        StringBuilder repoSSHURLBuilder = new StringBuilder("git@");
        repoSSHURLBuilder.append(baseURL.getAuthority());
        repoSSHURLBuilder.append(":");
        repoSSHURLBuilder.append(pathWithNamespaceBuilder);
        repoSSHURLBuilder.append(".git");
        this.repoSshURL = repoSSHURLBuilder.toString();
    }

    @Override
    public String toString() {
        return "GitRepoURL{" +
                "baseURL=" + baseURL +
                ", groups=" + groups +
                ", repoName='" + repoName + '\'' +
                ", repoURL='" + repoURL + '\'' +
                ", repoSshURL='" + repoSshURL + '\'' +
                ", pathWithNamespace='" + pathWithNamespace + '\'' +
                ", groupPathWithNamespace='" + groupPathWithNamespace + '\'' +
                '}';
    }
}
