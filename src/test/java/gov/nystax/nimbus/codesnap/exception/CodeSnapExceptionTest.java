package gov.nystax.nimbus.codesnap.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeSnapExceptionTest {

    @Test
    void cloneException_hasCategoryCloneError() {
        CloneException ex = new CloneException("clone failed");
        assertThat(ex.getCategory()).isEqualTo(CodeSnapErrorCategory.CLONE_ERROR);
        assertThat(ex.is(CodeSnapErrorCategory.CLONE_ERROR)).isTrue();
        assertThat(ex.is(CodeSnapErrorCategory.SCAN_ERROR)).isFalse();
        assertThat(ex.getMessage()).isEqualTo("clone failed");
        assertThat(ex).isInstanceOf(CodeSnapException.class);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void scanException_hasCategoryScanError() {
        ScanException ex = new ScanException("scan failed");
        assertThat(ex.getCategory()).isEqualTo(CodeSnapErrorCategory.SCAN_ERROR);
        assertThat(ex.is(CodeSnapErrorCategory.SCAN_ERROR)).isTrue();
    }

    @Test
    void parseException_hasCategoryParseError() {
        ParseException ex = new ParseException("parse failed");
        assertThat(ex.getCategory()).isEqualTo(CodeSnapErrorCategory.PARSE_ERROR);
        assertThat(ex.is(CodeSnapErrorCategory.PARSE_ERROR)).isTrue();
    }

    @Test
    void codeViolationException_hasCategoryCodeViolation() {
        CodeViolationException ex = new CodeViolationException("violation");
        assertThat(ex.getCategory()).isEqualTo(CodeSnapErrorCategory.CODE_VIOLATION);
        assertThat(ex.is(CodeSnapErrorCategory.CODE_VIOLATION)).isTrue();
    }

    @Test
    void processingException_hasCategoryProcessingError() {
        ProcessingException ex = new ProcessingException("processing failed");
        assertThat(ex.getCategory()).isEqualTo(CodeSnapErrorCategory.PROCESSING_ERROR);
        assertThat(ex.is(CodeSnapErrorCategory.PROCESSING_ERROR)).isTrue();
    }

    @Test
    void processingException_preservesOriginalCause() {
        RuntimeException cause = new RuntimeException("root cause");
        ProcessingException ex = new ProcessingException("processing failed", cause);
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getCategory()).isEqualTo(CodeSnapErrorCategory.PROCESSING_ERROR);
    }

    @Test
    void causeChaining_preservesOriginalCause() {
        RuntimeException cause = new RuntimeException("root cause");
        CloneException ex = new CloneException("clone failed", cause);
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getMessage()).isEqualTo("clone failed");
    }

    @Test
    void is_returnsExactMatchOnly() {
        CodeViolationException ex = new CodeViolationException("violation");
        assertThat(ex.is(CodeSnapErrorCategory.CODE_VIOLATION)).isTrue();
        assertThat(ex.is(CodeSnapErrorCategory.SCAN_ERROR)).isFalse();
        assertThat(ex.is(CodeSnapErrorCategory.CLONE_ERROR)).isFalse();
        assertThat(ex.is(CodeSnapErrorCategory.PARSE_ERROR)).isFalse();
        assertThat(ex.is(CodeSnapErrorCategory.PROCESSING_ERROR)).isFalse();
    }
}
