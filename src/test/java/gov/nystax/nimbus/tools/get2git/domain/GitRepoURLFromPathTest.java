package gov.nystax.nimbus.tools.get2git.domain;

import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitRepoURLFromPathTest {

    @Test
    void shouldParseStandardPath() throws MalformedURLException {
        GitRepoURL url = GitRepoURL.fromPath("ORG/java/services/product/service");

        assertThat(url.getGroups()).containsExactly("ORG", "java", "services", "product");
        assertThat(url.getRepoName()).isEqualTo("service");
    }

    @Test
    void shouldParseTwoSegmentPath() throws MalformedURLException {
        GitRepoURL url = GitRepoURL.fromPath("ORG/service");

        assertThat(url.getGroups()).containsExactly("ORG");
        assertThat(url.getRepoName()).isEqualTo("service");
    }

    @Test
    void shouldHandleTrailingSlash() throws MalformedURLException {
        GitRepoURL url = GitRepoURL.fromPath("ORG/java/services/product/service/");

        assertThat(url.getGroups()).containsExactly("ORG", "java", "services", "product");
        assertThat(url.getRepoName()).isEqualTo("service");
    }

    @Test
    void shouldHandleLeadingSlash() throws MalformedURLException {
        GitRepoURL url = GitRepoURL.fromPath("/ORG/java/services/product/service");

        assertThat(url.getGroups()).containsExactly("ORG", "java", "services", "product");
        assertThat(url.getRepoName()).isEqualTo("service");
    }

    @Test
    void shouldHandleConsecutiveSlashes() throws MalformedURLException {
        GitRepoURL url = GitRepoURL.fromPath("ORG//java///services/product/service");

        assertThat(url.getGroups()).containsExactly("ORG", "java", "services", "product");
        assertThat(url.getRepoName()).isEqualTo("service");
    }

    @Test
    void shouldBuildCorrectPathWithNamespace() throws MalformedURLException {
        GitRepoURL url = GitRepoURL.fromPath("ORG/java/services/product/service");

        assertThat(url.getPathWithNamespace()).isEqualTo("ORG/java/services/product/service");
        assertThat(url.getGroupPathWithNamespace()).isEqualTo("ORG/java/services/product/");
    }

    @Test
    void shouldBuildCorrectRepoURL() throws MalformedURLException {
        GitRepoURL url = GitRepoURL.fromPath("ORG/java/services/product/service");

        assertThat(url.getRepoURL()).isEqualTo("https://gitlab.com/ORG/java/services/product/service.git");
        assertThat(url.getRepoSshURL()).isEqualTo("git@gitlab.com:ORG/java/services/product/service.git");
    }

    @Test
    void shouldUseCustomBaseURL() throws MalformedURLException {
        GitRepoURL url = GitRepoURL.fromPath(
                "ORG/java/services/product/service",
                Optional.of("https://git.hospital.org"));

        assertThat(url.getRepoURL()).isEqualTo("https://git.hospital.org/ORG/java/services/product/service.git");
        assertThat(url.getRepoSshURL()).isEqualTo("git@git.hospital.org:ORG/java/services/product/service.git");
    }

    @Test
    void shouldUseDefaultBaseURLWhenEmpty() throws MalformedURLException {
        GitRepoURL url = GitRepoURL.fromPath("ORG/service", Optional.empty());

        assertThat(url.getBaseURL()).isEqualTo("https://gitlab.com");
    }

    @Test
    void shouldRejectNullPath() {
        assertThatThrownBy(() -> GitRepoURL.fromPath(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectEmptyPath() {
        assertThatThrownBy(() -> GitRepoURL.fromPath(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectSingleSegment() {
        assertThatThrownBy(() -> GitRepoURL.fromPath("service"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least a group and a repository name");
    }

    @Test
    void shouldRejectPathWithOnlySlashes() {
        assertThatThrownBy(() -> GitRepoURL.fromPath("/"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GitRepoURL.fromPath("//"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GitRepoURL.fromPath("///"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
