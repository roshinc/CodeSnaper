package gov.nystax.nimbus.codesnap.exception;

public class CodeViolationException extends CodeSnapException {

    public CodeViolationException(String message) {
        super(CodeSnapErrorCategory.CODE_VIOLATION, message);
    }

    public CodeViolationException(String message, Throwable cause) {
        super(CodeSnapErrorCategory.CODE_VIOLATION, message, cause);
    }
}
