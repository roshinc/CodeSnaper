package gov.nystax.nimbus.codesnap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeSnaperTest {

    @Test
    void getVersion_returnsNonNullValue() {
        String version = CodeSnaper.getVersion();
        assertThat(version).isNotNull();
    }
}
